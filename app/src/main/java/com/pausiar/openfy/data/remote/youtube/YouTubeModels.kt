package com.pausiar.openfy.data.remote.youtube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YouTubeListResponse<T>(
    val items: List<T> = emptyList(),
)

@Serializable
data class YouTubePlaylistDto(
    val id: String? = null,
    val snippet: YouTubeSnippetDto? = null,
)

@Serializable
data class YouTubePlaylistItemDto(
    val snippet: YouTubeSnippetDto? = null,
    val contentDetails: YouTubePlaylistItemContentDetailsDto? = null,
)

@Serializable
data class YouTubeVideoDto(
    val id: String? = null,
    val snippet: YouTubeSnippetDto? = null,
    val contentDetails: YouTubeVideoContentDetailsDto? = null,
)

@Serializable
data class YouTubeSnippetDto(
    val title: String? = null,
    val description: String? = null,
    val channelTitle: String? = null,
    @SerialName("videoOwnerChannelTitle")
    val videoOwnerChannelTitle: String? = null,
    val thumbnails: YouTubeThumbnailsDto? = null,
    val resourceId: YouTubeResourceIdDto? = null,
)

@Serializable
data class YouTubePlaylistItemContentDetailsDto(
    @SerialName("videoId")
    val videoId: String? = null,
)

@Serializable
data class YouTubeVideoContentDetailsDto(
    val duration: String? = null,
)

@Serializable
data class YouTubeResourceIdDto(
    @SerialName("videoId")
    val videoId: String? = null,
)

@Serializable
data class YouTubeThumbnailsDto(
    @SerialName("default")
    val defaultThumb: YouTubeThumbnailDto? = null,
    val medium: YouTubeThumbnailDto? = null,
    val high: YouTubeThumbnailDto? = null,
    val standard: YouTubeThumbnailDto? = null,
    val maxres: YouTubeThumbnailDto? = null,
) {
    fun bestUrl(): String? = maxres?.url ?: standard?.url ?: high?.url ?: medium?.url ?: defaultThumb?.url
}

@Serializable
data class YouTubeThumbnailDto(
    val url: String? = null,
)