package com.agroai.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.agroai.app.R
import com.agroai.app.data.ValidationError
import com.agroai.app.data.model.FarmerDraft
import com.agroai.app.ui.AppUiState
import com.agroai.app.ui.AppViewModel
import com.agroai.app.ui.Destination
import com.agroai.app.ui.components.*

private val states = listOf(
    LocalizedOption("Rajasthan", R.string.state_rajasthan), LocalizedOption("Uttar Pradesh", R.string.state_uttar_pradesh),
    LocalizedOption("Madhya Pradesh", R.string.state_madhya_pradesh), LocalizedOption("Maharashtra", R.string.state_maharashtra),
    LocalizedOption("Punjab", R.string.state_punjab), LocalizedOption("Bihar", R.string.state_bihar),
    LocalizedOption("Gujarat", R.string.state_gujarat), LocalizedOption("Karnataka", R.string.state_karnataka),
    LocalizedOption("Tamil Nadu", R.string.state_tamil_nadu), LocalizedOption("West Bengal", R.string.state_west_bengal),
)

private val districts = mapOf(
    "Rajasthan" to listOf(LocalizedOption("Udaipur", R.string.district_udaipur), LocalizedOption("Jaipur", R.string.district_jaipur), LocalizedOption("Jodhpur", R.string.district_jodhpur)),
    "Uttar Pradesh" to listOf(LocalizedOption("Lucknow", R.string.district_lucknow), LocalizedOption("Varanasi", R.string.district_varanasi), LocalizedOption("Agra", R.string.district_agra)),
    "Madhya Pradesh" to listOf(LocalizedOption("Bhopal", R.string.district_bhopal), LocalizedOption("Indore", R.string.district_indore), LocalizedOption("Jabalpur", R.string.district_jabalpur)),
    "Maharashtra" to listOf(LocalizedOption("Pune", R.string.district_pune), LocalizedOption("Nashik", R.string.district_nashik), LocalizedOption("Nagpur", R.string.district_nagpur)),
    "Punjab" to listOf(LocalizedOption("Ludhiana", R.string.district_ludhiana), LocalizedOption("Amritsar", R.string.district_amritsar), LocalizedOption("Patiala", R.string.district_patiala)),
    "Bihar" to listOf(LocalizedOption("Patna", R.string.district_patna), LocalizedOption("Gaya", R.string.district_gaya), LocalizedOption("Muzaffarpur", R.string.district_muzaffarpur)),
    "Gujarat" to listOf(LocalizedOption("Ahmedabad", R.string.district_ahmedabad), LocalizedOption("Rajkot", R.string.district_rajkot), LocalizedOption("Surat", R.string.district_surat)),
    "Karnataka" to listOf(LocalizedOption("Bengaluru Urban", R.string.district_bengaluru), LocalizedOption("Mysuru", R.string.district_mysuru), LocalizedOption("Belagavi", R.string.district_belagavi)),
    "Tamil Nadu" to listOf(LocalizedOption("Chennai", R.string.district_chennai), LocalizedOption("Coimbatore", R.string.district_coimbatore), LocalizedOption("Madurai", R.string.district_madurai)),
    "West Bengal" to listOf(LocalizedOption("Kolkata", R.string.district_kolkata), LocalizedOption("Darjeeling", R.string.district_darjeeling), LocalizedOption("Nadia", R.string.district_nadia)),
)

private val units = listOf(LocalizedOption("bigha", R.string.bigha), LocalizedOption("acre", R.string.acre), LocalizedOption("hectare", R.string.hectare))
private val soils = listOf(LocalizedOption("sandy", R.string.soil_sandy), LocalizedOption("clay", R.string.soil_clay), LocalizedOption("loamy", R.string.soil_loamy), LocalizedOption("black", R.string.soil_black), LocalizedOption("red", R.string.soil_red), LocalizedOption("unknown", R.string.unknown))
private val irrigations = listOf(LocalizedOption("yes", R.string.irrigation_yes), LocalizedOption("no", R.string.irrigation_no), LocalizedOption("seasonal", R.string.irrigation_seasonal))
private val crops = listOf(LocalizedOption("wheat", R.string.crop_wheat), LocalizedOption("mustard", R.string.crop_mustard), LocalizedOption("chickpea", R.string.crop_chickpea), LocalizedOption("maize", R.string.crop_maize), LocalizedOption("bajra", R.string.crop_bajra), LocalizedOption("rice", R.string.crop_rice), LocalizedOption("cotton", R.string.crop_cotton), LocalizedOption("sugarcane", R.string.crop_sugarcane))
private val goals = listOf(LocalizedOption("improve_yield", R.string.goal_yield), LocalizedOption("reduce_costs", R.string.goal_cost), LocalizedOption("save_water", R.string.goal_water), LocalizedOption("crop_health", R.string.goal_health))
private val experience = listOf(LocalizedOption("beginner", R.string.experience_beginner), LocalizedOption("intermediate", R.string.experience_intermediate), LocalizedOption("experienced", R.string.experience_experienced))

