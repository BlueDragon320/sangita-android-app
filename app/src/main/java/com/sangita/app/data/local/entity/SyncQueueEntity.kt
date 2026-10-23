package com.sangita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sangita.app.domain.model.SyncAction
import com.sangita.app.domain.model.SyncActionType

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "action_type")
    val actionType: SyncActionType,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null
) {
    fun toDomain(): SyncAction = SyncAction(
        id = id,
        type = actionType,
        payloadJson = payloadJson,
        timestamp = createdAt,
        retryCount = retryCount
    )

    companion object {
        fun fromDomain(action: SyncAction): SyncQueueEntity = SyncQueueEntity(
            id = action.id,
            actionType = action.type,
            payloadJson = action.payloadJson,
            createdAt = action.timestamp,
            retryCount = action.retryCount
        )
    }
}
