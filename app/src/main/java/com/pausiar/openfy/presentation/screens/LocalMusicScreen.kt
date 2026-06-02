package com.pausiar.openfy.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pausiar.openfy.presentation.components.EmptyStateCard
import com.pausiar.openfy.presentation.components.InfoBanner
import com.pausiar.openfy.presentation.components.PlaylistCard
import com.pausiar.openfy.presentation.viewmodel.LocalMusicUiState
import kotlinx.coroutines.delay

@Composable
fun LocalMusicScreen(
    uiState: LocalMusicUiState,
    onPlaylistNameChange: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onPickFiles: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onDismissMessage: () -> Unit,
) {
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
            Text("Musica local", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            OutlinedTextField(
                value = uiState.playlistName,
                onValueChange = onPlaylistNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nombre de playlist local") },
                singleLine = true,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onCreatePlaylist) { Text("Crear playlist local") }
                Button(onClick = onPickFiles) { Text("Anadir archivos") }
            }
        }

        uiState.message?.let { message ->
            item { InfoBanner(message = message) }
        }

        if (uiState.playlists.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Sin colecciones locales",
                    subtitle = "Crea una playlist vacia o importa archivos de audio desde el selector del sistema.",
                )
            }
        } else {
            items(uiState.playlists, key = { it.id }) { playlist ->
                PlaylistCard(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) })
            }
        }
    }
}