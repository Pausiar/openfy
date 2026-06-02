package com.pausiar.openfy.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pausiar.openfy.R
import com.pausiar.openfy.data.local.dao.PlaybackHistoryDao
import com.pausiar.openfy.data.local.dao.PlaylistDao
import com.pausiar.openfy.data.local.dao.TrackDao
import com.pausiar.openfy.data.local.entity.PlaybackHistoryEntity
import com.pausiar.openfy.data.local.entity.PlaylistEntity
import com.pausiar.openfy.data.local.entity.TrackEntity
import com.pausiar.openfy.data.local.model.PlaylistSummaryEntity
import com.pausiar.openfy.data.local.model.PlaylistWithTracks
import com.pausiar.openfy.data.remote.RemotePlaylistPayload
import com.pausiar.openfy.data.remote.RemoteTrackPayload
import com.pausiar.openfy.data.remote.spotify.SpotifyProxyRemoteDataSource
import com.pausiar.openfy.data.remote.youtube.YouTubeInnertubeDataSource
import com.pausiar.openfy.data.remote.youtube.YouTubeRemoteDataSource
import com.pausiar.openfy.domain.models.ImportResult
import com.pausiar.openfy.domain.models.LinkType
import com.pausiar.openfy.domain.models.Platform
import com.pausiar.openfy.domain.models.Playlist
import com.pausiar.openfy.domain.models.PlaylistSummary
import com.pausiar.openfy.domain.models.Track
import com.pausiar.openfy.utils.linkparser.LinkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

