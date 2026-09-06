package com.sagar.voice_shield.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val VoiceShieldDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CD7F6),
    onPrimary = Color(0xFF003640),
    primaryContainer = Color(0xFF06B6D4),
    onPrimaryContainer = Color(0xFF00424F),
    inversePrimary = Color(0xFF00687A),
    secondary = Color(0xFF4EDEA3),
    onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF00A572),
    onSecondaryContainer = Color(0xFF00311F),
    tertiary = Color(0xFFFFB3AD),
    onTertiary = Color(0xFF68000A),
    tertiaryContainer = Color(0xFFFF817A),
    onTertiaryContainer = Color(0xFF7E000F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF10131A),
    onBackground = Color(0xFFE1E2EC),
    surface = Color(0xFF10131A),
    onSurface = Color(0xFFE1E2EC),
    surfaceVariant = Color(0xFF32353D),
    onSurfaceVariant = Color(0xFFBCC9CD),
    surfaceTint = Color(0xFF4CD7F6),
    inverseSurface = Color(0xFFE1E2EC),
    inverseOnSurface = Color(0xFF2D3038),
    outline = Color(0xFF869397),
    outlineVariant = Color(0xFF3D494C),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF363941),
    surfaceDim = Color(0xFF10131A),
    surfaceContainer = Color(0xFF1D1F27),
    surfaceContainerHigh = Color(0xFF272A32),
    surfaceContainerHighest = Color(0xFF32353D),
    surfaceContainerLow = Color(0xFF191B23),
    surfaceContainerLowest = Color(0xFF0B0E15),
)

private val VoiceShieldLightColorScheme = lightColorScheme(
    primary = Color(0xFF00687A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFACEDFF),
    onPrimaryContainer = Color(0xFF001F26),
    inversePrimary = Color(0xFF4CD7F6),
    secondary = Color(0xFF006C4C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF89F8C6),
    onSecondaryContainer = Color(0xFF002114),
    tertiary = Color(0xFF904A45),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD7),
    onTertiaryContainer = Color(0xFF3B0908),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceTint = Color(0xFF00687A),
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF1F5F9),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFE2E8F0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFF1F5F9),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
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
