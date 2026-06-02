package com.pausiar.openfy.data.remote.spotify

import retrofit2.http.GET
import retrofit2.http.Path

interface SpotifyProxyService {
    @GET("playlist/{id}")
    suspend fun getPlaylist(@Path("id") playlistId: String): SpotifyPlaylistDto

    @GET("track/{id}")
    suspend fun getTrack(@Path("id") trackId: String): SpotifyTrackDto
}