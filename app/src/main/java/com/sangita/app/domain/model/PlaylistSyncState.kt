package com.sangita.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlaylistSyncState {
    SYNCED,
    PENDING,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    CONFLICT,
    ERROR
}
