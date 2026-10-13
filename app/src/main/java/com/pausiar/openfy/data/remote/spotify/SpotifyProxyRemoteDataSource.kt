package com.pausiar.openfy.data.remote.spotify

import com.pausiar.openfy.data.remote.RemotePlaylistPayload
import com.pausiar.openfy.data.remote.RemoteTrackPayload
import com.pausiar.openfy.domain.models.Platform

class SpotifyProxyRemoteDataSource(
    private val service: SpotifyProxyService?,
) {
    /** true si hay un proxy de metadatos de Spotify configurado (OPENFY_SPOTIFY_METADATA_BASE_URL). */
    val isConfigured: Boolean get() = service != null

    suspend fun fetchPlaylist(playlistId: String, originalUrl: String): RemotePlaylistPayload? {
        val api = service ?: return null
        val playlist = api.getPlaylist(playlistId)
        return RemotePlaylistPayload(
            title = playlist.title.ifBlank { "Playlist de Spotify" },
            platform = Platform.SPOTIFY,
            originalUrl = originalUrl,
            imageUrl = playlist.imageUrl,
            description = playlist.description,
            tracks = playlist.tracks.map { track -> track.toPayload(fallbackUrl = null) },
        )
    }

    suspend fun fetchTrack(trackId: String, originalUrl: String): RemotePlaylistPayload? {
        val api = service ?: return null
        val track = api.getTrack(trackId)
        return RemotePlaylistPayload(
            title = track.title.ifBlank { "Canción de Spotify" },
            platform = Platform.SPOTIFY,
            originalUrl = originalUrl,
            imageUrl = track.imageUrl,
            description = "Importada desde Spotify mediante el servidor proxy.",
            tracks = listOf(track.toPayload(fallbackUrl = originalUrl)),
        )
    }

    private fun SpotifyTrackDto.toPayload(fallbackUrl: String?) = RemoteTrackPayload(
        title = title.ifBlank { "Canción de Spotify" },
        artist = artist.ifBlank { "Spotify" },
        album = album,
        durationMs = durationMs,
        imageUrl = imageUrl,
        originalUrl = originalUrl?.takeIf { it.isNotBlank() }
            ?: id.takeIf { it.isNotBlank() }?.let { "https://open.spotify.com/track/$it" }
            ?: fallbackUrl,
        previewUrl = previewUrl?.takeIf { it.isNotBlank() },
        platform = Platform.SPOTIFY,
        isPlayableInApp = !previewUrl.isNullOrBlank(),
    )
}
