package com.pausiar.openfy.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pausiar.openfy.domain.models.PlaylistSummary
import com.pausiar.openfy.domain.models.Track
import com.pausiar.openfy.presentation.theme.OpenfyShapes
import com.pausiar.openfy.presentation.theme.OpenfySpacing
import com.pausiar.openfy.presentation.theme.OpenfyTheme
import com.pausiar.openfy.utils.toDurationLabel

/** "1 canción" / "N canciones". */
fun trackCountLabel(count: Int): String = if (count == 1) "1 canción" else "$count canciones"

/** Generic container card (surfaceContainer + hairline border, 16dp radius). */
@Composable
fun OpenfyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(OpenfySpacing.lg),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(1.dp, OpenfyTheme.colors.hairline)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = OpenfyShapes.card,
            color = containerColor,
            border = border,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = OpenfyShapes.card,
            color = containerColor,
            border = border,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Settings-style row: leading icon bubble, title/subtitle, trailing slot. */
@Composable
fun OpenfyListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(OpenfyShapes.row)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = OpenfySpacing.md, vertical = OpenfySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.md),
    ) {
        if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, OpenfyShapes.row),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailingContent?.invoke()
    }
}

/**
 * Horizontal playlist card for lists: cover 64dp, title, platform badge + song count.
 * [trailingContent] hosts row actions (rename / delete...).
 */
@Composable
fun PlaylistCard(
    playlist: PlaylistSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = OpenfyShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, OpenfyTheme.colors.hairline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OpenfySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                model = playlist.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                shape = OpenfyShapes.artworkSmall,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.sm),
                ) {
                    PlatformBadge(platform = playlist.platform)
                    Text(
                        text = trackCountLabel(playlist.trackCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val description = playlist.description
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailingContent?.invoke()
        }
    }
}

/** Square playlist tile for horizontal carousels (LazyRow). */
@Composable
fun PlaylistTile(
    playlist: PlaylistSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 152.dp,
) {
    Column(
        modifier = modifier
            .width(width)
            .clip(OpenfyShapes.card)
            .clickable(onClick = onClick)
            .padding(bottom = OpenfySpacing.xs),
        verticalArrangement = Arrangement.spacedBy(OpenfySpacing.xs),
    ) {
        Artwork(
            model = playlist.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = OpenfyShapes.artwork,
        )
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = OpenfySpacing.xs),
        )
        Text(
            text = "${playlist.platform.displayName} · ${trackCountLabel(playlist.trackCount)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Music-app style track row: 52dp cover, title/artist, duration, favorite toggle.
 * The row has 8dp inner horizontal padding: place it with 8dp list padding so the
 * cover aligns with the 16dp screen gutter.
 *
 * @param onFavoriteClick null hides the favorite button.
 * @param isCurrent highlights the row and overlays an equalizer on the cover.
 */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onFavoriteClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val rowBackground by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = tween(220),
        label = "trackRowBg",
    )
    val titleColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        track.canPlayInApp -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(OpenfyShapes.row)
            .background(rowBackground)
            .clickable(
                onClickLabel = if (track.canPlayInApp) "Reproducir" else "Abrir en la app externa",
                onClick = onClick,
            )
            .padding(horizontal = OpenfySpacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Artwork(
                model = track.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = OpenfyShapes.artworkSmall,
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.45f), OpenfyShapes.artworkSmall),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayingIndicator(isPlaying = isPlaying, color = Color.White, size = 18.dp)
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = OpenfySpacing.md),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.xs),
            ) {
                if (!track.canPlayInApp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Se abre en la app externa",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val durationMs = track.durationMs
        if (durationMs != null && durationMs > 0L) {
            Text(
                text = durationMs.toDurationLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = if (onFavoriteClick == null && trailingContent == null) OpenfySpacing.sm else 0.dp),
            )
        }
        if (onFavoriteClick != null) {
            val favoriteTint by animateColorAsState(
                targetValue = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(180),
                label = "favoriteTint",
            )
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (track.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                    tint = favoriteTint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        trailingContent?.invoke()
    }
}
