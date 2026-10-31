package com.sangita.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistDto(
    val id: String,
    val title: String,
    val description: String? = "",
    val owner: String? = null,
    val visibility: String = "private",
    @SerialName("is_folder_playlist") val isFolderPlaylist: Boolean = false,
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_saved") val isSaved: Boolean = false
)

@Serializable
data class PlaylistsResponseDto(
    val playlists: List<PlaylistDto> = emptyList()
)
