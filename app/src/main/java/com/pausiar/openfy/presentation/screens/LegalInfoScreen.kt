package com.pausiar.openfy.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.pausiar.openfy.presentation.components.InfoCard
import com.pausiar.openfy.presentation.components.OpenfyLazyColumn
import com.pausiar.openfy.presentation.components.OpenfyTopBar
import com.pausiar.openfy.presentation.theme.OpenfySpacing

private class LegalSection(
    val key: String,
    val title: String,
    val body: String,
    val icon: ImageVector,
)

private val LegalSections = listOf(
    LegalSection(
        key = "does",
        title = "Qué hace Openfy",
        body = "Openfy organiza los enlaces que importas, guarda metadatos en tu dispositivo y reproduce únicamente " +
            "archivos locales, demos incluidas, previews oficiales o streams autorizados.",
        icon = Icons.Rounded.CheckCircle,
    ),
    LegalSection(
        key = "doesnt",
        title = "Qué no hace Openfy",
        body = "La app no descarga audio protegido, no evita DRM, no bloquea anuncios de terceros y no intenta " +
            "reproducir el contenido completo de Spotify o YouTube sin permiso explícito.",
        icon = Icons.Rounded.Block,
    ),
    LegalSection(
        key = "platforms",
        title = "Plataformas externas",
        body = "Spotify y YouTube siguen sujetos a sus propias condiciones de uso. Cuando una canción no puede sonar " +
            "dentro de Openfy, la app te dirige a la aplicación oficial o al navegador.",
        icon = Icons.AutoMirrored.Rounded.OpenInNew,
    ),
    LegalSection(
        key = "privacy",
        title = "Privacidad y datos",
        body = "Tu biblioteca, favoritos, historial y ajustes se guardan solo en tu dispositivo y puedes exportarlos " +
            "o importarlos en formato JSON cuando quieras.",
        icon = Icons.Rounded.Lock,
    ),
)

@Composable
fun LegalInfoScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        OpenfyTopBar(
            title = "Información legal",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = OpenfySpacing.gutter),
        )
        OpenfyLazyColumn(
            contentPadding = OpenfySpacing.screenContentPadding,
            verticalArrangement = Arrangement.spacedBy(OpenfySpacing.md),
        ) {
            item(key = "intro", contentType = "intro") {
                Text(
                    text = "Openfy es un reproductor y organizador de música. Esto es lo que debes saber sobre cómo " +
                        "funciona y cómo trata tu información.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(
                items = LegalSections,
                key = { it.key },
                contentType = { "legal" },
            ) { section ->
                InfoCard(
                    title = section.title,
                    body = section.body,
                    icon = section.icon,
                )
            }
        }
    }
}
