package com.pausiar.openfy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pausiar.openfy.data.local.entity.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {
    @Insert
    suspend fun insert(entry: PlaybackHistoryEntity)

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT 30")
    fun observeRecent(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC")
    suspend fun getAll(): List<PlaybackHistoryEntity>

    @Query("DELETE FROM playback_history")
    suspend fun deleteAll()
}