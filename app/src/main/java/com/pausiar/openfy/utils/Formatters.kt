package com.pausiar.openfy.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.pausiar.openfy.domain.models.Platform
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun Long?.toDurationLabel(): String {
    val safeValue = (this ?: 0L).coerceAtLeast(0L)
    val totalSeconds = safeValue / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

fun Long.toDateLabel(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))

fun Context.openInPreferredApp(url: String, platform: Platform) {
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val preferredPackage = when (platform) {
        Platform.SPOTIFY -> "com.spotify.music"
        Platform.YOUTUBE -> "com.google.android.youtube"
        else -> null
    }
    if (preferredPackage == null) {
        startActivity(webIntent)
        return
    }

    val preferredIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .setPackage(preferredPackage)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(preferredIntent) }
        .onFailure { startActivity(webIntent) }
}

fun Platform.openLabel(): String = when (this) {
    Platform.SPOTIFY -> "Abrir en Spotify"
    Platform.YOUTUBE -> "Abrir en YouTube"
    else -> "Abrir origen"
}