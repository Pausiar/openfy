package com.pausiar.openfy.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pausiar.openfy.domain.models.Track
import com.pausiar.openfy.presentation.theme.OpenfyShapes
import com.pausiar.openfy.presentation.theme.OpenfySpacing
import com.pausiar.openfy.presentation.theme.OpenfyTheme

/**
 * Floating mini player shown above the bottom navigation.
 *
 * [progress] is read only inside the draw phase of the thin progress line, so
 * position ticks never recompose this composable (or its parents). Pass a lambda
 * that reads a `State<PlaybackProgress>`, e.g. `{ progressState.value.fraction }`.
 */
@Composable
fun MiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    progress: () -> Float,
    onTogglePlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    isPreparing: Boolean = false,
    onSkipNext: (() -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val trackLine = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Surface(
        onClick = onOpenPlayer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OpenfySpacing.sm)
            .semantics { contentDescription = "Abrir reproductor: ${track.title}, ${track.artist}" },
        shape = OpenfyShapes.miniPlayer,
        color = OpenfyTheme.colors.miniPlayer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, OpenfyTheme.colors.hairline),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = OpenfySpacing.sm, end = OpenfySpacing.xs, top = OpenfySpacing.sm, bottom = OpenfySpacing.sm + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = track,
                    contentKey = { it.id },
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 }) togetherWith
                            (fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 3 })
                    },
                    modifier = Modifier.weight(1f),
                    label = "miniPlayerTrack",
                ) { shownTrack ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(
                            model = shownTrack.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            shape = OpenfyShapes.artworkSmall,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = OpenfySpacing.md),
                        ) {
                            Text(
                                text = shownTrack.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = shownTrack.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                IconButton(onClick = onTogglePlayPause) {
                    if (isPreparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = accent,
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                if (onSkipNext != null) {
                    IconButton(onClick = onSkipNext) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Siguiente",
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
            // Thin progress line, drawn (not composed) from the progress lambda.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = OpenfySpacing.md)
                    .height(2.dp)
                    .drawBehind {
                        val fraction = progress().coerceIn(0f, 1f)
                        val radius = size.height / 2f
                        drawRoundRect(
                            color = trackLine,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                        )
                        if (fraction > 0f) {
                            drawRoundRect(
                                color = accent,
                                topLeft = Offset.Zero,
                                size = Size(size.width * fraction, size.height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                            )
                        }
                    },
            )
        }
    }
}
