package com.pausiar.openfy.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val OpenfyFontFamily = FontFamily.SansSerif

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = OpenfyFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

/**
 * Full typographic scale. Display/headline styles are bold with tight tracking
 * (editorial feel), body is neutral, labels are medium with slight tracking.
 */
val OpenfyTypography = Typography(
    displayLarge = style(48, 52, FontWeight.Black, -1.2),
    displayMedium = style(40, 44, FontWeight.Bold, -0.9),
    displaySmall = style(34, 40, FontWeight.Bold, -0.6),
    headlineLarge = style(30, 36, FontWeight.Bold, -0.5),
    headlineMedium = style(26, 32, FontWeight.Bold, -0.4),
    headlineSmall = style(22, 28, FontWeight.Bold, -0.2),
    titleLarge = style(20, 26, FontWeight.SemiBold, -0.1),
    titleMedium = style(16, 22, FontWeight.SemiBold, 0.0),
    titleSmall = style(15, 20, FontWeight.Medium, 0.0),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.1),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.1),
    bodySmall = style(12, 16, FontWeight.Normal, 0.2),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.3),
    labelSmall = style(11, 14, FontWeight.Medium, 0.5),
)