@Composable
fun OnboardingScreen(state: AppUiState, viewModel: AppViewModel) {
    BackHandler(enabled = state.onboardingStep > 0, onBack = viewModel::previousOnboardingStep)
    Column(Modifier.fillMaxSize()) {
        BrandHeader(state.language, viewModel::changeLanguage)
        ScreenColumn {
            if (state.onboardingStep > 0) IconButton(onClick = viewModel::previousOnboardingStep) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            when (state.onboardingStep) {
                0 -> PersonalStep(state, viewModel)
                1 -> LocationStep(state, viewModel)
                2 -> FarmStep(state, viewModel)
                3 -> PreferencesStep(state, viewModel)
                else -> ReviewStep(state, viewModel)
            }
            PrimaryButton(if (state.onboardingStep == 4) stringResource(R.string.finish_setup) else stringResource(R.string.continue_label), viewModel::nextOnboardingStep, enabled = !state.loading)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PersonalStep(state: AppUiState, viewModel: AppViewModel) {
    StepHeader(0, stringResource(R.string.personal_title), stringResource(R.string.personal_subtitle))
    PersonalFields(state.draft, state.errors, viewModel::updateDraft, state.offlineMode)
}

@Composable
private fun PersonalFields(draft: FarmerDraft, errors: Set<ValidationError>, update: ((FarmerDraft) -> FarmerDraft) -> Unit, offlineMode: Boolean = false) {
    OutlinedTextField(value = draft.name, onValueChange = { value -> update { it.copy(name = value) } }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.farmer_name)) }, placeholder = { Text(stringResource(R.string.farmer_name_hint)) }, isError = ValidationError.Name in errors, singleLine = true, shape = RoundedCornerShape(11.dp))
    ErrorText(ValidationError.Name in errors)
    Text(stringResource(R.string.preferred_language), style = MaterialTheme.typography.titleSmall)
    LanguageButtons(draft.preferredLanguage) { value -> update { it.copy(preferredLanguage = value) } }
    OutlinedTextField(value = if (offlineMode) stringResource(R.string.not_required_offline) else draft.phoneNumber, onValueChange = {}, modifier = Modifier.fillMaxWidth(), readOnly = true, label = { Text(stringResource(if (offlineMode) R.string.mobile_number else R.string.verified_mobile)) }, singleLine = true, shape = RoundedCornerShape(11.dp))
}

@Composable
private fun LocationStep(state: AppUiState, viewModel: AppViewModel) {
    StepHeader(1, stringResource(R.string.location_title), stringResource(R.string.location_subtitle))
    LocationFields(state.draft, state.errors, viewModel::updateDraft)
}

@Composable
private fun LocationFields(draft: FarmerDraft, errors: Set<ValidationError>, update: ((FarmerDraft) -> FarmerDraft) -> Unit) {
    OptionDropdown(stringResource(R.string.state), draft.state, states, { value -> update { it.copy(state = value, district = "") } }, ValidationError.State in errors)
    OptionDropdown(stringResource(R.string.district), draft.district, districts[draft.state].orEmpty(), { value -> update { it.copy(district = value) } }, ValidationError.District in errors)
    OutlinedTextField(value = draft.village, onValueChange = { value -> update { it.copy(village = value) } }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.village)) }, singleLine = true, shape = RoundedCornerShape(11.dp))
    ErrorText(ValidationError.State in errors || ValidationError.District in errors)
}

@Composable
private fun FarmStep(state: AppUiState, viewModel: AppViewModel) {
    StepHeader(2, stringResource(R.string.farm_title), stringResource(R.string.farm_subtitle))
    FarmFields(state.draft, state.errors, viewModel::updateDraft)
}

