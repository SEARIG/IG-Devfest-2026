package com.agroai.app.data.model

import com.google.firebase.Timestamp

data class FarmerProfile(
    val uid: String,
    val name: String,
    val phoneNumber: String,
    val preferredLanguage: String,
    val state: String,
    val district: String,
    val village: String = "",
    val area: Double,
    val areaUnit: String,
    val soilType: String,
    val irrigationType: String,
    val currentCrops: List<String> = emptyList(),
    val goals: List<String> = emptyList(),
    val experienceLevel: String = "",
    val onboardingCompleted: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
)

data class FarmerDraft(
    val name: String = "",
    val preferredLanguage: String = "en",
    val phoneNumber: String = "",
    val state: String = "",
    val district: String = "",
    val village: String = "",
    val area: String = "",
    val areaUnit: String = "acre",
    val soilType: String = "unknown",
    val irrigationType: String = "seasonal",
    val currentCrops: Set<String> = emptySet(),
    val goals: Set<String> = emptySet(),
    val experienceLevel: String = "",
) {
    fun toProfile(uid: String, existing: FarmerProfile? = null) = FarmerProfile(
        uid = uid,
        name = name.trim(),
        phoneNumber = phoneNumber,
        preferredLanguage = preferredLanguage,
        state = state,
        district = district.trim(),
        village = village.trim(),
        area = area.toDouble(),
        areaUnit = areaUnit,
        soilType = soilType,
        irrigationType = irrigationType,
        currentCrops = currentCrops.sorted(),
        goals = goals.sorted(),
        experienceLevel = experienceLevel,
        onboardingCompleted = true,
        createdAt = existing?.createdAt,
        updatedAt = existing?.updatedAt,
    )

    companion object {
        fun from(profile: FarmerProfile) = FarmerDraft(
            name = profile.name,
            preferredLanguage = profile.preferredLanguage,
            phoneNumber = profile.phoneNumber,
            state = profile.state,
            district = profile.district,
            village = profile.village,
            area = profile.area.toString().removeSuffix(".0"),
            areaUnit = profile.areaUnit,
            soilType = profile.soilType,
            irrigationType = profile.irrigationType,
            currentCrops = profile.currentCrops.toSet(),
            goals = profile.goals.toSet(),
            experienceLevel = profile.experienceLevel,
        )
    }
}
