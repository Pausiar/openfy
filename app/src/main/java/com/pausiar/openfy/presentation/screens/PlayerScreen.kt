package com.pausiar.openfy.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PauseCircleFilled
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pausiar.openfy.presentation.components.EmptyStateCard
import com.pausiar.openfy.presentation.components.InfoBanner
import com.pausiar.openfy.presentation.components.TrackRow
import com.pausiar.openfy.presentation.viewmodel.PlayerScreenUiState
import com.pausiar.openfy.utils.toDurationLabel
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    uiState: PlayerScreenUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onDismissError: () -> Unit,
) {
    val currentTrack = uiState.player.currentTrack

    LaunchedEffect(uiState.player.errorMessage) {
        if (uiState.player.errorMessage != null) {
            delay(3200)
            onDismissError()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                Text("Reproductor", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }

        uiState.player.errorMessage?.let { message ->
            item { InfoBanner(message = message) }
        }

        if (currentTrack == null) {
            item {
                EmptyStateCard(
                    title = "Nada sonando ahora",
                    subtitle = "Reproduce una pista local, una preview autorizada o la demo incluida para usar el mini-player y los controles multimedia.",
                )
            }
        } else {
            item {
                Text(currentTrack.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            item {
                Text(currentTrack.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Slider(
                    value = uiState.player.positionMs.toFloat().coerceAtMost(uiState.player.durationMs.coerceAtLeast(1L).toFloat()),
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..uiState.player.durationMs.coerceAtLeast(1L).toFloat(),
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(uiState.player.positionMs.toDurationLabel())
                    Text(uiState.player.durationMs.toDurationLabel())
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = "Aleatorio",
                            tint = if (uiState.player.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onSkipPrevious) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(36.dp)) }
                    IconButton(onClick = onTogglePlayPause) {
                        Icon(
                            if (uiState.player.isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                            contentDescription = if (uiState.player.isPlaying) "Pausar" else "Reproducir",
                            modifier = Modifier.size(52.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onSkipNext) { Icon(Icons.Rounded.SkipNext, contentDescription = "Siguiente", modifier = Modifier.size(36.dp)) }
                    IconButton(onClick = onCycleRepeat) {
                        Icon(Icons.Rounded.Repeat, contentDescription = "Repetir", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            item {
                Text("Cola de reproduccion", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            items(uiState.queue, key = { it.id }) { track ->
                TrackRow(track = track, onClick = {}, onFavoriteClick = {})
            }
        }
    }
}