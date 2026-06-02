package com.pausiar.openfy.data.remote.spotify

import com.pausiar.openfy.data.remote.RemotePlaylistPayload
import com.pausiar.openfy.data.remote.RemoteTrackPayload
import com.pausiar.openfy.domain.models.Platform

class SpotifyProxyRemoteDataSource(
    private val service: SpotifyProxyService?,
) {
    suspend fun fetchPlaylist(playlistId: String, originalUrl: String): RemotePlaylistPayload? {
        val api = service ?: return null
        val playlist = api.getPlaylist(playlistId)
        return RemotePlaylistPayload(
            title = playlist.title,
            platform = Platform.SPOTIFY,
            originalUrl = playlist.originalUrl ?: originalUrl,
            imageUrl = playlist.imageUrl,
            description = playlist.description,
            tracks = playlist.tracks.map { track ->
                RemoteTrackPayload(
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    imageUrl = track.imageUrl,
                    originalUrl = track.originalUrl,
                    previewUrl = track.previewUrl,
                    platform = Platform.SPOTIFY,
                    isPlayableInApp = !track.previewUrl.isNullOrBlank(),
                )
            },
        )
    }

    suspend fun fetchTrack(trackId: String, originalUrl: String): RemotePlaylistPayload? {
        val api = service ?: return null
        val track = api.getTrack(trackId)
        return RemotePlaylistPayload(
            title = track.title,
            platform = Platform.SPOTIFY,
            originalUrl = track.originalUrl ?: originalUrl,
            imageUrl = track.imageUrl,
            description = "Importado desde Spotify via backend seguro.",
            tracks = listOf(
                RemoteTrackPayload(
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    imageUrl = track.imageUrl,
                    originalUrl = track.originalUrl ?: originalUrl,
                    previewUrl = track.previewUrl,
                    platform = Platform.SPOTIFY,
                    isPlayableInApp = !track.previewUrl.isNullOrBlank(),
                )
            ),
        )
    }
}