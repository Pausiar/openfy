package com.pausiar.openfy.data.local.model

data class PlaylistSummaryEntity(
    val id: Long,
    val title: String,
    val platform: String,
    val originalUrl: String,
    val imageUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isLocal: Boolean,
    val description: String?,
    val trackCount: Int,
    val playableTrackCount: Int,
)