package com.pausiar.openfy.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pausiar.openfy.domain.models.Platform
import com.pausiar.openfy.domain.models.PlaylistSort
import com.pausiar.openfy.domain.models.PlaylistSummary
import com.pausiar.openfy.presentation.components.EmptyStateCard
import com.pausiar.openfy.presentation.components.InfoBanner
import com.pausiar.openfy.presentation.components.PlaylistCard
import com.pausiar.openfy.presentation.components.ToggleChip
import com.pausiar.openfy.presentation.viewmodel.LibraryUiState
import kotlinx.coroutines.delay

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onPlatformChange: (Platform?) -> Unit,
    onSortChange: (PlaylistSort) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var renameTarget by remember { mutableStateOf<PlaylistSummary?>(null) }
    var renameText by remember { mutableStateOf("") }

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
            Text("Biblioteca", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            OutlinedTextField(
                value = uiState.filter.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar playlists") },
                singleLine = true,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip(label = "Todas", selected = uiState.filter.platform == null) { onPlatformChange(null) }
                ToggleChip(label = "Spotify", selected = uiState.filter.platform == Platform.SPOTIFY) { onPlatformChange(Platform.SPOTIFY) }
                ToggleChip(label = "YouTube", selected = uiState.filter.platform == Platform.YOUTUBE) { onPlatformChange(Platform.YOUTUBE) }
                ToggleChip(label = "Local", selected = uiState.filter.platform == Platform.LOCAL || uiState.filter.platform == Platform.DEMO) { onPlatformChange(Platform.LOCAL) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaylistSort.entries.forEach { sort ->
                    ToggleChip(
                        label = sort.label,
                        selected = uiState.filter.sort == sort,
                        onClick = { onSortChange(sort) },
                    )
                }
            }
        }

        uiState.message?.let { message ->
            item { InfoBanner(message = message) }
        }

        if (uiState.playlists.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No hay playlists en esta vista",
                    subtitle = "Prueba otro filtro o vuelve a Inicio para importar un enlace nuevo.",
                )
            }
        } else {
            items(uiState.playlists, key = { it.id }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist.id) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                renameTarget = playlist
                                renameText = playlist.title
                            }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Renombrar")
                            }
                            IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar")
                            }
                        }
                    },
                )
            }
        }
    }

    renameTarget?.let { playlist ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renombrar playlist") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nuevo nombre") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenamePlaylist(playlist.id, renameText)
                    renameTarget = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancelar") }
            },
        )
    }
}