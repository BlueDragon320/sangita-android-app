package com.sangita.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreatePlaylistRequest(
    val title: String,
    val description: String? = null,
    val visibility: String = "private",
    val tracks: List<String>? = null,
    @SerialName("idempotency_key") val idempotencyKey: String? = null
)

@Serializable
data class UpdatePlaylistRequest(
    val title: String? = null,
    val description: String? = null,
    val visibility: String? = null,
    val tracks: List<String>? = null
)

@Serializable
data class ClonePlaylistRequest(
    val title: String? = null,
    val description: String? = null,
    val visibility: String = "private",
    @SerialName("idempotency_key") val idempotencyKey: String? = null
)

@Serializable
data class AddTrackRequest(
    @SerialName("track_path") val trackPath: String
)
