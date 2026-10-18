package com.pausiar.openfy.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pausiar.openfy.presentation.theme.OpenfyShapes

/**
 * Cover art with a graceful gradient placeholder and Coil crossfade.
 * [model] can be a URL, Uri, drawable res id or null (placeholder only).
 */
@Composable
fun Artwork(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = OpenfyShapes.artwork,
    placeholderIcon: ImageVector = Icons.Rounded.MusicNote,
) {
    val context = LocalContext.current
    val request = remember(model) {
        model?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .build()
        }
    }
    val placeholderBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(placeholderBrush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = placeholderIcon,
            contentDescription = if (request == null) contentDescription else null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxSize(0.38f),
        )
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * Small animated equalizer used to flag the track that is currently playing.
 * Animation values are read in the draw phase only (no recomposition per frame).
 */
@Composable
fun PlayingIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 16.dp,
) {
    if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "equalizer")
        val bar1 = transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 420, easing = LinearEasing), RepeatMode.Reverse),
            label = "bar1",
        )
        val bar2 = transition.animateFloat(
            initialValue = 0.9f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 360, easing = LinearEasing), RepeatMode.Reverse),
            label = "bar2",
        )
        val bar3 = transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 500, easing = LinearEasing), RepeatMode.Reverse),
            label = "bar3",
        )
        EqualizerBars(bar1, bar2, bar3, color, modifier.size(size))
    } else {
        Canvas(modifier = modifier.size(size)) {
            drawBars(listOf(0.35f, 0.6f, 0.45f), color)
        }
    }
}

@Composable
private fun EqualizerBars(
    bar1: State<Float>,
    bar2: State<Float>,
    bar3: State<Float>,
    color: Color,
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        drawBars(listOf(bar1.value, bar2.value, bar3.value), color)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBars(heights: List<Float>, color: Color) {
    val gap = size.width * 0.14f
    val barWidth = (size.width - gap * (heights.size - 1)) / heights.size
    heights.forEachIndexed { index, fraction ->
        val barHeight = size.height * fraction.coerceIn(0.1f, 1f)
        drawRoundRect(
            color = color,
            topLeft = Offset(x = index * (barWidth + gap), y = size.height - barHeight),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
        )
    }
}
