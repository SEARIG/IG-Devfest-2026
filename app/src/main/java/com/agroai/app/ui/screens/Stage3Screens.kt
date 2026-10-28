@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.agroai.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.agroai.app.AgroAiApplication
import com.agroai.app.R
import com.agroai.app.domain.*
import com.agroai.app.platform.AndroidSpeechInputProvider
import com.agroai.app.platform.AndroidSpeechOutputProvider
import com.agroai.app.ui.*
import com.agroai.app.ui.components.InfoCard
import com.agroai.app.ui.components.PrimaryButton
import com.agroai.app.ui.components.SummaryRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun AssistantScreen(appState: AppUiState, state: Stage3UiState, appViewModel: AppViewModel, viewModel: Stage3ViewModel) {
    BackHandler { appViewModel.navigate(Destination.Home) }
    val context = LocalContext.current
    val inputProvider = remember { AndroidSpeechInputProvider(context) }
    val outputProvider = remember { AndroidSpeechOutputProvider(context) }
    var input by remember { mutableStateOf("") }
    var speechState by remember { mutableStateOf(SpeechState.Idle) }
    var speechError by remember { mutableStateOf<String?>(null) }
    val startListening = {
        inputProvider.start(appState.language, { input = it }, { speechState = it }, { speechError = it })
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else speechError = "permission"
    }
    DisposableEffect(Unit) { onDispose { inputProvider.close(); outputProvider.close() } }

    MainScaffold(appState, appViewModel, Destination.Home) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Stage3BackTitle(R.string.voice_ai) { appViewModel.navigate(Destination.Home) }
            Text(stringResource(R.string.local_assistant_disclosure), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.messages.isEmpty()) {
                    item { Text(stringResource(R.string.assistant_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(R.string.suggest_crop, R.string.suggest_seed, R.string.suggest_today).forEach { id -> AssistChip(onClick = { input = context.getString(id) }, label = { Text(stringResource(id)) }) }
                        }
                    }
                }
                items(state.messages, key = ChatMessage::id) { message -> ChatBubble(message, appState.language) {
                    if (speechState == SpeechState.Speaking) outputProvider.stop()
                    else if (!outputProvider.speak(message.text, appState.language) { speechState = it }) speechError = "tts"
                } }
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            speechError?.let {
                Text(stringResource(speechErrorRes(it)), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(speechStatusRes(speechState)), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.ask_farm_question)) }, maxLines = 3)
                IconButton(onClick = {
                    speechError = null
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening() else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Icon(if (speechState == SpeechState.Listening) Icons.Outlined.Stop else Icons.Outlined.Mic, stringResource(R.string.voice_input)) }
                IconButton(onClick = { if (viewModel.send(input, appState.language)) input = "" }, enabled = input.isNotBlank() && !state.loading) { Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.send)) }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, language: String, playback: () -> Unit) {
    val farmer = message.role == "farmer"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (farmer) Arrangement.End else Arrangement.Start) {
        Card(colors = CardDefaults.cardColors(containerColor = if (farmer) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth(.86f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(message.text)
                if (message.cardLines.isNotEmpty()) {
                    HorizontalDivider()
                    message.cardTitle?.let { Text(it, fontWeight = FontWeight.Bold) }
                    message.cardLines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (!farmer) IconButton(onClick = playback, modifier = Modifier.align(Alignment.End).size(32.dp)) { Icon(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.play_voice)) }
            }
        }
    }
}

