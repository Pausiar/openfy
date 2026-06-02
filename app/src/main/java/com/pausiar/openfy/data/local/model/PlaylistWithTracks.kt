package com.pausiar.openfy.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.pausiar.openfy.data.local.entity.PlaylistEntity
import com.pausiar.openfy.data.local.entity.TrackEntity

data class PlaylistWithTracks(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId",
    )
    val tracks: List<TrackEntity>,
)