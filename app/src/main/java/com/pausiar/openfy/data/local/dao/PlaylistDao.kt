package com.pausiar.openfy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pausiar.openfy.data.local.entity.PlaybackHistoryEntity
import com.pausiar.openfy.data.local.entity.PlaylistEntity
import com.pausiar.openfy.data.local.entity.TrackEntity
import com.pausiar.openfy.data.local.model.PlaylistSummaryEntity
import com.pausiar.openfy.data.local.model.PlaylistWithTracks
import kotlinx.coroutines.flow.Flow

/**
 * Clase abstracta (no interfaz) para poder declarar metodos @Transaction con cuerpo que
 * escriben playlists, pistas e historial de forma atomica.
 */
@Dao
abstract class PlaylistDao {
    @Query(
        """
        SELECT playlists.id, playlists.title, playlists.platform, playlists.originalUrl, playlists.imageUrl,
               playlists.createdAt, playlists.updatedAt, playlists.isLocal, playlists.description,
               COUNT(tracks.id) AS trackCount,
               COALESCE(SUM(CASE WHEN tracks.isPlayableInApp = 1 THEN 1 ELSE 0 END), 0) AS playableTrackCount
        FROM playlists
        LEFT JOIN tracks ON playlists.id = tracks.playlistId
        GROUP BY playlists.id
        ORDER BY playlists.updatedAt DESC
        """
    )
    abstract fun observePlaylistSummaries(): Flow<List<PlaylistSummaryEntity>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    abstract fun observePlaylist(playlistId: Long): Flow<PlaylistWithTracks?>

    @Query("SELECT * FROM playlists WHERE isLocal = 1 ORDER BY updatedAt DESC")
    abstract fun observeLocalPlaylists(): Flow<List<PlaylistEntity>>

    @Insert
    abstract suspend fun insert(playlist: PlaylistEntity): Long

    @Insert
    abstract suspend fun insertAll(playlists: List<PlaylistEntity>)

    @Insert
    abstract suspend fun insertTracks(tracks: List<TrackEntity>)

    @Insert
    abstract suspend fun insertHistory(entries: List<PlaybackHistoryEntity>)

    @Update
    abstract suspend fun update(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET title = :title, updatedAt = :updatedAt WHERE id = :playlistId")
    abstract suspend fun rename(playlistId: Long, title: String, updatedAt: Long)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    abstract suspend fun touch(playlistId: Long, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    abstract suspend fun delete(playlistId: Long)

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    abstract suspend fun getById(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE title = :title AND isLocal = 1 AND platform = 'LOCAL' LIMIT 1")
    abstract suspend fun findLocalByTitle(title: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE originalUrl = :originalUrl ORDER BY id ASC LIMIT 1")
    abstract suspend fun findByOriginalUrl(originalUrl: String): PlaylistEntity?

    @Query("SELECT COUNT(*) FROM playlists")
    abstract suspend fun count(): Int

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    abstract suspend fun getAll(): List<PlaylistEntity>

    @Query("DELETE FROM playlists")
    abstract suspend fun deleteAll()

    @Query("DELETE FROM tracks WHERE playlistId = :playlistId")
    abstract suspend fun deleteTracksOf(playlistId: Long)

    @Query("DELETE FROM tracks")
    abstract suspend fun deleteAllTracks()

    @Query("DELETE FROM playback_history")
    abstract suspend fun deleteAllHistory()

    @Query("SELECT COUNT(*) FROM tracks WHERE playlistId = :playlistId")
    abstract suspend fun countTracks(playlistId: Long): Int

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM tracks WHERE playlistId = :playlistId")
    abstract suspend fun nextPosition(playlistId: Long): Int

    @Query("SELECT originalUrl FROM tracks WHERE playlistId = :playlistId AND isFavorite = 1 AND originalUrl IS NOT NULL")
    abstract suspend fun favoriteOriginalUrls(playlistId: Long): List<String>

    @Query("SELECT localUri FROM tracks WHERE playlistId = :playlistId AND localUri IS NOT NULL")
    abstract suspend fun localUrisOf(playlistId: Long): List<String>

    /**
     * Crea o actualiza (por originalUrl) una playlist importada y reemplaza sus pistas en una
     * unica transaccion. Conserva los favoritos de las pistas que siguen existiendo.
     */
    @Transaction
    open suspend fun upsertImportedPlaylist(playlist: PlaylistEntity, tracks: List<TrackEntity>): Long {
        val existing = findByOriginalUrl(playlist.originalUrl)
        val playlistId: Long
        val favorites: Set<String>
        if (existing != null) {
            playlistId = existing.id
            favorites = favoriteOriginalUrls(playlistId).toSet()
            update(
                existing.copy(
                    title = playlist.title,
                    platform = playlist.platform,
                    imageUrl = playlist.imageUrl,
                    updatedAt = playlist.updatedAt,
                    description = playlist.description,
                )
            )
            deleteTracksOf(playlistId)
        } else {
            playlistId = insert(playlist)
            favorites = emptySet()
        }
        insertTracks(
            tracks.mapIndexed { index, track ->
                track.copy(
                    id = 0,
                    playlistId = playlistId,
                    position = index,
                    isFavorite = track.isFavorite || (track.originalUrl != null && track.originalUrl in favorites),
                )
            }
        )
        return playlistId
    }

    /**
     * Anade pistas locales a una playlist (la crea si no existe) saltando las que ya estan por
     * localUri. Devuelve el id de playlist y cuantas pistas nuevas se insertaron.
     */
    @Transaction
    open suspend fun appendLocalTracks(
        existingPlaylistId: Long?,
        newPlaylist: PlaylistEntity,
        tracks: List<TrackEntity>,
    ): Pair<Long, Int> {
        val playlistId = existingPlaylistId ?: insert(newPlaylist)
        val knownUris = localUrisOf(playlistId).toHashSet()
        val fresh = tracks.filter { it.localUri != null && knownUris.add(it.localUri) }
        val start = nextPosition(playlistId)
        if (fresh.isNotEmpty()) {
            insertTracks(
                fresh.mapIndexed { index, track ->
                    track.copy(id = 0, playlistId = playlistId, position = start + index)
                }
            )
            touch(playlistId, System.currentTimeMillis())
        }
        return playlistId to fresh.size
    }

    /** Sustituye toda la biblioteca de forma atomica: si algo falla se conserva la anterior. */
    @Transaction
    open suspend fun replaceAllData(
        playlists: List<PlaylistEntity>,
        tracks: List<TrackEntity>,
        history: List<PlaybackHistoryEntity>,
    ) {
        deleteAllHistory()
        deleteAllTracks()
        deleteAll()
        if (playlists.isNotEmpty()) insertAll(playlists)
        if (tracks.isNotEmpty()) insertTracks(tracks)
        if (history.isNotEmpty()) insertHistory(history)
    }

    /** Inserta una playlist con sus pistas de forma atomica (usado para la demo). */
    @Transaction
    open suspend fun insertPlaylistWithTracks(playlist: PlaylistEntity, tracks: List<TrackEntity>): Long {
        val playlistId = insert(playlist)
        insertTracks(tracks.map { it.copy(playlistId = playlistId) })
        return playlistId
    }
}
