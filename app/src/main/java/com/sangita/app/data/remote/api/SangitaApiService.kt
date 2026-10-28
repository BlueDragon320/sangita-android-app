package com.sangita.app.data.remote.api

import com.sangita.app.data.remote.dto.CreateUserPlaylistRequest
import com.sangita.app.data.remote.dto.DataUsageResponse
import com.sangita.app.data.remote.dto.LoginRequest
import com.sangita.app.data.remote.dto.LoginResponse
import com.sangita.app.data.remote.dto.LyricsResponse
import com.sangita.app.data.remote.dto.MeResponse
import com.sangita.app.data.remote.dto.PlaylistTrackActionRequest
import com.sangita.app.data.remote.dto.RecordDataUsageRequest
import com.sangita.app.data.remote.dto.StatsPingRequest
import com.sangita.app.data.remote.dto.SetFavoriteRequest
import com.sangita.app.data.remote.dto.TrackMetadataDto
import com.sangita.app.data.remote.dto.ToggleFavoriteRequest
import com.sangita.app.data.remote.dto.ToggleFavoriteResponse
import com.sangita.app.data.remote.dto.UserPlaylistsResponse
import com.sangita.app.data.remote.dto.UserStatsResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

interface SangitaApiService {

    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("/api/me")
    suspend fun getMe(): Response<MeResponse>

    @GET("/api/playlists")
    suspend fun getPlaylists(): Response<Map<String, List<String>>>

    @GET("/api/durations")
    suspend fun getDurations(): Response<Map<String, Double?>>

    @GET("/api/track-metadata")
    suspend fun getTrackMetadata(): Response<Map<String, TrackMetadataDto>>

    @Streaming
    @GET("/api/stream/{filepath}")
    suspend fun streamAudio(@Path("filepath", encoded = true) filepath: String): Response<ResponseBody>

    @GET("/api/cover/{filepath}")
    suspend fun getCoverArt(@Path("filepath", encoded = true) filepath: String): Response<ResponseBody>

    @GET("/api/lyrics/{filepath}")
    suspend fun getLyrics(@Path("filepath", encoded = true) filepath: String): Response<LyricsResponse>

    @GET("/api/favorites")
    suspend fun getFavorites(): Response<List<String>>

    @POST("/api/favorites/toggle")
    suspend fun toggleFavorite(@Body request: ToggleFavoriteRequest): Response<ToggleFavoriteResponse>

    @POST("/api/favorites/set")
    suspend fun setFavorite(@Body request: SetFavoriteRequest): Response<ToggleFavoriteResponse>

    @GET("/api/user-playlists")
    suspend fun getUserPlaylists(): Response<UserPlaylistsResponse>

    @POST("/api/user-playlists")
    suspend fun createUserPlaylist(@Body request: CreateUserPlaylistRequest): Response<Map<String, String>>

    @POST("/api/user-playlists/{name}/tracks")
    suspend fun addTrackToUserPlaylist(
        @Path("name") name: String,
        @Body request: PlaylistTrackActionRequest
    ): Response<Map<String, String>>

    @DELETE("/api/user-playlists/{name}/tracks")
    suspend fun removeTrackFromUserPlaylist(
        @Path("name") name: String,
        @Body request: PlaylistTrackActionRequest
    ): Response<Map<String, String>>

    @POST("/api/stats/ping")
    suspend fun pingStats(@Body request: StatsPingRequest): Response<Map<String, String>>

    @GET("/api/user/stats")
    suspend fun getUserStats(): Response<UserStatsResponse>

    @GET("/api/user/data-usage")
    suspend fun getDataUsage(): Response<DataUsageResponse>

    @POST("/api/user/data-usage")
    suspend fun recordDataUsage(@Body request: RecordDataUsageRequest): Response<Map<String, String>>
}
