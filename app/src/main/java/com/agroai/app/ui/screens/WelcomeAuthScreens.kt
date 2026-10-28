package com.agroai.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agroai.app.R
import com.agroai.app.ui.AppUiState
import com.agroai.app.ui.AppViewModel
import com.agroai.app.ui.components.*

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(painterResource(R.drawable.ic_agro_logo), null, Modifier.size(88.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            CircularProgressIndicator()
            Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun WelcomeScreen(language: String, onLanguage: (String) -> Unit, onStart: () -> Unit, onDemo: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BrandHeader(language, onLanguage)
        ScreenColumn {
            Image(painterResource(R.drawable.agro_farm_illustration), null, Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.FillWidth)
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.welcome_tagline), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.welcome_tagline_hindi), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.choose_language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LanguageButtons(language, onLanguage)
            InfoCard { Text(stringResource(R.string.language_note), color = MaterialTheme.colorScheme.primary) }
            PrimaryButton(stringResource(R.string.get_started), onStart)
            OutlinedButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.try_demo)) }
            TextButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.already_account)) }
        }
    }
}

@Composable
fun LoginScreen(state: AppUiState, viewModel: AppViewModel) {
    val activity = LocalContext.current.findActivity()
    BackHandler { viewModel.openWelcome() }
    ScreenColumn {
        IconButton(onClick = viewModel::openWelcome) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
        Image(painterResource(R.drawable.ic_agro_logo), null, Modifier.size(72.dp).align(Alignment.CenterHorizontally))
        Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.login_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = state.phoneInput, onValueChange = viewModel::updatePhone, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.mobile_number)) }, placeholder = { Text(stringResource(R.string.mobile_hint)) },
            prefix = { Text(stringResource(R.string.mobile_prefix) + " ") }, leadingIcon = { Icon(Icons.Outlined.Phone, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, shape = RoundedCornerShape(11.dp),
        )
        PrimaryButton(stringResource(R.string.send_otp), { activity?.let { viewModel.requestOtp(it) } }, enabled = !state.loading && state.firebaseConfigured)
        if (!state.firebaseConfigured) InfoCard { Text(stringResource(R.string.firebase_missing), color = MaterialTheme.colorScheme.primary) }
        OutlinedButton(onClick = viewModel::continueOffline, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.continue_offline)) }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
fun OtpScreen(state: AppUiState, viewModel: AppViewModel) {
    val activity = LocalContext.current.findActivity()
    BackHandler { viewModel.navigate(com.agroai.app.ui.Destination.Login) }
    ScreenColumn {
        IconButton(onClick = { viewModel.navigate(com.agroai.app.ui.Destination.Login) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
        Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(64.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.otp_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.otp_subtitle, state.phoneInput), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = state.otpInput, onValueChange = viewModel::updateOtp, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.otp_code)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true, shape = RoundedCornerShape(11.dp),
        )
        PrimaryButton(stringResource(R.string.verify_otp), viewModel::verifyOtp, enabled = !state.loading && state.otpInput.length == 6)
        TextButton(onClick = { activity?.let { viewModel.requestOtp(it, true) } }, enabled = state.resendSeconds == 0 && !state.loading, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (state.resendSeconds > 0) stringResource(R.string.resend_in, state.resendSeconds) else stringResource(R.string.resend_otp))
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
