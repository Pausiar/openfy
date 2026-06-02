package com.pausiar.openfy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val platform: String,
    val originalUrl: String,
    val imageUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isLocal: Boolean,
    val description: String?,
)