package com.sangita.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val username: String,
    val role: String = "user",
    val allowedPlaylists: List<String> = listOf("*"),
    val token: String? = null,
    val serverUrl: String = ""
)
