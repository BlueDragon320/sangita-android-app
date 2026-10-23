package com.sangita.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LyricsResponse(
    @SerialName("has_lyrics") val hasLyrics: Boolean = false,
    val raw: String = "",
    val filepath: String = "",
    val error: String? = null
)

@Serializable
data class ToggleFavoriteRequest(
    @SerialName("track_path") val trackPath: String
)

@Serializable
data class ToggleFavoriteResponse(
    @SerialName("is_favorite") val isFavorite: Boolean,
    @SerialName("track_path") val trackPath: String,
    val favorites: List<String> = emptyList()
)

@Serializable
data class CreateUserPlaylistRequest(
    val name: String
)

@Serializable
data class PlaylistTrackActionRequest(
    @SerialName("track_path") val trackPath: String
)

@Serializable
data class UserPlaylistsResponse(
    val playlists: List<String> = emptyList()
)

@Serializable
data class StatsPingRequest(
    @SerialName("track_id") val trackId: String,
    val playlist: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("device_type") val deviceType: String = "mobile",
    val browser: String = "Sangita Android Native",
    val os: String = "Android",
    @SerialName("duration_sec") val durationSec: Int
)

@Serializable
data class UserStatsResponse(
    val username: String = "",
    @SerialName("total_seconds") val totalSeconds: Long = 0,
    @SerialName("unique_tracks") val uniqueTracks: Int = 0,
    @SerialName("total_plays") val totalPlays: Int = 0,
    @SerialName("top_tracks") val topTracks: List<TopTrackDto> = emptyList(),
    @SerialName("recently_played") val recentlyPlayed: List<RecentlyPlayedDto> = emptyList(),
    @SerialName("top_artists") val topArtists: List<TopArtistDto> = emptyList(),
    @SerialName("data_consumed_today") val dataConsumedToday: DataConsumptionItemDto? = null,
    @SerialName("data_consumed_total") val dataConsumedTotal: DataConsumptionItemDto? = null
)

@Serializable
data class TopTrackDto(
    val path: String,
    val name: String,
    val artist: String,
    val playlist: String,
    @SerialName("total_seconds") val totalSeconds: Long = 0,
    @SerialName("play_count") val playCount: Int = 0
)

@Serializable
data class RecentlyPlayedDto(
    val path: String,
    val name: String,
    val artist: String,
    val playlist: String,
    @SerialName("duration_sec") val durationSec: Int = 0,
    @SerialName("played_at") val playedAt: String = ""
)

@Serializable
data class TopArtistDto(
    val artist: String,
    @SerialName("play_count") val playCount: Int = 0,
    @SerialName("total_seconds") val totalSeconds: Long = 0,
    val tracks: Int = 0
)

@Serializable
data class DataConsumptionItemDto(
    val bytes: Long = 0,
    val formatted: String = "0 B",
    val date: String? = null
)

@Serializable
data class RecordDataUsageRequest(
    val bytes: Long,
    @SerialName("device_id") val deviceId: String? = null
)

@Serializable
data class DataUsageResponse(
    val username: String = "",
    val today: DataUsageTodayDto = DataUsageTodayDto(),
    val total: DataUsageTotalDto = DataUsageTotalDto()
)

@Serializable
data class DataUsageTodayDto(
    val date: String? = null,
    val bytes: Long = 0L,
    val formatted: String = "0 B",
    val requests: Int = 0,
    @SerialName("last_updated") val lastUpdated: String? = null
)

@Serializable
data class DataUsageTotalDto(
    val bytes: Long = 0L,
    val formatted: String = "0 B"
)

