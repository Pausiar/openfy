package com.pausiar.openfy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pausiar.openfy.data.local.dao.PlaybackHistoryDao
import com.pausiar.openfy.data.local.dao.PlaylistDao
import com.pausiar.openfy.data.local.dao.TrackDao
import com.pausiar.openfy.data.local.entity.PlaybackHistoryEntity
import com.pausiar.openfy.data.local.entity.PlaylistEntity
import com.pausiar.openfy.data.local.entity.TrackEntity

@Database(
    entities = [PlaylistEntity::class, TrackEntity::class, PlaybackHistoryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class OpenfyDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun trackDao(): TrackDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao

    companion object {
        /**
         * v1 -> v2: nuevos indices (tracks por playlist+posicion, playlists por originalUrl,
         * historial por fecha). Registrar con `.addMigrations(OpenfyDatabase.MIGRATION_1_2)` en AppContainer
         * para no perder datos; sin ello `fallbackToDestructiveMigration` borra la biblioteca.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_tracks_playlistId`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_playlistId_position` ON `tracks` (`playlistId`, `position`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlists_originalUrl` ON `playlists` (`originalUrl`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_playedAt` ON `playback_history` (`playedAt`)")
            }
        }
    }
}
