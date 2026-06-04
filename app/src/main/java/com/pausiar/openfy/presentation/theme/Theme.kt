package com.pausiar.openfy.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.pausiar.openfy.domain.models.ThemeMode

private val OpenfyDarkColorScheme = darkColorScheme(
    primary = OpenfyAccent,
    secondary = OpenfyCyan,
    tertiary = OpenfyAccentHover,
    error = OpenfyError,
    background = OpenfyBackground,
    surface = OpenfySurface,
    surfaceVariant = OpenfySurfaceAlt,
    onPrimary = OpenfyBackground,
    onSecondary = OpenfyBackground,
    onTertiary = OpenfyBackground,
    onError = OpenfyText,
    onBackground = OpenfyText,
    onSurface = OpenfyText,
    onSurfaceVariant = OpenfyMuted
)

private val OpenfyLightColorScheme = lightColorScheme(
    primary = OpenfyAccent,
    secondary = OpenfyCyan,
    tertiary = OpenfyAccentHover,
    error = OpenfyError,
    background = OpenfyLightBackground,
    surface = OpenfyLightSurface,
    surfaceVariant = OpenfyLightSurfaceAlt,
    onPrimary = OpenfyBackground,
    onSecondary = OpenfyBackground,
    onTertiary = OpenfyBackground,
    onError = OpenfyText,
    onBackground = OpenfyLightText,
    onSurface = OpenfyLightText,
    onSurfaceVariant = OpenfyLightMuted
)

@Composable
fun OpenfyTheme(themeMode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) OpenfyDarkColorScheme else OpenfyLightColorScheme,
        typography = OpenfyTypography,
        content = content
    )
}
