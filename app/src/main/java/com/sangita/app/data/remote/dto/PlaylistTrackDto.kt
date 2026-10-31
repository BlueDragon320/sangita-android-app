package com.sangita.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistTrackDto(
    val id: Long? = null,
    val position: Int = 0,
    @SerialName("track_path") val trackPath: String,
    val title: String,
    val artist: String = "",
    val duration: Double? = null,
    @SerialName("is_available") val isAvailable: Boolean = true
)
