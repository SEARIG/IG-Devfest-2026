package com.agroai.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.agroai.app.R
import com.agroai.app.domain.*
import com.agroai.app.ui.*
import com.agroai.app.ui.components.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

@Composable
fun MyFarmScreen(appState: AppUiState, stage: Stage2UiState, appViewModel: AppViewModel, stageViewModel: Stage2ViewModel) {
    var editing by remember { mutableStateOf<FarmPlot?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    MainScaffold(appState, appViewModel, Destination.MyFarm) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.my_farm), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.my_farm_summary, stage.plots.size, stage.plans.count { it.status == "active" }), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = { editing = null; showEditor = true }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.add_plot)) }
            }
            if (stage.plots.isEmpty()) InfoCard {
                Text(stringResource(R.string.no_plots_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.no_plots_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            stage.plots.forEach { plot ->
                val boundary = stage.boundaries.firstOrNull { it.plotId == plot.id }
                val plans = stage.plans.count { it.plotId == plot.id && it.status != "completed" }
                Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(plot.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${plot.area.clean()} ${areaUnitLabel(plot.areaUnit)} · ${soilLabel(plot.soilType)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editing = plot; showEditor = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit)) }
                            IconButton(onClick = { stageViewModel.deletePlot(plot.id) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
                        }
                        Text(stringResource(R.string.plot_plan_count, plans))
                        Text(if (boundary == null) stringResource(R.string.no_boundary) else stringResource(R.string.boundary_area, boundary.areaSquareMeters.clean(), boundary.points.size))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { stageViewModel.selectPlot(plot.id); appViewModel.navigate(Destination.FarmScan) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.farm_scan)) }
                            Button(onClick = { stageViewModel.selectPlot(plot.id); appViewModel.navigate(Destination.CropPlanner) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.plan_crop)) }
                        }
                    }
                }
            }
        }
    }
    if (showEditor) PlotEditorDialog(editing, stageViewModel, { showEditor = false })
}

