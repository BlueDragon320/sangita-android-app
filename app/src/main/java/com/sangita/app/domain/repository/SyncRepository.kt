package com.sangita.app.domain.repository

import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.domain.model.SyncActionType
import com.sangita.app.sync.AddTrackPayload
import com.sangita.app.sync.ClonePlaylistPayload
import com.sangita.app.sync.CreatePlaylistPayload
import com.sangita.app.sync.DeletePlaylistPayload
import com.sangita.app.sync.RemoveTrackPayload
import com.sangita.app.sync.ToggleFavoritePayload
import com.sangita.app.sync.UpdatePlaylistPayload
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    suspend fun enqueueAction(type: SyncActionType, payloadJson: String)
    suspend fun syncPendingActions(): Result<Int>
    fun getSyncQueueSize(): Flow<Int>

    suspend fun enqueueCreatePlaylist(payload: CreatePlaylistPayload) {}
    suspend fun enqueueUpdatePlaylist(payload: UpdatePlaylistPayload) {}
    suspend fun enqueueDeletePlaylist(payload: DeletePlaylistPayload) {}
    suspend fun enqueueAddTrack(payload: AddTrackPayload) {}
    suspend fun enqueueRemoveTrack(payload: RemoveTrackPayload) {}
    suspend fun enqueueClonePlaylist(payload: ClonePlaylistPayload) {}
    suspend fun enqueueToggleFavorite(payload: ToggleFavoritePayload) {}

    fun observeFailedActions(accountKey: String): Flow<List<SyncQueueEntity>>
    suspend fun retryFailedAction(id: Long)
    suspend fun dismissFailedAction(id: Long)
}
