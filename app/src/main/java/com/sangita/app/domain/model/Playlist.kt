package com.sangita.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Playlist(
    val name: String,
    val isVirtual: Boolean = false,
    val isFavorites: Boolean = false,
    val trackCount: Int = 0,
    val coverTrackPath: String? = null,
    val tracks: List<Track> = emptyList()
)
