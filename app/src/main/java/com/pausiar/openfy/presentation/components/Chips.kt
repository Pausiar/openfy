package com.pausiar.openfy.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pausiar.openfy.domain.models.Platform
import com.pausiar.openfy.presentation.theme.OpenfyShapes
import com.pausiar.openfy.presentation.theme.OpenfyTheme

/**
 * Selectable pill used for filters / segmented choices.
 * `onClick` is the last parameter so it can be used as a trailing lambda.
 */
@Composable
fun ToggleChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(180),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(180),
        label = "chipContent",
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 36.dp)
            .semantics {
                this.selected = selected
            },
        shape = OpenfyShapes.pill,
        color = container,
        contentColor = content,
        border = if (selected) null else BorderStroke(1.dp, OpenfyTheme.colors.hairline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Small, non-interactive metadata pill. */
@Composable
fun TagPill(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        modifier = modifier,
        shape = OpenfyShapes.pill,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Platform origin badge: colored dot + name, tinted with the platform color. */
@Composable
fun PlatformBadge(
    platform: Platform,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val platformColor = OpenfyTheme.colors.platformColor(platform)
    Surface(
        modifier = modifier,
        shape = OpenfyShapes.pill,
        color = platformColor.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (showLabel) 10.dp else 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(platformColor, OpenfyShapes.pill),
            )
            if (showLabel) {
                Text(
                    text = platform.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
