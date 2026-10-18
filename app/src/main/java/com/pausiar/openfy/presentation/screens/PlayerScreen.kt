package com.pausiar.openfy.presentation.screens

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pausiar.openfy.domain.models.RepeatPreference
import com.pausiar.openfy.domain.models.Track
import com.pausiar.openfy.player.controller.PlaybackProgress
import com.pausiar.openfy.presentation.components.Artwork
import com.pausiar.openfy.presentation.components.BannerTone
import com.pausiar.openfy.presentation.components.EmptyState
import com.pausiar.openfy.presentation.components.MessageBanner
import com.pausiar.openfy.presentation.components.PlatformBadge
import com.pausiar.openfy.presentation.components.PlayPauseButton
import com.pausiar.openfy.presentation.components.SecondaryButton
import com.pausiar.openfy.presentation.components.TrackRow
import com.pausiar.openfy.presentation.components.trackCountLabel
import com.pausiar.openfy.presentation.theme.OpenfyShapes
import com.pausiar.openfy.presentation.theme.OpenfySpacing
import com.pausiar.openfy.presentation.viewmodel.PlayerScreenUiState
import com.pausiar.openfy.utils.toDurationLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * Full-screen player. Position ticks are collected only inside [PlayerSeekBar], so the rest
 * of the screen recomposes only on "slow" state changes (track, play/pause, modes).
 */
@Composable
fun PlayerScreen(
    uiState: PlayerScreenUiState,
    progress: StateFlow<PlaybackProgress>,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onDismissError: () -> Unit,
) {
    val player = uiState.player
    val currentTrack = player.currentTrack
    val queue = uiState.queue
    val currentQueueIndex = remember(queue, currentTrack?.id, player.currentIndex) {
        val fromState = player.currentIndex
        if (fromState in queue.indices && queue[fromState].id == currentTrack?.id) {
            fromState
        } else {
            queue.indexOfFirst { it.id == currentTrack?.id }
        }
    }
    var showQueue by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PlayerBackdrop(imageUrl = currentTrack?.imageUrl)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = OpenfySpacing.gutterWide),
        ) {
            PlayerTopBar(
                onBack = onBack,
                onOpenQueue = { showQueue = true },
                queueEnabled = queue.isNotEmpty(),
            )

            if (currentTrack == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        title = if (player.isPreparing) "Preparando reproducción" else "No suena nada ahora",
                        subtitle = if (player.isPreparing) {
                            "Openfy está resolviendo el audio. Esto puede tardar unos segundos."
                        } else {
                            "Elige una playlist o una pista local para empezar a escuchar."
                        },
                        icon = Icons.Rounded.MusicNote,
                    )
                }
            } else {
                // Artwork: largest square that fits the remaining space.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = OpenfySpacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayerArtwork(track = currentTrack, isPlaying = player.isPlaying)
                }

                TrackHeadline(track = currentTrack)

                Spacer(Modifier.height(OpenfySpacing.lg))

                PlayerSeekBar(
                    progress = progress,
                    fallbackDurationMs = player.durationMs.takeIf { it > 0L } ?: (currentTrack.durationMs ?: 0L),
                    onSeekTo = onSeekTo,
                )

                Spacer(Modifier.height(OpenfySpacing.sm))

                PlayerControls(
                    isPlaying = player.isPlaying,
                    isLoading = player.isPreparing || player.isBuffering,
                    shuffleEnabled = player.shuffleEnabled,
                    repeatPreference = player.repeatPreference,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onCycleRepeat = onCycleRepeat,
                    onToggleShuffle = onToggleShuffle,
                )

                Spacer(Modifier.height(OpenfySpacing.lg))

                UpNextRow(
                    nextTrack = queue.getOrNull(currentQueueIndex + 1).takeIf { currentQueueIndex >= 0 && !player.shuffleEnabled },
                    queueSize = queue.size,
                    onOpenQueue = { showQueue = true },
                )

                Spacer(Modifier.height(OpenfySpacing.md))
            }
        }

        MessageBanner(
            message = player.errorMessage,
            onDismiss = onDismissError,
            tone = BannerTone.Error,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = OpenfySpacing.gutter, vertical = 64.dp),
        )
    }

    if (showQueue) {
        QueueSheet(
            queue = queue,
            currentIndex = currentQueueIndex,
            isPlaying = player.isPlaying,
            onPlayQueueItem = onPlayQueueItem,
            onDismiss = { showQueue = false },
        )
    }
}

