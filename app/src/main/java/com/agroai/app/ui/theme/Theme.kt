package com.agroai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AgroGreen = Color(0xFF0F5132)
val AgroAccent = Color(0xFF198754)
val AgroBackground = Color(0xFFF9FAF8)
val AgroSoft = Color(0xFFF4F9F6)
val AgroInk = Color(0xFF1A1D1A)

private val LightColors = lightColorScheme(
    primary = AgroGreen, onPrimary = Color.White, secondary = AgroAccent,
    background = AgroBackground, surface = Color.White, surfaceVariant = AgroSoft,
    onBackground = AgroInk, onSurface = AgroInk,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DD3A5), onPrimary = Color(0xFF00391F), secondary = Color(0xFF73D196),
    background = Color(0xFF111512), surface = Color(0xFF1A211C), surfaceVariant = Color(0xFF243128),
    onBackground = Color(0xFFE5EAE6), onSurface = Color(0xFFE5EAE6),
)

@Composable
fun AgroAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
