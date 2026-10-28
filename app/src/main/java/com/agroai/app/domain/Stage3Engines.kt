package com.agroai.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object SpeechStateMachine {
    fun next(current: SpeechState, event: SpeechEvent): SpeechState = when (event) {
        SpeechEvent.StartListening -> SpeechState.Listening
        SpeechEvent.RecognitionReady -> if (current == SpeechState.Listening) SpeechState.Processing else current
        SpeechEvent.RecognitionComplete, SpeechEvent.Stop -> SpeechState.Idle
        SpeechEvent.StartSpeaking -> SpeechState.Speaking
        SpeechEvent.Fail -> SpeechState.Error
    }
}

class LocalAgronomyKnowledgeProvider : AgronomyKnowledgeProvider {
    override fun crop(idOrName: String) = CropCatalog.crops.firstOrNull {
        it.id.equals(idOrName, true) || it.nameEn.equals(idOrName, true) || it.nameHi == idOrName
    }

    override fun recommendations(season: String, soil: String, irrigation: String) =
        CropRecommendationEngine.recommend(season, soil, irrigation)
}

class LocalAiAssistantProvider(
    private val agronomy: AgronomyKnowledgeProvider = LocalAgronomyKnowledgeProvider(),
    private val today: () -> LocalDate = LocalDate::now,
) : AiAssistantProvider {
    override suspend fun answer(question: String, context: FarmerContext, language: String, history: List<ChatMessage>): AssistantReply {
        val normalized = question.lowercase().trim()
        val hindi = language == "hi"
        if (normalized.isBlank()) return AssistantReply(if (hindi) "कृपया प्रश्न लिखें या बोलें।" else "Please type or speak a question.")

        if (containsAny(normalized, "today", "आज", "scheduled", "कार्य", "activities")) {
            val zone = ZoneId.systemDefault()
            val activities = context.activities.filter { Instant.ofEpochMilli(it.scheduledAt).atZone(zone).toLocalDate() == today() }
            return if (activities.isEmpty()) AssistantReply(if (hindi) "आज कोई सेव किया हुआ खेत कार्य नहीं है।" else "There are no saved farm activities for today.")
            else AssistantReply(
                if (hindi) "आज ${activities.size} खेत कार्य सेव हैं।" else "You have ${activities.size} saved farm activities today.",
                if (hindi) "आज के कार्य" else "Today's activities",
                activities.map { it.title },
            )
        }

        if (containsAny(normalized, "plot", "plots", "farm", "खेत", "प्लॉट")) {
            return if (context.plots.isEmpty()) AssistantReply(if (hindi) "अभी कोई खेत प्लॉट सेव नहीं है।" else "No farm plots are saved yet.")
            else AssistantReply(
                if (hindi) "आपके ${context.plots.size} खेत प्लॉट सेव हैं।" else "You have ${context.plots.size} saved farm plots.",
                if (hindi) "सेव खेत" else "Saved plots",
                context.plots.map { "${it.name}: ${it.area.toString().removeSuffix(".0")} ${it.areaUnit}" },
            )
        }

        if (containsAny(normalized, "plan", "योजना")) {
            val plan = context.plans.firstOrNull { it.status == "active" } ?: context.plans.firstOrNull()
            return if (plan == null) AssistantReply(if (hindi) "अभी कोई फसल योजना सेव नहीं है।" else "No crop plan is saved yet.")
            else {
                val crop = agronomy.crop(plan.cropId)
                AssistantReply(
                    if (hindi) "${crop?.nameHi ?: plan.cropId} योजना ${plan.status} है।" else "Your ${crop?.nameEn ?: plan.cropId} plan is ${plan.status}.",
                    if (hindi) "फसल योजना" else "Crop plan",
                    listOf("${plan.sowingDate}", "${plan.harvestStart} – ${plan.harvestEnd}"),
                )
            }
        }

        if (containsAny(normalized, "seed", "बीज")) {
            val amount = Regex("(\\d+(?:\\.\\d+)?)").find(normalized)?.value?.toDoubleOrNull()
            val crop = CropCatalog.crops.firstOrNull { normalized.contains(it.id) || normalized.contains(it.nameEn.lowercase()) || normalized.contains(it.nameHi) }
            val unit = when {
                containsAny(normalized, "hectare", "hectares", "हेक्टेयर") -> "hectare"
                containsAny(normalized, "acre", "acres", "एकड़") -> "acre"
                else -> null
            }
            if (amount != null && amount > 0 && crop != null && unit != null) {
                val hectares = FarmCalculators.toHectares(amount, unit)
                val low = FarmCalculators.seedQuantity(hectares, crop.seedRateKgPerHectare.start)
                val high = FarmCalculators.seedQuantity(hectares, crop.seedRateKgPerHectare.endInclusive)
                return AssistantReply(
                    if (hindi) "संदर्भ सीमा लगभग %.1f–%.1f किग्रा है। स्थानीय किस्म और बुवाई विधि से जरूरत बदल सकती है।".format(low, high)
                    else "The reference range is about %.1f–%.1f kg. Variety and sowing method can change the requirement.".format(low, high),
                    if (hindi) "बीज गणना" else "Seed calculation",
                    listOf("${crop.nameEn}: ${crop.seedRateKgPerHectare.start.toInt()}–${crop.seedRateKgPerHectare.endInclusive.toInt()} kg/ha"),
                )
            }
            return AssistantReply(if (hindi) "फसल, क्षेत्र और एकड़ या हेक्टेयर लिखें—जैसे: 2 एकड़ गेहूँ के लिए कितना बीज?" else "Include a crop, area, and acre or hectare—for example: How much wheat seed for 2 acres?")
        }

        if (containsAny(normalized, "irrigate", "irrigation", "water my", "सिंचाई", "पानी कब")) {
            val crop = CropCatalog.crops.firstOrNull { normalized.contains(it.id) || normalized.contains(it.nameEn.lowercase()) || normalized.contains(it.nameHi) }
            val planIds = context.plans.filter { crop == null || it.cropId == crop.id }.mapTo(mutableSetOf(), CropPlan::id)
            val next = context.activities.filter { it.planId in planIds && it.type == "irrigation" && it.scheduledAt >= System.currentTimeMillis() }.minByOrNull(CalendarActivity::scheduledAt)
            return if (next != null) {
                val date = Instant.ofEpochMilli(next.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate()
                AssistantReply(if (hindi) "सेव योजना में मिट्टी की नमी जाँच $date के लिए तय है। खेत की हालत देखकर ही सिंचाई करें।" else "Your saved plan schedules a soil-moisture check for $date. Irrigate only after checking field conditions.")
            } else AssistantReply(if (hindi) "कोई सिंचाई तारीख सेव नहीं है। मिट्टी की नमी और फसल चरण जाँचें तथा स्थानीय KVK से पुष्टि करें।" else "No irrigation date is saved. Check soil moisture and crop stage, and confirm with your local KVK.")
        }

        if (containsAny(normalized, "yellow leaves", "yellowing", "पीले पत्ते", "पत्ते पीले")) {
            return AssistantReply(if (hindi) "पीले पत्तों के कई कारण हो सकते हैं। फसल डॉक्टर में दिखने वाले लक्षण दर्ज करें; फोटो का स्वचालित निदान नहीं होगा।" else "Yellow leaves can have several causes. Record the visible symptoms in Crop Doctor; the photo will not be automatically diagnosed.")
        }

        if (containsAny(normalized, "which crop", "grow this season", "कौन सी फसल", "इस मौसम")) {
            val plot = context.plots.firstOrNull() ?: return AssistantReply(if (hindi) "सुझाव के लिए पहले खेत प्लॉट सेव करें।" else "Save a farm plot before requesting a crop recommendation.")
            val season = when (today().monthValue) { in 6..9 -> "kharif"; in 10..12, in 1..3 -> "rabi"; else -> "zaid" }
            val matches = agronomy.recommendations(season, plot.soilType, plot.irrigationMethod).take(3)
            return if (matches.isEmpty()) AssistantReply(if (hindi) "सेव जानकारी से कोई समर्थित मिलान नहीं मिला। स्थानीय KVK से सलाह लें।" else "No supported match was found from the saved details. Consult your local KVK.")
            else AssistantReply(
                if (hindi) "यह स्थानीय नियम-आधारित मिलान है, AI पूर्वानुमान नहीं।" else "These are local rule-based matches, not an AI prediction.",
                if (hindi) "${season} मिलान" else "$season matches",
                matches.map { if (hindi) "${it.crop.nameHi}: ${it.score}/10" else "${it.crop.nameEn}: ${it.score}/10" },
            )
        }

        if (containsAny(normalized, "harvest", "कटाई", "उपज")) {
            val harvest = context.harvests.maxByOrNull { it.actualHarvestDate }
            return if (harvest == null) AssistantReply(if (hindi) "अभी कोई कटाई रिपोर्ट सेव नहीं है।" else "No harvest report is saved yet.")
            else AssistantReply(
                if (hindi) "नवीनतम कटाई ${harvest.actualHarvestDate} को दर्ज की गई थी।" else "The latest harvest was recorded on ${harvest.actualHarvestDate}.",
                if (hindi) "कटाई रिपोर्ट" else "Harvest report",
                listOf("${harvest.quantity.toString().removeSuffix(".0")} ${harvest.unit}", "Expenses: ₹${"%.2f".format(harvest.actualTotalExpenses)}"),
            )
        }

        return AssistantReply(
            if (hindi) "यह प्रश्न स्थानीय सहायक के समर्थित दायरे से बाहर है। कनेक्टेड AI उपलब्ध होने पर इसे भेजा जा सकता है; अन्यथा KVK या कृषि विशेषज्ञ से सलाह लें।"
            else "That question is outside the local assistant's supported scope. It can use connected AI when configured; otherwise consult a KVK or agricultural expert.",
            remoteEligible = true,
        )
    }

    private fun containsAny(value: String, vararg terms: String) = terms.any(value::contains)
}

class LocalCropDiagnosisProvider : CropDiagnosisProvider {
    override suspend fun assess(request: CropDiagnosisRequest): DiagnosisResult {
        val causes = request.symptoms.flatMap {
            when (it) {
                "yellowing" -> listOf("nutrient_or_water_stress")
                "brown_spots" -> listOf("leaf_injury_or_infection")
                "wilting" -> listOf("water_or_root_stress")
                "curling" -> listOf("pest_or_heat_stress")
                "insects" -> listOf("possible_pest_presence")
                "leaf_damage" -> listOf("feeding_or_physical_damage")
                else -> emptyList()
            }
        }.distinct()
        return DiagnosisResult(
            possibleCauses = causes.ifEmpty { listOf("insufficient_symptom_information") },
            evidenceNeeded = listOf("symptom_distribution", "soil_moisture", "close_leaf_views", "recent_field_changes"),
            nextSteps = listOf("record_spread", "inspect_both_leaf_sides", "avoid_unverified_treatment", "consult_local_expert"),
        )
    }
}

object WeatherAdvisoryEngine {
    fun evaluate(records: List<WeatherSnapshot>): List<WeatherAdvisory> = records.flatMap { record ->
        buildList {
            if (record.rainMillimetres >= 5) add(WeatherAdvisory("review_irrigation", record.id))
            if (record.windKph >= 25) add(WeatherAdvisory("review_spraying", record.id))
            if (record.temperatureC >= 40) add(WeatherAdvisory("monitor_heat_stress", record.id))
        }
    }
}

object MarketPriceAnalysis {
    fun compare(records: List<MarketPriceRecord>) = records.sortedByDescending { it.modalPricePerQuintal }
    fun estimatedGrossRevenue(quantityQuintals: Double, modalPricePerQuintal: Double): Double {
        require(quantityQuintals >= 0 && modalPricePerQuintal >= 0)
        return quantityQuintals * modalPricePerQuintal
    }
}

object HarvestAnalytics {
    fun compare(plan: CropPlan, harvest: HarvestRecord): HarvestComparison {
        require(harvest.planId == plan.id && harvest.quantity > 0 && harvest.actualTotalExpenses >= 0)
        val revenue = harvest.sellingPricePerUnit?.let { harvest.quantity * it }
        return HarvestComparison(
            actualRevenue = revenue,
            actualGrossMargin = revenue?.minus(harvest.actualTotalExpenses),
            harvestDateDifferenceDays = harvest.actualHarvestDate.toEpochDay() - plan.harvestStart.toEpochDay(),
            estimatedYieldAvailable = false,
            estimatedCostAvailable = false,
            expectedRevenueAvailable = false,
        )
    }
}

object AlertEngine {
    fun generate(activities: List<CalendarActivity>, plans: List<CropPlan>, harvests: List<HarvestRecord>, now: Long): List<FarmAlert> {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val harvestedPlanIds = harvests.mapTo(mutableSetOf(), HarvestRecord::planId)
        val activityAlerts = activities.filterNot { it.completed }.mapNotNull { activity ->
            val date = Instant.ofEpochMilli(activity.scheduledAt).atZone(zone).toLocalDate()
            val code = when {
                date == today -> "today"
                date.isBefore(today) -> "overdue"
                !date.isAfter(today.plusDays(3)) -> "upcoming"
                else -> null
            } ?: return@mapNotNull null
            alert("activity:${activity.id}:$code", activity.title, code, activity.planId, activity.plotId, if (code == "overdue") "high" else "normal", maxOf(now + 5_000, activity.scheduledAt), "calendar")
        }
        val harvestAlerts = plans.filter { it.status == "active" && it.id !in harvestedPlanIds && !it.harvestStart.isAfter(today.plusDays(14)) }.map { plan ->
            alert("harvest:${plan.id}", "Harvest preparation", "harvest_due", plan.id, plan.plotId, "high", now + 5_000, "harvest")
        }
        return (activityAlerts + harvestAlerts).distinctBy { it.sourceKey }
    }

    private fun alert(sourceKey: String, title: String, message: String, planId: String?, plotId: String?, priority: String, at: Long, action: String) =
        FarmAlert("alert-${sourceKey.hashCode()}", sourceKey, title, message, planId, plotId, priority, at, false, action)
}
