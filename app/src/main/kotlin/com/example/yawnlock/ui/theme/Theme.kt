package com.example.yawnlock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Purple500,
    onPrimary = NightSurface,
    primaryContainer = Purple50,
    onPrimaryContainer = Night900,
    secondary = Purple200,
    onSecondary = Night900,
    secondaryContainer = Lilac,
    onSecondaryContainer = Purple900,
    background = NightSurface,
    onBackground = Night900,
    surface = NightSurface,
    onSurface = Night900,
    surfaceVariant = Purple50,
    onSurfaceVariant = Purple700,
    error = Color(0xFFDC2626),
    onError = NightSurface,
)

@Composable
fun YawnLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = Typography, content = content)
}
