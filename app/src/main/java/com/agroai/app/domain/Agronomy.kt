package com.agroai.app.domain

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

object CropCatalog {
    // Seed-rate ranges are from Government of India, Agricultural Statistics at a Glance 2022, Appendix VIII.
    val crops = listOf(
        CropReference("wheat", "Wheat", "गेहूँ", setOf("rabi"), setOf("loamy", "clay", "black"), "medium", 120..150, 100.0..125.0, "Cool-season cereal; timely sowing and drainage matter.", "ठंडे मौसम की फसल; समय पर बुवाई और जल निकास महत्वपूर्ण हैं।"),
        CropReference("chickpea", "Chickpea", "चना", setOf("rabi"), setOf("loamy", "black", "red"), "low", 100..130, 75.0..100.0, "Avoid standing water; seed rate depends on seed size.", "जलभराव से बचें; बीज दर दाने के आकार पर निर्भर करती है।"),
        CropReference("mustard", "Mustard", "सरसों", setOf("rabi"), setOf("loamy", "sandy", "clay"), "low", 105..140, 3.0..5.0, "Needs a fine seedbed and timely moisture at flowering.", "महीन क्यारी और फूल आने पर समय पर नमी उपयोगी है।"),
        CropReference("rice", "Rice", "धान", setOf("kharif"), setOf("clay", "loamy"), "high", 110..150, 35.0..40.0, "Water demand and seed rate vary greatly by establishment method.", "पानी और बीज की जरूरत रोपण विधि के अनुसार बहुत बदलती है।"),
        CropReference("maize", "Maize", "मक्का", setOf("kharif", "zaid"), setOf("loamy", "black", "red"), "medium", 90..120, 18.0..20.0, "Good drainage is important; hybrids may use a different rate.", "अच्छा जल निकास जरूरी है; संकर किस्म की बीज दर अलग हो सकती है।"),
        CropReference("bajra", "Pearl millet", "बाजरा", setOf("kharif"), setOf("sandy", "loamy", "red"), "low", 75..100, 4.0..5.0, "Suited to lower-water conditions; avoid waterlogging.", "कम पानी की दशाओं के अनुकूल; जलभराव से बचें।"),
    )

    fun byId(id: String) = crops.firstOrNull { it.id == id }
}

object CropRecommendationEngine {
    fun recommend(season: String, soilType: String, irrigationMethod: String): List<CropRecommendation> =
        CropCatalog.crops.map { crop ->
            val reasons = mutableListOf<String>()
            val cautions = mutableListOf<String>()
            var score = 0
            if (season in crop.seasons) { score += 5; reasons += "season_match" }
            else cautions += "season_mismatch"
            if (soilType == "unknown") cautions += "soil_unknown"
            else if (soilType in crop.suitableSoils) { score += 3; reasons += "soil_match" }
            else cautions += "soil_mismatch"
            val waterAvailable = irrigationMethod != "no"
            if (crop.waterNeed == "low" || waterAvailable) { score += 2; reasons += if (crop.waterNeed == "low") "low_water" else "water_match" }
            else cautions += "water_mismatch"
            CropRecommendation(crop, score, reasons, cautions)
        }.filter { it.score >= 5 }.sortedWith(compareByDescending<CropRecommendation> { it.score }.thenBy { it.crop.nameEn })
}

object CropPlanGenerator {
    fun create(plot: FarmPlot, crop: CropReference, sowingDate: LocalDate, bighaSquareMeters: Double? = null): CropPlan {
        val hectares = FarmCalculators.toHectares(plot.area, plot.areaUnit, bighaSquareMeters)
        require(hectares > 0) { "Plot area must be positive" }
        return CropPlan(
            id = UUID.randomUUID().toString(), plotId = plot.id, cropId = crop.id,
            sowingDate = sowingDate, areaHectares = hectares, irrigationMethod = plot.irrigationMethod,
            seedQuantityKgLow = hectares * crop.seedRateKgPerHectare.start,
            seedQuantityKgHigh = hectares * crop.seedRateKgPerHectare.endInclusive,
            harvestStart = sowingDate.plusDays(crop.durationDays.first.toLong()),
            harvestEnd = sowingDate.plusDays(crop.durationDays.last.toLong()),
        )
    }

    fun activities(plan: CropPlan): List<CropPlanActivity> {
        fun at(day: Long) = plan.sowingDate.plusDays(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun item(type: String, title: String, day: Long, note: String) = CropPlanActivity(UUID.randomUUID().toString(), plan.id, plan.plotId, type, title, at(day), note)
        return listOf(
            item("land", "Prepare field", -7, "Review drainage, field condition, and locally recommended seedbed practice."),
            item("seed", "Sow crop", 0, "Use the plan seed range and confirm variety suitability with a local expert."),
            item("irrigation", "Check soil moisture", 14, "Irrigate only when field conditions and crop stage require it."),
            item("nutrient", "Review soil test and nutrients", 21, "Use a current soil test or local KVK advice before applying nutrients."),
            item("inspection", "Inspect crop health", 35, "Record weeds, pests, disease symptoms, and uneven growth."),
            item("harvest", "Prepare for harvest window", plan.harvestStart.toEpochDay() - plan.sowingDate.toEpochDay(), "Check crop maturity and local weather before harvesting."),
        )
    }
}
