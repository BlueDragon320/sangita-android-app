package com.sangita.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LyricsData(
    val trackId: String,
    val lines: List<LyricLine> = emptyList(),
    val isSynced: Boolean = false,
    val offsetSec: Double = 0.0,
    val hasLyrics: Boolean = false,
    val rawLrc: String = ""
)
