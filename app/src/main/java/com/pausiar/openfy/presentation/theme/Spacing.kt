package com.pausiar.openfy.presentation.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/** Spacing tokens (4dp grid). Use [gutter] for screen side padding. */
object OpenfySpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Standard horizontal screen gutter. */
    val gutter = 16.dp

    /** Wide gutter for hero/player content. */
    val gutterWide = 24.dp

    /** Gap between sections in a screen. */
    val section = 28.dp

    /** Gap between list items that are cards. */
    val listGap = 10.dp

    /** Default content padding for a screen-level LazyColumn. */
    val screenContentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
}
