package com.pausiar.openfy.data.repository

import android.net.Uri
import com.pausiar.openfy.data.local.entity.PlaybackHistoryEntity
import com.pausiar.openfy.data.local.entity.PlaylistEntity
import com.pausiar.openfy.data.local.entity.TrackEntity
import com.pausiar.openfy.domain.models.ImportResult
import com.pausiar.openfy.domain.models.Playlist
import com.pausiar.openfy.domain.models.PlaylistSummary
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun observePlaylistSummaries(): Flow<List<PlaylistSummary>>
    fun observePlaylist(playlistId: Long): Flow<Playlist?>
    fun observeRecentHistory(): Flow<List<PlaybackHistoryEntity>>
    suspend fun importFromUrl(url: String): ImportResult
    suspend fun createLocalPlaylist(title: String): Long
    suspend fun importLocalTracks(playlistTitle: String, uris: List<Uri>): ImportResult
    suspend fun renamePlaylist(playlistId: Long, title: String)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun toggleFavorite(trackId: Long)
    suspend fun logPlayback(trackId: Long, progressMs: Long)
    suspend fun getAllPlaylists(): List<PlaylistEntity>
    suspend fun getAllTracks(): List<TrackEntity>
    suspend fun getAllHistory(): List<PlaybackHistoryEntity>
    suspend fun replaceLibrary(
        playlists: List<PlaylistEntity>,
        tracks: List<TrackEntity>,
        history: List<PlaybackHistoryEntity>,
    )
    suspend fun seedDemoPlaylistIfNeeded()
}