@Composable
private fun PlayerBackdrop(imageUrl: String?) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background
    val accentGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(accentGlow, Color.Transparent))),
        )
        Crossfade(targetState = imageUrl, animationSpec = tween(600), label = "playerBackdrop") { url ->
            if (url != null) {
                val request = remember(url) {
                    ImageRequest.Builder(context).data(url).crossfade(true).build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(80.dp)
                        .graphicsLayer { alpha = if (canBlur) 0.6f else 0.18f },
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        // Fade the backdrop into the background towards the controls for legibility.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.15f),
                        0.45f to background.copy(alpha = 0.55f),
                        1f to background.copy(alpha = 0.96f),
                    ),
                ),
        )
    }
}

@Composable
private fun PlayerTopBar(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    queueEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = OpenfySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Cerrar reproductor",
                modifier = Modifier.size(30.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "REPRODUCIENDO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Openfy",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        IconButton(onClick = onOpenQueue, enabled = queueEnabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = "Ver cola de reproducción",
            )
        }
    }
}

@Composable
private fun PlayerArtwork(track: Track, isPlaying: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "artworkScale",
    )
    Crossfade(
        targetState = track.imageUrl,
        animationSpec = tween(400),
        label = "playerArtwork",
        modifier = Modifier
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) { imageUrl ->
        Artwork(
            model = imageUrl,
            contentDescription = "Portada de ${track.title}",
            modifier = Modifier
                .fillMaxSize()
                .shadow(elevation = 28.dp, shape = OpenfyShapes.artworkLarge, clip = false),
            shape = OpenfyShapes.artworkLarge,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackHeadline(track: Track) {
    AnimatedContent(
        targetState = track,
        contentKey = { it.id },
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
        label = "trackHeadline",
    ) { shown ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(OpenfySpacing.xs),
        ) {
            Text(
                text = shown.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.sm),
            ) {
                Text(
                    text = shown.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                PlatformBadge(platform = shown.platform)
            }
        }
    }
}

/**
 * Seek bar + time labels. This is the ONLY place that collects playback progress.
 * While dragging it keeps a local value and only seeks on release.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSeekBar(
    progress: StateFlow<PlaybackProgress>,
    fallbackDurationMs: Long,
    onSeekTo: (Long) -> Unit,
) {
    val playback by progress.collectAsStateWithLifecycle()
    val durationMs = if (playback.durationMs > 0L) playback.durationMs else fallbackDurationMs
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    // After releasing, hold the requested position until the next progress tick arrives.
    var pendingSeekFraction by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(pendingSeekFraction) {
        if (pendingSeekFraction != null) {
            delay(900)
            pendingSeekFraction = null
        }
    }
    val playedFraction = if (durationMs > 0L) {
        (playback.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bufferedFraction = playback.bufferedFraction
    val shownFraction = dragFraction ?: pendingSeekFraction ?: playedFraction
    val isDragging = dragFraction != null
    val shownPositionMs = (shownFraction * durationMs).toLong()

    val activeColor = MaterialTheme.colorScheme.primary
    val bufferColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val thumbColor by animateColorAsState(
        targetValue = if (isDragging) activeColor else MaterialTheme.colorScheme.onSurface,
        label = "thumbColor",
    )
    val thumbSize by animateFloatAsState(
        targetValue = if (isDragging) 18f else 12f,
        label = "thumbSize",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = shownFraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { fraction ->
                    if (durationMs > 0L) {
                        pendingSeekFraction = fraction
                        onSeekTo((fraction * durationMs).toLong())
                    }
                }
                dragFraction = null
            },
            enabled = durationMs > 0L,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = inactiveColor,
            ),
            thumb = {
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(thumbSize.dp)
                            .background(thumbColor, OpenfyShapes.pill),
                    )
                }
            },
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .drawBehind {
                            val radius = CornerRadius(size.height / 2f, size.height / 2f)
                            drawRoundRect(color = inactiveColor, cornerRadius = radius)
                            if (bufferedFraction > 0f) {
                                drawRoundRect(
                                    color = bufferColor,
                                    size = Size(size.width * bufferedFraction, size.height),
                                    cornerRadius = radius,
                                )
                            }
                            drawRoundRect(
                                color = activeColor,
                                size = Size(size.width * shownFraction, size.height),
                                cornerRadius = radius,
                            )
                        },
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Posición de reproducción"
                    stateDescription = "${shownPositionMs.toDurationLabel()} de ${durationMs.toDurationLabel()}"
                },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = shownPositionMs.toDurationLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isDragging) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (durationMs > 0L) durationMs.toDurationLabel() else "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    shuffleEnabled: Boolean,
    repeatPreference: RepeatPreference,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ModeToggleButton(
            active = shuffleEnabled,
            onClick = onToggleShuffle,
            contentDescription = if (shuffleEnabled) "Aleatorio activado" else "Aleatorio desactivado",
        ) { tint ->
            Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = tint)
        }
        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(38.dp))
        }
        PlayPauseButton(
            isPlaying = isPlaying,
            onClick = onTogglePlayPause,
            size = 72.dp,
            isLoading = isLoading && !isPlaying,
        )
        IconButton(onClick = onSkipNext, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Siguiente", modifier = Modifier.size(38.dp))
        }
        ModeToggleButton(
            active = repeatPreference != RepeatPreference.OFF,
            onClick = onCycleRepeat,
            contentDescription = repeatPreference.label,
        ) { tint ->
            Icon(
                imageVector = if (repeatPreference == RepeatPreference.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = null,
                tint = tint,
            )
        }
    }
}

/** Icon button with an "active" accent tint and a small dot underneath. */
@Composable
private fun ModeToggleButton(
    active: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    icon: @Composable (Color) -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "modeTint",
    )
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon(tint)
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(4.dp)
                    .background(if (active) tint else Color.Transparent, OpenfyShapes.pill),
            )
        }
    }
}

