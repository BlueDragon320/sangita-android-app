package com.sangita.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LibraryArtistsResponseDto(
    val artists: List<ArtistDto> = emptyList()
)

@Serializable
data class LibraryTracksResponseDto(
    val tracks: List<PlaylistTrackDto> = emptyList()
)

@Serializable
data class SaveTrackRequest(
    @SerialName("track_path") val trackPath: String
)

@Serializable
data class SaveActionResponseDto(
    val status: String = "saved",
    val id: String? = null,
    @SerialName("track_path") val trackPath: String? = null
)