@Composable
fun CropDoctorScreen(appState: AppUiState, state: Stage3UiState, appViewModel: AppViewModel, viewModel: Stage3ViewModel) {
    BackHandler { appViewModel.navigate(Destination.Home) }
    val context = LocalContext.current
    val imageStore = (context.applicationContext as AgroAiApplication).container.inspectionImageStore
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCapture by remember { mutableStateOf<Uri?>(null) }
    var cameraDenied by remember { mutableStateOf(false) }
    var crop by remember { mutableStateOf("") }
    var observations by remember { mutableStateOf("") }
    var selectedPlot by remember { mutableStateOf<String?>(null) }
    var symptoms by remember { mutableStateOf(setOf<String>()) }
    var parts by remember { mutableStateOf(setOf<String>()) }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) imageUri = pendingCapture else pendingCapture = null }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraDenied = !granted
        if (granted) imageStore.newCaptureUri().also { pendingCapture = it; capture.launch(it) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { imageUri = imageStore.import(it) } }
    val symptomOptions = listOf("yellowing", "brown_spots", "wilting", "curling", "insects", "leaf_damage")
    val partOptions = listOf("leaves", "stem", "roots", "fruit", "whole_plant")

    MainScaffold(appState, appViewModel, Destination.Home) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Stage3BackTitle(R.string.crop_doctor) { appViewModel.navigate(Destination.Home) }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard {
                    Text(stringResource(R.string.crop_doctor_connected_disclosure), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    imageUri?.let { InspectionImage(it); TextButton(onClick = { imageUri = null }) { Text(stringResource(R.string.remove_photo)) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) imageStore.newCaptureUri().also { pendingCapture = it; capture.launch(it) }
                            else cameraPermission.launch(Manifest.permission.CAMERA)
                        }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PhotoCamera, null); Text(stringResource(R.string.take_photo)) }
                        OutlinedButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PhotoLibrary, null); Text(stringResource(R.string.choose_photo)) }
                    }
                    if (cameraDenied) Text(stringResource(R.string.camera_permission_denied), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(crop, { crop = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.crop_name)) })
                    SimpleDropdown(stringResource(R.string.related_plot), state.plots.map { it.id }, selectedPlot, { id -> state.plots.firstOrNull { it.id == id }?.name ?: stringResource(R.string.none) }) { selectedPlot = it }
                    Text(stringResource(R.string.visible_symptoms), fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { symptomOptions.forEach { code -> FilterChip(code in symptoms, { symptoms = symptoms.toggle(code) }, { Text(stringResource(symptomRes(code))) }) } }
                    Text(stringResource(R.string.affected_parts), fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { partOptions.forEach { code -> FilterChip(code in parts, { parts = parts.toggle(code) }, { Text(stringResource(partRes(code))) }) } }
                    OutlinedTextField(observations, { observations = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.additional_observations)) }, minLines = 2)
                    PrimaryButton(stringResource(R.string.assess_symptoms), {
                        if (viewModel.saveInspection(selectedPlot, imageUri?.toString(), crop, symptoms, parts, observations, appState.language)) { crop = ""; symptoms = emptySet(); parts = emptySet(); observations = ""; imageUri = null }
                    })
                }
                Text(stringResource(R.string.saved_inspections), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (state.inspections.isEmpty()) Text(stringResource(R.string.no_inspections), color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.inspections.forEach { inspection -> InspectionCard(inspection) { viewModel.deleteInspection(inspection.id) } }
            }
        }
    }
}

@Composable
private fun InspectionImage(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) { value = runCatching { context.contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input) } }.getOrNull() }
    bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop) }
}

@Composable
private fun InspectionCard(inspection: CropInspection, delete: () -> Unit) {
    val symptomNames = inspection.symptoms.map { stringResource(symptomRes(it)) }.joinToString()
    InfoCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(inspection.cropName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); IconButton(onClick = delete) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) } }
        Text(stringResource(R.string.symptoms_reported, symptomNames))
        Text(stringResource(R.string.possible_causes), fontWeight = FontWeight.Bold)
        inspection.possibleCauses.forEach { DiagnosisLine(it) }
        Text(stringResource(R.string.evidence_needed), fontWeight = FontWeight.Bold)
        inspection.evidenceNeeded.forEach { DiagnosisLine(it) }
        Text(stringResource(R.string.safe_next_steps), fontWeight = FontWeight.Bold)
        inspection.nextSteps.forEach { DiagnosisLine(it) }
        Text(stringResource(if (inspection.imageModelUsed) R.string.image_model_used else R.string.image_not_analyzed), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.consult_expert), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun WeatherScreen(appState: AppUiState, state: Stage3UiState, appViewModel: AppViewModel, viewModel: Stage3ViewModel) {
    var location by remember(appState.profile?.district) { mutableStateOf(appState.profile?.district.orEmpty()) }
    Stage3ListScreen(appState, appViewModel, R.string.weather, Destination.Weather) {
        OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.location)) })
        PrimaryButton(stringResource(R.string.refresh_live_weather), { viewModel.refreshWeather(location) }, enabled = !state.loading)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.weather.isEmpty()) DisconnectedCard(R.string.live_weather_not_connected, R.string.weather_empty_body)
        else {
            val advisories = WeatherAdvisoryEngine.evaluate(state.weather)
            state.weather.forEach { record -> InfoCard {
                Text(record.location, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SummaryRow(stringResource(R.string.temperature), "${record.temperatureC} °C")
                SummaryRow(stringResource(R.string.humidity), "${record.humidityPercent}%")
                SummaryRow(stringResource(R.string.wind), "${record.windKph} km/h")
                SummaryRow(stringResource(R.string.rainfall), "${record.rainMillimetres} mm")
                SummaryRow(stringResource(R.string.data_source), record.source)
                SummaryRow(stringResource(R.string.updated_at), Instant.ofEpochMilli(record.observedAt).atZone(ZoneId.systemDefault()).toLocalDateTime().toString())
                SummaryRow(stringResource(R.string.freshness), stringResource(if (System.currentTimeMillis() - record.observedAt > 6 * 60 * 60 * 1000) R.string.weather_stale else R.string.weather_current))
            } }
            advisories.forEach { Text("• ${stringResource(weatherAdvisoryRes(it.code))}") }
        }
    }
}

