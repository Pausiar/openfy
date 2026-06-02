package com.pausiar.openfy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val imageUrl: String?,
    val originalUrl: String?,
    val localUri: String?,
    val previewUrl: String?,
    val platform: String,
    val position: Int,
    val isFavorite: Boolean,
    val isPlayableInApp: Boolean,
)