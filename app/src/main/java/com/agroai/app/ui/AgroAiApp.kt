package com.agroai.app.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agroai.app.R
import com.agroai.app.ui.screens.*

private val Destination.route get() = name.lowercase()

@Composable
fun AgroAiApp(viewModel: AppViewModel, stage2ViewModel: Stage2ViewModel, stage3ViewModel: Stage3ViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stage2State by stage2ViewModel.state.collectAsStateWithLifecycle()
    val stage3State by stage3ViewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHost = remember { SnackbarHostState() }
    val message = state.message?.let { stringResource(it.stringRes) }
    val stage2Message = stage2State.error?.let { stringResource(it.stage2StringRes) }
    val stage3Message = stage3State.error?.let { stringResource(it.stage3StringRes) }

    LaunchedEffect(state.language) {
        val locales = LocaleListCompat.forLanguageTags(state.language)
        if (AppCompatDelegate.getApplicationLocales() != locales) AppCompatDelegate.setApplicationLocales(locales)
    }
    LaunchedEffect(state.destination) {
        val target = state.destination.route
        if (navController.currentDestination?.route != target) {
            navController.navigate(target) { popUpTo(navController.graph.startDestinationId) { inclusive = target != Destination.Splash.route }; launchSingleTop = true }
        }
    }
    LaunchedEffect(message) {
        if (message != null) { snackbarHost.showSnackbar(message); viewModel.consumeMessage() }
    }
    LaunchedEffect(stage2Message) {
        if (stage2Message != null) { snackbarHost.showSnackbar(stage2Message); stage2ViewModel.clearError() }
    }
    LaunchedEffect(stage3Message) {
        if (stage3Message != null) { snackbarHost.showSnackbar(stage3Message); stage3ViewModel.clearError() }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController, startDestination = Destination.Splash.route) {
            composable(Destination.Splash.route) { SplashScreen() }
            composable(Destination.Welcome.route) { WelcomeScreen(state.language, viewModel::changeLanguage, viewModel::openLogin, viewModel::startDemo) }
            composable(Destination.Login.route) { LoginScreen(state, viewModel) }
            composable(Destination.Otp.route) { OtpScreen(state, viewModel) }
            composable(Destination.Onboarding.route) { OnboardingScreen(state, viewModel) }
            composable(Destination.Home.route) { DashboardScreen(state, stage2State, stage3State, viewModel) }
            composable(Destination.MyFarm.route) { MyFarmScreen(state, stage2State, viewModel, stage2ViewModel) }
            composable(Destination.FarmScan.route) { FarmScanScreen(state, stage2State, viewModel, stage2ViewModel) }
            composable(Destination.CropPlanner.route) { CropPlannerScreen(state, stage2State, viewModel, stage2ViewModel) }
            composable(Destination.Calculator.route) { CalculatorScreen(state, viewModel) }
            composable(Destination.Calendar.route) { FarmCalendarScreen(state, stage2State, viewModel, stage2ViewModel) }
            composable(Destination.Assistant.route) { AssistantScreen(state, stage3State, viewModel, stage3ViewModel) }
            composable(Destination.CropDoctor.route) { CropDoctorScreen(state, stage3State, viewModel, stage3ViewModel) }
            composable(Destination.Weather.route) { WeatherScreen(state, stage3State, viewModel, stage3ViewModel) }
            composable(Destination.Market.route) { MarketScreen(state, stage3State, viewModel, stage3ViewModel) }
            composable(Destination.Harvest.route) { HarvestScreen(state, stage3State, viewModel, stage3ViewModel) }
            composable(Destination.Alerts.route) { AlertsScreen(state, stage3State, viewModel, stage3ViewModel) }
            composable(Destination.Profile.route) { ProfileScreen(state, viewModel) }
            composable(Destination.EditProfile.route) { EditProfileScreen(state, viewModel) }
            composable(Destination.ComingSoon.route) { ComingSoonScreen(state, viewModel) }
        }
        SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

private val String.stage3StringRes get() = when (this) {
    "assistant_failed" -> R.string.assistant_failed
    "inspection_invalid" -> R.string.inspection_invalid
    "inspection_failed" -> R.string.inspection_failed
    "weather_failed" -> R.string.weather_failed
    "market_failed" -> R.string.market_failed
    "market_empty" -> R.string.market_empty
    else -> R.string.harvest_invalid
}

private val UiMessage.stringRes get() = when (this) {
    UiMessage.FirebaseMissing -> R.string.firebase_missing
    UiMessage.InvalidPhone -> R.string.invalid_phone
    UiMessage.InvalidOtp -> R.string.invalid_otp
    UiMessage.OtpExpired -> R.string.otp_expired
    UiMessage.TooManyRequests -> R.string.too_many_requests
    UiMessage.Network -> R.string.network_error
    UiMessage.RequiredFields -> R.string.required_fields
    UiMessage.SaveFailed -> R.string.save_failed
    UiMessage.SignedOut -> R.string.signed_out
    UiMessage.OfflineSession -> R.string.offline_verified_session
    UiMessage.LinkFailed -> R.string.link_local_data_failed
}

private val String.stage2StringRes get() = when (this) {
    "plot_invalid" -> R.string.plot_invalid
    "points" -> R.string.boundary_points_invalid
    "range" -> R.string.boundary_range_invalid
    "cross" -> R.string.boundary_cross_invalid
    "small" -> R.string.boundary_small_invalid
    "bigha_invalid" -> R.string.bigha_invalid
    "activity_invalid" -> R.string.activity_invalid
    else -> R.string.plan_invalid
}
