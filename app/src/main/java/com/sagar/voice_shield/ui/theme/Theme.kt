package com.sagar.voice_shield.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VoiceShieldDarkColorScheme = darkColorScheme(
    primary = VsPrimary,
    onPrimary = VsOnPrimary,
    primaryContainer = VsPrimaryContainer,
    onPrimaryContainer = VsOnPrimaryContainer,
    inversePrimary = VsInversePrimary,
    secondary = VsSecondary,
    onSecondary = VsOnSecondary,
    secondaryContainer = VsSecondaryContainer,
    onSecondaryContainer = VsOnSecondaryContainer,
    tertiary = VsTertiary,
    onTertiary = VsOnTertiary,
    tertiaryContainer = VsTertiaryContainer,
    onTertiaryContainer = VsOnTertiaryContainer,
    error = VsError,
    onError = VsOnError,
    errorContainer = VsErrorContainer,
    onErrorContainer = VsOnErrorContainer,
    background = VsBackground,
    onBackground = VsOnBackground,
    surface = VsSurface,
    onSurface = VsOnSurface,
    surfaceVariant = VsSurfaceVariant,
    onSurfaceVariant = VsOnSurfaceVariant,
    surfaceTint = VsSurfaceTint,
    inverseSurface = VsInverseSurface,
    inverseOnSurface = VsInverseOnSurface,
    outline = VsOutline,
    outlineVariant = VsOutlineVariant,
    scrim = VsScrim,
    surfaceBright = VsSurfaceBright,
    surfaceDim = VsSurfaceDim,
    surfaceContainer = VsSurfaceContainer,
    surfaceContainerHigh = VsSurfaceContainerHigh,
    surfaceContainerHighest = VsSurfaceContainerHighest,
    surfaceContainerLow = VsSurfaceContainerLow,
    surfaceContainerLowest = VsSurfaceContainerLowest,
)

@Composable
fun VoiceShieldTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = VoiceShieldDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = VsBackground.toArgb()
            window.navigationBarColor = VsSurfaceContainer.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VoiceShieldTypography,
        content = content
    )
}
