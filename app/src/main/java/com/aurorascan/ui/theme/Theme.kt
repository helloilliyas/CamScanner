package com.aurorascan.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Restrained identity: neutral surfaces + one branded accent (blueprint 9.1).
private val Accent = Color(0xFF2563EB)
private val AccentDark = Color(0xFF93B4FF)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    surface = Color(0xFFFDFDFD),
    background = Color(0xFFF7F8FA),
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF0B1220),
    surface = Color(0xFF14161A),
    background = Color(0xFF0E0F12),
)

@Composable
fun AuroraScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = colorScheme.background.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuroraTypography,
        content = content,
    )
}
