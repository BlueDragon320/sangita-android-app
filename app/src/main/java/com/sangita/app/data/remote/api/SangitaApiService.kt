package com.sangita.app.data.remote.api

import com.sangita.app.data.remote.dto.AddArtistCollectionRequest
import com.sangita.app.data.remote.dto.AddTrackRequest
import com.sangita.app.data.remote.dto.AddTrackResponseDto
import com.sangita.app.data.remote.dto.ArtistAddCollectionResponseDto
import com.sangita.app.data.remote.dto.ArtistDetailDto
import com.sangita.app.data.remote.dto.ArtistsResponseDto
import com.sangita.app.data.remote.dto.ClonePlaylistRequest
import com.sangita.app.data.remote.dto.CreatePlaylistRequest
import com.sangita.app.data.remote.dto.CreateUserPlaylistRequest
import com.sangita.app.data.remote.dto.DataUsageResponse
import com.sangita.app.data.remote.dto.DeletePlaylistResponseDto
import com.sangita.app.data.remote.dto.LegacyPlaylistMutationDto
import com.sangita.app.data.remote.dto.LoginRequest
import com.sangita.app.data.remote.dto.LoginResponse
import com.sangita.app.data.remote.dto.LyricsResponse
import com.sangita.app.data.remote.dto.MeResponse
import com.sangita.app.data.remote.dto.PlaylistDetailDto
import com.sangita.app.data.remote.dto.PlaylistDto
import com.sangita.app.data.remote.dto.PlaylistsResponseDto
import com.sangita.app.data.remote.dto.PlaylistTrackActionRequest
import com.sangita.app.data.remote.dto.RecordDataUsageRequest
import com.sangita.app.data.remote.dto.RemoveTrackResponseDto
import com.sangita.app.data.remote.dto.SourcesResponseDto
import com.sangita.app.data.remote.dto.StatsPingRequest
import com.sangita.app.data.remote.dto.SetFavoriteRequest
import com.sangita.app.data.remote.dto.TrackMetadataDto
import com.sangita.app.data.remote.dto.ToggleFavoriteRequest
import com.sangita.app.data.remote.dto.ToggleFavoriteResponse
import com.sangita.app.data.remote.dto.UpdatePlaylistRequest
import com.sangita.app.data.remote.dto.UserPlaylistsResponse
import com.sangita.app.data.remote.dto.UserStatsResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface SangitaApiService {

    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("/api/me")
    suspend fun getMe(): Response<MeResponse>

    @GET("/api/playlists")
    suspend fun getLegacyPlaylists(): Response<Map<String, List<String>>>

    // V2 Catalog & Discovery Endpoints
    @GET("api/v2/catalog/sources")
    suspend fun getCatalogSources(): Response<SourcesResponseDto>

    @GET("api/v2/artists")
    suspend fun getArtists(): Response<ArtistsResponseDto>

    @GET("api/v2/artists/{id}")
    suspend fun getArtistDetail(@Path("id") artistId: String): Response<ArtistDetailDto>

    @POST("api/v2/artists/{id}/add-to-playlist")
    suspend fun addArtistCollectionToPlaylist(
        @Path("id") artistId: String,
        @Body request: AddArtistCollectionRequest
    ): Response<ArtistAddCollectionResponseDto>

    // V2 Playlist Endpoints
    @GET("api/v2/playlists")
    suspend fun getPlaylists(@Query("discover") discover: Boolean? = null): Response<PlaylistsResponseDto>

    @POST("api/v2/playlists")
    suspend fun createPlaylist(@Body request: CreatePlaylistRequest): Response<PlaylistDetailDto>

    @GET("api/v2/playlists/{id}")
    suspend fun getPlaylistDetail(@Path("id") playlistId: String): Response<PlaylistDetailDto>

    @PUT("api/v2/playlists/{id}")
    suspend fun updatePlaylist(@Path("id") playlistId: String, @Body request: UpdatePlaylistRequest): Response<PlaylistDto>

    @DELETE("api/v2/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") playlistId: String): Response<DeletePlaylistResponseDto>

    @POST("api/v2/playlists/{id}/save")
    suspend fun savePlaylistToLibrary(@Path("id") playlistId: String): Response<Map<String, String>>

    @DELETE("api/v2/playlists/{id}/save")
    suspend fun removePlaylistFromLibrary(@Path("id") playlistId: String): Response<Map<String, String>>

    @POST("api/v2/playlists/{id}/clone")
    suspend fun clonePlaylist(@Path("id") playlistId: String, @Body request: ClonePlaylistRequest): Response<PlaylistDetailDto>

    @POST("api/v2/playlists/{id}/tracks")
    suspend fun addTrackToPlaylist(@Path("id") playlistId: String, @Body request: AddTrackRequest): Response<AddTrackResponseDto>

    @DELETE("api/v2/playlists/{id}/tracks")
    suspend fun removeTrackFromPlaylist(
        @Path("id") playlistId: String,
        @Query("track_path") trackPath: String
    ): Response<RemoveTrackResponseDto>

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

    @POST("api/user-playlists")
    suspend fun createLegacyPlaylist(@Body body: Map<String, String>): Response<LegacyPlaylistMutationDto>

    @POST("/api/user-playlists/{name}/tracks")
    suspend fun addTrackToUserPlaylist(
        @Path("name") name: String,
        @Body request: PlaylistTrackActionRequest
    ): Response<Map<String, String>>

    @POST("api/user-playlists/{name}/tracks")
    suspend fun addLegacyTrack(
        @Path("name") name: String,
        @Body body: Map<String, String>
    ): Response<LegacyPlaylistMutationDto>

    @DELETE("/api/user-playlists/{name}/tracks")
    suspend fun removeTrackFromUserPlaylist(
        @Path("name") name: String,
        @Body request: PlaylistTrackActionRequest
    ): Response<Map<String, String>>

    @DELETE("api/user-playlists/{name}/tracks")
    suspend fun removeLegacyTrack(
        @Path("name") name: String,
        @Query("track_path") trackPath: String
    ): Response<LegacyPlaylistMutationDto>

    @PUT("api/user-playlists/{name}")
    suspend fun updateLegacyPlaylist(
        @Path("name") name: String,
        @Body body: Map<String, String>
    ): Response<LegacyPlaylistMutationDto>

    @DELETE("api/user-playlists/{name}")
    suspend fun deleteLegacyPlaylist(@Path("name") name: String): Response<LegacyPlaylistMutationDto>

    @POST("/api/stats/ping")
    suspend fun pingStats(@Body request: StatsPingRequest): Response<Map<String, String>>

    @GET("/api/user/stats")
    suspend fun getUserStats(): Response<UserStatsResponse>

    @GET("/api/user/data-usage")
    suspend fun getDataUsage(): Response<DataUsageResponse>

    @POST("/api/user/data-usage")
    suspend fun recordDataUsage(@Body request: RecordDataUsageRequest): Response<Map<String, String>>
}
