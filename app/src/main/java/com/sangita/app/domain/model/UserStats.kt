package com.sangita.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserStats(
    val username: String = "",
    val totalSeconds: Long = 0,
    val uniqueTracks: Int = 0,
    val totalPlays: Int = 0,
    val topTracks: List<TopTrack> = emptyList(),
    val recentlyPlayed: List<RecentlyPlayedTrack> = emptyList(),
    val topArtists: List<TopArtist> = emptyList(),
    val dataConsumedToday: String = "0 B",
    val dataConsumedTotal: String = "0 B"
)

@Serializable
data class TopTrack(
    val path: String,
    val name: String,
    val artist: String,
    val playlist: String,
    val totalSeconds: Long,
    val playCount: Int
)

@Serializable
data class RecentlyPlayedTrack(
    val path: String,
    val name: String,
    val artist: String,
    val playlist: String,
    val durationSec: Int,
    val playedAt: String
)

@Serializable
data class TopArtist(
    val artist: String,
    val playCount: Int,
    val totalSeconds: Long,
    val tracks: Int
)
