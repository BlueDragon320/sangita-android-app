package com.sangita.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddArtistCollectionRequest(
    @SerialName("playlist_id") val playlistId: String? = null,
    @SerialName("new_playlist_title") val newPlaylistTitle: String? = null,
    val title: String? = null,
    val description: String? = null,
    val visibility: String = "private"
)

@Serializable
data class ArtistAddCollectionResponseDto(
    val status: String = "added",
    @SerialName("playlist_id") val playlistId: String? = null,
    @SerialName("playlist_title") val playlistTitle: String? = null,
    @SerialName("tracks_added") val tracksAdded: Int = 0,
    @SerialName("duplicates_skipped") val duplicatesSkipped: Int = 0,
    @SerialName("total_tracks") val totalTracks: Int = 0,
    val message: String? = null,
    val error: String? = null
)
