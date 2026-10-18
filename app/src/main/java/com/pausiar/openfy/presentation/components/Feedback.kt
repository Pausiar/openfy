package com.pausiar.openfy.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pausiar.openfy.presentation.theme.OpenfyShapes
import com.pausiar.openfy.presentation.theme.OpenfySpacing
import com.pausiar.openfy.presentation.theme.OpenfyTheme
import kotlinx.coroutines.delay

enum class BannerTone { Info, Success, Error }

/** Centered empty state with icon bubble, title, subtitle and optional action. */
@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.LibraryMusic,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OpenfySpacing.xl, vertical = OpenfySpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OpenfySpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), OpenfyShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = OpenfySpacing.sm),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Box(modifier = Modifier.padding(top = OpenfySpacing.md)) { action() }
        }
    }
}

/** Left-aligned information card (title + body), optional leading icon. */
@Composable
fun InfoCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = OpenfyShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, OpenfyTheme.colors.hairline),
    ) {
        Row(
            modifier = Modifier.padding(OpenfySpacing.lg + OpenfySpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.md),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Kept for compatibility: same as [InfoCard]. */
@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    InfoCard(title = title, body = subtitle, modifier = modifier)
}

/** Inline banner for status messages. */
@Composable
fun InfoBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    onDismiss: (() -> Unit)? = null,
) {
    val (icon, accent) = when (tone) {
        BannerTone.Info -> Icons.Rounded.Info to MaterialTheme.colorScheme.primary
        BannerTone.Success -> Icons.Rounded.CheckCircle to OpenfyTheme.colors.success
        BannerTone.Error -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = OpenfyShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = OpenfySpacing.lg, end = OpenfySpacing.xs, top = OpenfySpacing.md, bottom = OpenfySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.md),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (onDismiss == null) OpenfySpacing.md else 0.dp),
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cerrar mensaje",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Calls [onDismiss] [durationMillis] after a non-null [message] appears. */
@Composable
fun AutoDismissEffect(
    message: String?,
    onDismiss: () -> Unit,
    durationMillis: Long = 3200L,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        if (message != null) {
            delay(durationMillis)
            currentOnDismiss()
        }
    }
}

/**
 * Animated, auto-dismissing toast-like banner. Place it in a Box aligned to the
 * bottom (or top) of the screen; it keeps showing the last message while animating out.
 */
@Composable
fun MessageBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    autoDismissMillis: Long? = 3200L,
) {
    if (autoDismissMillis != null) {
        AutoDismissEffect(message = message, onDismiss = onDismiss, durationMillis = autoDismissMillis)
    }
    var lastMessage by remember { mutableStateOf(message) }
    SideEffect {
        if (message != null) lastMessage = message
    }
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        InfoBanner(
            message = message ?: lastMessage.orEmpty(),
            tone = tone,
            onDismiss = onDismiss,
        )
    }
}

/** Centered spinner with optional message. */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(OpenfySpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OpenfySpacing.lg),
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp),
        )
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
