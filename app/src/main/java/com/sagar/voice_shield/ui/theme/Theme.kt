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

private val VoiceShieldLightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF00687A),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFACEDFF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001F26),
    inversePrimary = VsPrimary,
    secondary = androidx.compose.ui.graphics.Color(0xFF006C4C),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF89F8C6),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF002114),
    tertiary = androidx.compose.ui.graphics.Color(0xFF904A45),
    onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD7),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF3B0908),
    error = androidx.compose.ui.graphics.Color(0xFFBA1A1A),
    onError = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    errorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF410002),
    background = androidx.compose.ui.graphics.Color(0xFFF8FAFC),
    onBackground = androidx.compose.ui.graphics.Color(0xFF191C1E),
    surface = androidx.compose.ui.graphics.Color(0xFFF8FAFC),
    onSurface = androidx.compose.ui.graphics.Color(0xFF191C1E),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE2E8F0),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF475569),
    surfaceTint = androidx.compose.ui.graphics.Color(0xFF00687A),
    inverseSurface = androidx.compose.ui.graphics.Color(0xFF1E293B),
    inverseOnSurface = androidx.compose.ui.graphics.Color(0xFFF1F5F9),
    outline = androidx.compose.ui.graphics.Color(0xFF94A3B8),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFCBD5E1),
    scrim = androidx.compose.ui.graphics.Color(0xFF000000),
    surfaceBright = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceDim = androidx.compose.ui.graphics.Color(0xFFE2E8F0),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFF1F5F9),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFE2E8F0),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFCBD5E1),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF8FAFC),
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
)

@Composable
fun VoiceShieldTheme(
    themeMode: String = "SYSTEM",
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode.uppercase()) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemDark
    }

    val colorScheme = if (isDark) VoiceShieldDarkColorScheme else VoiceShieldLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surfaceContainer.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VoiceShieldTypography,
        content = content
    )
}
