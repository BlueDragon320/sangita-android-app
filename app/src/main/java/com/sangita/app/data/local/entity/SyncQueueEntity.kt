package com.sangita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sangita.app.domain.model.SyncAction
import com.sangita.app.domain.model.SyncActionType

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["account_key"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "account_key")
    val accountKey: String = "default_account",

    @ColumnInfo(name = "action_type")
    val actionType: SyncActionType,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "is_failed", defaultValue = "0")
    val is_failed: Boolean = false,

    @ColumnInfo(name = "failure_reason")
    val failure_reason: String? = null,

    @ColumnInfo(name = "failed_at")
    val failed_at: Long? = null
) {
    @get:Ignore
    val isFailed: Boolean get() = is_failed

    @get:Ignore
    val failureReason: String? get() = failure_reason

    @get:Ignore
    val failedAt: Long? get() = failed_at

    fun toDomain(): SyncAction = SyncAction(
        id = id,
        type = actionType,
        payloadJson = payloadJson,
        timestamp = createdAt,
        retryCount = retryCount
    )

    companion object {
        fun fromDomain(action: SyncAction, accountKey: String = "default_account"): SyncQueueEntity = SyncQueueEntity(
            id = action.id,
            accountKey = accountKey,
            actionType = action.type,
            payloadJson = action.payloadJson,
            createdAt = action.timestamp,
            retryCount = action.retryCount
        )
    }
}
