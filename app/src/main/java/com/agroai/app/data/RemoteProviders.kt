package com.agroai.app.data

import com.agroai.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID

class BackendApi(private val baseUrl: String, private val token: suspend () -> String?) {
    val configured: Boolean get() = baseUrl.isNotBlank()

    suspend fun chat(question: String, context: FarmerContext, language: String, history: List<ChatMessage>): String = post(
        "/v1/chat",
        JSONObject().put("question", question).put("language", language).put("history", JSONArray(history.takeLast(10).map {
            JSONObject().put("role", if (it.role == "farmer") "farmer" else "assistant").put("text", it.text.take(1200))
        })).put("farm_context", JSONObject()
            .put("plots", JSONArray(context.plots.take(12).map { "${it.name}: ${it.area} ${it.areaUnit}, ${it.soilType}" }))
            .put("plans", JSONArray(context.plans.take(12).map { "${it.cropId}: ${it.status}, sowing ${it.sowingDate}" }))
            .put("upcoming_activities", JSONArray(context.activities.take(12).map { "${it.title}: ${it.scheduledAt}" }))
            .put("recent_harvests", JSONArray(context.harvests.take(6).map { "${it.actualHarvestDate}: ${it.quantity} ${it.unit}" }))),
    ).getString("text")

    suspend fun analyze(request: CropDiagnosisRequest): DiagnosisResult {
        val image = requireNotNull(request.image)
        val json = post("/v1/crop-analysis", JSONObject()
            .put("image_base64", image.base64).put("mime_type", image.mimeType)
            .put("crop_name", request.cropName).put("symptoms", JSONArray(request.symptoms.toList()))
            .put("affected_parts", JSONArray(request.affectedParts.toList()))
            .put("observations", request.observations).put("language", request.language))
        return DiagnosisResult(
            possibleCauses = json.strings("possible_causes"),
            evidenceNeeded = json.strings("evidence_needed") + json.strings("supporting_observations"),
            nextSteps = json.strings("safe_actions") + listOf(json.optString("expert_recommendation")).filter(String::isNotBlank),
            uncertainty = json.optString("uncertainty", "Image evidence is uncertain."),
            imageModelUsed = true,
        )
    }

    suspend fun mandi(commodity: String, state: String, district: String): List<MarketPriceRecord> {
        val query = "commodity=${commodity.encoded()}&state=${state.encoded()}&district=${district.encoded()}"
        val json = request("GET", "/v1/mandi?$query", null)
        val savedAt = json.optLong("fetched_at", System.currentTimeMillis())
        return json.getJSONArray("records").objects().mapNotNull { item ->
            runCatching {
                MarketPriceRecord(
                    id = "mandi-${UUID.nameUUIDFromBytes(item.toString().toByteArray())}",
                    crop = item.getString("commodity"), state = item.getString("state"), district = item.getString("district"),
                    market = item.getString("market"), minPricePerQuintal = item.getDouble("min_price"),
                    maxPricePerQuintal = item.getDouble("max_price"), modalPricePerQuintal = item.getDouble("modal_price"),
                    dataDate = LocalDate.parse(item.getString("arrival_date")), source = json.optString("source", "AGMARKNET via data.gov.in"), savedAt = savedAt,
                )
            }.getOrNull()
        }
    }

    private suspend fun post(path: String, body: JSONObject) = request("POST", path, body)

    private suspend fun request(method: String, path: String, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        val idToken = token()?.takeIf(String::isNotBlank) ?: error("Sign in is required for live data")
        val connection = URI(baseUrl.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("Authorization", "Bearer $idToken")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error(JSONObject(response.ifBlank { "{}" }).optString("detail", "Service unavailable"))
            require(response.length <= 1_000_000) { "Provider response is too large" }
            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}

class HybridAiAssistantProvider(
    private val local: AiAssistantProvider,
    private val backend: BackendApi,
) : AiAssistantProvider {
    override suspend fun answer(question: String, context: FarmerContext, language: String, history: List<ChatMessage>): AssistantReply {
        val localReply = local.answer(question, context, language, history)
        if (!localReply.remoteEligible || !backend.configured) return localReply
        return runCatching { AssistantReply(backend.chat(question, context, language, history)) }.getOrElse {
            localReply.copy(text = if (language == "hi") "कनेक्टेड AI अभी उपलब्ध नहीं है। ${localReply.text}" else "Connected AI is temporarily unavailable. ${localReply.text}")
        }
    }
}

class HybridCropDiagnosisProvider(
    private val local: CropDiagnosisProvider,
    private val backend: BackendApi,
) : CropDiagnosisProvider {
    override suspend fun assess(request: CropDiagnosisRequest): DiagnosisResult {
        if (request.image == null || !backend.configured) return local.assess(request)
        return runCatching { backend.analyze(request) }.getOrElse { local.assess(request) }
    }
}

class BackendMarketPriceProvider(private val backend: BackendApi) : MarketPriceRefreshProvider {
    override suspend fun fetch(commodity: String, state: String, district: String) = backend.mandi(commodity, state, district)
}

class OpenMeteoWeatherProvider : WeatherRefreshProvider {
    override suspend fun fetch(location: String): List<WeatherSnapshot> = withContext(Dispatchers.IO) {
        require(location.trim().length in 2..100)
        val place = getJson("https://geocoding-api.open-meteo.com/v1/search?name=${location.trim().encoded()}&count=1&language=en&format=json")
            .optJSONArray("results")?.optJSONObject(0) ?: error("Location not found")
        val name = listOf(place.optString("name"), place.optString("admin1")).filter(String::isNotBlank).joinToString(", ")
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.getDouble("latitude")}&longitude=${place.getDouble("longitude")}" +
            "&hourly=temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m&forecast_days=3&timeformat=unixtime&timezone=auto"
        val hourly = getJson(url).getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val now = System.currentTimeMillis()
        (0 until times.length()).filter { it % 3 == 0 && times.getLong(it) * 1000 >= now - 3_600_000 }.take(17).map { index ->
            val forecastFor = times.getLong(index) * 1000
            WeatherSnapshot(
                id = "weather-${place.getDouble("latitude")}-${place.getDouble("longitude")}-$forecastFor",
                location = name, observedAt = now, forecastFor = forecastFor,
                temperatureC = hourly.getJSONArray("temperature_2m").getDouble(index),
                humidityPercent = hourly.getJSONArray("relative_humidity_2m").getDouble(index),
                windKph = hourly.getJSONArray("wind_speed_10m").getDouble(index),
                rainMillimetres = hourly.getJSONArray("precipitation").getDouble(index),
                condition = weatherCondition(hourly.getJSONArray("weather_code").getInt(index)),
                source = "Open-Meteo.com — CC BY 4.0",
            )
        }
    }

    private fun getJson(url: String): JSONObject {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            require(response.length <= 1_000_000)
            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun weatherCondition(code: Int) = when (code) {
        0 -> "Clear"
        1, 2, 3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..67, in 80..82 -> "Rain"
        in 71..77, in 85..86 -> "Snow"
        in 95..99 -> "Thunderstorm"
        else -> "Forecast"
    }
}

private fun String.encoded() = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
private fun JSONObject.strings(name: String): List<String> = optJSONArray(name)?.let { array ->
    (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
}.orEmpty()
private fun JSONArray.objects() = (0 until length()).mapNotNull(::optJSONObject)
