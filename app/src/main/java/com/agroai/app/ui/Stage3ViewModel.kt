package com.agroai.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.agroai.app.AppContainer
import com.agroai.app.data.DEMO_OWNER
import com.agroai.app.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class Stage3UiState(
    val messages: List<ChatMessage> = emptyList(),
    val inspections: List<CropInspection> = emptyList(),
    val harvests: List<HarvestRecord> = emptyList(),
    val expenses: List<ActualExpense> = emptyList(),
    val alerts: List<FarmAlert> = emptyList(),
    val weather: List<WeatherSnapshot> = emptyList(),
    val prices: List<MarketPriceRecord> = emptyList(),
    val plots: List<FarmPlot> = emptyList(),
    val plans: List<CropPlan> = emptyList(),
    val activities: List<CalendarActivity> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class Stage3ViewModel(private val container: AppContainer) : ViewModel() {
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val conversationId = "local-farm-assistant"

    val state: StateFlow<Stage3UiState> = combine(
        container.stage3Repository.messages(conversationId),
        container.stage3Repository.inspections,
        container.stage3Repository.harvests,
        container.stage3Repository.expenses,
        container.stage3Repository.alerts,
        container.stage3Repository.snapshots,
        container.stage3Repository.prices,
        container.farmRepository.plots,
        container.farmRepository.plans,
        container.farmRepository.activities,
        loading,
        error,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        Stage3UiState(
            messages = values[0] as List<ChatMessage>, inspections = values[1] as List<CropInspection>,
            harvests = values[2] as List<HarvestRecord>, expenses = values[3] as List<ActualExpense>,
            alerts = values[4] as List<FarmAlert>, weather = values[5] as List<WeatherSnapshot>,
            prices = values[6] as List<MarketPriceRecord>, plots = values[7] as List<FarmPlot>,
            plans = values[8] as List<CropPlan>, activities = values[9] as List<CalendarActivity>,
            loading = values[10] as Boolean, error = values[11] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Stage3UiState())

    init {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            container.stage3Repository.saveConversation(AiConversation(conversationId, "Farm assistant", now, now))
        }
        viewModelScope.launch {
            combine(container.farmRepository.activities, container.farmRepository.plans, container.stage3Repository.harvests) { activities, plans, harvests ->
                AlertEngine.generate(activities, plans, harvests, System.currentTimeMillis())
            }.collect { alerts ->
                if (alerts.isNotEmpty()) {
                    container.stage3Repository.saveAlerts(alerts).forEach(container.reminderScheduler::schedule)
                }
            }
        }
    }

    fun clearError() { error.value = null }

    fun send(question: String, language: String): Boolean {
        val clean = question.trim()
        if (clean.isEmpty() || loading.value) return false
        viewModelScope.launch {
            loading.value = true
            runCatching {
                val now = System.currentTimeMillis()
                container.stage3Repository.saveConversation(AiConversation(conversationId, clean.take(50), now, now))
                container.stage3Repository.saveMessage(ChatMessage(UUID.randomUUID().toString(), conversationId, "farmer", clean, now))
                val reply = container.assistantProvider.answer(clean, container.farmerContextProvider.current(), language, state.value.messages.takeLast(10))
                container.stage3Repository.saveMessage(ChatMessage(UUID.randomUUID().toString(), conversationId, "assistant", reply.text, now + 1, reply.cardTitle, reply.cardLines))
                container.stage3Repository.saveConversation(AiConversation(conversationId, clean.take(50), now, now + 1))
            }.onFailure { error.value = "assistant_failed" }
            loading.value = false
        }
        return true
    }

    fun saveInspection(plotId: String?, imageUri: String?, cropName: String, symptoms: Set<String>, parts: Set<String>, observations: String, language: String): Boolean {
        if (cropName.trim().length !in 2..60 || symptoms.isEmpty()) { error.value = "inspection_invalid"; return false }
        viewModelScope.launch {
            loading.value = true
            runCatching {
                val image = imageUri?.let { runCatching { container.inspectionImageStore.prepareUpload(Uri.parse(it)) }.getOrNull() }
                val result = container.diagnosisProvider.assess(CropDiagnosisRequest(cropName.trim(), symptoms, parts, observations.trim(), image, language))
                val inspection = CropInspection(
                    UUID.randomUUID().toString(), plotId, imageUri, cropName.trim(), symptoms, parts, observations.trim(),
                    result.possibleCauses, result.evidenceNeeded, result.nextSteps, System.currentTimeMillis(), result.imageModelUsed,
                )
                container.stage3Repository.saveInspection(inspection)
            }.onFailure { error.value = "inspection_failed" }
            loading.value = false
        }
        return true
    }

    fun refreshWeather(location: String): Boolean {
        if (container.accountScope.current == DEMO_OWNER || location.trim().length !in 2..100 || loading.value) return false
        viewModelScope.launch {
            loading.value = true
            try {
                container.stage3Repository.saveWeather(container.weatherRefreshProvider.fetch(location.trim()))
            } catch (_: Exception) {
                error.value = "weather_failed"
            }
            loading.value = false
        }
        return true
    }

    fun refreshMarket(commodity: String, stateName: String, district: String): Boolean {
        if (container.accountScope.current == DEMO_OWNER || listOf(commodity, stateName, district).any { it.trim().length !in 2..80 } || loading.value) return false
        viewModelScope.launch {
            loading.value = true
            try {
                val values = container.marketRefreshProvider.fetch(commodity.trim(), stateName.trim(), district.trim())
                if (values.isEmpty()) error.value = "market_empty" else container.stage3Repository.saveMarketPrices(values)
            } catch (_: Exception) {
                error.value = "market_failed"
            }
            loading.value = false
        }
        return true
    }

    fun deleteInspection(id: String) = viewModelScope.launch { container.stage3Repository.deleteInspection(id) }

    fun saveHarvest(planId: String, dateText: String, quantityText: String, unit: String, priceText: String, totalExpenseText: String, extraLabel: String, extraAmountText: String, notes: String): Boolean {
        val plan = state.value.plans.firstOrNull { it.id == planId }
        val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
        val quantity = quantityText.toDoubleOrNull()
        val price = priceText.takeIf(String::isNotBlank)?.toDoubleOrNull()
        val total = totalExpenseText.toDoubleOrNull()
        val extra = extraAmountText.takeIf(String::isNotBlank)?.toDoubleOrNull() ?: 0.0
        if (plan == null || date == null || quantity == null || quantity <= 0 || unit !in setOf("kg", "quintal", "tonne") || (priceText.isNotBlank() && (price == null || price < 0)) || total == null || total < 0 || extra < 0) {
            error.value = "harvest_invalid"
            return false
        }
        val existing = state.value.harvests.firstOrNull { it.planId == planId }
        val harvest = HarvestRecord(existing?.id ?: UUID.randomUUID().toString(), planId, date, quantity, unit, price, total, notes.trim(), existing?.createdAt ?: System.currentTimeMillis())
        val expenses = if (extra > 0) listOf(ActualExpense("${harvest.id}:additional", harvest.id, extraLabel.trim().ifBlank { "Additional" }, extra)) else emptyList()
        viewModelScope.launch {
            container.stage3Repository.saveHarvest(harvest, expenses)
            container.farmRepository.savePlan(plan.copy(status = "completed", updatedAt = System.currentTimeMillis()), state.value.activities.filter { it.planId == plan.id })
        }
        return true
    }

    fun deleteHarvest(id: String) = viewModelScope.launch { container.stage3Repository.deleteHarvest(id) }
    fun markAlert(alert: FarmAlert, read: Boolean) = viewModelScope.launch { container.stage3Repository.markAlert(alert.id, read) }
}

class Stage3ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = Stage3ViewModel(container) as T
}