@Composable
private fun PlotEditorDialog(plot: FarmPlot?, viewModel: Stage2ViewModel, dismiss: () -> Unit) {
    var name by remember(plot?.id) { mutableStateOf(plot?.name.orEmpty()) }
    var area by remember(plot?.id) { mutableStateOf(plot?.area?.clean().orEmpty()) }
    var unit by remember(plot?.id) { mutableStateOf(plot?.areaUnit ?: "acre") }
    var soil by remember(plot?.id) { mutableStateOf(plot?.soilType ?: "unknown") }
    var irrigation by remember(plot?.id) { mutableStateOf(plot?.irrigationMethod ?: "seasonal") }
    var history by remember(plot?.id) { mutableStateOf(plot?.cropHistory.orEmpty()) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(if (plot == null) R.string.add_plot else R.string.edit_plot)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.plot_name)) }, singleLine = true)
            OutlinedTextField(area, { area = it }, label = { Text(stringResource(R.string.farm_area)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            ChoiceField(stringResource(R.string.area_unit), unit, listOf("acre", "hectare", "bigha"), { areaUnitLabel(it) }, { unit = it })
            ChoiceField(stringResource(R.string.soil_type), soil, listOf("sandy", "clay", "loamy", "black", "red", "unknown"), { soilLabel(it) }, { soil = it })
            ChoiceField(stringResource(R.string.irrigation), irrigation, listOf("yes", "seasonal", "no"), { irrigationLabel(it) }, { irrigation = it })
            OutlinedTextField(history, { history = it }, label = { Text(stringResource(R.string.crop_history)) }, minLines = 2)
        } },
        confirmButton = { TextButton(onClick = { if (viewModel.savePlot(plot?.id, name, area, unit, soil, irrigation, history)) dismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun FarmScanScreen(appState: AppUiState, stage: Stage2UiState, appViewModel: AppViewModel, stageViewModel: Stage2ViewModel) {
    BackHandler { appViewModel.navigate(Destination.MyFarm) }
    val context = LocalContext.current
    var selectedId by remember(stage.selectedPlotId, stage.plots) { mutableStateOf(stage.selectedPlotId ?: stage.plots.firstOrNull()?.id) }
    val existing = stage.boundaries.firstOrNull { it.plotId == selectedId }
    var points by remember(selectedId, existing?.updatedAt) { mutableStateOf(existing?.points.orEmpty()) }
    var accuracy by remember(selectedId, existing?.updatedAt) { mutableStateOf(existing?.accuracyMeters) }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    fun capture() = captureCurrentPoint(context, { location ->
        points = points + GeoPoint(location.latitude, location.longitude); accuracy = location.accuracy; locationMessage = null
    }, { locationMessage = it })
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) capture() else locationMessage = context.getString(R.string.location_permission_denied)
    }
    MainScaffold(appState, appViewModel, Destination.MyFarm) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BackTitle(R.string.farm_scan, { appViewModel.navigate(Destination.MyFarm) })
            Text(stringResource(R.string.farm_scan_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceField(stringResource(R.string.plot_name), selectedId.orEmpty(), stage.plots.map(FarmPlot::id), { id -> stage.plots.firstOrNull { it.id == id }?.name.orEmpty() }, { selectedId = it; stageViewModel.selectPlot(it) })
            BoundaryCanvas(points)
            Text(stringResource(R.string.captured_points, points.size))
            if (accuracy != null) Text(stringResource(R.string.gps_accuracy, accuracy!!.clean()), style = MaterialTheme.typography.bodySmall)
            locationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) capture() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.MyLocation, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.capture_gps_point)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(latitude, { latitude = it }, label = { Text(stringResource(R.string.latitude)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(longitude, { longitude = it }, label = { Text(stringResource(R.string.longitude)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            OutlinedButton(onClick = {
                val point = latitude.toDoubleOrNull()?.let { lat -> longitude.toDoubleOrNull()?.let { lon -> GeoPoint(lat, lon) } }
                if (point == null || point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0) locationMessage = context.getString(R.string.invalid_coordinate)
                else { points = points + point; latitude = ""; longitude = ""; locationMessage = null }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_manual_point)) }
            val result = remember(points) { if (points.size >= 3) BoundaryMath.validate(points) else null }
            if (result != null) Text(if (result.valid) stringResource(R.string.estimated_boundary_area, result.areaSquareMeters.clean()) else boundaryError(result.error), color = if (result.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.boundary_uncertainty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { points = points.dropLast(1) }, enabled = points.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.undo_point)) }
                OutlinedButton(onClick = { points = emptyList() }, enabled = points.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.clear)) }
            }
            Button(onClick = { selectedId?.let { if (stageViewModel.saveBoundary(it, points, accuracy)) appViewModel.navigate(Destination.MyFarm) } }, enabled = selectedId != null && result?.valid == true, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_boundary)) }
            if (existing != null) TextButton(onClick = { stageViewModel.deleteBoundary(existing.plotId); points = emptyList() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete_boundary)) }
        }
    }
}

@Composable
private fun BoundaryCanvas(points: List<GeoPoint>) {
    Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Canvas(Modifier.fillMaxWidth().height(220.dp).padding(16.dp)) {
            if (points.isEmpty()) return@Canvas
            val minLat = points.minOf { it.latitude }; val maxLat = points.maxOf { it.latitude }
            val minLon = points.minOf { it.longitude }; val maxLon = points.maxOf { it.longitude }
            fun offset(point: GeoPoint) = Offset(
                (((point.longitude - minLon) / max(maxLon - minLon, 0.000001)) * (size.width - 24f) + 12f).toFloat(),
                (size.height - (((point.latitude - minLat) / max(maxLat - minLat, 0.000001)) * (size.height - 24f) + 12f)).toFloat(),
            )
            points.forEachIndexed { index, point ->
                val start = offset(point); drawCircle(Color(0xFF1B6B3A), 8f, start)
                if (index > 0) drawLine(Color(0xFF1B6B3A), offset(points[index - 1]), start, 5f)
            }
            if (points.size >= 3) drawLine(Color(0xFF1B6B3A), offset(points.last()), offset(points.first()), 5f)
        }
    }
}