@Composable
fun MarketScreen(appState: AppUiState, state: Stage3UiState, appViewModel: AppViewModel, viewModel: Stage3ViewModel) {
    var queryCrop by remember { mutableStateOf(appState.profile?.currentCrops?.firstOrNull().orEmpty()) }
    var queryState by remember(appState.profile?.state) { mutableStateOf(appState.profile?.state.orEmpty()) }
    var queryDistrict by remember(appState.profile?.district) { mutableStateOf(appState.profile?.district.orEmpty()) }
    Stage3ListScreen(appState, appViewModel, R.string.market_prices, Destination.Market) {
        OutlinedTextField(queryCrop, { queryCrop = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.crop_name)) })
        OutlinedTextField(queryState, { queryState = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.state)) })
        OutlinedTextField(queryDistrict, { queryDistrict = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.district)) })
        PrimaryButton(stringResource(R.string.refresh_mandi_prices), { viewModel.refreshMarket(queryCrop, queryState, queryDistrict) }, enabled = !state.loading)
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.prices.isEmpty()) DisconnectedCard(R.string.market_data_not_connected, R.string.market_empty_body)
        else {
            var crop by remember { mutableStateOf(state.prices.first().crop) }
            var selectedState by remember { mutableStateOf(state.prices.first().state) }
            var district by remember { mutableStateOf(state.prices.first().district) }
            var market by remember { mutableStateOf(state.prices.first().market) }
            var quantity by remember { mutableStateOf("") }
            SimpleDropdown(stringResource(R.string.crop_name), state.prices.map(MarketPriceRecord::crop).distinct(), crop, { it.orEmpty() }) { crop = it.orEmpty() }
            SimpleDropdown(stringResource(R.string.state), state.prices.filter { it.crop == crop }.map(MarketPriceRecord::state).distinct(), selectedState, { it.orEmpty() }) { selectedState = it.orEmpty() }
            SimpleDropdown(stringResource(R.string.district), state.prices.filter { it.crop == crop && it.state == selectedState }.map(MarketPriceRecord::district).distinct(), district, { it.orEmpty() }) { district = it.orEmpty() }
            SimpleDropdown(stringResource(R.string.market), state.prices.filter { it.crop == crop && it.state == selectedState && it.district == district }.map(MarketPriceRecord::market).distinct(), market, { it.orEmpty() }) { market = it.orEmpty() }
            OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.quantity_quintals)) })
            MarketPriceAnalysis.compare(state.prices.filter { it.crop == crop && it.state == selectedState && it.district == district && it.market == market }).forEach { price -> InfoCard {
                Text("${price.crop} · ${price.market}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${price.district}, ${price.state}")
                SummaryRow(stringResource(R.string.minimum_price), "₹${price.minPricePerQuintal}/q")
                SummaryRow(stringResource(R.string.maximum_price), "₹${price.maxPricePerQuintal}/q")
                SummaryRow(stringResource(R.string.modal_price), "₹${price.modalPricePerQuintal}/q")
                SummaryRow(stringResource(R.string.data_date), price.dataDate.toString())
                SummaryRow(stringResource(R.string.data_source), price.source)
                val age = ChronoUnit.DAYS.between(price.dataDate, LocalDate.now()).coerceAtLeast(0).toInt()
                SummaryRow(stringResource(R.string.freshness), pluralStringResource(R.plurals.days_old, age, age))
                quantity.toDoubleOrNull()?.takeIf { it >= 0 }?.let { SummaryRow(stringResource(R.string.estimated_gross_revenue), "₹${"%.2f".format(MarketPriceAnalysis.estimatedGrossRevenue(it, price.modalPricePerQuintal))}") }
            } }
        }
    }
}

