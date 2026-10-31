package com.sangita.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlaylistKind {
    PERSONAL,
    FOLDER,
    PUBLIC,
    FAVORITES,
    LEGACY_PENDING
}