@SuppressLint("MissingPermission")
private fun captureCurrentPoint(context: Context, success: (Location) -> Unit, failure: (String) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java)
    val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    if (provider == null) { failure(context.getString(R.string.location_unavailable)); return }
    manager.requestSingleUpdate(provider, object : LocationListener {
        override fun onLocationChanged(location: Location) { manager.removeUpdates(this); success(location) }
        @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) { manager.removeUpdates(this); failure(context.getString(R.string.location_unavailable)) }
    }, Looper.getMainLooper())
}

@Composable
fun CropPlannerScreen(appState: AppUiState, stage: Stage2UiState, appViewModel: AppViewModel, stageViewModel: Stage2ViewModel) {
    BackHandler { appViewModel.navigate(Destination.Home) }
    var plotId by remember(stage.selectedPlotId, stage.plots) { mutableStateOf(stage.selectedPlotId ?: stage.plots.firstOrNull()?.id.orEmpty()) }
    var season by remember { mutableStateOf("kharif") }
    var cropId by remember(stage.selectedCropId) { mutableStateOf(stage.selectedCropId.orEmpty()) }
    var sowingDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var localBigha by remember { mutableStateOf("") }
    val recommendations = remember(stage.plots, plotId, season) { stageViewModel.recommendations(season, plotId) }
    val selectedCrop = CropCatalog.byId(cropId)
    MainScaffold(appState, appViewModel, Destination.Home) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BackTitle(R.string.crop_planner, { appViewModel.navigate(Destination.Home) })
            Text(stringResource(R.string.planner_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (stage.plots.isEmpty()) {
                InfoCard { Text(stringResource(R.string.planner_needs_plot)); Button(onClick = { appViewModel.navigate(Destination.MyFarm) }) { Text(stringResource(R.string.add_plot)) } }
                return@Column
            }
            Text(stringResource(R.string.planner_step, 1), fontWeight = FontWeight.Bold)
            ChoiceField(stringResource(R.string.select_plot), plotId, stage.plots.map(FarmPlot::id), { id -> stage.plots.firstOrNull { it.id == id }?.name.orEmpty() }, { plotId = it; stageViewModel.selectPlot(it); cropId = "" })
            Text(stringResource(R.string.planner_step, 2), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("kharif" to R.string.season_kharif, "rabi" to R.string.season_rabi, "zaid" to R.string.season_zaid).forEach { (value, label) ->
                    FilterChip(selected = season == value, onClick = { season = value; cropId = "" }, label = { Text(stringResource(label)) })
                }
            }
            Text(stringResource(R.string.recommendation_explanation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            recommendations.forEach { recommendation ->
                val chosen = cropId == recommendation.crop.id
                Card(Modifier.fillMaxWidth().clickable { cropId = recommendation.crop.id; stageViewModel.selectCrop(recommendation.crop.id) }, border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row { Text(cropName(recommendation.crop, appState.language), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(stringResource(R.string.match_score, recommendation.score, 10)) }
                        recommendation.reasons.forEach { Text("✓ ${recommendationReason(it)}", color = MaterialTheme.colorScheme.primary) }
                        recommendation.cautions.forEach { Text("• ${recommendationReason(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            selectedCrop?.let { crop ->
                Text(stringResource(R.string.planner_step, 3), fontWeight = FontWeight.Bold)
                val seasonNames = buildList { crop.seasons.forEach { add(when (it) { "rabi" -> if (appState.language == "hi") "रबी" else "Rabi"; "zaid" -> if (appState.language == "hi") "ज़ायद" else "Zaid"; else -> if (appState.language == "hi") "खरीफ" else "Kharif" }) } }.joinToString()
                InfoCard {
                    Text(cropName(crop, appState.language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    SummaryRow(stringResource(R.string.suitable_seasons), seasonNames)
                    SummaryRow(stringResource(R.string.water_need), waterLabel(crop.waterNeed))
                    SummaryRow(stringResource(R.string.crop_duration), stringResource(R.string.days_range, crop.durationDays.first, crop.durationDays.last))
                    SummaryRow(stringResource(R.string.reference_seed_rate), stringResource(R.string.seed_rate_range, crop.seedRateKgPerHectare.start.clean(), crop.seedRateKgPerHectare.endInclusive.clean()))
                    Text(stringResource(R.string.reference_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (appState.language == "hi") crop.notesHi else crop.notesEn)
                }
                Text(stringResource(R.string.planner_step, 4), fontWeight = FontWeight.Bold)
                OutlinedTextField(sowingDate, { sowingDate = it }, label = { Text(stringResource(R.string.sowing_date)) }, supportingText = { Text(stringResource(R.string.date_format_hint)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (stage.plots.firstOrNull { it.id == plotId }?.areaUnit == "bigha") OutlinedTextField(localBigha, { localBigha = it }, label = { Text(stringResource(R.string.local_bigha_size)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.planner_step, 5), fontWeight = FontWeight.Bold)
                val preview = remember(plotId, cropId, sowingDate, localBigha, stage.plots) { runCatching {
                    val plot = requireNotNull(stage.plots.firstOrNull { it.id == plotId })
                    CropPlanGenerator.create(plot, crop, LocalDate.parse(sowingDate), localBigha.toDoubleOrNull())
                }.getOrNull() }
                preview?.let { plan ->
                    InfoCard {
                        SummaryRow(stringResource(R.string.seed_required), stringResource(R.string.kg_range, plan.seedQuantityKgLow.clean(), plan.seedQuantityKgHigh.clean()))
                        SummaryRow(stringResource(R.string.harvest_window), "${plan.harvestStart} – ${plan.harvestEnd}")
                        Text(stringResource(R.string.plan_safety_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick = { stageViewModel.createPlan(plotId, crop.id, sowingDate, localBigha) }, enabled = preview != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_crop_plan)) }
            }
            if (stage.plans.isNotEmpty()) {
                HorizontalDivider(); Text(stringResource(R.string.saved_plans), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                stage.plans.forEach { plan ->
                    val plot = stage.plots.firstOrNull { it.id == plan.plotId }
                    val crop = CropCatalog.byId(plan.cropId)
                    Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${crop?.let { cropName(it, appState.language) } ?: plan.cropId} · ${plot?.name.orEmpty()}", fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.plan_dates, plan.sowingDate.toString(), plan.harvestStart.toString(), plan.harvestEnd.toString()))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("planned" to R.string.status_planned, "active" to R.string.status_active, "completed" to R.string.status_completed).forEach { (value, label) -> FilterChip(selected = plan.status == value, onClick = { stageViewModel.setPlanStatus(plan, value) }, label = { Text(stringResource(label)) }) }
                        }
                        TextButton(onClick = { stageViewModel.deletePlan(plan.id) }) { Text(stringResource(R.string.delete_plan)) }
                    } }
                }
            }
        }
    }
}

@Composable
fun CalculatorScreen(appState: AppUiState, appViewModel: AppViewModel) {
    BackHandler { appViewModel.navigate(Destination.Home) }
    var area by remember { mutableStateOf("1") }; var unit by remember { mutableStateOf("acre") }; var bigha by remember { mutableStateOf("") }
    var seedRate by remember { mutableStateOf("100") }; var depth by remember { mutableStateOf("25") }; var efficiency by remember { mutableStateOf("80") }
    var seedCost by remember { mutableStateOf("0") }; var inputCost by remember { mutableStateOf("0") }; var labourCost by remember { mutableStateOf("0") }; var waterCost by remember { mutableStateOf("0") }; var otherCost by remember { mutableStateOf("0") }
    var yieldLow by remember { mutableStateOf("0") }; var yieldHigh by remember { mutableStateOf("0") }; var price by remember { mutableStateOf("0") }
    val hectares = runCatching { FarmCalculators.toHectares(area.toDouble(), unit, bigha.toDoubleOrNull()) }.getOrNull()
    val totalCost = runCatching { FarmCalculators.cultivationCost(listOf(seedCost, inputCost, labourCost, waterCost, otherCost).mapIndexed { index, value -> FarmExpense(index.toString(), value.toDouble()) }) }.getOrNull()
    MainScaffold(appState, appViewModel, Destination.Home) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BackTitle(R.string.farm_calculator, { appViewModel.navigate(Destination.Home) })
            Text(stringResource(R.string.calculator_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            CalculatorCard(R.string.area_seed_calculator) {
                NumberField(area, { area = it }, R.string.farm_area)
                ChoiceField(stringResource(R.string.area_unit), unit, listOf("acre", "hectare", "bigha", "square_metre", "square_foot"), { areaUnitLabel(it) }, { unit = it })
                if (unit == "bigha") NumberField(bigha, { bigha = it }, R.string.local_bigha_size)
                NumberField(seedRate, { seedRate = it }, R.string.seed_rate_input)
                hectares?.let { ha ->
                    Text(stringResource(R.string.area_result, ha.clean()))
                    seedRate.toDoubleOrNull()?.takeIf { it > 0 }?.let { Text(stringResource(R.string.seed_result, FarmCalculators.seedQuantity(ha, it).clean())) }
                }
                Text(stringResource(R.string.area_formula), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CalculatorCard(R.string.irrigation_calculator) {
                NumberField(depth, { depth = it }, R.string.water_depth)
                NumberField(efficiency, { efficiency = it }, R.string.irrigation_efficiency)
                val volume = runCatching { FarmCalculators.irrigationVolumeLitres(requireNotNull(hectares), depth.toDouble(), efficiency.toDouble()) }.getOrNull()
                volume?.let { Text(stringResource(R.string.water_result, it.clean())) }
                Text(stringResource(R.string.water_formula), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CalculatorCard(R.string.cost_calculator) {
                NumberField(seedCost, { seedCost = it }, R.string.cost_seed); NumberField(inputCost, { inputCost = it }, R.string.cost_inputs)
                NumberField(labourCost, { labourCost = it }, R.string.cost_labour); NumberField(waterCost, { waterCost = it }, R.string.cost_irrigation); NumberField(otherCost, { otherCost = it }, R.string.cost_other)
                totalCost?.let { Text(stringResource(R.string.total_cost, it.clean()), fontWeight = FontWeight.Bold) }
            }
            CalculatorCard(R.string.yield_calculator) {
                NumberField(yieldLow, { yieldLow = it }, R.string.yield_low); NumberField(yieldHigh, { yieldHigh = it }, R.string.yield_high); NumberField(price, { price = it }, R.string.price_per_kg)
                val result = runCatching { FarmCalculators.yieldRevenue(requireNotNull(hectares), yieldLow.toDouble(), yieldHigh.toDouble(), price.toDouble(), requireNotNull(totalCost)) }.getOrNull()
                result?.let {
                    Text(stringResource(R.string.production_result, it.productionTonnesLow.clean(), it.productionTonnesHigh.clean()))
                    Text(stringResource(R.string.revenue_result, it.revenueLow.clean(), it.revenueHigh.clean()))
                    Text(stringResource(R.string.margin_result, it.grossMarginLow.clean(), it.grossMarginHigh.clean()))
                }
                Text(stringResource(R.string.yield_formula), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CalculatorCard(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); content() } }
}

@Composable
private fun NumberField(value: String, change: (String) -> Unit, label: Int) = OutlinedTextField(value, change, label = { Text(stringResource(label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())

@Composable
fun FarmCalendarScreen(appState: AppUiState, stage: Stage2UiState, appViewModel: AppViewModel, stageViewModel: Stage2ViewModel) {
    val context = LocalContext.current
    val today = LocalDate.now(); val zone = ZoneId.systemDefault()
    var editing by remember { mutableStateOf<CalendarActivity?>(null) }; var showEditor by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permissionDenied = !granted }
    fun dayOf(activity: CalendarActivity) = Instant.ofEpochMilli(activity.scheduledAt).atZone(zone).toLocalDate()
    val todayItems = stage.activities.filter { !it.completed && dayOf(it) == today }
    val upcoming = stage.activities.filter { !it.completed && dayOf(it) > today }
    val completed = stage.activities.filter { it.completed }
    MainScaffold(appState, appViewModel, Destination.Calendar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.farm_calendar), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { editing = null; showEditor = true }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.add_activity)) }
            }
            MonthGrid(today, stage.activities)
            if (permissionDenied) Text(stringResource(R.string.notification_permission_denied), color = MaterialTheme.colorScheme.error)
            ActivitySection(R.string.today, todayItems, appState.language, { editing = it; showEditor = true }, stageViewModel)
            ActivitySection(R.string.upcoming, upcoming, appState.language, { editing = it; showEditor = true }, stageViewModel)
            ActivitySection(R.string.completed, completed, appState.language, { editing = it; showEditor = true }, stageViewModel)
        }
    }
    if (showEditor) ActivityEditorDialog(editing, { reminderRequested ->
        if (reminderRequested && android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }, stageViewModel, { showEditor = false })
}

@Composable
private fun MonthGrid(today: LocalDate, activities: List<CalendarActivity>) {
    val month = YearMonth.from(today); val firstBlank = month.atDay(1).dayOfWeek.value - 1
    val days = List(firstBlank) { 0 } + (1..month.lengthOfMonth()).toList()
    InfoCard {
        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.month_activity_count, activities.count { Instant.ofEpochMilli(it.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate().let { date -> YearMonth.from(date) == month } }), color = MaterialTheme.colorScheme.onSurfaceVariant)
        days.chunked(7).forEach { week -> Row(Modifier.fillMaxWidth()) { week.forEach { day -> Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) { if (day > 0) Text(day.toString(), color = if (day == today.dayOfMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (day == today.dayOfMonth) FontWeight.Bold else FontWeight.Normal) } }; repeat(7 - week.size) { Spacer(Modifier.weight(1f)) } } }
    }
}

@Composable
private fun ActivitySection(title: Int, activities: List<CalendarActivity>, language: String, edit: (CalendarActivity) -> Unit, viewModel: Stage2ViewModel) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (activities.isEmpty()) Text(stringResource(R.string.no_activities), color = MaterialTheme.colorScheme.onSurfaceVariant)
    activities.forEach { activity ->
        Card(Modifier.fillMaxWidth().clickable { edit(activity) }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(activity.completed, { viewModel.toggleActivity(activity) })
                Column(Modifier.weight(1f)) {
                    Text(activityTitle(activity), fontWeight = FontWeight.SemiBold)
                    Text(Instant.ofEpochMilli(activity.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)), style = MaterialTheme.typography.bodySmall)
                    if (activity.notes.isNotBlank() && activity.type == "manual") Text(activity.notes, style = MaterialTheme.typography.bodySmall)
                }
                if (activity.reminderEnabled) Icon(Icons.Outlined.Notifications, stringResource(R.string.reminder))
                IconButton(onClick = { viewModel.deleteActivity(activity) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun ActivityEditorDialog(activity: CalendarActivity?, permission: (Boolean) -> Unit, viewModel: Stage2ViewModel, dismiss: () -> Unit) {
    val initialTitle = if (activity == null) "" else activityTitle(activity)
    val initialNotes = if (activity == null) "" else if (activity.type == "manual") activity.notes else stringResource(activityNoteRes(activity.type))
    var title by remember(activity?.id, initialTitle) { mutableStateOf(initialTitle) }
    var date by remember(activity?.id) { mutableStateOf(activity?.let { Instant.ofEpochMilli(it.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() } ?: LocalDate.now().toString()) }
    var notes by remember(activity?.id, initialNotes) { mutableStateOf(initialNotes) }
    var reminder by remember(activity?.id) { mutableStateOf(activity?.reminderEnabled ?: false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(if (activity == null) R.string.add_activity else R.string.edit_activity)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (activity != null && activity.type != "manual") Text(stringResource(R.string.generated_activity_edit_note), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(title, { title = it }, enabled = activity == null || activity.type == "manual", label = { Text(stringResource(R.string.activity_title)) })
        OutlinedTextField(date, { date = it }, label = { Text(stringResource(R.string.activity_date)) }, supportingText = { Text(stringResource(R.string.date_format_hint)) })
        OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) })
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(reminder, { reminder = it; permission(it) }); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.reminder)) }
    } }, confirmButton = { TextButton(onClick = { if (viewModel.saveManualActivity(activity?.id, title.ifBlank { activity?.title.orEmpty() }, date, notes, reminder)) dismiss() }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun activityTitle(activity: CalendarActivity) = if (activity.type == "manual") activity.title else stringResource(when (activity.type) {
    "land" -> R.string.activity_land; "seed" -> R.string.activity_seed; "irrigation" -> R.string.activity_irrigation; "nutrient" -> R.string.activity_nutrient; "inspection" -> R.string.activity_inspection; "harvest" -> R.string.activity_harvest; else -> R.string.farm_activity
})
private fun activityNoteRes(type: String) = when (type) { "land" -> R.string.activity_land_note; "seed" -> R.string.activity_seed_note; "irrigation" -> R.string.activity_irrigation_note; "nutrient" -> R.string.activity_nutrient_note; "inspection" -> R.string.activity_inspection_note; else -> R.string.activity_harvest_note }

@Composable
private fun BackTitle(title: Int, back: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }; Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }

@Composable
private fun ChoiceField(label: String, value: String, values: List<String>, display: @Composable (String) -> String, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label + ": " + display(value), modifier = Modifier.weight(1f)); Icon(Icons.Outlined.ArrowDropDown, null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { values.forEach { item -> DropdownMenuItem(text = { Text(display(item)) }, onClick = { select(item); expanded = false }) } }
    }
}

@Composable private fun cropName(crop: CropReference, language: String) = if (language == "hi") crop.nameHi else crop.nameEn
@Composable private fun seasonLabel(value: String) = stringResource(when (value) { "rabi" -> R.string.season_rabi; "zaid" -> R.string.season_zaid; else -> R.string.season_kharif })
@Composable private fun waterLabel(value: String) = stringResource(when (value) { "low" -> R.string.water_low; "high" -> R.string.water_high; else -> R.string.water_medium })
@Composable private fun recommendationReason(value: String) = stringResource(when (value) {
    "season_match" -> R.string.reason_season_match; "season_mismatch" -> R.string.reason_season_mismatch; "soil_match" -> R.string.reason_soil_match; "soil_unknown" -> R.string.reason_soil_unknown; "soil_mismatch" -> R.string.reason_soil_mismatch; "low_water" -> R.string.reason_low_water; "water_match" -> R.string.reason_water_match; else -> R.string.reason_water_mismatch
})
@Composable private fun boundaryError(value: String?) = stringResource(when (value) { "range" -> R.string.boundary_range_invalid; "cross" -> R.string.boundary_cross_invalid; "small" -> R.string.boundary_small_invalid; else -> R.string.boundary_points_invalid })
@Composable private fun areaUnitLabel(value: String) = stringResource(when (value) { "acre" -> R.string.acre; "hectare" -> R.string.hectare; "bigha" -> R.string.bigha; "square_metre" -> R.string.square_metre; else -> R.string.square_foot })
@Composable private fun soilLabel(value: String) = stringResource(when (value) { "sandy" -> R.string.soil_sandy; "clay" -> R.string.soil_clay; "loamy" -> R.string.soil_loamy; "black" -> R.string.soil_black; "red" -> R.string.soil_red; else -> R.string.unknown })
@Composable private fun irrigationLabel(value: String) = stringResource(when (value) { "yes" -> R.string.irrigation_yes; "no" -> R.string.irrigation_no; else -> R.string.irrigation_seasonal })
private fun Double.clean() = if (this % 1.0 == 0.0) toLong().toString() else "%.2f".format(this)
private fun Float.clean() = if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)
