package com.sangita.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SyncAction(
    val id: Long = 0,
    val type: SyncActionType,
    val payloadJson: String,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)

enum class SyncActionType {
    TOGGLE_FAVORITE,
    CREATE_PLAYLIST,
    ADD_TRACK_TO_PLAYLIST,
    REMOVE_TRACK_FROM_PLAYLIST,
    PLAY_EVENT,
    DATA_CONSUMPTION
}
