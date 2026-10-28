package com.agroai.app.data

import com.agroai.app.data.model.FarmerDraft

enum class ValidationError { Phone, Otp, Name, State, District, Area, Unit, Soil, Irrigation, Crops, Goals }

object Validation {
    private val indianPhone = Regex("^[6-9]\\d{9}$")
    private val otp = Regex("^\\d{6}$")

    fun normalizeIndianPhone(value: String): String? {
        val raw = value.filter(Char::isDigit)
        val digits = if (raw.length == 12 && raw.startsWith("91")) raw.drop(2) else raw
        return if (indianPhone.matches(digits)) "+91$digits" else null
    }

    fun isValidOtp(value: String) = otp.matches(value.trim())

    fun personal(draft: FarmerDraft): Set<ValidationError> = buildSet {
        if (draft.name.trim().length !in 2..80) add(ValidationError.Name)
        if (normalizeIndianPhone(draft.phoneNumber) == null) add(ValidationError.Phone)
    }

    fun location(draft: FarmerDraft): Set<ValidationError> = buildSet {
        if (draft.state.isBlank()) add(ValidationError.State)
        if (draft.district.trim().isBlank()) add(ValidationError.District)
    }

    fun farm(draft: FarmerDraft): Set<ValidationError> = buildSet {
        val area = draft.area.toDoubleOrNull()
        if (area == null || area <= 0 || area > 100_000) add(ValidationError.Area)
        if (draft.areaUnit !in setOf("bigha", "acre", "hectare")) add(ValidationError.Unit)
        if (draft.soilType !in setOf("sandy", "clay", "loamy", "black", "red", "unknown")) add(ValidationError.Soil)
        if (draft.irrigationType !in setOf("yes", "no", "seasonal")) add(ValidationError.Irrigation)
    }

    fun preferences(draft: FarmerDraft): Set<ValidationError> = buildSet {
        if (draft.currentCrops.isEmpty()) add(ValidationError.Crops)
        if (draft.goals.isEmpty()) add(ValidationError.Goals)
    }

    fun profile(draft: FarmerDraft) = personal(draft) + location(draft) + farm(draft) + preferences(draft)
}
