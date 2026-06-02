package com.pausiar.openfy.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import com.pausiar.openfy.domain.models.ThemeMode

private val OpenfyDarkColorScheme = darkColorScheme(
    primary = OpenfyMint,
    secondary = OpenfyBlue,
    tertiary = OpenfyViolet,
    background = DeepBlue,
    surface = OpenfySurface,
    surfaceVariant = OpenfySurfaceAlt,
    onPrimary = DeepBlue,
    onSecondary = OpenfyText,
    onTertiary = OpenfyText,
    onBackground = OpenfyText,
    onSurface = OpenfyText,
    onSurfaceVariant = OpenfyMuted
)

private val OpenfyLightColorScheme = lightColorScheme(
    primary = OpenfyBlue,
    secondary = OpenfyMint,
    tertiary = OpenfyViolet,
    background = Color(0xFFF4F8FF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFDDE8F6),
    onPrimary = OpenfyText,
    onSecondary = DeepBlue,
    onTertiary = OpenfyText,
    onBackground = DeepBlue,
    onSurface = DeepBlue,
    onSurfaceVariant = Color(0xFF526176)
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