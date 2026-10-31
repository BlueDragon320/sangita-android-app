package com.sangita.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistDetailDto(
    val id: String,
    val title: String,
    val description: String? = "",
    val owner: String? = null,
    val visibility: String = "private",
    @SerialName("is_folder_playlist") val isFolderPlaylist: Boolean = false,
    @SerialName("track_count") val trackCount: Int = 0,
    @SerialName("unavailable_count") val unavailableCount: Int = 0,
    val tracks: List<PlaylistTrackDto> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_saved") val isSaved: Boolean = false
)
