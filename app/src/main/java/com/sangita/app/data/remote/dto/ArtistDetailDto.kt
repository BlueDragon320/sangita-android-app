package com.sangita.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArtistDetailDto(
    val id: String,
    val name: String,
    val bio: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("track_count") val trackCount: Int = 0,
    val tracks: List<ArtistTrackDto> = emptyList()
)
