package com.anatomy.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Neon dark theme — Deep black + high-contrast neon accents.
 * Optimized for blind / low-vision accessibility on OLED screens.
 */
private val NeonDarkScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = TextOnNeon,
    primaryContainer = SurfaceCard,
    onPrimaryContainer = NeonCyan,
    secondary = NeonAmber,
    onSecondary = TextOnNeon,
    secondaryContainer = SurfaceMedium,
    onSecondaryContainer = NeonAmber,
    tertiary = NeonPurple,
    onTertiary = TextOnNeon,
    background = BackgroundPitch,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondary,
    error = NeonMagenta,
    onError = TextPrimary
)

@Composable
fun AnatomyAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeonDarkScheme,
        typography = AnatomyTypography,
        content = content
    )
}
