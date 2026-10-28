package com.agroai.app.domain

import java.time.LocalDate

data class FarmPlot(
    val id: String,
    val name: String,
    val area: Double,
    val areaUnit: String,
    val soilType: String,
    val irrigationMethod: String,
    val cropHistory: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

data class GeoPoint(val latitude: Double, val longitude: Double)

data class FarmBoundary(
    val id: String,
    val plotId: String,
    val points: List<GeoPoint>,
    val areaSquareMeters: Double,
    val accuracyMeters: Float? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class SoilProfile(val type: String, val notes: String = "")

data class CropReference(
    val id: String,
    val nameEn: String,
    val nameHi: String,
    val seasons: Set<String>,
    val suitableSoils: Set<String>,
    val waterNeed: String,
    val durationDays: IntRange,
    val seedRateKgPerHectare: ClosedFloatingPointRange<Double>,
    val notesEn: String,
    val notesHi: String,
)

data class CropRecommendation(
    val crop: CropReference,
    val score: Int,
    val reasons: List<String>,
    val cautions: List<String>,
)

data class CropPlan(
    val id: String,
    val plotId: String,
    val cropId: String,
    val sowingDate: LocalDate,
    val areaHectares: Double,
    val irrigationMethod: String,
    val seedVariety: String = "",
    val status: String = "planned",
    val seedQuantityKgLow: Double,
    val seedQuantityKgHigh: Double,
    val harvestStart: LocalDate,
    val harvestEnd: LocalDate,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class CropPlanActivity(
    val id: String,
    val planId: String?,
    val plotId: String?,
    val type: String,
    val title: String,
    val scheduledAt: Long,
    val notes: String = "",
    val completed: Boolean = false,
    val reminderEnabled: Boolean = false,
)

data class FarmExpense(val label: String, val amount: Double)

typealias CalendarActivity = CropPlanActivity
