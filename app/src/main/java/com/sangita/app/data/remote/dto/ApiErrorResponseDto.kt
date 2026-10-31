package com.sangita.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorResponseDto(
    val error: String,
    val status: String? = null
)
