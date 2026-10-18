package com.pausiar.openfy.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.pausiar.openfy.presentation.components.BannerTone
import com.pausiar.openfy.presentation.components.EmptyState
import com.pausiar.openfy.presentation.components.InfoCard
import com.pausiar.openfy.presentation.components.MessageBanner
import com.pausiar.openfy.presentation.components.OpenfyCard
import com.pausiar.openfy.presentation.components.OpenfyLazyColumn
import com.pausiar.openfy.presentation.components.OpenfyTextField
import com.pausiar.openfy.presentation.components.PlaylistCard
import com.pausiar.openfy.presentation.components.PrimaryButton
import com.pausiar.openfy.presentation.components.ScreenHeader
import com.pausiar.openfy.presentation.components.SecondaryButton
import com.pausiar.openfy.presentation.components.SectionHeader
import com.pausiar.openfy.presentation.theme.OpenfySpacing
import com.pausiar.openfy.presentation.viewmodel.LocalMusicUiState

@Composable
fun LocalMusicScreen(
    uiState: LocalMusicUiState,
    onPlaylistNameChange: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onPickFiles: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onDismissMessage: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Box(modifier = Modifier.fillMaxSize()) {
        OpenfyLazyColumn(
            contentPadding = OpenfySpacing.screenContentPadding,
            verticalArrangement = Arrangement.spacedBy(OpenfySpacing.lg),
        ) {
            item(key = "header", contentType = "header") {
                ScreenHeader(
                    title = "Música local",
                    subtitle = "Los archivos de audio de tu dispositivo",
                )
            }

            item(key = "info", contentType = "info") {
                InfoCard(
                    title = "Reproduce tus propios archivos",
                    body = "Añade canciones desde tu dispositivo y Openfy las organizará en playlists locales. " +
                        "Los archivos se quedan en tu móvil.",
                    icon = Icons.Rounded.PhoneAndroid,
                )
            }

            item(key = "create", contentType = "create") {
                OpenfyCard {
                    Column(verticalArrangement = Arrangement.spacedBy(OpenfySpacing.md)) {
                        Column(verticalArrangement = Arrangement.spacedBy(OpenfySpacing.xs)) {
                            Text(text = "Nueva colección", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "El nombre se usa al crear una playlist vacía y al añadir archivos. " +
                                    "Si lo dejas en blanco se usará uno por defecto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OpenfyTextField(
                            value = uiState.playlistName,
                            onValueChange = onPlaylistNameChange,
                            placeholder = "Nombre de la playlist local",
                            leadingIcon = Icons.Rounded.Edit,
                            enabled = !uiState.isImporting,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        )
                        PrimaryButton(
                            text = if (uiState.isImporting) "Importando archivos…" else "Añadir archivos de audio",
                            onClick = {
                                focusManager.clearFocus()
                                onPickFiles()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.AudioFile,
                            loading = uiState.isImporting,
                        )
                        SecondaryButton(
                            text = "Crear playlist vacía",
                            onClick = {
                                focusManager.clearFocus()
                                onCreatePlaylist()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.Add,
                            enabled = !uiState.isImporting,
                        )
                    }
                }
            }

            if (uiState.playlists.isEmpty()) {
                item(key = "empty", contentType = "state") {
                    EmptyState(
                        title = "Sin colecciones locales",
                        subtitle = "Crea una playlist vacía o añade archivos de audio con el selector del sistema.",
                        icon = Icons.Rounded.AudioFile,
                    )
                }
            } else {
                item(key = "list-header", contentType = "section") {
                    SectionHeader(
                        title = "Tus colecciones",
                        subtitle = collectionsLabel(uiState.playlists.size),
                    )
                }
                items(
                    items = uiState.playlists,
                    key = { it.id },
                    contentType = { "playlist" },
                ) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist.id) },
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
}

private fun collectionsLabel(count: Int): String =
    if (count == 1) "1 playlist local" else "$count playlists locales"
