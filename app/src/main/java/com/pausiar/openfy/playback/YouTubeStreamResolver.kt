package com.pausiar.openfy.playback

import com.pausiar.openfy.domain.models.Platform
import com.pausiar.openfy.domain.models.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

class YouTubeStreamResolver {
    suspend fun resolvePlaybackUri(track: Track): String? = withContext(Dispatchers.IO) {
        when {
            !track.localUri.isNullOrBlank() -> track.localUri
            !track.previewUrl.isNullOrBlank() -> track.previewUrl
            track.platform == Platform.YOUTUBE && track.isPlayableInApp -> resolveYouTubeUrl(track.originalUrl)
            else -> null
        }
    }

    private fun resolveYouTubeUrl(watchUrl: String?): String? {
        val url = watchUrl?.trim().orEmpty()
        if (url.isBlank()) return null

        return runCatching {
            val service = NewPipe.getService(ServiceList.YouTube.serviceId)
            val streamInfo = StreamInfo.getInfo(service, url)

            streamInfo.audioStreams
                .maxByOrNull { it.averageBitrate }
                ?.content
                ?: streamInfo.videoStreams
                    .filterNot { it.isVideoOnly }
                    .maxByOrNull { it.bitrate }
                    ?.content
                ?: streamInfo.videoStreams
                    .maxByOrNull { it.bitrate }
                    ?.content
                ?: streamInfo.hlsUrl?.takeIf { it.isNotBlank() }
                ?: streamInfo.dashMpdUrl?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
