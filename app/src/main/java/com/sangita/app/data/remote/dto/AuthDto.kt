package com.sangita.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String? = null,
    val username: String? = null,
    val role: String? = null,
    val error: String? = null,
    @SerialName("retry_after") val retryAfter: Int? = null
)

@Serializable
data class MeResponse(
    val username: String,
    val role: String,
    val rules: MeRules = MeRules()
)

@Serializable
data class MeRules(
    @SerialName("allowed_playlists") val allowedPlaylists: List<String> = listOf("*")
)
