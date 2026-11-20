package com.multify.traderpro.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFF77D9B7),
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF00513D),
    onPrimaryContainer = Color(0xFF9AF4D6),
    secondary = Color(0xFF9BC9F3),
    onSecondary = Color(0xFF073451),
    secondaryContainer = Color(0xFF214A6C),
    onSecondaryContainer = Color(0xFFD4E8FF),
    tertiary = Color(0xFFE6C36C),
    onTertiary = Color(0xFF3C2F00),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D141B),
    onBackground = Color(0xFFE6EDF4),
    surface = Color(0xFF111A23),
    onSurface = Color(0xFFE6EDF4),
    surfaceVariant = Color(0xFF202C38),
    onSurfaceVariant = Color(0xFFB9C7D5),
    outline = Color(0xFF526273),
    outlineVariant = Color(0xFF344454)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7F0E2),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF315F83),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E8F8),
    onSecondaryContainer = Color(0xFF102F49),
    tertiary = Color(0xFF715B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF241A00),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF17212B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17212B),
    surfaceVariant = Color(0xFFE9EFF4),
    onSurfaceVariant = Color(0xFF52616E),
    outline = Color(0xFF7B8996),
    outlineVariant = Color(0xFFD6DEE6)
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
        typography = Typography(),
        shapes = androidx.compose.material3.Shapes(),
        content = content
    )
}
