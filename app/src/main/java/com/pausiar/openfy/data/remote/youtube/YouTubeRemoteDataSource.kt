package com.pausiar.openfy.data.remote.youtube

import com.pausiar.openfy.data.remote.RemotePlaylistPayload
import com.pausiar.openfy.data.remote.RemoteTrackPayload
import com.pausiar.openfy.domain.models.Platform
import java.time.Duration

class YouTubeRemoteDataSource(
    private val service: YouTubeApiService,
    private val apiKey: String,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    suspend fun fetchPlaylist(playlistId: String, originalUrl: String): RemotePlaylistPayload? {
        if (apiKey.isBlank()) return null

        val playlist = service.getPlaylist(id = playlistId, key = apiKey).items.firstOrNull() ?: return null

        // Paginacion completa con nextPageToken (50 por pagina).
        val items = ArrayList<YouTubePlaylistItemDto>()
        var pageToken: String? = null
        var pages = 0
        do {
            val page = service.getPlaylistItems(playlistId = playlistId, pageToken = pageToken, key = apiKey)
            items += page.items
            pageToken = page.nextPageToken?.takeIf { it.isNotBlank() }
            pages++
        } while (pageToken != null && pages < MAX_PAGES)

        val usableItems = items.filter { item ->
            val title = item.snippet?.title.orEmpty()
            title != "Private video" && title != "Deleted video" &&
                (item.contentDetails?.videoId ?: item.snippet?.resourceId?.videoId) != null
        }

        // videos.list admite como maximo 50 ids por peticion.
        val videoMap = HashMap<String, YouTubeVideoDto>()
        usableItems
            .mapNotNull { it.contentDetails?.videoId ?: it.snippet?.resourceId?.videoId }
            .distinct()
            .chunked(50)
            .forEach { chunk ->
                service.getVideos(ids = chunk.joinToString(","), key = apiKey).items.forEach { video ->
                    video.id?.let { videoMap[it] = video }
                }
            }

        return RemotePlaylistPayload(
            title = playlist.snippet?.title?.takeIf { it.isNotBlank() } ?: "Playlist de YouTube",
            platform = Platform.YOUTUBE,
            originalUrl = originalUrl,
            imageUrl = playlist.snippet?.thumbnails?.bestUrl(),
            description = playlist.snippet?.description?.takeIf { it.isNotBlank() },
            tracks = usableItems.mapIndexedNotNull { index, item ->
                val videoId = item.contentDetails?.videoId ?: item.snippet?.resourceId?.videoId
                    ?: return@mapIndexedNotNull null
                val video = videoMap[videoId]
                RemoteTrackPayload(
                    title = item.snippet?.title?.takeIf { it.isNotBlank() } ?: "Vídeo ${index + 1}",
                    artist = (item.snippet?.videoOwnerChannelTitle ?: item.snippet?.channelTitle)
                        ?.removeSuffix(" - Topic")
                        ?.takeIf { it.isNotBlank() }
                        ?: "YouTube",
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
            title = video.snippet?.title ?: "Vídeo de YouTube",
            platform = Platform.YOUTUBE,
            originalUrl = originalUrl,
            imageUrl = video.snippet?.thumbnails?.bestUrl(),
            description = video.snippet?.description,
            tracks = listOf(
                RemoteTrackPayload(
                    title = video.snippet?.title ?: "Vídeo de YouTube",
                    artist = (video.snippet?.videoOwnerChannelTitle ?: video.snippet?.channelTitle)
                        ?.removeSuffix(" - Topic")
                        ?.takeIf { it.isNotBlank() }
                        ?: "YouTube",
                    album = null,
                    durationMs = video.contentDetails?.duration?.toDurationMillis(),
                    imageUrl = video.snippet?.thumbnails?.bestUrl(),
                    originalUrl = "https://www.youtube.com/watch?v=$videoId",
                    platform = Platform.YOUTUBE,
                    isPlayableInApp = true,
                )
            ),
        )
    }

    private companion object {
        const val MAX_PAGES = 100
    }
}

private fun String.toDurationMillis(): Long? = runCatching { Duration.parse(this).toMillis() }.getOrNull()
