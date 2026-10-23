package com.sangita.app.domain.repository

import com.sangita.app.domain.model.SyncActionType
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    suspend fun enqueueAction(type: SyncActionType, payloadJson: String)
    suspend fun syncPendingActions(): Result<Int>
    fun getSyncQueueSize(): Flow<Int>
}
