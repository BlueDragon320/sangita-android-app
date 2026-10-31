package com.sangita.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CatalogSourceDto(
    val id: Int,
    val name: String,
    @SerialName("display_name") val displayName: String,
    val classification: String,
    @SerialName("artist_id") val artistId: String? = null,
    @SerialName("has_access") val hasAccess: Boolean,
    @SerialName("track_count") val trackCount: Int
)

@Serializable
data class SourcesResponseDto(
    val sources: List<CatalogSourceDto> = emptyList()
)
