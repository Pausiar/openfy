package com.pausiar.openfy.presentation.theme

import androidx.compose.ui.graphics.Color

/*
 * Openfy palette — "Ember".
 * Dark-first: deep violet-tinted near-black surfaces and a single warm coral accent.
 */

// Accent (coral / ember)
val Ember300 = Color(0xFFFF9A85)
val Ember400 = Color(0xFFFF7A62)
val Ember500 = Color(0xFFFF6A55) // primary accent (dark theme)
val Ember600 = Color(0xFFD63F28) // primary accent (light theme, higher contrast)
val Ember900 = Color(0xFF3A1A14)
val Ember50 = Color(0xFFFFE6E0)

// Warm amber used only for subtle gradients/highlights
val Amber400 = Color(0xFFFFB86B)
val Amber700 = Color(0xFFB86A12)

// Dark neutrals (violet-tinted)
val Night0 = Color(0xFF060509)
val Night50 = Color(0xFF0B0A0F) // background
val Night100 = Color(0xFF110F16) // surfaceContainerLow
val Night150 = Color(0xFF131119) // surface
val Night200 = Color(0xFF17151D) // surfaceContainer
val Night300 = Color(0xFF1E1B25) // surfaceContainerHigh
val Night400 = Color(0xFF26232E) // surfaceContainerHighest
val Night500 = Color(0xFF2C2834) // outlineVariant
val Night600 = Color(0xFF4A4555) // outline
val NightText = Color(0xFFF4F1F7)
val NightMuted = Color(0xFFA6A1B1)

// Light neutrals
val Day0 = Color(0xFFFFFFFF)
val Day50 = Color(0xFFFAF8FB) // background
val Day100 = Color(0xFFF6F3F8) // surfaceContainerLow
val Day200 = Color(0xFFF1EDF4) // surfaceContainer
val Day300 = Color(0xFFEBE7EF) // surfaceContainerHigh
val Day400 = Color(0xFFE4E0E9) // surfaceContainerHighest
val Day500 = Color(0xFFD9D4DF) // outlineVariant
val Day600 = Color(0xFF8C8696) // outline
val DayText = Color(0xFF17151C)
val DayMuted = Color(0xFF5E5968)

// Secondary (muted lavender neutral, never competes with the accent)
val Lilac200 = Color(0xFFCFC6DA)
val Lilac800 = Color(0xFF2A2633)
val Lilac100 = Color(0xFFE9E3F2)
val Lilac600 = Color(0xFF615A6E)

// Status
val ErrorDark = Color(0xFFFF5C6C)
val ErrorLight = Color(0xFFC62839)
val SuccessDark = Color(0xFF5FD39A)
val SuccessLight = Color(0xFF1E8A57)
val WarningDark = Color(0xFFFFC266)
val WarningLight = Color(0xFFA86200)

// Platform brand hints (used only in small badges)
val PlatformYouTube = Color(0xFFFF4E45)
val PlatformSpotify = Color(0xFF1ED760)
val PlatformLocal = Color(0xFF9B8CFF)
val PlatformDemo = Amber400
val PlatformUnknown = Color(0xFF8C8696)
