package com.healthdashboard.companion.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = ClinicalSurface,
    primaryContainer = IndigoLight,
    onPrimaryContainer = IndigoPrimary,
    secondary = TealAccent,
    onSecondary = ClinicalSurface,
    secondaryContainer = TealLight,
    onSecondaryContainer = TealAccent,
    background = ClinicalBackground,
    onBackground = ClinicalTextPrimary,
    surface = ClinicalSurface,
    onSurface = ClinicalTextPrimary,
    surfaceVariant = ClinicalSurfaceVariant,
    onSurfaceVariant = ClinicalTextSecondary,
    outline = ClinicalBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = IndigoPrimary,
    onPrimary = DarkTextPrimary,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = IndigoLight,
    secondary = TealAccent,
    onSecondary = DarkTextPrimary,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = TealLight,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder
)

@Composable
fun HealthDashboardCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
