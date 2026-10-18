package com.pausiar.openfy.utils.linkparser

import android.net.Uri
import com.pausiar.openfy.domain.models.LinkType
import com.pausiar.openfy.domain.models.ParsedLink
import com.pausiar.openfy.domain.models.Platform
import com.pausiar.openfy.utils.validators.LinkValidators

class LinkParser {
    fun parse(rawUrl: String): ParsedLink {
        val sanitized = LinkValidators.sanitizeUrl(rawUrl)
        if (!LinkValidators.looksLikeUrl(sanitized)) {
            return ParsedLink(
                type = LinkType.INVALID,
                originalUrl = rawUrl,
                normalizedUrl = sanitized,
                platform = Platform.UNKNOWN,
            )
        }

        return when {
            sanitized.startsWith("spotify:", ignoreCase = true) -> parseSpotifyUri(sanitized, rawUrl)
            else -> parseHttpUrl(sanitized, rawUrl)
        }
    }

    /** spotify:playlist:ID, spotify:track:ID, spotify:album:ID, spotify:user:NAME:playlist:ID */
    private fun parseSpotifyUri(uri: String, original: String): ParsedLink {
        val parts = uri.split(':')
        val typeIndex = parts.indexOfFirst { part -> SPOTIFY_TYPES.any { it.equals(part, ignoreCase = true) } }
        val id = parts.getOrNull(typeIndex + 1)?.takeIf { typeIndex >= 0 && SPOTIFY_ID_REGEX.matches(it) }
            ?: return invalid(original, uri)
        return spotifyByType(parts[typeIndex].lowercase(), original, id)
    }

    private fun parseHttpUrl(url: String, original: String): ParsedLink {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return invalid(original, url)
        val host = uri.host.orEmpty().lowercase()
        val segments = uri.pathSegments.orEmpty()

        return when {
            hostIs(host, "spotify.com") -> parseSpotifyWeb(segments, original, url)
            hostIs(host, "spotify.link") || host == "spotify.app.link" ->
                parseSpotifyShortLink(host, segments, original, url)
            hostIs(host, "youtube.com") || hostIs(host, "youtube-nocookie.com") || host == "youtu.be" ->
                parseYouTube(uri, host, segments, original, url)

            else -> invalid(original, url)
        }
    }

    /**
     * Soporta open.spotify.com/playlist/ID, /intl-es/playlist/ID, /embed/playlist/ID,
     * /user/NAME/playlist/ID, /album/ID y ?si=... (los parametros se descartan).
     */
    private fun parseSpotifyWeb(segments: List<String>, original: String, url: String): ParsedLink {
        val typeIndex = segments.indexOfFirst { it in SPOTIFY_TYPES }
        val id = segments.getOrNull(typeIndex + 1)?.takeIf { typeIndex >= 0 && SPOTIFY_ID_REGEX.matches(it) }
            ?: return invalid(original, url)
        return spotifyByType(segments[typeIndex], original, id)
    }

    /** spotify.link/CODIGO: el destino real se resuelve (siguiendo redirecciones) al importar. */
    private fun parseSpotifyShortLink(host: String, segments: List<String>, original: String, url: String): ParsedLink {
        val code = segments.firstOrNull()?.takeIf { SPOTIFY_SHORT_CODE_REGEX.matches(it) }
            ?: return invalid(original, url)
        return parsed(original, "https://$host/$code", LinkType.SPOTIFY_SHORT_LINK, Platform.SPOTIFY, code)
    }

    private fun spotifyByType(type: String, original: String, id: String): ParsedLink = when (type) {
        "playlist" -> spotifyPlaylist(original, id)
        "album" -> parsed(original, "https://open.spotify.com/album/$id", LinkType.SPOTIFY_ALBUM, Platform.SPOTIFY, id)
        else -> spotifyTrack(original, id)
    }

    private fun parseYouTube(
        uri: Uri,
        host: String,
        segments: List<String>,
        original: String,
        url: String,
    ): ParsedLink {
        val list = uri.getQueryParameter("list")?.takeIf { YOUTUBE_LIST_REGEX.matches(it) }
        val videoParam = uri.getQueryParameter("v")?.takeIf { YOUTUBE_VIDEO_REGEX.matches(it) }
        val pathVideo = when {
            host == "youtu.be" -> segments.firstOrNull()
            segments.size >= 2 && segments[0] in VIDEO_PATH_PREFIXES -> segments[1]
            else -> null
        }?.takeIf { YOUTUBE_VIDEO_REGEX.matches(it) }
        val videoId = videoParam ?: pathVideo

        // Los "mixes" automaticos (RD...) no son playlists reales: solo se importa el video (si lo hay).
        val isAutoMix = list != null && (list.startsWith("RD") || list.startsWith("UL"))
        return when {
            list != null && !isAutoMix ->
                parsed(original, "https://www.youtube.com/playlist?list=$list", LinkType.YOUTUBE_PLAYLIST, Platform.YOUTUBE, list)

            videoId != null ->
                parsed(original, "https://www.youtube.com/watch?v=$videoId", LinkType.YOUTUBE_VIDEO, Platform.YOUTUBE, videoId)

            else -> invalid(original, url)
        }
    }

    private fun spotifyPlaylist(original: String, id: String) =
        parsed(original, "https://open.spotify.com/playlist/$id", LinkType.SPOTIFY_PLAYLIST, Platform.SPOTIFY, id)

    private fun spotifyTrack(original: String, id: String) =
        parsed(original, "https://open.spotify.com/track/$id", LinkType.SPOTIFY_TRACK, Platform.SPOTIFY, id)

    private fun hostIs(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    private fun parsed(
        original: String,
        normalized: String,
        type: LinkType,
        platform: Platform,
        id: String?,
    ) = ParsedLink(
        type = type,
        id = id,
        originalUrl = original,
        normalizedUrl = normalized,
        platform = platform,
    )

    private fun invalid(original: String, normalized: String) = ParsedLink(
        type = LinkType.INVALID,
        id = null,
        originalUrl = original,
        normalizedUrl = normalized,
        platform = Platform.UNKNOWN,
    )

    private companion object {
        val SPOTIFY_ID_REGEX = Regex("[A-Za-z0-9]{10,40}")
        val SPOTIFY_SHORT_CODE_REGEX = Regex("[A-Za-z0-9_-]{4,64}")
        val SPOTIFY_TYPES = setOf("playlist", "track", "album")
        val YOUTUBE_LIST_REGEX = Regex("[A-Za-z0-9_-]{10,64}")
        val YOUTUBE_VIDEO_REGEX = Regex("[A-Za-z0-9_-]{11}")
        val VIDEO_PATH_PREFIXES = setOf("shorts", "embed", "live", "v")
    }
}
