package com.pausiar.openfy.presentation.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.pausiar.openfy.domain.models.Platform
import com.pausiar.openfy.domain.models.ThemeMode

private val OpenfyDarkColorScheme = darkColorScheme(
    primary = Ember500,
    onPrimary = Color(0xFF1C0A06),
    primaryContainer = Ember900,
    onPrimaryContainer = Ember50,
    inversePrimary = Ember600,
    secondary = Lilac200,
    onSecondary = Night50,
    secondaryContainer = Lilac800,
    onSecondaryContainer = Lilac100,
    tertiary = Amber400,
    onTertiary = Color(0xFF2A1600),
    tertiaryContainer = Color(0xFF3B2610),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = Night50,
    onBackground = NightText,
    surface = Night150,
    onSurface = NightText,
    surfaceVariant = Night400,
    onSurfaceVariant = NightMuted,
    surfaceTint = Ember500,
    inverseSurface = NightText,
    inverseOnSurface = Night150,
    error = ErrorDark,
    onError = Color(0xFF2B0006),
    errorContainer = Color(0xFF4A1219),
    onErrorContainer = Color(0xFFFFDADC),
    outline = Night600,
    outlineVariant = Night500,
    scrim = Color.Black,
    surfaceBright = Night400,
    surfaceDim = Night50,
    surfaceContainerLowest = Night0,
    surfaceContainerLow = Night100,
    surfaceContainer = Night200,
    surfaceContainerHigh = Night300,
    surfaceContainerHighest = Night400,
)

private val OpenfyLightColorScheme = lightColorScheme(
    primary = Ember600,
    onPrimary = Color.White,
    primaryContainer = Ember50,
    onPrimaryContainer = Color(0xFF3D0D04),
    inversePrimary = Ember400,
    secondary = Lilac600,
    onSecondary = Color.White,
    secondaryContainer = Lilac100,
    onSecondaryContainer = Color(0xFF1E1A26),
    tertiary = Amber700,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3C2),
    onTertiaryContainer = Color(0xFF2E1800),
    background = Day50,
    onBackground = DayText,
    surface = Day0,
    onSurface = DayText,
    surfaceVariant = Day300,
    onSurfaceVariant = DayMuted,
    surfaceTint = Ember600,
    inverseSurface = Color(0xFF2B2832),
    inverseOnSurface = Day100,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDADC),
    onErrorContainer = Color(0xFF410009),
    outline = Day600,
    outlineVariant = Day500,
    scrim = Color.Black,
    surfaceBright = Day0,
    surfaceDim = Day400,
    surfaceContainerLowest = Day0,
    surfaceContainerLow = Day100,
    surfaceContainer = Day200,
    surfaceContainerHigh = Day300,
    surfaceContainerHighest = Day400,
)

/** Colors that are not part of the Material [ColorScheme]. */
@Immutable
data class OpenfyColors(
    val isDark: Boolean,
    /** Top color of the app background gradient (accent tinted). */
    val backgroundGlow: Color,
    /** Secondary warm glow for the background gradient. */
    val backgroundGlowSecondary: Color,
    /** Hairline divider/border color. */
    val hairline: Color,
    /** Color for the floating mini player container. */
    val miniPlayer: Color,
    /** Bottom navigation container color. */
    val navBar: Color,
    val success: Color,
    val warning: Color,
    val youtube: Color,
    val spotify: Color,
    val local: Color,
    val demo: Color,
    val unknown: Color,
) {
    fun platformColor(platform: Platform): Color = when (platform) {
        Platform.YOUTUBE -> youtube
        Platform.SPOTIFY -> spotify
        Platform.LOCAL -> local
        Platform.DEMO -> demo
        Platform.UNKNOWN -> unknown
    }
}

private val DarkOpenfyColors = OpenfyColors(
    isDark = true,
    backgroundGlow = Ember500.copy(alpha = 0.16f),
    backgroundGlowSecondary = PlatformLocal.copy(alpha = 0.08f),
    hairline = Color.White.copy(alpha = 0.07f),
    miniPlayer = Night300,
    navBar = Night50.copy(alpha = 0.97f),
    success = SuccessDark,
    warning = WarningDark,
    youtube = PlatformYouTube,
    spotify = PlatformSpotify,
    local = PlatformLocal,
    demo = PlatformDemo,
    unknown = PlatformUnknown,
)

private val LightOpenfyColors = OpenfyColors(
    isDark = false,
    backgroundGlow = Ember600.copy(alpha = 0.10f),
    backgroundGlowSecondary = PlatformLocal.copy(alpha = 0.08f),
    hairline = Color.Black.copy(alpha = 0.08f),
    miniPlayer = Day0,
    navBar = Day0.copy(alpha = 0.97f),
    success = SuccessLight,
    warning = WarningLight,
    youtube = Color(0xFFD9271E),
    spotify = Color(0xFF138A3E),
    local = Color(0xFF5B4BD6),
    demo = Amber700,
    unknown = Day600,
)

val LocalOpenfyColors = staticCompositionLocalOf { DarkOpenfyColors }

/** Accessors for Openfy design tokens: `OpenfyTheme.colors`, `OpenfyTheme.spacing`, `OpenfyTheme.shapes`. */
object OpenfyTheme {
    val colors: OpenfyColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOpenfyColors.current

    val spacing: OpenfySpacing get() = OpenfySpacing

    val shapes: OpenfyShapes get() = OpenfyShapes
}

@Composable
fun OpenfyTheme(themeMode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !useDarkTheme
            controller.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    CompositionLocalProvider(LocalOpenfyColors provides if (useDarkTheme) DarkOpenfyColors else LightOpenfyColors) {
        MaterialTheme(
            colorScheme = if (useDarkTheme) OpenfyDarkColorScheme else OpenfyLightColorScheme,
            typography = OpenfyTypography,
            shapes = OpenfyMaterialShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
