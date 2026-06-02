package com.pausiar.openfy.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pausiar.openfy.presentation.components.EmptyStateCard

@Composable
fun LegalInfoScreen(onBack: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                Text("Uso legal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        item {
            EmptyStateCard(
                title = "Que hace Openfy",
                subtitle = "Openfy organiza enlaces importados, guarda metadatos locales y reproduce unicamente archivos locales, demos incluidas, previews oficiales o streams autorizados.",
            )
        }
        item {
            EmptyStateCard(
                title = "Que no hace Openfy",
                subtitle = "La app no descarga audio protegido, no evita DRM, no bloquea anuncios de terceros y no intenta reproducir contenido completo de Spotify o YouTube sin permiso explicito.",
            )
        }
        item {
            EmptyStateCard(
                title = "Plataformas externas",
                subtitle = "Spotify y YouTube siguen sujetos a sus propias condiciones de uso. Cuando una pista no puede sonar dentro de Openfy, la app dirige al usuario a la aplicacion oficial o al navegador.",
            )
        }
        item {
            EmptyStateCard(
                title = "Privacidad y datos",
                subtitle = "La biblioteca, favoritos, historial y ajustes se guardan localmente en el dispositivo y pueden exportarse o importarse en JSON a eleccion del usuario.",
            )
        }
    }
}