package com.pausiar.openfy.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pausiar.openfy.R
import com.pausiar.openfy.domain.models.Platform
import com.pausiar.openfy.domain.models.Track
import com.pausiar.openfy.domain.models.TrackSort
import com.pausiar.openfy.presentation.components.EmptyStateCard
import com.pausiar.openfy.presentation.components.InfoBanner
import com.pausiar.openfy.presentation.components.TagPill
import com.pausiar.openfy.presentation.components.ToggleChip
import com.pausiar.openfy.presentation.components.TrackRow
import com.pausiar.openfy.presentation.viewmodel.PlaylistDetailUiState
import com.pausiar.openfy.utils.openLabel
import com.pausiar.openfy.utils.toDurationLabel
import kotlinx.coroutines.delay

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
) {
    val playlist = uiState.playlist

    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            delay(3200)
            onDismissMessage()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                Text(
                    text = playlist?.title ?: "Playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AsyncImage(
                    model = playlist?.imageUrl ?: R.drawable.openfy_logo,
                    contentDescription = playlist?.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    text = playlist?.description ?: "Biblioteca musical organizada en Openfy.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    playlist?.platform?.let { TagPill(label = it.displayName) }
                    TagPill(label = "${playlist?.tracks?.size ?: 0} pistas")
                    TagPill(label = playlist?.tracks?.sumOf { it.durationMs ?: 0L }.toDurationLabel())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { onPlay(false) }) { Text("Reproducir") }
                    Button(onClick = { onPlay(true) }) { Text("Aleatorio") }
                    if (playlist != null && playlist.originalUrl.startsWith("http")) {
                        Button(onClick = { onOpenOriginal(playlist.originalUrl, playlist.platform) }) {
                            Text(playlist.platform.openLabel())
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = uiState.filter.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar canciones") },
                singleLine = true,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip(label = "Favoritas", selected = uiState.filter.favoritesOnly, onClick = onToggleFavorites)
                TrackSort.entries.forEach { sort ->
                    ToggleChip(label = sort.label, selected = uiState.filter.sort == sort, onClick = { onSortChange(sort) })
                }
            }
        }

        uiState.message?.let { message ->
            item { InfoBanner(message = message) }
        }

        if (uiState.tracks.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Sin pistas visibles",
                    subtitle = playlist?.description ?: "Si esta playlist procede de YouTube o Spotify, revisa la configuracion de APIs para enriquecer el contenido.",
                )
            }
        } else {
            items(uiState.tracks, key = { it.id }) { track ->
                TrackRow(track = track, onClick = { onTrackClick(track) }, onFavoriteClick = { onFavoriteToggle(track.id) })
            }
        }
    }
}