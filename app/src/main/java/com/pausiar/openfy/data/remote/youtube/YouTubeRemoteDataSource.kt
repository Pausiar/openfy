package com.pausiar.openfy.data.remote.youtube

import com.pausiar.openfy.data.remote.RemotePlaylistPayload
import com.pausiar.openfy.data.remote.RemoteTrackPayload
import com.pausiar.openfy.domain.models.Platform
import java.time.Duration

class YouTubeRemoteDataSource(
    private val service: YouTubeApiService,
    private val apiKey: String,
) {
    suspend fun fetchPlaylist(playlistId: String, originalUrl: String): RemotePlaylistPayload? {
        if (apiKey.isBlank()) return null

        val playlist = service.getPlaylist(id = playlistId, key = apiKey).items.firstOrNull() ?: return null
        val items = service.getPlaylistItems(playlistId = playlistId, key = apiKey).items
        val videoIds = items.mapNotNull { item ->
            item.contentDetails?.videoId ?: item.snippet?.resourceId?.videoId
        }
        val videoMap = if (videoIds.isNotEmpty()) {
            service.getVideos(ids = videoIds.joinToString(","), key = apiKey).items.associateBy { it.id.orEmpty() }
        } else {
            emptyMap()
        }

        return RemotePlaylistPayload(
            title = playlist.snippet?.title ?: "Playlist de YouTube",
            platform = Platform.YOUTUBE,
            originalUrl = originalUrl,
            imageUrl = playlist.snippet?.thumbnails?.bestUrl(),
            description = playlist.snippet?.description,
            tracks = items.mapIndexedNotNull { index, item ->
                val videoId = item.contentDetails?.videoId ?: item.snippet?.resourceId?.videoId ?: return@mapIndexedNotNull null
                val video = videoMap[videoId]
                RemoteTrackPayload(
                    title = item.snippet?.title ?: "Video ${index + 1}",
                    artist = item.snippet?.videoOwnerChannelTitle ?: item.snippet?.channelTitle ?: "YouTube",
                    album = null,
                    durationMs = video?.contentDetails?.duration?.toDurationMillis(),
                    imageUrl = item.snippet?.thumbnails?.bestUrl() ?: video?.snippet?.thumbnails?.bestUrl(),
                    originalUrl = "https://www.youtube.com/watch?v=$videoId",
                    platform = Platform.YOUTUBE,
                    isPlayableInApp = true,
                )
            },
        )
    }

    suspend fun fetchVideo(videoId: String, originalUrl: String): RemotePlaylistPayload? {
        if (apiKey.isBlank()) return null

        val video = service.getVideos(ids = videoId, key = apiKey).items.firstOrNull() ?: return null
        return RemotePlaylistPayload(
            title = video.snippet?.title ?: "Video de YouTube",
            platform = Platform.YOUTUBE,
            originalUrl = originalUrl,
            imageUrl = video.snippet?.thumbnails?.bestUrl(),
            description = video.snippet?.description,
            tracks = listOf(
                RemoteTrackPayload(
                    title = video.snippet?.title ?: "Video de YouTube",
                    artist = video.snippet?.videoOwnerChannelTitle ?: video.snippet?.channelTitle ?: "YouTube",
                    album = null,
                    durationMs = video.contentDetails?.duration?.toDurationMillis(),
                    imageUrl = video.snippet?.thumbnails?.bestUrl(),
                    originalUrl = originalUrl,
                    platform = Platform.YOUTUBE,
                    isPlayableInApp = true,
                )
            ),
        )
    }
}

private fun String.toDurationMillis(): Long? = runCatching { Duration.parse(this).toMillis() }.getOrNull()