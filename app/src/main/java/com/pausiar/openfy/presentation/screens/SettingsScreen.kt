package com.pausiar.openfy.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pausiar.openfy.BuildConfig
import com.pausiar.openfy.domain.models.RepeatPreference
import com.pausiar.openfy.domain.models.ThemeMode
import com.pausiar.openfy.presentation.components.BannerTone
import com.pausiar.openfy.presentation.components.InfoCard
import com.pausiar.openfy.presentation.components.MessageBanner
import com.pausiar.openfy.presentation.components.OpenfyCard
import com.pausiar.openfy.presentation.components.OpenfyLazyColumn
import com.pausiar.openfy.presentation.components.OpenfyListItem
import com.pausiar.openfy.presentation.components.ScreenHeader
import com.pausiar.openfy.presentation.components.SectionHeader
import com.pausiar.openfy.presentation.components.TagPill
import com.pausiar.openfy.presentation.components.ToggleChip
import com.pausiar.openfy.presentation.theme.OpenfyShapes
import com.pausiar.openfy.presentation.theme.OpenfySpacing
import com.pausiar.openfy.presentation.theme.OpenfyTheme
import com.pausiar.openfy.presentation.viewmodel.SettingsUiState
import com.pausiar.openfy.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val settings = uiState.settings

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = viewModel.exportBackup()
            val json = result.getOrNull()
            if (json == null) {
                localMessage = result.exceptionOrNull()?.message ?: "No se ha podido exportar la biblioteca."
            } else {
                val written = runCatching {
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("No se puede abrir el archivo de destino.")
                        stream.bufferedWriter().use { writer -> writer.write(json) }
                    }
                }
                localMessage = if (written.isSuccess) {
                    "Biblioteca exportada correctamente."
                } else {
                    "No se ha podido guardar el archivo de la copia."
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
            }.getOrNull()
            if (raw.isNullOrBlank()) {
                localMessage = "El archivo seleccionado está vacío o no se puede leer."
            } else {
                viewModel.importBackup(raw)
            }
        }
    }

    val bannerMessage = localMessage ?: uiState.message

    Box(modifier = Modifier.fillMaxSize()) {
        OpenfyLazyColumn(
            contentPadding = OpenfySpacing.screenContentPadding,
            verticalArrangement = Arrangement.spacedBy(OpenfySpacing.xl),
        ) {
            item(key = "header", contentType = "header") {
                ScreenHeader(
                    title = "Ajustes",
                    subtitle = "Personaliza Openfy a tu gusto",
                )
            }

            settingsSection(key = "appearance", title = "Apariencia") {
                AppearanceSection(
                    themeMode = settings.themeMode,
                    onThemeChange = viewModel::updateTheme,
                )
            }

            settingsSection(key = "playback", title = "Reproducción") {
                PlaybackSection(
                    autoplay = settings.autoplayEnabled,
                    shuffle = settings.shuffleEnabled,
                    repeat = settings.repeatPreference,
                    onAutoplayChange = viewModel::updateAutoplay,
                    onShuffleChange = viewModel::updateShuffle,
                    onRepeatChange = viewModel::updateRepeat,
                )
            }

            settingsSection(key = "library", title = "Biblioteca") {
                OpenfyListItem(
                    title = "Exportar copia de seguridad",
                    subtitle = "Guarda tu biblioteca y ajustes en un archivo JSON",
                    leadingIcon = Icons.Rounded.FileUpload,
                    onClick = { exportLauncher.launch("openfy-library.json") },
                    trailingContent = { ChevronIcon() },
                )
                SettingsDivider()
                OpenfyListItem(
                    title = "Importar copia de seguridad",
                    subtitle = "Restaura una copia exportada anteriormente",
                    leadingIcon = Icons.Rounded.FileDownload,
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    trailingContent = { ChevronIcon() },
                )
                SettingsDivider()
                OpenfyListItem(
                    title = "Borrar datos locales",
                    subtitle = "Elimina playlists, favoritos e historial de este dispositivo",
                    leadingIcon = Icons.Rounded.DeleteOutline,
                    onClick = { showDeleteDialog = true },
                    trailingContent = {
                        TagPill(
                            label = "Irreversible",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    },
                )
            }

            settingsSection(key = "about", title = "Información") {
                OpenfyListItem(
                    title = "Información legal",
                    subtitle = "Qué hace y qué no hace Openfy",
                    leadingIcon = Icons.Rounded.Gavel,
                    onClick = onOpenLegal,
                    trailingContent = { ChevronIcon() },
                )
                SettingsDivider()
                OpenfyListItem(
                    title = "Versión",
                    subtitle = "Openfy ${BuildConfig.VERSION_NAME}",
                    leadingIcon = Icons.Rounded.Info,
                )
            }

            item(key = "credits", contentType = "credits") {
                InfoCard(
                    title = "Créditos",
                    body = "Openfy usa Jetpack Compose, Room, DataStore, Coil y Media3. Spotify y YouTube se abren " +
                        "o consultan respetando siempre sus condiciones de uso y los metadatos disponibles.",
                )
            }
        }

        MessageBanner(
            message = bannerMessage,
            onDismiss = {
                if (localMessage != null) localMessage = null else onDismissMessage()
            },
            tone = bannerToneFor(bannerMessage),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(OpenfySpacing.lg),
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = OpenfyShapes.cardLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text("Borrar datos locales") },
            text = {
                Text(
                    text = "Se eliminarán las playlists, canciones, favoritos e historial guardados en este dispositivo, " +
                        "y los ajustes volverán a sus valores iniciales.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllData()
                        showDeleteDialog = false
                    },
                ) {
                    Text(text = "Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

/** Adds a titled section (header + rounded card) to the settings list. */
private fun LazyListScope.settingsSection(
    key: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = key, contentType = "section") {
        Column(verticalArrangement = Arrangement.spacedBy(OpenfySpacing.sm)) {
            SectionHeader(title = title)
            OpenfyCard(contentPadding = PaddingValues(OpenfySpacing.xs), content = content)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OpenfySpacing.xs)) {
        OpenfyListItem(
            title = "Tema",
            subtitle = "Elige cómo se ve Openfy",
            leadingIcon = Icons.Rounded.Palette,
        )
        FlowRow(
            modifier = Modifier.padding(start = OpenfySpacing.md, end = OpenfySpacing.md, bottom = OpenfySpacing.md),
            horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(OpenfySpacing.sm),
        ) {
            ThemeMode.entries.forEach { mode ->
                ToggleChip(
                    label = mode.label,
                    selected = themeMode == mode,
                    onClick = { onThemeChange(mode) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackSection(
    autoplay: Boolean,
    shuffle: Boolean,
    repeat: RepeatPreference,
    onAutoplayChange: (Boolean) -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatChange: (RepeatPreference) -> Unit,
) {
    Column {
        SwitchRow(
            title = "Reproducción automática",
            subtitle = "Pasa a la siguiente canción al terminar",
            icon = Icons.Rounded.PlayCircle,
            checked = autoplay,
            onCheckedChange = onAutoplayChange,
        )
        SettingsDivider()
        SwitchRow(
            title = "Modo aleatorio",
            subtitle = "Mezcla las canciones al empezar una playlist",
            icon = Icons.Rounded.Shuffle,
            checked = shuffle,
            onCheckedChange = onShuffleChange,
        )
        SettingsDivider()
        OpenfyListItem(
            title = "Repetición",
            subtitle = "Qué ocurre al llegar al final de la cola",
            leadingIcon = Icons.Rounded.Repeat,
        )
        FlowRow(
            modifier = Modifier.padding(start = OpenfySpacing.md, end = OpenfySpacing.md, bottom = OpenfySpacing.md),
            horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(OpenfySpacing.sm),
        ) {
            RepeatPreference.entries.forEach { option ->
                ToggleChip(
                    label = option.label,
                    selected = repeat == option,
                    onClick = { onRepeatChange(option) },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    OpenfyListItem(
        title = title,
        subtitle = subtitle,
        leadingIcon = icon,
        onClick = { onCheckedChange(!checked) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null)
        },
    )
}

@Composable
private fun ChevronIcon() {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = OpenfySpacing.md),
        thickness = 1.dp,
        color = OpenfyTheme.colors.hairline,
    )
}

/** Picks a banner tone from the (Spanish) status message produced by the view model. */
private fun bannerToneFor(message: String?): BannerTone = when {
    message == null -> BannerTone.Info
    message.contains("No se ") || message.contains("no se puede") -> BannerTone.Error
    message.contains("correctamente") || message.contains("borrados") -> BannerTone.Success
    else -> BannerTone.Info
}
