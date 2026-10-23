package com.sangita.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LyricLine(
    val time: Double,              // Time in seconds
    val text: String,
    val isInstrumental: Boolean = false
)
