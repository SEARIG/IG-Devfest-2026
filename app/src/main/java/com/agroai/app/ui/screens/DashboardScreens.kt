package com.agroai.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agroai.app.R
import com.agroai.app.data.CloudSyncStatus
import com.agroai.app.ui.AppUiState
import com.agroai.app.ui.AppViewModel
import com.agroai.app.ui.Destination
import com.agroai.app.ui.Stage2UiState
import com.agroai.app.ui.Stage3UiState
import com.agroai.app.ui.components.BrandHeader
import com.agroai.app.ui.components.InfoCard
import com.agroai.app.ui.components.PrimaryButton
import com.agroai.app.ui.components.SummaryRow

private data class Feature(val code: String, @StringRes val title: Int, @StringRes val stage: Int, val icon: ImageVector)
private val features = listOf(
    Feature("voice", R.string.voice_ai, R.string.stage_3_label, Icons.Outlined.Mic),
    Feature("farm", R.string.my_farm, R.string.stage_2_label, Icons.Outlined.Landscape),
    Feature("planner", R.string.crop_planner, R.string.stage_2_label, Icons.Outlined.Agriculture),
    Feature("doctor", R.string.crop_doctor, R.string.stage_3_label, Icons.Outlined.LocalFlorist),
    Feature("calculator", R.string.farm_calculator, R.string.stage_2_label, Icons.Outlined.Calculate),
    Feature("calendar", R.string.farm_calendar, R.string.stage_2_label, Icons.Outlined.CalendarMonth),
    Feature("weather", R.string.weather, R.string.stage_3_label, Icons.Outlined.Cloud),
    Feature("market", R.string.market_prices, R.string.stage_3_label, Icons.Outlined.Storefront),
    Feature("harvest", R.string.harvest_tracking, R.string.stage_3_label, Icons.Outlined.Inventory),
    Feature("alerts", R.string.alerts, R.string.stage_3_label, Icons.Outlined.Notifications),
)

@Composable
fun MainScaffold(state: AppUiState, viewModel: AppViewModel, selected: Destination, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = { BrandHeader(state.language, viewModel::changeLanguage) },
        bottomBar = { BottomNavigation(selected, viewModel::navigate) },
        content = content,
    )
}

@Composable
private fun BottomNavigation(selected: Destination, onNavigate: (Destination) -> Unit) {
    NavigationBar {
        listOf(
            Triple(Destination.Home, R.string.home, Icons.Outlined.Home), Triple(Destination.MyFarm, R.string.my_farm, Icons.Outlined.Landscape),
            Triple(Destination.Calendar, R.string.calendar, Icons.Outlined.CalendarMonth), Triple(Destination.Profile, R.string.profile, Icons.Outlined.Person),
        ).forEach { (destination, label, icon) ->
            NavigationBarItem(selected = selected == destination, onClick = { onNavigate(destination) }, icon = { Icon(icon, null) }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
fun DashboardScreen(state: AppUiState, stage: Stage2UiState, stage3: Stage3UiState, viewModel: AppViewModel) {
    val profile = state.profile ?: return
    MainScaffold(state, viewModel, Destination.Home) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.greeting, profile.name.substringBefore(' ')), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.location_format, profile.district, profile.state), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.navigate(Destination.Profile) }) { Icon(Icons.Outlined.AccountCircle, stringResource(R.string.profile), Modifier.size(34.dp)) }
            }
            if (state.demoMode) InfoCard {
                Text(stringResource(R.string.demo_mode_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.demo_mode_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = viewModel::resetDemo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reset_demo)) }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.hero_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.hero_title_hindi), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f))
                    FilledTonalButton(onClick = { viewModel.navigate(Destination.Assistant) }) { Icon(Icons.Outlined.Mic, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ask_agro)) }
                    Text(stringResource(R.string.local_rule_based), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (!state.offlineMode) SyncStatusCard(state, viewModel)
            if (state.localDataAvailable) InfoCard {
                Text(stringResource(R.string.link_local_data_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.link_local_data_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                PrimaryButton(stringResource(R.string.link_local_data), viewModel::linkLocalData, enabled = !state.loading)
                TextButton(onClick = viewModel::keepLocalDataSeparate, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.keep_local_data_separate)) }
            }
            InfoCard {
                Text(stringResource(R.string.farm_snapshot), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SummaryRow(stringResource(R.string.plots), stage.plots.size.toString())
                SummaryRow(stringResource(R.string.active_plans), stage.plans.count { it.status == "active" }.toString())
                SummaryRow(stringResource(R.string.upcoming_activities), stage.activities.count { !it.completed && it.scheduledAt >= System.currentTimeMillis() }.toString())
                SummaryRow(stringResource(R.string.inspections), stage3.inspections.size.toString())
                SummaryRow(stringResource(R.string.unread_alerts), stage3.alerts.count { !it.read }.toString())
                SummaryRow(stringResource(R.string.harvest_records), stage3.harvests.size.toString())
            }
            features.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { feature -> FeatureCard(feature, { when (feature.code) {
                        "farm" -> viewModel.navigate(Destination.MyFarm)
                        "planner" -> viewModel.navigate(Destination.CropPlanner)
                        "calculator" -> viewModel.navigate(Destination.Calculator)
                        "calendar" -> viewModel.navigate(Destination.Calendar)
                        "voice" -> viewModel.navigate(Destination.Assistant)
                        "doctor" -> viewModel.navigate(Destination.CropDoctor)
                        "weather" -> viewModel.navigate(Destination.Weather)
                        "market" -> viewModel.navigate(Destination.Market)
                        "harvest" -> viewModel.navigate(Destination.Harvest)
                        "alerts" -> viewModel.navigate(Destination.Alerts)
                        else -> viewModel.openComingSoon(feature.code)
                    } }, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SyncStatusCard(state: AppUiState, viewModel: AppViewModel) {
    val title = when (state.sync.status) {
        CloudSyncStatus.Synced -> R.string.sync_synced
        CloudSyncStatus.Pending -> R.string.sync_pending
        CloudSyncStatus.Offline -> R.string.sync_offline
        CloudSyncStatus.Failed -> R.string.sync_failed
        CloudSyncStatus.Conflict -> R.string.sync_conflict
        CloudSyncStatus.AuthenticationRequired -> R.string.sync_auth_required
    }
    InfoCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudSync, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), fontWeight = FontWeight.Bold)
                if (state.sync.pendingCount > 0) Text(stringResource(R.string.sync_pending_count, state.sync.pendingCount), style = MaterialTheme.typography.bodySmall)
            }
        }
        when (state.sync.status) {
            CloudSyncStatus.Conflict -> {
                Text(stringResource(R.string.sync_conflict_body), color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::useCloudConflictVersion, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.use_cloud_version)) }
                    Button(onClick = viewModel::keepLocalConflictVersion, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.keep_local_version)) }
                }
            }
            CloudSyncStatus.Failed, CloudSyncStatus.Offline, CloudSyncStatus.Pending ->
                OutlinedButton(onClick = viewModel::retrySync, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.retry_sync)) }
            else -> Unit
        }
    }
}

