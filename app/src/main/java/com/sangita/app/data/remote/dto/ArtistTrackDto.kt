package com.sangita.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ArtistTrackDto(
    val path: String,
    val title: String,
    val artist: String = "",
    val duration: Double? = null
)
