package com.pausiar.openfy.presentation.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.blur
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pausiar.openfy.domain.models.Platform
import com.pausiar.openfy.domain.models.Playlist
import com.pausiar.openfy.domain.models.Track
import com.pausiar.openfy.domain.models.TrackSort
import com.pausiar.openfy.presentation.components.Artwork
import com.pausiar.openfy.presentation.components.BannerTone
import com.pausiar.openfy.presentation.components.CircleIconButton
import com.pausiar.openfy.presentation.components.EmptyState
import com.pausiar.openfy.presentation.components.LoadingState
import com.pausiar.openfy.presentation.components.MessageBanner
import com.pausiar.openfy.presentation.components.OpenfySearchField
import com.pausiar.openfy.presentation.components.PlatformBadge
import com.pausiar.openfy.presentation.components.PrimaryButton
import com.pausiar.openfy.presentation.components.SecondaryButton
import com.pausiar.openfy.presentation.components.ToggleChip
import com.pausiar.openfy.presentation.components.TrackRow
import com.pausiar.openfy.presentation.components.trackCountLabel
import com.pausiar.openfy.presentation.theme.OpenfyShapes
import com.pausiar.openfy.presentation.theme.OpenfySpacing
import com.pausiar.openfy.presentation.viewmodel.PlaylistDetailUiState
import com.pausiar.openfy.utils.openLabel

@Composable
fun PlaylistDetailScreen(
    uiState: PlaylistDetailUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleFavorites: () -> Unit,
    onSortChange: (TrackSort) -> Unit,
    onPlay: (Boolean) -> Unit,
    onTrackClick: (Track) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
    onOpenOriginal: (String, Platform) -> Unit,
    onDismissMessage: () -> Unit,
    currentTrackId: Long? = null,
    isPlaying: Boolean = false,
) {
    val playlist = uiState.playlist
    val listState = rememberLazyListState()
    val collapseThresholdPx = with(LocalDensity.current) { 260.dp.toPx() }
    val showCompactBar by remember(collapseThresholdPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > collapseThresholdPx
        }
    }
    val totalDurationMs = remember(playlist?.tracks) { playlist?.tracks?.sumOf { it.durationMs ?: 0L } ?: 0L }
    val isFiltering = uiState.filter.query.isNotBlank() || uiState.filter.favoritesOnly

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = OpenfySpacing.xxl),
        ) {
            item(key = "hero", contentType = "hero") {
                PlaylistHero(
                    playlist = playlist,
                    totalDurationMs = totalDurationMs,
                    onPlay = onPlay,
                    onOpenOriginal = onOpenOriginal,
                )
            }

            item(key = "filters", contentType = "filters") {
                Column(
                    modifier = Modifier.padding(top = OpenfySpacing.lg, bottom = OpenfySpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OpenfySpacing.md),
                ) {
                    OpenfySearchField(
                        query = uiState.filter.query,
                        onQueryChange = onQueryChange,
                        placeholder = "Buscar en esta playlist",
                        modifier = Modifier.padding(horizontal = OpenfySpacing.gutter),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = OpenfySpacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.sm),
                    ) {
                        item(key = "favorites") {
                            ToggleChip(
                                label = "Favoritas",
                                selected = uiState.filter.favoritesOnly,
                                leadingIcon = Icons.Rounded.Favorite,
                                onClick = onToggleFavorites,
                            )
                        }
                        items(TrackSort.entries, key = { it.name }) { sort ->
                            ToggleChip(
                                label = sort.displayLabel(),
                                selected = uiState.filter.sort == sort,
                                onClick = { onSortChange(sort) },
                            )
                        }
                    }
                }
            }

            when {
                playlist == null -> item(key = "loading", contentType = "state") {
                    LoadingState(message = "Cargando playlist…")
                }

                uiState.tracks.isEmpty() -> item(key = "empty", contentType = "state") {
                    if (isFiltering) {
                        EmptyState(
                            title = "Sin resultados",
                            subtitle = "Prueba con otra búsqueda o quita el filtro de favoritas.",
                            icon = Icons.Rounded.SearchOff,
                        )
                    } else {
                        EmptyState(
                            title = "Aún no hay canciones",
                            subtitle = playlist.description?.takeIf { it.isNotBlank() }
                                ?: "Si esta playlist procede de YouTube o Spotify, revisa la configuración de APIs para enriquecer el contenido.",
                            icon = Icons.Rounded.MusicOff,
                        )
                    }
                }

                else -> items(
                    items = uiState.tracks,
                    key = { it.id },
                    contentType = { "track" },
                ) { track ->
                    TrackRow(
                        track = track,
                        onClick = { onTrackClick(track) },
                        onFavoriteClick = { onFavoriteToggle(track.id) },
                        isCurrent = track.id == currentTrackId,
                        isPlaying = isPlaying,
                        modifier = Modifier.padding(horizontal = OpenfySpacing.sm),
                    )
                }
            }
        }

        // Compact top bar that fades in once the hero scrolls away.
        AnimatedVisibility(
            visible = showCompactBar,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f))
                    .statusBarsPadding()
                    .height(60.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = playlist?.title ?: "Playlist",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 72.dp),
                )
            }
        }

        CircleIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Volver",
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = OpenfySpacing.md, top = 10.dp),
        )

        MessageBanner(
            message = uiState.message,
            onDismiss = onDismissMessage,
            tone = BannerTone.Info,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = OpenfySpacing.gutter, vertical = OpenfySpacing.md),
        )
    }
}

