package com.pausiar.openfy.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import com.pausiar.openfy.domain.models.LinkType
import com.pausiar.openfy.presentation.components.EmptyStateCard
import com.pausiar.openfy.presentation.components.InfoBanner
import com.pausiar.openfy.presentation.components.PlaylistCard
import com.pausiar.openfy.presentation.components.TagPill
import com.pausiar.openfy.presentation.viewmodel.HomeUiState
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onUrlChange: (String) -> Unit,
    onImport: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenSettings: () -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Openfy",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Importa playlists, organiza tu biblioteca y reproduce audio local con una experiencia moderna.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.inputUrl,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pega un enlace de Spotify o YouTube") },
                    singleLine = true,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onImport, enabled = !uiState.isImporting) {
                        Text(if (uiState.isImporting) "Importando..." else "Importar playlist")
                    }
                    Button(onClick = onOpenLibrary) { Text("Biblioteca") }
                    Button(onClick = onOpenLocal) { Text("Audio local") }
                    Button(onClick = onOpenSettings) { Text("Ajustes") }
                }
                uiState.detectedLink?.let { parsed ->
                    TagPill(label = if (parsed.type == LinkType.INVALID) "Enlace no valido" else parsed.type.label)
                }
            }
        }

        uiState.message?.let { message ->
            item { InfoBanner(message = message) }
        }

        item {
            Text(
                text = "Recientes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (uiState.recentPlaylists.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Tu biblioteca esta lista",
                    subtitle = "Importa una playlist o anade canciones locales. Openfy incluye ademas una demo reproducible desde el primer arranque.",
                )
            }
        } else {
            items(uiState.recentPlaylists, key = { it.id }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist.id) },
                )
            }
        }
    }
}