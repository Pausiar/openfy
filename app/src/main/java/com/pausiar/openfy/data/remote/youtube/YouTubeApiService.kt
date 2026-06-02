package com.pausiar.openfy.data.remote.youtube

import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApiService {
    @GET("youtube/v3/playlists")
    suspend fun getPlaylist(
        @Query("part") part: String = "snippet",
        @Query("id") id: String,
        @Query("key") key: String,
    ): YouTubeListResponse<YouTubePlaylistDto>

    @GET("youtube/v3/playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("key") key: String,
    ): YouTubeListResponse<YouTubePlaylistItemDto>

    @GET("youtube/v3/videos")
    suspend fun getVideos(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("id") ids: String,
        @Query("key") key: String,
    ): YouTubeListResponse<YouTubeVideoDto>
}