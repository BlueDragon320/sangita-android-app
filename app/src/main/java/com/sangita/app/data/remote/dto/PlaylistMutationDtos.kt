package com.sangita.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddTrackResponseDto(
    val status: String,
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("track_path") val trackPath: String,
    val position: Int = 0
)

@Serializable
data class RemoveTrackResponseDto(
    val status: String,
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("track_path") val trackPath: String
)

@Serializable
data class DeletePlaylistResponseDto(
    val status: String,
    val id: String
)

@Serializable
data class LegacyPlaylistMutationDto(
    val status: String? = null,
    val name: String? = null,
    val id: String? = null
)