@Composable
fun HarvestScreen(appState: AppUiState, state: Stage3UiState, appViewModel: AppViewModel, viewModel: Stage3ViewModel) {
    var planId by remember { mutableStateOf(state.plans.firstOrNull { it.status == "active" }?.id ?: state.plans.firstOrNull()?.id.orEmpty()) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("kg") }
    var price by remember { mutableStateOf("") }
    var expenses by remember { mutableStateOf("") }
    var extraLabel by remember { mutableStateOf("") }
    var extraAmount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    Stage3ListScreen(appState, appViewModel, R.string.harvest_tracking, Destination.Harvest) {
        InfoCard {
            if (state.plans.isEmpty()) Text(stringResource(R.string.harvest_needs_plan))
            else {
                SimpleDropdown(stringResource(R.string.crop_plan), state.plans.map(CropPlan::id), planId, { id -> state.plans.firstOrNull { it.id == id }?.let { CropCatalog.byId(it.cropId)?.nameEn ?: it.cropId } ?: id.orEmpty() }) { planId = it.orEmpty() }
                OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.actual_harvest_date)) })
                OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.harvest_quantity)) })
                SimpleDropdown(stringResource(R.string.measurement_unit), listOf("kg", "quintal", "tonne"), unit, { it ?: "" }) { unit = it.orEmpty() }
                OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.selling_price_per_unit)) })
                OutlinedTextField(expenses, { expenses = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.actual_total_expenses)) })
                OutlinedTextField(extraLabel, { extraLabel = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.additional_expense_label)) })
                OutlinedTextField(extraAmount, { extraAmount = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.additional_expense_amount)) })
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.notes)) })
                PrimaryButton(stringResource(R.string.save_harvest), { if (viewModel.saveHarvest(planId, date, quantity, unit, price, expenses, extraLabel, extraAmount, notes)) { quantity = ""; price = ""; expenses = ""; extraAmount = ""; notes = "" } })
            }
        }
        Text(stringResource(R.string.farm_history), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (state.harvests.isEmpty()) Text(stringResource(R.string.no_harvest_records), color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.harvests.forEach { harvest ->
            val plan = state.plans.firstOrNull { it.id == harvest.planId }
            InfoCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(plan?.let { CropCatalog.byId(it.cropId)?.nameEn } ?: stringResource(R.string.harvest), Modifier.weight(1f), fontWeight = FontWeight.Bold); IconButton(onClick = { viewModel.deleteHarvest(harvest.id) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) } }
                SummaryRow(stringResource(R.string.actual_harvest_date), harvest.actualHarvestDate.toString())
                SummaryRow(stringResource(R.string.actual_yield), "${harvest.quantity} ${harvest.unit}")
                SummaryRow(stringResource(R.string.actual_total_expenses), "₹${"%.2f".format(harvest.actualTotalExpenses)}")
                plan?.let { p ->
                    val comparison = HarvestAnalytics.compare(p, harvest)
                    comparison.actualRevenue?.let { SummaryRow(stringResource(R.string.actual_revenue), "₹${"%.2f".format(it)}") }
                    comparison.actualGrossMargin?.let { SummaryRow(stringResource(R.string.actual_margin), "₹${"%.2f".format(it)}") }
                    SummaryRow(stringResource(R.string.harvest_date_variance), pluralStringResource(R.plurals.days_value, comparison.harvestDateDifferenceDays.toInt(), comparison.harvestDateDifferenceDays))
                    Text(stringResource(R.string.estimate_comparison_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun AlertsScreen(appState: AppUiState, state: Stage3UiState, appViewModel: AppViewModel, viewModel: Stage3ViewModel) {
    Stage3ListScreen(appState, appViewModel, R.string.alerts, Destination.Alerts) {
        Text(stringResource(R.string.alerts_local_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.alerts.isEmpty()) Text(stringResource(R.string.no_alerts))
        state.alerts.forEach { alert -> InfoCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(if (alert.read) Icons.Outlined.NotificationsNone else Icons.Outlined.NotificationImportant, null); Spacer(Modifier.width(8.dp)); Text(stringResource(if (alert.action == "harvest") R.string.harvest_preparation else R.string.farm_activity), Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(stringResource(if (alert.priority == "high") R.string.priority_high else R.string.priority_normal)) }
            Text(stringResource(alertMessageRes(alert.message)))
            Text(Instant.ofEpochMilli(alert.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDateTime().toString(), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { viewModel.markAlert(alert, !alert.read) }) { Text(stringResource(if (alert.read) R.string.mark_unread else R.string.mark_read)) }
        } }
    }
}

@Composable
private fun Stage3ListScreen(appState: AppUiState, appViewModel: AppViewModel, title: Int, selected: Destination, content: @Composable ColumnScope.() -> Unit) {
    BackHandler { appViewModel.navigate(Destination.Home) }
    MainScaffold(appState, appViewModel, selected) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Stage3BackTitle(title) { appViewModel.navigate(Destination.Home) }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun Stage3BackTitle(title: Int, back: () -> Unit) = Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun DisconnectedCard(title: Int, body: Int) = InfoCard {
    Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(stringResource(body), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun <T> SimpleDropdown(label: String, values: List<T>, selected: T?, display: @Composable (T?) -> String, select: (T?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: ${display(selected)}") }
        DropdownMenu(expanded, { expanded = false }) { values.forEach { value -> DropdownMenuItem({ Text(display(value)) }, { select(value); expanded = false }) } }
    }
}

private fun Set<String>.toggle(value: String) = if (value in this) this - value else this + value
private fun speechStatusRes(state: SpeechState) = when (state) { SpeechState.Idle -> R.string.speech_idle; SpeechState.Listening -> R.string.speech_listening; SpeechState.Processing -> R.string.speech_processing; SpeechState.Speaking -> R.string.speech_speaking; SpeechState.Error -> R.string.speech_error }
private fun speechErrorRes(code: String) = when (code) { "permission" -> R.string.microphone_permission_denied; "speech_timeout" -> R.string.speech_timeout; "speech_unavailable" -> R.string.speech_unavailable; "tts" -> R.string.tts_unavailable; else -> R.string.speech_failed }
private fun symptomRes(code: String) = when (code) { "yellowing" -> R.string.symptom_yellowing; "brown_spots" -> R.string.symptom_brown_spots; "wilting" -> R.string.symptom_wilting; "curling" -> R.string.symptom_curling; "insects" -> R.string.symptom_insects; else -> R.string.symptom_leaf_damage }
private fun partRes(code: String) = when (code) { "leaves" -> R.string.part_leaves; "stem" -> R.string.part_stem; "roots" -> R.string.part_roots; "fruit" -> R.string.part_fruit; else -> R.string.part_whole_plant }
@Composable private fun DiagnosisLine(value: String) {
    val resource = diagnosisRes(value)
    Text("• ${resource?.let { stringResource(it) } ?: value}")
}
private fun diagnosisRes(code: String): Int? = when (code) {
    "nutrient_or_water_stress" -> R.string.cause_nutrient_water; "leaf_injury_or_infection" -> R.string.cause_leaf_injury; "water_or_root_stress" -> R.string.cause_water_root
    "pest_or_heat_stress" -> R.string.cause_pest_heat; "possible_pest_presence" -> R.string.cause_pest_presence; "feeding_or_physical_damage" -> R.string.cause_feeding_damage
    "insufficient_symptom_information" -> R.string.cause_insufficient; "symptom_distribution" -> R.string.evidence_distribution; "soil_moisture" -> R.string.evidence_soil_moisture
    "close_leaf_views" -> R.string.evidence_close_views; "recent_field_changes" -> R.string.evidence_recent_changes; "record_spread" -> R.string.step_record_spread
    "inspect_both_leaf_sides" -> R.string.step_inspect_sides; "avoid_unverified_treatment" -> R.string.step_avoid_treatment; "consult_local_expert" -> R.string.step_consult_expert; else -> null
}
private fun weatherAdvisoryRes(code: String) = when (code) { "review_irrigation" -> R.string.advisory_rain; "review_spraying" -> R.string.advisory_wind; else -> R.string.advisory_heat }
private fun alertMessageRes(code: String) = when (code) { "today" -> R.string.alert_today; "overdue" -> R.string.alert_overdue; "upcoming" -> R.string.alert_upcoming; else -> R.string.alert_harvest_due }
