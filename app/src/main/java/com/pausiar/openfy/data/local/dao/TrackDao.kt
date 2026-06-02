package com.pausiar.openfy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pausiar.openfy.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert
    suspend fun insertAll(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeByPlaylist(playlistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getById(trackId: Long): TrackEntity?

    @Query("SELECT COUNT(*) FROM tracks WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int

    @Query("UPDATE tracks SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END WHERE id = :trackId")
    suspend fun toggleFavorite(trackId: Long)

    @Query("DELETE FROM tracks WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT * FROM tracks ORDER BY playlistId, position")
    suspend fun getAll(): List<TrackEntity>

    @Query("DELETE FROM tracks")
    suspend fun deleteAll()
}