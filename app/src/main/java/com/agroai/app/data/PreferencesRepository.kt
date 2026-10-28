package com.agroai.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.agroai.app.data.model.FarmerDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("agro_ai_preferences")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val language = stringPreferencesKey("language")
        val name = stringPreferencesKey("draft_name")
        val phone = stringPreferencesKey("draft_phone")
        val state = stringPreferencesKey("draft_state")
        val district = stringPreferencesKey("draft_district")
        val village = stringPreferencesKey("draft_village")
        val area = stringPreferencesKey("draft_area")
        val unit = stringPreferencesKey("draft_unit")
        val soil = stringPreferencesKey("draft_soil")
        val irrigation = stringPreferencesKey("draft_irrigation")
        val crops = stringPreferencesKey("draft_crops")
        val goals = stringPreferencesKey("draft_goals")
        val experience = stringPreferencesKey("draft_experience")
        val step = intPreferencesKey("draft_step")
        val offlineActive = booleanPreferencesKey("offline_active")
        val offlineProfileComplete = booleanPreferencesKey("offline_profile_complete")
    }

    val language: Flow<String> = context.dataStore.data.map { it[Keys.language] ?: "en" }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[Keys.language] = language }
    }

    suspend fun loadDraft(phoneNumber: String): FarmerDraft {
        val p = context.dataStore.data.first()
        return FarmerDraft(
            name = p[Keys.name].orEmpty(),
            preferredLanguage = p[Keys.language] ?: "en",
            phoneNumber = phoneNumber.ifBlank { p[Keys.phone].orEmpty() },
            state = p[Keys.state].orEmpty(), district = p[Keys.district].orEmpty(),
            village = p[Keys.village].orEmpty(), area = p[Keys.area].orEmpty(),
            areaUnit = p[Keys.unit] ?: "acre", soilType = p[Keys.soil] ?: "unknown",
            irrigationType = p[Keys.irrigation] ?: "seasonal",
            currentCrops = decode(p[Keys.crops]), goals = decode(p[Keys.goals]),
            experienceLevel = p[Keys.experience].orEmpty(),
        )
    }

    suspend fun saveDraft(draft: FarmerDraft) {
        context.dataStore.edit { p ->
            p[Keys.language] = draft.preferredLanguage
            p[Keys.name] = draft.name; p[Keys.phone] = draft.phoneNumber
            p[Keys.state] = draft.state; p[Keys.district] = draft.district
            p[Keys.village] = draft.village; p[Keys.area] = draft.area
            p[Keys.unit] = draft.areaUnit; p[Keys.soil] = draft.soilType
            p[Keys.irrigation] = draft.irrigationType
            p[Keys.crops] = draft.currentCrops.joinToString("|")
            p[Keys.goals] = draft.goals.joinToString("|")
            p[Keys.experience] = draft.experienceLevel
        }
    }

    suspend fun loadStep() = context.dataStore.data.first()[Keys.step] ?: 0

    suspend fun saveStep(step: Int) {
        context.dataStore.edit { it[Keys.step] = step }
    }

    suspend fun loadOfflineActive() = context.dataStore.data.first()[Keys.offlineActive] ?: false

    suspend fun loadOfflineProfileComplete() = context.dataStore.data.first()[Keys.offlineProfileComplete] ?: false

    suspend fun setOfflineSession(active: Boolean, profileComplete: Boolean? = null) {
        context.dataStore.edit { p ->
            p[Keys.offlineActive] = active
            profileComplete?.let { p[Keys.offlineProfileComplete] = it }
        }
    }

    suspend fun clearDraft() {
        context.dataStore.edit { p ->
            listOf(Keys.name, Keys.phone, Keys.state, Keys.district, Keys.village, Keys.area, Keys.unit, Keys.soil, Keys.irrigation, Keys.crops, Keys.goals, Keys.experience).forEach(p::remove)
            p.remove(Keys.step)
        }
    }

    private fun decode(value: String?) = value.orEmpty().split('|').filter(String::isNotBlank).toSet()
}