@Composable
private fun FeatureCard(feature: Feature, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.heightIn(min = 132.dp).clickable(onClick = onClick), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(feature.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(stringResource(feature.title), fontWeight = FontWeight.Bold)
            Text(stringResource(feature.stage), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PlaceholderTabScreen(state: AppUiState, viewModel: AppViewModel, @StringRes title: Int) {
    MainScaffold(state, viewModel, if (title == R.string.my_farm) Destination.MyFarm else Destination.Calendar) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            InfoCard {
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.stage_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ComingSoonScreen(state: AppUiState, viewModel: AppViewModel) {
    BackHandler { viewModel.navigate(Destination.Home) }
    val title = features.firstOrNull { it.code == state.comingSoonFeature }?.title ?: R.string.coming_soon
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.Agriculture, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.coming_soon_body, stringResource(title)), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryButton(stringResource(R.string.return_home), { viewModel.navigate(Destination.Home) })
        }
    }
}

@Composable
fun ProfileScreen(state: AppUiState, viewModel: AppViewModel) {
    val profile = state.profile ?: return
    var confirmLogout by remember { mutableStateOf(false) }
    MainScaffold(state, viewModel, Destination.Profile) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            InfoCard {
                SummaryRow(stringResource(R.string.farmer_name), profile.name)
                SummaryRow(stringResource(if (state.offlineMode) R.string.session_mode else R.string.verified_mobile), if (state.offlineMode) stringResource(R.string.offline_mode) else profile.phoneNumber)
                SummaryRow(stringResource(R.string.preferred_language), if (profile.preferredLanguage == "hi") stringResource(R.string.hindi) else stringResource(R.string.english))
                SummaryRow(stringResource(R.string.state), profile.state)
                SummaryRow(stringResource(R.string.district), profile.district)
                SummaryRow(stringResource(R.string.village), profile.village)
            }
            InfoCard {
                Text(stringResource(R.string.farm_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SummaryRow(stringResource(R.string.farm_area), "${profile.area.toString().removeSuffix(".0")} ${localizedProfileValue("unit", profile.areaUnit)}")
                SummaryRow(stringResource(R.string.soil_type), localizedProfileValue("soil", profile.soilType))
                SummaryRow(stringResource(R.string.irrigation), localizedProfileValue("irrigation", profile.irrigationType))
                SummaryRow(stringResource(R.string.crops), localizedProfileValues("crop", profile.currentCrops))
                SummaryRow(stringResource(R.string.goals), localizedProfileValues("goal", profile.goals))
                SummaryRow(stringResource(R.string.experience), localizedProfileValue("experience", profile.experienceLevel))
            }
            PrimaryButton(stringResource(R.string.edit_profile), viewModel::openEditProfile)
            OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Outlined.Logout, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.logout)) }
        }
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false }, title = { Text(stringResource(R.string.logout_confirm_title)) },
        text = { Text(stringResource(R.string.logout_confirm_body)) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; viewModel.logout() }) { Text(stringResource(R.string.confirm_logout)) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
