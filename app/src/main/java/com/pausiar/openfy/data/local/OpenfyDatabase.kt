package com.pausiar.openfy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pausiar.openfy.data.local.dao.PlaybackHistoryDao
import com.pausiar.openfy.data.local.dao.PlaylistDao
import com.pausiar.openfy.data.local.dao.TrackDao
import com.pausiar.openfy.data.local.entity.PlaybackHistoryEntity
import com.pausiar.openfy.data.local.entity.PlaylistEntity
import com.pausiar.openfy.data.local.entity.TrackEntity

@Database(
    entities = [PlaylistEntity::class, TrackEntity::class, PlaybackHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class OpenfyDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun trackDao(): TrackDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
}