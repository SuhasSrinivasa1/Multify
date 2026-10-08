package com.multify.traderpro.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFF33D6A6),
    onPrimary = Color(0xFF03251B),
    primaryContainer = Color(0xFF0C3B2D),
    onPrimaryContainer = Color(0xFF9AF4D6),
    secondary = Color(0xFF76B7FF),
    onSecondary = Color(0xFF052C52),
    secondaryContainer = Color(0xFF173B5E),
    onSecondaryContainer = Color(0xFFD4E8FF),
    tertiary = Color(0xFFFFC66D),
    error = Color(0xFFFF6B78),
    background = Color(0xFF0A0F16),
    onBackground = Color(0xFFE8EEF5),
    surface = Color(0xFF101721),
    onSurface = Color(0xFFE8EEF5),
    surfaceVariant = Color(0xFF18212D),
    onSurfaceVariant = Color(0xFFABB8C7),
    outline = Color(0xFF344354)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8FF8D1),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF0B5E9E),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF151B23),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151B23),
    surfaceVariant = Color(0xFFEAF0F6),
    onSurfaceVariant = Color(0xFF53606E),
    error = Color(0xFFBA1A1A)
)

@Composable
fun MultifyTraderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
    }
    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        shapes = androidx.compose.material3.Shapes(),
        content = content
    )
}
