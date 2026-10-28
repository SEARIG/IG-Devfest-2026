package com.agroai.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agroai.app.AppContainer
import com.agroai.app.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class Stage2UiState(
    val plots: List<FarmPlot> = emptyList(),
    val boundaries: List<FarmBoundary> = emptyList(),
    val plans: List<CropPlan> = emptyList(),
    val activities: List<CalendarActivity> = emptyList(),
    val selectedPlotId: String? = null,
    val selectedCropId: String? = null,
    val error: String? = null,
)

class Stage2ViewModel(private val container: AppContainer) : ViewModel() {
    private val selectedPlotId = MutableStateFlow<String?>(null)
    private val selectedCropId = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<Stage2UiState> = combine(
        container.farmRepository.plots,
        container.farmRepository.boundaries,
        container.farmRepository.plans,
        container.farmRepository.activities,
        selectedPlotId,
        selectedCropId,
        error,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        Stage2UiState(
            plots = values[0] as List<FarmPlot>, boundaries = values[1] as List<FarmBoundary>,
            plans = values[2] as List<CropPlan>, activities = values[3] as List<CalendarActivity>,
            selectedPlotId = values[4] as String?, selectedCropId = values[5] as String?, error = values[6] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Stage2UiState())

    fun selectPlot(id: String?) { selectedPlotId.value = id }
    fun selectCrop(id: String?) { selectedCropId.value = id }
    fun clearError() { error.value = null }

    fun savePlot(id: String?, name: String, areaText: String, unit: String, soil: String, irrigation: String, history: String): Boolean {
        val area = areaText.toDoubleOrNull()
        if (name.trim().length !in 2..60 || area == null || area <= 0 || unit !in setOf("acre", "hectare", "bigha") || soil !in setOf("sandy", "clay", "loamy", "black", "red", "unknown") || irrigation !in setOf("yes", "no", "seasonal")) {
            error.value = "plot_invalid"
            return false
        }
        val plot = FarmPlot(id ?: UUID.randomUUID().toString(), name.trim(), area, unit, soil, irrigation, history.trim())
        viewModelScope.launch { container.farmRepository.savePlot(plot); selectedPlotId.value = plot.id }
        return true
    }

    fun deletePlot(id: String) = viewModelScope.launch { container.farmRepository.deletePlot(id); if (selectedPlotId.value == id) selectedPlotId.value = null }

    fun saveBoundary(plotId: String, points: List<GeoPoint>, accuracy: Float?): Boolean {
        val result = BoundaryMath.validate(points)
        if (!result.valid) { error.value = result.error; return false }
        val existing = state.value.boundaries.firstOrNull { it.plotId == plotId }
        val boundary = FarmBoundary(existing?.id ?: UUID.randomUUID().toString(), plotId, points, result.areaSquareMeters, accuracy)
        viewModelScope.launch { container.farmRepository.saveBoundary(boundary) }
        return true
    }

    fun deleteBoundary(plotId: String) = viewModelScope.launch { container.farmRepository.deleteBoundary(plotId) }

    fun recommendations(season: String, plotId: String): List<CropRecommendation> {
        val plot = state.value.plots.firstOrNull { it.id == plotId } ?: return emptyList()
        return CropRecommendationEngine.recommend(season, plot.soilType, plot.irrigationMethod)
    }

    fun createPlan(plotId: String, cropId: String, sowingText: String, localBighaSquareMetres: String): Boolean {
        val plot = state.value.plots.firstOrNull { it.id == plotId }
        val crop = CropCatalog.byId(cropId)
        val date = runCatching { LocalDate.parse(sowingText) }.getOrNull()
        if (plot == null || crop == null || date == null) { error.value = "plan_invalid"; return false }
        val bigha = localBighaSquareMetres.toDoubleOrNull()
        if (plot.areaUnit == "bigha" && (bigha == null || bigha <= 0)) { error.value = "bigha_invalid"; return false }
        val plan = runCatching { CropPlanGenerator.create(plot, crop, date, bigha) }.getOrElse { error.value = "plan_invalid"; return false }
        val activities = CropPlanGenerator.activities(plan)
        viewModelScope.launch { container.farmRepository.savePlan(plan, activities); selectedCropId.value = cropId }
        return true
    }

    fun setPlanStatus(plan: CropPlan, status: String) {
        if (status !in setOf("planned", "active", "completed")) return
        viewModelScope.launch { container.farmRepository.savePlan(plan.copy(status = status, updatedAt = System.currentTimeMillis()), state.value.activities.filter { it.planId == plan.id }) }
    }

    fun deletePlan(id: String) = viewModelScope.launch { container.farmRepository.deletePlan(id) }

    fun saveManualActivity(id: String?, title: String, dateText: String, notes: String, reminder: Boolean): Boolean {
        val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
        if (title.trim().isEmpty() || date == null) { error.value = "activity_invalid"; return false }
        val existing = state.value.activities.firstOrNull { it.id == id }
        val activity = (existing ?: CropPlanActivity(UUID.randomUUID().toString(), null, selectedPlotId.value, "manual", title.trim(), 0L)).copy(
            title = title.trim(), scheduledAt = date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), notes = notes.trim(), reminderEnabled = reminder,
        )
        viewModelScope.launch {
            container.farmRepository.saveActivity(activity)
            if (reminder) container.reminderScheduler.schedule(activity) else container.reminderScheduler.cancel(activity.id)
        }
        return true
    }

    fun toggleActivity(activity: CalendarActivity) = viewModelScope.launch {
        val updated = activity.copy(completed = !activity.completed)
        container.farmRepository.saveActivity(updated)
        if (updated.completed) container.reminderScheduler.cancel(updated.id) else if (updated.reminderEnabled) container.reminderScheduler.schedule(updated)
    }

    fun deleteActivity(activity: CalendarActivity) = viewModelScope.launch {
        container.reminderScheduler.cancel(activity.id)
        container.farmRepository.deleteActivity(activity.id)
    }
}

class Stage2ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = Stage2ViewModel(container) as T
}