@Composable
private fun UpNextRow(
    nextTrack: Track?,
    queueSize: Int,
    onOpenQueue: () -> Unit,
) {
    AnimatedVisibility(
        visible = queueSize > 0,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (nextTrack != null) "A continuación" else "Cola",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = nextTrack?.let { "${it.title} · ${it.artist}" } ?: trackCountLabel(queueSize),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(OpenfySpacing.md))
            SecondaryButton(
                text = "Cola",
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                onClick = onOpenQueue,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    queue: List<Track>,
    currentIndex: Int,
    isPlaying: Boolean,
    onPlayQueueItem: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OpenfySpacing.gutter, vertical = OpenfySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Cola de reproducción",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = trackCountLabel(queue.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = OpenfySpacing.sm,
                    end = OpenfySpacing.sm,
                    bottom = OpenfySpacing.xl,
                ),
            ) {
                itemsIndexed(
                    items = queue,
                    // Index + id: a queue may legitimately contain the same track twice.
                    key = { index, track -> "$index-${track.id}" },
                    contentType = { _, _ -> "queueTrack" },
                ) { index, track ->
                    TrackRow(
                        track = track,
                        onClick = { onPlayQueueItem(index) },
                        onFavoriteClick = null,
                        isCurrent = index == currentIndex,
                        isPlaying = isPlaying,
                    )
                }
            }
        }
    }
}