@Composable
private fun FarmFields(draft: FarmerDraft, errors: Set<ValidationError>, update: ((FarmerDraft) -> FarmerDraft) -> Unit) {
    OutlinedTextField(value = draft.area, onValueChange = { value -> update { it.copy(area = value) } }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.farm_area)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = ValidationError.Area in errors, singleLine = true, shape = RoundedCornerShape(11.dp))
    OptionDropdown(stringResource(R.string.area_unit), draft.areaUnit, units, { value -> update { it.copy(areaUnit = value) } }, ValidationError.Unit in errors)
    if (draft.areaUnit == "bigha") InfoCard { Text(stringResource(R.string.bigha_note), color = MaterialTheme.colorScheme.primary) }
    OptionDropdown(stringResource(R.string.soil_type), draft.soilType, soils, { value -> update { it.copy(soilType = value) } }, ValidationError.Soil in errors)
    OptionDropdown(stringResource(R.string.irrigation), draft.irrigationType, irrigations, { value -> update { it.copy(irrigationType = value) } }, ValidationError.Irrigation in errors)
    ErrorText(errors.any { it in setOf(ValidationError.Area, ValidationError.Unit, ValidationError.Soil, ValidationError.Irrigation) })
}

@Composable
private fun PreferencesStep(state: AppUiState, viewModel: AppViewModel) {
    StepHeader(3, stringResource(R.string.preferences_title), stringResource(R.string.preferences_subtitle))
    PreferenceFields(state.draft, state.errors, viewModel::updateDraft)
}

@Composable
private fun PreferenceFields(draft: FarmerDraft, errors: Set<ValidationError>, update: ((FarmerDraft) -> FarmerDraft) -> Unit) {
    MultiChoice(stringResource(R.string.current_crops), crops, draft.currentCrops, { value -> update { it.copy(currentCrops = it.currentCrops.toggle(value)) } }, ValidationError.Crops in errors)
    MultiChoice(stringResource(R.string.farming_goals), goals, draft.goals, { value -> update { it.copy(goals = it.goals.toggle(value)) } }, ValidationError.Goals in errors)
    OptionDropdown(stringResource(R.string.experience_level), draft.experienceLevel, experience, { value -> update { it.copy(experienceLevel = value) } })
    ErrorText(ValidationError.Crops in errors || ValidationError.Goals in errors)
}

@Composable
private fun ReviewStep(state: AppUiState, viewModel: AppViewModel) {
    StepHeader(4, stringResource(R.string.review_title), stringResource(R.string.review_subtitle))
    InfoCard {
        SummaryRow(stringResource(R.string.farmer_name), state.draft.name) { viewModel.editOnboardingStep(0) }
        SummaryRow(stringResource(R.string.state), state.draft.state) { viewModel.editOnboardingStep(1) }
        SummaryRow(stringResource(R.string.district), state.draft.district)
        SummaryRow(stringResource(R.string.farm_area), "${state.draft.area} ${localizedValue(units, state.draft.areaUnit)}") { viewModel.editOnboardingStep(2) }
        SummaryRow(stringResource(R.string.current_crops), localizedValues(crops, state.draft.currentCrops)) { viewModel.editOnboardingStep(3) }
        SummaryRow(stringResource(R.string.farming_goals), localizedValues(goals, state.draft.goals))
    }
}

@Composable
fun EditProfileScreen(state: AppUiState, viewModel: AppViewModel) {
    BackHandler { viewModel.navigate(Destination.Profile) }
    Column(Modifier.fillMaxSize()) {
        BrandHeader(state.language, viewModel::changeLanguage)
        ScreenColumn {
            Text(stringResource(R.string.edit_profile), style = MaterialTheme.typography.headlineSmall)
            PersonalFields(state.draft, state.errors, viewModel::updateDraft, state.offlineMode)
            HorizontalDivider(); LocationFields(state.draft, state.errors, viewModel::updateDraft)
            HorizontalDivider(); FarmFields(state.draft, state.errors, viewModel::updateDraft)
            HorizontalDivider(); PreferenceFields(state.draft, state.errors, viewModel::updateDraft)
            PrimaryButton(stringResource(R.string.save), viewModel::saveProfile, enabled = !state.loading)
            TextButton(onClick = { viewModel.navigate(Destination.Profile) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
        }
    }
}

private fun Set<String>.toggle(value: String) = if (value in this) this - value else this + value

@Composable
private fun localizedValue(options: List<LocalizedOption>, value: String): String = options.firstOrNull { it.value == value }?.let { stringResource(it.label) } ?: value

@Composable
private fun localizedValues(options: List<LocalizedOption>, values: Set<String>) = values.sorted().map { localizedValue(options, it) }.joinToString(", ")

@Composable
fun localizedProfileValues(type: String, values: List<String>): String = when (type) {
    "crop" -> localizedValues(crops, values.toSet())
    "goal" -> localizedValues(goals, values.toSet())
    else -> values.joinToString(", ")
}

@Composable
fun localizedProfileValue(type: String, value: String): String = when (type) {
    "unit" -> localizedValue(units, value)
    "soil" -> localizedValue(soils, value)
    "irrigation" -> localizedValue(irrigations, value)
    "experience" -> localizedValue(experience, value)
    else -> value
}
