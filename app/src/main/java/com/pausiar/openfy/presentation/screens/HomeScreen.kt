package com.pausiar.openfy.presentation.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pausiar.openfy.domain.models.LinkType
import com.pausiar.openfy.domain.models.ParsedLink
import com.pausiar.openfy.presentation.components.BannerTone
import com.pausiar.openfy.presentation.components.CircleIconButton
import com.pausiar.openfy.presentation.components.EmptyState
import com.pausiar.openfy.presentation.components.MessageBanner
import com.pausiar.openfy.presentation.components.OpenfyCard
import com.pausiar.openfy.presentation.components.OpenfyLazyColumn
import com.pausiar.openfy.presentation.components.OpenfyTextField
import com.pausiar.openfy.presentation.components.PlatformBadge
import com.pausiar.openfy.presentation.components.PlaylistTile
import com.pausiar.openfy.presentation.components.PrimaryButton
import com.pausiar.openfy.presentation.components.ScreenHeader
import com.pausiar.openfy.presentation.components.SecondaryButton
import com.pausiar.openfy.presentation.components.SectionHeader
import com.pausiar.openfy.presentation.theme.OpenfyShapes
import com.pausiar.openfy.presentation.theme.OpenfySpacing
import com.pausiar.openfy.presentation.theme.OpenfyTheme
import com.pausiar.openfy.presentation.viewmodel.HomeUiState
import java.util.Calendar

/**
 * Vertical-only content padding: every item adds the horizontal gutter itself so the
 * "Recientes" carousel can scroll edge to edge.
 */
private val HomeContentPadding = PaddingValues(top = OpenfySpacing.lg, bottom = OpenfySpacing.xxl)

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
    val greeting = remember { timeOfDayGreeting() }

    Box(modifier = Modifier.fillMaxSize()) {
        OpenfyLazyColumn(
            contentPadding = HomeContentPadding,
            verticalArrangement = Arrangement.spacedBy(OpenfySpacing.xl),
        ) {
            item(key = "header", contentType = "header") {
                ScreenHeader(
                    title = "Openfy",
                    eyebrow = greeting,
                    subtitle = "Importa playlists, organiza tu biblioteca y escucha tu música local.",
                    modifier = Modifier.padding(horizontal = OpenfySpacing.gutter),
                    actions = {
                        CircleIconButton(
                            icon = Icons.Rounded.Settings,
                            contentDescription = "Abrir ajustes",
                            onClick = onOpenSettings,
                        )
                    },
                )
            }

            item(key = "import", contentType = "import") {
                ImportCard(
                    uiState = uiState,
                    onUrlChange = onUrlChange,
                    onImport = onImport,
                    modifier = Modifier.padding(horizontal = OpenfySpacing.gutter),
                )
            }

            item(key = "shortcuts", contentType = "shortcuts") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OpenfySpacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.md),
                ) {
                    ShortcutCard(
                        title = "Biblioteca",
                        subtitle = "Todas tus playlists",
                        icon = Icons.Rounded.LibraryMusic,
                        onClick = onOpenLibrary,
                        modifier = Modifier.weight(1f),
                    )
                    ShortcutCard(
                        title = "Música local",
                        subtitle = "Archivos del móvil",
                        icon = Icons.Rounded.PhoneAndroid,
                        onClick = onOpenLocal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (uiState.recentPlaylists.isEmpty()) {
                item(key = "empty", contentType = "state") {
                    EmptyState(
                        title = "Aún no tienes playlists",
                        subtitle = "Pega un enlace de Spotify o YouTube para importarla, o añade canciones desde tu dispositivo.",
                        action = {
                            SecondaryButton(
                                text = "Añadir música local",
                                onClick = onOpenLocal,
                                icon = Icons.Rounded.PhoneAndroid,
                            )
                        },
                    )
                }
            } else {
                item(key = "recents", contentType = "carousel") {
                    Column(verticalArrangement = Arrangement.spacedBy(OpenfySpacing.md)) {
                        SectionHeader(
                            title = "Recientes",
                            subtitle = "Tus últimas playlists",
                            actionLabel = "Ver todo",
                            onAction = onOpenLibrary,
                            modifier = Modifier.padding(horizontal = OpenfySpacing.gutter),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = OpenfySpacing.gutter),
                            horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.md),
                        ) {
                            items(
                                items = uiState.recentPlaylists,
                                key = { it.id },
                                contentType = { "playlist" },
                            ) { playlist ->
                                PlaylistTile(
                                    playlist = playlist,
                                    onClick = { onOpenPlaylist(playlist.id) },
                                )
                            }
                        }
                    }
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

/** Card with the link field, detected-link feedback and the import button. */
@Composable
private fun ImportCard(
    uiState: HomeUiState,
    onUrlChange: (String) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val link = uiState.detectedLink

    OpenfyCard(modifier = modifier.animateContentSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.md),
        ) {
            IconBubble(icon = Icons.Rounded.Link)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "Importar playlist", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Funciona con enlaces de Spotify y YouTube",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(OpenfySpacing.lg))

        OpenfyTextField(
            value = uiState.inputUrl,
            onValueChange = onUrlChange,
            placeholder = "Pega un enlace de Spotify o YouTube",
            leadingIcon = Icons.Rounded.Link,
            enabled = !uiState.isImporting,
            isError = link?.type == LinkType.INVALID,
            trailingContent = {
                if (uiState.inputUrl.isEmpty()) {
                    IconButton(
                        onClick = {
                            val pasted = clipboard.getText()?.text?.trim().orEmpty()
                            if (pasted.isNotEmpty()) onUrlChange(pasted)
                        },
                    ) {
                        Icon(imageVector = Icons.Rounded.ContentPaste, contentDescription = "Pegar desde el portapapeles")
                    }
                } else {
                    IconButton(onClick = { onUrlChange("") }, enabled = !uiState.isImporting) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Borrar enlace")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    focusManager.clearFocus()
                    onImport()
                },
            ),
        )

        if (link != null) {
            LinkFeedback(link = link)
        }

        Spacer(modifier = Modifier.height(OpenfySpacing.lg))

        PrimaryButton(
            text = if (uiState.isImporting) "Importando…" else "Importar playlist",
            onClick = {
                focusManager.clearFocus()
                onImport()
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.FileDownload,
            loading = uiState.isImporting,
        )
    }
}

/** One-line feedback about the pasted link: recognised type + platform, or an error hint. */
@Composable
private fun LinkFeedback(link: ParsedLink) {
    val isValid = link.type != LinkType.INVALID
    val accent = if (isValid) OpenfyTheme.colors.success else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = OpenfySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OpenfySpacing.sm),
    ) {
        Icon(
            imageVector = if (isValid) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = if (isValid) link.type.label else "Enlace no válido. Usa un enlace de Spotify o YouTube.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isValid) MaterialTheme.colorScheme.onSurface else accent,
            modifier = Modifier.weight(1f),
        )
        if (isValid) {
            PlatformBadge(platform = link.platform)
        }
    }
}

/** Compact quick-access card (icon on top, title and subtitle below). */
@Composable
private fun ShortcutCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OpenfyCard(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(OpenfySpacing.md + OpenfySpacing.xs),
    ) {
        IconBubble(icon = icon)
        Spacer(modifier = Modifier.height(OpenfySpacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 40dp rounded square with an accent-tinted background and accent icon. */
@Composable
private fun IconBubble(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), OpenfyShapes.row),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun timeOfDayGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 6..11 -> "Buenos días"
        in 12..19 -> "Buenas tardes"
        else -> "Buenas noches"
    }
}
