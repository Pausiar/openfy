package com.pausiar.openfy.playback

import android.net.Uri
import com.pausiar.openfy.domain.models.Platform
import com.pausiar.openfy.domain.models.Track

/**
 * URIs ligeras que se ponen en la cola del reproductor. Las de YouTube / busqueda no son
 * reproducibles directamente: el servicio las resuelve de forma perezosa (en el hilo de
 * carga de ExoPlayer) mediante un ResolvingDataSource y [YouTubeStreamResolver].
 *
 *  - `openfy-yt://video/<videoId>`
 *  - `openfy-search://track/<trackId>?q=<consulta>&fallback=<url opcional>`
 */
object PlaybackUris {
    const val SCHEME_YOUTUBE = "openfy-yt"
    const val SCHEME_SEARCH = "openfy-search"

    private const val YOUTUBE_AUTHORITY = "video"
    private const val SEARCH_AUTHORITY = "track"
    private const val PARAM_QUERY = "q"
    private const val PARAM_FALLBACK = "fallback"

    private val PLACEHOLDER_TITLES = setOf("canción de spotify", "cancion de spotify")

    sealed interface Target {
        data class Video(val videoId: String) : Target
        data class Search(val query: String, val fallbackUrl: String?) : Target
    }

    /** URI a usar en el MediaItem de [track], o null si no es reproducible dentro de Openfy. */
    fun forTrack(track: Track): String? {
        track.localUri?.takeIf { it.isNotBlank() }?.let { return it }
        val preview = track.previewUrl?.takeIf { it.isNotBlank() }

        // Spotify: siempre reproducible via busqueda en YouTube (cancion completa), aunque el
        // import la marque como no reproducible; si la busqueda falla se usa el preview oficial.
        if (track.platform == Platform.SPOTIFY) {
            searchQueryFor(track)?.let { query ->
                return search(key = track.id.toString(), query = query, fallbackUrl = preview)
            }
        }

        if (!track.isPlayableInApp) return null
        if (track.platform == Platform.YOUTUBE) {
            YouTubeHttp.extractVideoId(track.originalUrl)?.let { return youtube(it) }
        }
        return preview
    }

    fun youtube(videoId: String): String = "$SCHEME_YOUTUBE://$YOUTUBE_AUTHORITY/$videoId"

    fun search(key: String, query: String, fallbackUrl: String?): String =
        Uri.Builder()
            .scheme(SCHEME_SEARCH)
            .authority(SEARCH_AUTHORITY)
            .appendPath(key)
            .appendQueryParameter(PARAM_QUERY, query)
            .apply {
                if (!fallbackUrl.isNullOrBlank()) appendQueryParameter(PARAM_FALLBACK, fallbackUrl)
            }
            .build()
            .toString()

    fun isCustom(uri: Uri?): Boolean {
        val scheme = uri?.scheme ?: return false
        return scheme == SCHEME_YOUTUBE || scheme == SCHEME_SEARCH
    }

    fun parse(uri: Uri?): Target? {
        if (uri == null) return null
        return when (uri.scheme) {
            SCHEME_YOUTUBE -> uri.lastPathSegment
                ?.takeIf { YouTubeHttp.isValidVideoId(it) }
                ?.let { Target.Video(it) }
            SCHEME_SEARCH -> {
                val query = uri.getQueryParameter(PARAM_QUERY)?.trim().orEmpty()
                if (query.isBlank()) {
                    null
                } else {
                    Target.Search(
                        query = query,
                        fallbackUrl = uri.getQueryParameter(PARAM_FALLBACK)?.takeIf { it.isNotBlank() },
                    )
                }
            }
            else -> null
        }
    }

    private fun searchQueryFor(track: Track): String? {
        val title = track.title.trim()
        // Sin titulo real (placeholder del import) no hay nada que buscar.
        if (title.isBlank() || title.lowercase() in PLACEHOLDER_TITLES) return null
        val artist = track.artist.trim().takeUnless { it.isBlank() || it.equals("Spotify", ignoreCase = true) }
        return if (artist != null) "$artist - $title" else title
    }
}
