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
import com.pausiar.openfy.domain.models.ParsedLink
import com.pausiar.openfy.domain.models.Platform
import com.pausiar.openfy.domain.models.Playlist
import com.pausiar.openfy.domain.models.PlaylistSummary
import com.pausiar.openfy.domain.models.Track
import com.pausiar.openfy.utils.linkparser.LinkParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

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

    // El mapeo a dominio se hace fuera del hilo principal (puede ser pesado en listas grandes).
    override fun observePlaylistSummaries(): Flow<List<PlaylistSummary>> =
        playlistDao.observePlaylistSummaries()
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override fun observePlaylist(playlistId: Long): Flow<Playlist?> =
        playlistDao.observePlaylist(playlistId)
            .map { it?.toDomain() }
            .flowOn(Dispatchers.Default)

    override fun observeRecentHistory(): Flow<List<PlaybackHistoryEntity>> = playbackHistoryDao.observeRecent()

    override suspend fun importFromUrl(url: String): ImportResult = withContext(Dispatchers.IO) {
        val parsed = linkParser.parse(url)
        val linkId = parsed.id
        if (parsed.type == LinkType.INVALID || linkId.isNullOrBlank()) {
            return@withContext ImportResult.Error(
                "No he podido reconocer ese enlace. Usa un enlace de playlist o canción de Spotify, " +
                    "o de playlist o vídeo de YouTube."
            )
        }

        val isSpotify = parsed.platform == Platform.SPOTIFY
        if (isSpotify && !spotifyRemoteDataSource.isConfigured) {
            return@withContext ImportResult.Error(SPOTIFY_NOT_CONFIGURED_MESSAGE)
        }

        val fetched: RemotePlaylistPayload? = try {
            withTimeoutOrNull(IMPORT_TIMEOUT_MS) { fetchPayload(parsed, linkId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return@withContext ImportResult.Error(describeImportFailure(error, isSpotify))
        }
        val payload = fetched ?: return@withContext ImportResult.Error(
            "No se ha podido leer ese enlace. Comprueba que la playlist sea pública y que tengas conexión a internet."
        )

        val tracks = payload.tracks.take(MAX_IMPORTED_TRACKS)
        if (tracks.isEmpty()) {
            return@withContext ImportResult.Error(
                "No se han podido importar canciones de ese enlace. Comprueba que la playlist sea pública e inténtalo de nuevo."
            )
        }

        // La URL canonica (sin si=, sin music./m., etc.) es la clave para no duplicar importaciones.
        val canonicalUrl = parsed.normalizedUrl
        val alreadyImported = playlistDao.findByOriginalUrl(canonicalUrl) != null
        val now = System.currentTimeMillis()
        val playlistId = playlistDao.upsertImportedPlaylist(
            playlist = PlaylistEntity(
                title = payload.title.trim().ifBlank { parsed.type.label },
                platform = payload.platform.name,
                originalUrl = canonicalUrl,
                imageUrl = payload.imageUrl,
                createdAt = now,
                updatedAt = now,
                isLocal = false,
                description = payload.description,
            ),
            tracks = tracks.map { it.toEntity() },
        )

        ImportResult.Success(
            playlistId = playlistId,
            message = buildSuccessMessage(parsed.type, tracks, alreadyImported, payload.requiresApiConfiguration),
            requiresApiConfiguration = payload.requiresApiConfiguration,
        )
    }

    private suspend fun fetchPayload(parsed: ParsedLink, id: String): RemotePlaylistPayload? {
        val url = parsed.normalizedUrl
        return when (parsed.type) {
            LinkType.YOUTUBE_PLAYLIST -> {
                val official = if (youtubeRemoteDataSource.isConfigured) {
                    try {
                        youtubeRemoteDataSource.fetchPlaylist(id, url)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (ignored: Exception) {
                        null // cuota agotada / clave invalida: se prueba el metodo sin API key
                    }
                } else {
                    null
                }
                official?.takeIf { it.tracks.isNotEmpty() } ?: youtubeInnertubeDataSource.fetchPlaylist(id, url)
            }

            LinkType.YOUTUBE_VIDEO -> {
                val official = if (youtubeRemoteDataSource.isConfigured) {
                    try {
                        youtubeRemoteDataSource.fetchVideo(id, url)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (ignored: Exception) {
                        null
                    }
                } else {
                    null
                }
                official ?: youtubeInnertubeDataSource.fetchVideo(id, url)
            }

            LinkType.SPOTIFY_PLAYLIST -> spotifyRemoteDataSource.fetchPlaylist(id, url)
            LinkType.SPOTIFY_TRACK -> spotifyRemoteDataSource.fetchTrack(id, url)
            LinkType.INVALID -> null
        }
    }

    private fun buildSuccessMessage(
        type: LinkType,
        tracks: List<RemoteTrackPayload>,
        alreadyImported: Boolean,
        requiresApiConfiguration: Boolean,
    ): String {
        if (requiresApiConfiguration) {
            return "Importado con metadatos limitados. Configura las APIs oficiales para obtener más detalle."
        }
        return when (type) {
            LinkType.YOUTUBE_VIDEO -> "Vídeo importado."
            LinkType.SPOTIFY_TRACK -> if (tracks.any { it.isPlayableInApp }) {
                "Canción importada."
            } else {
                "Canción importada. No tiene preview reproducible: puedes abrirla en Spotify."
            }

            else -> {
                val verb = if (alreadyImported) "actualizada" else "importada"
                val playable = tracks.count { it.isPlayableInApp }
                when {
                    playable == tracks.size -> "Playlist $verb: ${tracks.size} canciones listas para reproducir."
                    playable == 0 -> "Playlist $verb: ${tracks.size} canciones. Ninguna se puede reproducir en la app; " +
                        "puedes abrirlas en Spotify."

                    else -> "Playlist $verb: ${tracks.size} canciones, $playable reproducibles en la app."
                }
            }
        }
    }

    private fun describeImportFailure(error: Throwable, isSpotify: Boolean): String {
        val source = if (isSpotify) "el servidor de Spotify" else "YouTube"
        return when (error) {
            is HttpException -> when (error.code()) {
                401, 403 -> "No se ha podido acceder a esa playlist: es privada o $source ha denegado el acceso."
                404 -> "No se ha encontrado esa playlist. Comprueba el enlace y que sea pública."
                429 -> "Demasiadas peticiones a $source. Inténtalo de nuevo en unos minutos."
                else -> "$source ha devuelto un error (${error.code()}). Inténtalo de nuevo más tarde."
            }

            is IOException -> "No se ha podido conectar con $source. Revisa tu conexión a internet e inténtalo de nuevo."
            is SerializationException -> "La respuesta de $source no tiene el formato esperado."
            else -> "No se ha podido importar el enlace ahora mismo. Inténtalo de nuevo más tarde."
        }
    }

    override suspend fun createLocalPlaylist(title: String): Long = withContext(Dispatchers.IO) {
        val normalizedTitle = title.trim().ifBlank { "Nueva playlist local" }
        playlistDao.findLocalByTitle(normalizedTitle)?.id ?: playlistDao.insert(
            newLocalPlaylist(normalizedTitle, "Playlist local creada en Openfy.")
        )
    }

    override suspend fun importLocalTracks(playlistTitle: String, uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext ImportResult.Error("No se han seleccionado archivos de audio.")

        val normalizedTitle = playlistTitle.trim().ifBlank { "Colección local" }
        val existingId = playlistDao.findLocalByTitle(normalizedTitle)?.id

        // Lectura de metadatos en paralelo (de 4 en 4) para no tardar una eternidad con muchos archivos.
        val tracks = uris.withIndex().toList().chunked(LOCAL_METADATA_PARALLELISM).flatMap { chunk ->
            coroutineScope {
                chunk.map { (index, uri) -> async { resolveLocalTrack(uri, index) } }.awaitAll()
            }
        }.filterNotNull()

        if (tracks.isEmpty()) {
            return@withContext ImportResult.Error("No se ha podido leer ninguno de los archivos seleccionados.")
        }

        val (playlistId, inserted) = playlistDao.appendLocalTracks(
            existingPlaylistId = existingId,
            newPlaylist = newLocalPlaylist(normalizedTitle, "Canciones locales importadas desde el dispositivo."),
            tracks = tracks,
        )
        if (inserted == 0) {
            return@withContext ImportResult.Error("Esos archivos ya estaban en la playlist \"$normalizedTitle\".")
        }
        ImportResult.Success(
            playlistId = playlistId,
            message = if (inserted == 1) {
                "Se ha añadido 1 archivo local a \"$normalizedTitle\"."
            } else {
                "Se han añadido $inserted archivos locales a \"$normalizedTitle\"."
            },
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
            // Si la pista se borro mientras sonaba, el insert violaria la FK: se ignora.
            try {
                playbackHistoryDao.insert(
                    PlaybackHistoryEntity(
                        trackId = trackId,
                        playedAt = System.currentTimeMillis(),
                        progressMs = progressMs,
                    )
                )
                playbackHistoryDao.trimTo(MAX_HISTORY_ENTRIES)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ignored: Exception) {
            }
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
            // Se descartan referencias huerfanas (un backup editado a mano rompería las claves foráneas).
            val playlistIds = playlists.mapTo(HashSet()) { it.id }
            val validTracks = tracks.filter { it.playlistId in playlistIds }
            val trackIds = validTracks.mapTo(HashSet()) { it.id }
            val validHistory = history.filter { it.trackId in trackIds }
            // Una sola transaccion: si falla algo, la biblioteca anterior se conserva intacta.
            playlistDao.replaceAllData(playlists, validTracks, validHistory)
        }
    }

    override suspend fun seedDemoPlaylistIfNeeded() {
        withContext(Dispatchers.IO) {
            if (playlistDao.count() > 0) return@withContext

            val now = System.currentTimeMillis()
            playlistDao.insertPlaylistWithTracks(
                playlist = PlaylistEntity(
                    title = "Openfy Demo Sessions",
                    platform = Platform.DEMO.name,
                    originalUrl = "openfy://demo/sessions",
                    imageUrl = null,
                    createdAt = now,
                    updatedAt = now,
                    isLocal = true,
                    description = "Melodía original incluida con Openfy para probar la reproducción en segundo plano y las notificaciones.",
                ),
                tracks = listOf(
                    TrackEntity(
                        playlistId = 0,
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
                ),
            )
        }
    }

    private fun newLocalPlaylist(title: String, description: String): PlaylistEntity {
        val now = System.currentTimeMillis()
        return PlaylistEntity(
            title = title,
            platform = Platform.LOCAL.name,
            originalUrl = "openfy://local/${title.lowercase().replace(' ', '-')}",
            imageUrl = null,
            createdAt = now,
            updatedAt = now,
            isLocal = true,
            description = description,
        )
    }

    private fun RemoteTrackPayload.toEntity() = TrackEntity(
        playlistId = 0, // lo fija el DAO al insertar
        title = title.trim().ifBlank { "Sin título" },
        artist = artist.trim().ifBlank { "Desconocido" },
        album = album,
        durationMs = durationMs,
        imageUrl = imageUrl,
        originalUrl = originalUrl,
        localUri = localUri,
        previewUrl = previewUrl,
        platform = platform.name,
        position = 0, // lo fija el DAO al insertar
        isFavorite = isFavorite,
        isPlayableInApp = isPlayableInApp,
    )

    /** Lee los metadatos de un archivo de audio local. Devuelve null si no se puede leer. */
    private fun resolveLocalTrack(uri: Uri, index: Int): TrackEntity? {
        val retriever = MediaMetadataRetriever()
        return try {
            val documentFile = DocumentFile.fromSingleUri(appContext, uri)
            retriever.setDataSource(appContext, uri)
            val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val rawAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val rawDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()

            TrackEntity(
                playlistId = 0, // lo fija el DAO al insertar
                title = rawTitle?.takeIf { it.isNotBlank() }
                    ?: documentFile?.name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                    ?: "Audio local ${index + 1}",
                artist = rawArtist?.takeIf { it.isNotBlank() } ?: "Archivo local",
                album = rawAlbum,
                durationMs = rawDuration,
                imageUrl = null,
                originalUrl = null,
                localUri = uri.toString(),
                previewUrl = null,
                platform = Platform.LOCAL.name,
                position = 0, // lo fija el DAO al insertar
                isFavorite = false,
                isPlayableInApp = true,
            )
        } catch (ignored: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val IMPORT_TIMEOUT_MS = 120_000L
        const val MAX_IMPORTED_TRACKS = 5_000
        const val MAX_HISTORY_ENTRIES = 500
        const val LOCAL_METADATA_PARALLELISM = 4
        const val SPOTIFY_NOT_CONFIGURED_MESSAGE =
            "Importar desde Spotify requiere un servidor proxy de metadatos que aún no está configurado. " +
                "Define OPENFY_SPOTIFY_METADATA_BASE_URL en local.properties y vuelve a compilar la app. " +
                "Mientras tanto puedes importar playlists y vídeos de YouTube."
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
