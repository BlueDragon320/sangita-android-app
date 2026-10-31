package com.sangita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.sync.SyncPayloadSerialization
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE account_key = :accountKey AND is_failed = 0 ORDER BY created_at ASC, id ASC")
    fun observePendingActions(accountKey: String): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE is_failed = 0 ORDER BY created_at ASC, id ASC")
    fun observePendingActions(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE account_key = :accountKey AND is_failed = 0 ORDER BY created_at ASC, id ASC")
    suspend fun getPendingActions(accountKey: String): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE is_failed = 0 ORDER BY created_at ASC, id ASC")
    suspend fun getPendingActions(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE account_key = :accountKey AND is_failed = 0")
    fun observeQueueSize(accountKey: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE is_failed = 0")
    fun observeQueueSize(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE account_key = :accountKey AND is_failed = 0")
    suspend fun getQueueSize(accountKey: String): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE is_failed = 0")
    suspend fun getQueueSize(): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE account_key = :accountKey AND is_failed = 0")
    suspend fun getActionCount(accountKey: String): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE is_failed = 0")
    suspend fun getActionCount(): Int

    @Query("SELECT * FROM sync_queue WHERE is_failed = 1 AND (account_key = :accountKey OR :accountKey = '' OR (account_key IS NULL AND :accountKey = 'default_account')) ORDER BY failed_at DESC, id DESC")
    fun observeFailedActions(accountKey: String): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE is_failed = 1 ORDER BY failed_at DESC, id DESC")
    fun observeFailedActions(): Flow<List<SyncQueueEntity>>

    @Query("UPDATE sync_queue SET is_failed = 0, failure_reason = NULL, failed_at = NULL, retry_count = 0 WHERE id = :actionId")
    suspend fun retryFailedAction(actionId: Long)

    @Query("DELETE FROM sync_queue WHERE id = :actionId")
    suspend fun dismissFailedAction(actionId: Long)

    @Query("UPDATE sync_queue SET is_failed = 1, failure_reason = :failureReason, failed_at = :failedAt WHERE id = :actionId")
    suspend fun markActionFailed(actionId: Long, failureReason: String?, failedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueAction(action: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE id = :actionId")
    suspend fun deleteAction(actionId: Long)

    @Query("UPDATE sync_queue SET retry_count = retry_count + 1, last_error = :error WHERE id = :actionId")
    suspend fun incrementRetry(actionId: Long, error: String?)

    @Query("DELETE FROM sync_queue WHERE account_key = :accountKey")
    suspend fun clearAll(accountKey: String)

    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()

    @Query("SELECT * FROM sync_queue WHERE id = :actionId LIMIT 1")
    suspend fun getActionById(actionId: Long): SyncQueueEntity?

    @Update
    suspend fun updateAction(action: SyncQueueEntity)

    @Query("UPDATE sync_queue SET payload_json = :payloadJson WHERE id = :actionId")
    suspend fun updatePayload(actionId: Long, payloadJson: String)

    @Query("DELETE FROM sync_queue WHERE account_key = :accountKey AND payload_json LIKE '%' || :localKey || '%'")
    suspend fun deleteActionsByLocalKey(accountKey: String, localKey: String)

    @Query("DELETE FROM sync_queue WHERE payload_json LIKE '%' || :localKey || '%'")
    suspend fun deleteActionsByLocalKey(localKey: String)

    @Query("UPDATE sync_queue SET account_key = :targetAccountKey WHERE account_key = 'default_account' AND payload_json LIKE '%' || :localKey || '%'")
    suspend fun migrateActionsToAccount(localKey: String, targetAccountKey: String)

    @Query("SELECT * FROM sync_queue WHERE account_key = :accountKey ORDER BY created_at ASC, id ASC")
    suspend fun getAllActionsForAccount(accountKey: String): List<SyncQueueEntity>

    suspend fun migrateActionsForPlaylist(
        localKey: String,
        playlistTitle: String,
        targetAccountKey: String,
        newTitle: String = playlistTitle,
        isTitleAmbiguous: Boolean = false
    ) {
        val candidates = getAllActionsForAccount("default_account")
        for (action in candidates) {
            val retargeted = SyncPayloadSerialization.matchAndRetargetPlaylistAction(
                action = action,
                localKey = localKey,
                oldTitle = playlistTitle,
                newTitle = newTitle,
                isTitleAmbiguous = isTitleAmbiguous
            )
            if (retargeted != null) {
                updateAction(retargeted.copy(accountKey = targetAccountKey))
            }
        }
    }
}

