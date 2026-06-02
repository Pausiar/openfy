package com.pausiar.openfy.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pausiar.openfy.domain.models.RepeatPreference
import com.pausiar.openfy.domain.models.ThemeMode
import com.pausiar.openfy.presentation.components.EmptyStateCard
import com.pausiar.openfy.presentation.components.InfoBanner
import com.pausiar.openfy.presentation.components.ToggleChip
import com.pausiar.openfy.presentation.viewmodel.SettingsUiState
import com.pausiar.openfy.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onOpenLegal: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(localMessage, uiState.message) {
        if (localMessage != null || uiState.message != null) {
            delay(3200)
            if (localMessage != null) {
                localMessage = null
            } else {
                onDismissMessage()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            viewModel.exportBackup()
                .onSuccess { json ->
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer -> writer.write(json) }
                    localMessage = "Biblioteca exportada correctamente."
                }
                .onFailure { error ->
                    localMessage = error.message ?: "No se ha podido exportar la biblioteca."
                }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (raw.isNullOrBlank()) {
                localMessage = "El archivo seleccionado esta vacio o no se puede leer."
            } else {
                viewModel.importBackup(raw)
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text("Ajustes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Text("Tema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { themeMode ->
                    ToggleChip(label = themeMode.label, selected = uiState.settings.themeMode == themeMode, onClick = { viewModel.updateTheme(themeMode) })
                }
            }
        }
        item {
            Text("Reproduccion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Autoplay")
                Switch(checked = uiState.settings.autoplayEnabled, onCheckedChange = viewModel::updateAutoplay)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Shuffle")
                Switch(checked = uiState.settings.shuffleEnabled, onCheckedChange = viewModel::updateShuffle)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepeatPreference.entries.forEach { repeatPreference ->
                    ToggleChip(label = repeatPreference.label, selected = uiState.settings.repeatPreference == repeatPreference, onClick = { viewModel.updateRepeat(repeatPreference) })
                }
            }
        }
        item {
            Text("Biblioteca local", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { exportLauncher.launch("openfy-library.json") }) { Text("Exportar JSON") }
                Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text("Importar JSON") }
            }
        }
        item {
            Button(onClick = { showDeleteDialog = true }) { Text("Borrar datos") }
        }
        item {
            Button(onClick = onOpenLegal) { Text("Informacion legal") }
        }
        item {
            EmptyStateCard(
                title = "Creditos",
                subtitle = "Openfy usa Jetpack Compose, Room, DataStore, Coil y Media3. Spotify y YouTube se abren o consultan siempre respetando sus condiciones y los metadatos disponibles.",
            )
        }

        (localMessage ?: uiState.message)?.let { message ->
            item { InfoBanner(message = message) }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Borrar datos locales") },
            text = { Text("Se eliminaran playlists, canciones, favoritos e historial guardados en este dispositivo.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllData()
                    showDeleteDialog = false
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            },
        )
    }
}