class MusicRepositoryImpl(
    private val appContext: Context,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val linkParser: LinkParser,
    private val youtubeRemoteDataSource: YouTubeRemoteDataSource,
    private val youtubeInnertubeDataSource: YouTubeInnertubeDataSource,
    private val spotifyRemoteDataSource: SpotifyProxyRemoteDataSource,
) : MusicRepository {

    override fun observePlaylistSummaries(): Flow<List<PlaylistSummary>> =
        playlistDao.observePlaylistSummaries().map { list -> list.map { it.toDomain() } }

    override fun observePlaylist(playlistId: Long): Flow<Playlist?> =
        playlistDao.observePlaylist(playlistId).map { it?.toDomain() }

    override fun observeRecentHistory(): Flow<List<PlaybackHistoryEntity>> = playbackHistoryDao.observeRecent()

    override suspend fun importFromUrl(url: String): ImportResult = withContext(Dispatchers.IO) {
        val parsed = linkParser.parse(url)
        if (parsed.type == LinkType.INVALID || parsed.id.isNullOrBlank()) {
            return@withContext ImportResult.Error("No he podido reconocer ese enlace de Spotify o YouTube.")
        }

        val payload = runCatching {
            when (parsed.type) {
                LinkType.YOUTUBE_PLAYLIST -> youtubeRemoteDataSource.fetchPlaylist(parsed.id, parsed.normalizedUrl)
                    ?: youtubeInnertubeDataSource.fetchPlaylist(parsed.id, parsed.normalizedUrl)
                    ?: fallbackYouTubePlaylist(parsed.id, parsed.normalizedUrl)

                LinkType.YOUTUBE_VIDEO -> youtubeRemoteDataSource.fetchVideo(parsed.id, parsed.normalizedUrl)
                    ?: youtubeInnertubeDataSource.fetchVideo(parsed.id, parsed.normalizedUrl)
                    ?: fallbackYouTubeVideo(parsed.id, parsed.normalizedUrl)

                LinkType.SPOTIFY_PLAYLIST -> spotifyRemoteDataSource.fetchPlaylist(parsed.id, parsed.normalizedUrl)
                    ?: fallbackSpotifyPlaylist(parsed.id, parsed.normalizedUrl)

                LinkType.SPOTIFY_TRACK -> spotifyRemoteDataSource.fetchTrack(parsed.id, parsed.normalizedUrl)
                    ?: fallbackSpotifyTrack(parsed.id, parsed.normalizedUrl)

                LinkType.INVALID -> null
            }
        }.getOrElse {
            return@withContext ImportResult.Error(
                "No se ha podido importar la playlist ahora mismo. Revisa la conexion o la configuracion de APIs."
            )
        } ?: return@withContext ImportResult.Error("No se ha encontrado informacion util para ese enlace.")

        if (payload.tracks.isEmpty()) {
            return@withContext ImportResult.Error(
                "No se han podido importar canciones de ese enlace. Comprueba que la playlist sea publica e intentalo de nuevo."
            )
        }

        val playlistId = persistRemotePlaylist(payload)
        ImportResult.Success(
            playlistId = playlistId,
            message = if (payload.requiresApiConfiguration) {
                "Playlist importada con metadatos limitados. Configura las APIs oficiales para mas detalle."
            } else {
                "Playlist importada: ${payload.tracks.size} pistas listas para reproducir."
            },
            requiresApiConfiguration = payload.requiresApiConfiguration,
        )
    }

    override suspend fun createLocalPlaylist(title: String): Long = withContext(Dispatchers.IO) {
        val normalizedTitle = title.trim().ifBlank { "Nueva playlist local" }
        playlistDao.findLocalByTitle(normalizedTitle)?.id ?: playlistDao.insert(
            PlaylistEntity(
                title = normalizedTitle,
                platform = Platform.LOCAL.name,
                originalUrl = "openfy://local/${normalizedTitle.lowercase().replace(' ', '-')}",
                imageUrl = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isLocal = true,
                description = "Playlist local creada en Openfy.",
            )
        )
    }

    override suspend fun importLocalTracks(playlistTitle: String, uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext ImportResult.Error("No se han seleccionado archivos de audio.")

        val normalizedTitle = playlistTitle.trim().ifBlank { "Coleccion local" }
        val now = System.currentTimeMillis()
        val existingPlaylist = playlistDao.findLocalByTitle(normalizedTitle)
        val playlistId = existingPlaylist?.id ?: playlistDao.insert(
            PlaylistEntity(
                title = normalizedTitle,
                platform = Platform.LOCAL.name,
                originalUrl = "openfy://local/${normalizedTitle.lowercase().replace(' ', '-')}",
                imageUrl = null,
                createdAt = now,
                updatedAt = now,
                isLocal = true,
                description = "Canciones locales importadas desde el dispositivo.",
            )
        )
        val startPosition = trackDao.countByPlaylist(playlistId)
        val tracks = uris.mapIndexedNotNull { index, uri ->
            resolveLocalTrack(uri, playlistId, startPosition + index)
        }
        if (tracks.isEmpty()) {
            return@withContext ImportResult.Error("No se ha podido leer ninguno de los archivos seleccionados.")
        }
        trackDao.insertAll(tracks)
        playlistDao.touch(playlistId, System.currentTimeMillis())
        ImportResult.Success(
            playlistId = playlistId,
            message = "Se han agregado ${tracks.size} archivos locales a \"$normalizedTitle\".",
        )
    }

    override suspend fun renamePlaylist(playlistId: Long, title: String) {
        withContext(Dispatchers.IO) {
            val normalized = title.trim()
            if (normalized.isNotBlank()) {
                playlistDao.rename(playlistId, normalized, System.currentTimeMillis())
            }
        }
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        withContext(Dispatchers.IO) { playlistDao.delete(playlistId) }
    }

    override suspend fun toggleFavorite(trackId: Long) {
        withContext(Dispatchers.IO) { trackDao.toggleFavorite(trackId) }
    }

    override suspend fun logPlayback(trackId: Long, progressMs: Long) {
        withContext(Dispatchers.IO) {
            playbackHistoryDao.insert(
                PlaybackHistoryEntity(
                    trackId = trackId,
                    playedAt = System.currentTimeMillis(),
                    progressMs = progressMs,
                )
            )
        }
    }

    override suspend fun getAllPlaylists(): List<PlaylistEntity> = withContext(Dispatchers.IO) { playlistDao.getAll() }

    override suspend fun getAllTracks(): List<TrackEntity> = withContext(Dispatchers.IO) { trackDao.getAll() }

    override suspend fun getAllHistory(): List<PlaybackHistoryEntity> = withContext(Dispatchers.IO) { playbackHistoryDao.getAll() }

    override suspend fun replaceLibrary(
        playlists: List<PlaylistEntity>,
        tracks: List<TrackEntity>,
        history: List<PlaybackHistoryEntity>,
    ) {
        withContext(Dispatchers.IO) {
            playbackHistoryDao.deleteAll()
            trackDao.deleteAll()
            playlistDao.deleteAll()
            playlists.forEach { playlistDao.insert(it) }
            tracks.chunked(100).forEach { trackDao.insertAll(it) }
            history.forEach { playbackHistoryDao.insert(it) }
        }
    }

    override suspend fun seedDemoPlaylistIfNeeded() {
        withContext(Dispatchers.IO) {
            if (playlistDao.count() > 0) return@withContext

            val now = System.currentTimeMillis()
            val demoPlaylistId = playlistDao.insert(
                PlaylistEntity(
                    title = "Openfy Demo Sessions",
                    platform = Platform.DEMO.name,
                    originalUrl = "openfy://demo/sessions",
                    imageUrl = null,
                    createdAt = now,
                    updatedAt = now,
                    isLocal = true,
                    description = "Melodia original incluida con Openfy para probar reproduccion en segundo plano y notificaciones.",
                )
            )

            trackDao.insertAll(
                listOf(
                    TrackEntity(
                        playlistId = demoPlaylistId,
                        title = "Openfy Demo Theme",
                        artist = "Openfy Studio",
                        album = "Openfy Originals",
                        durationMs = 5400,
                        imageUrl = null,
                        originalUrl = null,
                        localUri = "android.resource://${appContext.packageName}/${R.raw.openfy_demo_theme}",
                        previewUrl = null,
                        platform = Platform.DEMO.name,
                        position = 0,
                        isFavorite = false,
                        isPlayableInApp = true,
                    )
                )
            )
        }
    }

    private suspend fun persistRemotePlaylist(payload: RemotePlaylistPayload): Long {
        val now = System.currentTimeMillis()
        val existing = playlistDao.findByOriginalUrl(payload.originalUrl)
        val playlistId = if (existing != null) {
            playlistDao.update(
                existing.copy(
                    title = payload.title,
                    platform = payload.platform.name,
                    imageUrl = payload.imageUrl,
                    updatedAt = now,
                    description = payload.description,
                )
            )
            trackDao.deleteByPlaylist(existing.id)
            existing.id
        } else {
            playlistDao.insert(
                PlaylistEntity(
                    title = payload.title,
                    platform = payload.platform.name,
                    originalUrl = payload.originalUrl,
                    imageUrl = payload.imageUrl,
                    createdAt = now,
                    updatedAt = now,
                    isLocal = payload.platform == Platform.LOCAL || payload.platform == Platform.DEMO,
                    description = payload.description,
                )
            )
        }

        trackDao.insertAll(
            payload.tracks.mapIndexed { index, track ->
                TrackEntity(
                    playlistId = playlistId,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    imageUrl = track.imageUrl,
                    originalUrl = track.originalUrl,
                    localUri = track.localUri,
                    previewUrl = track.previewUrl,
                    platform = track.platform.name,
                    position = index,
                    isFavorite = track.isFavorite,
                    isPlayableInApp = track.isPlayableInApp,
                )
            }
        )
        return playlistId
    }

    private fun fallbackYouTubePlaylist(playlistId: String, url: String) = RemotePlaylistPayload(
        title = "Playlist de YouTube",
        platform = Platform.YOUTUBE,
        originalUrl = url,
        imageUrl = null,
        description = "Configura OPENFY_YOUTUBE_API_KEY en local.properties para importar titulos y canciones de la playlist.",
        tracks = emptyList(),
        requiresApiConfiguration = true,
    )

    private fun fallbackYouTubeVideo(videoId: String, url: String) = RemotePlaylistPayload(
        title = "Video de YouTube",
        platform = Platform.YOUTUBE,
        originalUrl = url,
        imageUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        description = "Referencia de YouTube importada en Openfy.",
        tracks = listOf(
            RemoteTrackPayload(
                title = "Video de YouTube",
                artist = "YouTube",
                album = null,
                durationMs = null,
                imageUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                originalUrl = url,
                platform = Platform.YOUTUBE,
                isPlayableInApp = true,
            )
        ),
        requiresApiConfiguration = true,
    )

    private fun fallbackSpotifyPlaylist(playlistId: String, url: String) = RemotePlaylistPayload(
        title = "Playlist de Spotify",
        platform = Platform.SPOTIFY,
        originalUrl = url,
        imageUrl = null,
        description = "Para importar metadatos completos de Spotify configura OPENFY_SPOTIFY_METADATA_BASE_URL apuntando a un backend seguro.",
        tracks = emptyList(),
        requiresApiConfiguration = true,
    )

    private fun fallbackSpotifyTrack(trackId: String, url: String) = RemotePlaylistPayload(
        title = "Cancion de Spotify",
        platform = Platform.SPOTIFY,
        originalUrl = url,
        imageUrl = null,
        description = "Referencia externa de Spotify. Si existe preview oficial podras reproducirla cuando tu proxy la proporcione.",
        tracks = listOf(
            RemoteTrackPayload(
                title = "Cancion de Spotify",
                artist = "Spotify",
                album = null,
                durationMs = null,
                imageUrl = null,
                originalUrl = url,
                platform = Platform.SPOTIFY,
                isPlayableInApp = false,
            )
        ),
        requiresApiConfiguration = true,
    )

    private fun resolveLocalTrack(uri: Uri, playlistId: Long, position: Int): TrackEntity? {
        return runCatching {
            if (appContext.contentResolver.openInputStream(uri) == null) {
                throw FileNotFoundException(uri.toString())
            }
            val documentFile = DocumentFile.fromSingleUri(appContext, uri)
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(appContext, uri)
            val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val rawAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val rawDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()

            TrackEntity(
                playlistId = playlistId,
                title = rawTitle ?: documentFile?.name?.substringBeforeLast('.') ?: "Audio local ${position + 1}",
                artist = rawArtist ?: "Archivo local",
                album = rawAlbum,
                durationMs = rawDuration,
                imageUrl = null,
                originalUrl = null,
                localUri = uri.toString(),
                previewUrl = null,
                platform = Platform.LOCAL.name,
                position = position,
                isFavorite = false,
                isPlayableInApp = true,
            )
        }.getOrNull()
    }
}

private fun PlaylistSummaryEntity.toDomain() = PlaylistSummary(
    id = id,
    title = title,
    platform = Platform.fromValue(platform),
    originalUrl = originalUrl,
    imageUrl = imageUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isLocal = isLocal,
    description = description,
    trackCount = trackCount,
    playableTrackCount = playableTrackCount,
)

private fun PlaylistWithTracks.toDomain() = Playlist(
    id = playlist.id,
    title = playlist.title,
    platform = Platform.fromValue(playlist.platform),
    originalUrl = playlist.originalUrl,
    imageUrl = playlist.imageUrl,
    createdAt = playlist.createdAt,
    updatedAt = playlist.updatedAt,
    isLocal = playlist.isLocal,
    description = playlist.description,
    tracks = tracks.sortedBy { it.position }.map { it.toDomain() },
)

private fun TrackEntity.toDomain() = Track(
    id = id,
    playlistId = playlistId,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    imageUrl = imageUrl,
    originalUrl = originalUrl,
    localUri = localUri,
    previewUrl = previewUrl,
    platform = Platform.fromValue(platform),
    position = position,
    isFavorite = isFavorite,
    isPlayableInApp = isPlayableInApp,
)