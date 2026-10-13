package com.pausiar.openfy.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.pausiar.openfy.domain.models.Platform
import com.pausiar.openfy.domain.models.PlaylistSort
import com.pausiar.openfy.domain.models.PlaylistSummary
import com.pausiar.openfy.presentation.components.BannerTone
import com.pausiar.openfy.presentation.components.EmptyState
import com.pausiar.openfy.presentation.components.MessageBanner
import com.pausiar.openfy.presentation.components.OpenfyLazyColumn
import com.pausiar.openfy.presentation.components.OpenfySearchField
import com.pausiar.openfy.presentation.components.OpenfyTextField
import com.pausiar.openfy.presentation.components.PlaylistCard
import com.pausiar.openfy.presentation.components.ScreenHeader
import com.pausiar.openfy.presentation.components.SecondaryButton
import com.pausiar.openfy.presentation.components.ToggleChip
import com.pausiar.openfy.presentation.theme.OpenfyShapes
import com.pausiar.openfy.presentation.theme.OpenfySpacing
import com.pausiar.openfy.presentation.viewmodel.LibraryUiState

/**
 * Vertical-only padding: each item adds the horizontal gutter itself so the platform
 * filter chips can scroll edge to edge.
 */
private val LibraryContentPadding = PaddingValues(top = OpenfySpacing.lg, bottom = OpenfySpacing.xxl)

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
    var deleteTarget by remember { mutableStateOf<PlaylistSummary?>(null) }

    val filter = uiState.filter
    val isFiltering = filter.query.isNotBlank() || filter.platform != null
    val selectedPlatform = when (filter.platform) {
        Platform.DEMO -> Platform.LOCAL
        else -> filter.platform
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OpenfyLazyColumn(
            contentPadding = LibraryContentPadding,
            verticalArrangement = Arrangement.spacedBy(OpenfySpacing.md),
        ) {
            item(key = "header", contentType = "header") {
                ScreenHeader(
                    title = "Biblioteca",
                    subtitle = "Todas tus playlists en un solo lugar",
                    modifier = Modifier.padding(horizontal = OpenfySpacing.gutter),
                )
            }

            item(key = "search", contentType = "search") {
                OpenfySearchField(
                    query = filter.query,
                    onQueryChange = onQueryChange,
                    placeholder = "Buscar playlists",
                    modifier = Modifier.padding(horizontal = OpenfySpacing.gutter),
                )
            }

            item(key = "platforms", contentType = "platforms") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = OpenfySpacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.sm),
                ) {
                    items(
                        items = PlatformFilters,
                        key = { it.label },
                        contentType = { "chip" },
                    ) { option ->
                        ToggleChip(
                            label = option.label,
                            selected = selectedPlatform == option.platform,
                            onClick = { onPlatformChange(option.platform) },
                        )
                    }
                }
            }

            item(key = "summary", contentType = "summary") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OpenfySpacing.gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = resultsLabel(count = uiState.playlists.size, isFiltering = isFiltering),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    SortMenu(current = filter.sort, onSortChange = onSortChange)
                }
            }

            if (uiState.playlists.isEmpty()) {
                item(key = "empty", contentType = "state") {
                    if (isFiltering) {
                        EmptyState(
                            title = "Sin resultados",
                            subtitle = "No hay playlists que coincidan con tu búsqueda o filtro.",
                            icon = Icons.Rounded.SearchOff,
                            action = {
                                SecondaryButton(
                                    text = "Quitar filtros",
                                    onClick = {
                                        onQueryChange("")
                                        onPlatformChange(null)
                                    },
                                )
                            },
                        )
                    } else {
                        EmptyState(
                            title = "Tu biblioteca está vacía",
                            subtitle = "Importa una playlist desde Inicio o crea una con tu música local.",
                            icon = Icons.Rounded.LibraryMusic,
                        )
                    }
                }
            } else {
                items(
                    items = uiState.playlists,
                    key = { it.id },
                    contentType = { "playlist" },
                ) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist.id) },
                        modifier = Modifier.padding(horizontal = OpenfySpacing.gutter),
                        trailingContent = {
                            PlaylistActionsMenu(
                                onRename = { renameTarget = playlist },
                                onDelete = { deleteTarget = playlist },
                            )
                        },
                    )
                }
            }
        }

        MessageBanner(
            message = uiState.message,
            onDismiss = onDismissMessage,
            tone = BannerTone.Info,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(OpenfySpacing.lg),
        )
    }

    renameTarget?.let { playlist ->
        RenamePlaylistDialog(
            playlist = playlist,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                onRenamePlaylist(playlist.id, newName)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            shape = OpenfyShapes.cardLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text("Eliminar playlist") },
            text = {
                Text(
                    text = "«${playlist.title}» se eliminará de tu biblioteca. Esta acción no se puede deshacer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePlaylist(playlist.id)
                        deleteTarget = null
                    },
                ) {
                    Text(text = "Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            },
        )
    }
}

private class PlatformFilterOption(val label: String, val platform: Platform?)

private val PlatformFilters = listOf(
    PlatformFilterOption("Todas", null),
    PlatformFilterOption("Spotify", Platform.SPOTIFY),
    PlatformFilterOption("YouTube", Platform.YOUTUBE),
    PlatformFilterOption("Local", Platform.LOCAL),
)

private fun resultsLabel(count: Int, isFiltering: Boolean): String = when {
    isFiltering -> if (count == 1) "1 resultado" else "$count resultados"
    else -> if (count == 1) "1 playlist" else "$count playlists"
}

/** "Ordenar" chip that opens a dropdown with the available [PlaylistSort] options. */
@Composable
private fun SortMenu(
    current: PlaylistSort,
    onSortChange: (PlaylistSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToggleChip(
            label = current.label,
            selected = false,
            leadingIcon = Icons.Rounded.SwapVert,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            PlaylistSort.entries.forEach { sort ->
                val isSelected = sort == current
                DropdownMenuItem(
                    text = {
                        Text(
                            text = sort.label,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSortChange(sort)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Overflow (three dots) menu of a playlist row: rename / delete. */
@Composable
private fun PlaylistActionsMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Más opciones",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Renombrar") },
                leadingIcon = { Icon(imageVector = Icons.Rounded.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(text = "Eliminar", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun RenamePlaylistDialog(
    playlist: PlaylistSummary,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(playlist.id) { mutableStateOf(playlist.title) }
    val canSave = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = OpenfyShapes.cardLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Renombrar playlist") },
        text = {
            OpenfyTextField(
                value = name,
                onValueChange = { name = it },
                label = "Nuevo nombre",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onConfirm(name.trim()) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = canSave) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
