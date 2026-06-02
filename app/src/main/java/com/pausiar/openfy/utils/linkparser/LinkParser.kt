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
            sanitized.startsWith("spotify:") -> parseSpotifyUri(sanitized, rawUrl)
            else -> parseHttpUrl(sanitized, rawUrl)
        }
    }

    private fun parseSpotifyUri(uri: String, original: String): ParsedLink {
        val parts = uri.split(':')
        if (parts.size >= 3) {
            val type = parts[1]
            val id = parts[2]
            return when (type) {
                "playlist" -> parsed(original, uri, LinkType.SPOTIFY_PLAYLIST, Platform.SPOTIFY, id)
                "track" -> parsed(original, uri, LinkType.SPOTIFY_TRACK, Platform.SPOTIFY, id)
                else -> invalid(original, uri)
            }
        }
        return invalid(original, uri)
    }

    private fun parseHttpUrl(url: String, original: String): ParsedLink {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return invalid(original, url)
        val host = uri.host.orEmpty().lowercase()
        val pathSegments = uri.pathSegments

        return when {
            host.contains("spotify.com") && pathSegments.size >= 2 && pathSegments[0] == "playlist" -> {
                parsed(original, url, LinkType.SPOTIFY_PLAYLIST, Platform.SPOTIFY, pathSegments[1])
            }

            host.contains("spotify.com") && pathSegments.size >= 2 && pathSegments[0] == "track" -> {
                parsed(original, url, LinkType.SPOTIFY_TRACK, Platform.SPOTIFY, pathSegments[1])
            }

            (host.contains("youtube.com") || host.contains("music.youtube.com")) && !uri.getQueryParameter("list").isNullOrBlank() -> {
                parsed(original, url, LinkType.YOUTUBE_PLAYLIST, Platform.YOUTUBE, uri.getQueryParameter("list"))
            }

            host.contains("youtube.com") && !uri.getQueryParameter("v").isNullOrBlank() -> {
                parsed(original, url, LinkType.YOUTUBE_VIDEO, Platform.YOUTUBE, uri.getQueryParameter("v"))
            }

            host == "youtu.be" && !pathSegments.firstOrNull().isNullOrBlank() -> {
                parsed(original, url, LinkType.YOUTUBE_VIDEO, Platform.YOUTUBE, pathSegments.first())
            }

            else -> invalid(original, url)
        }
    }

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
}