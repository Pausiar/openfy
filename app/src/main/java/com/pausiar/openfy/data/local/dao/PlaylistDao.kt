package com.pausiar.openfy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pausiar.openfy.data.local.entity.PlaylistEntity
import com.pausiar.openfy.data.local.model.PlaylistSummaryEntity
import com.pausiar.openfy.data.local.model.PlaylistWithTracks
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
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
    fun observePlaylistSummaries(): Flow<List<PlaylistSummaryEntity>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylist(playlistId: Long): Flow<PlaylistWithTracks?>

    @Query("SELECT * FROM playlists WHERE isLocal = 1 ORDER BY updatedAt DESC")
    fun observeLocalPlaylists(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET title = :title, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, title: String, updatedAt: Long)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun touch(playlistId: Long, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun delete(playlistId: Long)

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getById(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE title = :title AND isLocal = 1 LIMIT 1")
    suspend fun findLocalByTitle(title: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE originalUrl = :originalUrl LIMIT 1")
    suspend fun findByOriginalUrl(originalUrl: String): PlaylistEntity?

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("DELETE FROM playlists")
    suspend fun deleteAll()
}