@Composable
private fun PlaylistHero(
    playlist: Playlist?,
    totalDurationMs: Long,
    onPlay: (Boolean) -> Unit,
    onOpenOriginal: (String, Platform) -> Unit,
) {
    val context = LocalContext.current
    val imageUrl = playlist?.imageUrl
    val backdropRequest = remember(imageUrl) {
        imageUrl?.let { ImageRequest.Builder(context).data(it).crossfade(true).build() }
    }
    // Blur is only available on Android 12+; keep the backdrop subtler on older versions.
    val backdropAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.75f else 0.3f
    val accentGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)

    Box(modifier = Modifier.fillMaxWidth()) {
        // Backdrop: blurred cover that fades out to transparent, plus an accent glow.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            0.55f to Color.Black.copy(alpha = 0.6f),
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(accentGlow, Color.Transparent))),
            )
            if (backdropRequest != null) {
                AsyncImage(
                    model = backdropRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(56.dp)
                        .graphicsLayer { alpha = backdropAlpha },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = OpenfySpacing.gutterWide,
                    end = OpenfySpacing.gutterWide,
                    top = 64.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(
                model = imageUrl,
                contentDescription = playlist?.title?.let { "Portada de $it" },
                modifier = Modifier
                    .size(216.dp)
                    .shadow(elevation = 24.dp, shape = OpenfyShapes.artworkLarge, clip = false),
                shape = OpenfyShapes.artworkLarge,
            )

            Spacer(Modifier.height(OpenfySpacing.xl))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "PLAYLIST",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = playlist?.title ?: "Playlist",
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                val description = playlist?.description
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.sm),
                    modifier = Modifier.padding(top = OpenfySpacing.xs),
                ) {
                    if (playlist != null) {
                        PlatformBadge(platform = playlist.platform)
                    }
                    Text(
                        text = buildString {
                            append(trackCountLabel(playlist?.tracks?.size ?: 0))
                            if (totalDurationMs > 0L) {
                                append(" · ")
                                append(formatTotalDuration(totalDurationMs))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(OpenfySpacing.xl - OpenfySpacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryButton(
                    text = "Reproducir",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = { onPlay(false) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(OpenfySpacing.md))
                SecondaryButton(
                    text = "Aleatorio",
                    icon = Icons.Rounded.Shuffle,
                    onClick = { onPlay(true) },
                    modifier = Modifier.weight(1f),
                )
                if (playlist != null && playlist.originalUrl.startsWith("http")) {
                    Spacer(Modifier.width(OpenfySpacing.sm))
                    CircleIconButton(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = playlist.platform.openLabel(),
                        onClick = { onOpenOriginal(playlist.originalUrl, playlist.platform) },
                        size = 48.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
        }
    }
}

private fun TrackSort.displayLabel(): String = when (this) {
    TrackSort.POSITION -> "Orden original"
    TrackSort.TITLE -> "Título"
    TrackSort.DURATION -> "Duración"
}

private fun formatTotalDuration(totalMs: Long): String {
    val totalMinutes = totalMs / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours h $minutes min"
        hours > 0 -> "$hours h"
        totalMinutes > 0 -> "$minutes min"
        else -> "${totalMs / 1000L} s"
    }
}
