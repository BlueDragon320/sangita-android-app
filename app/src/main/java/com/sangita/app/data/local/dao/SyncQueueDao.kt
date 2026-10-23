package com.sangita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sangita.app.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY created_at ASC")
    fun observePendingActions(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue ORDER BY created_at ASC")
    suspend fun getPendingActions(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observeQueueSize(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun getQueueSize(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueAction(action: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE id = :actionId")
    suspend fun deleteAction(actionId: Long)

    @Query("UPDATE sync_queue SET retry_count = retry_count + 1, last_error = :error WHERE id = :actionId")
    suspend fun incrementRetry(actionId: Long, error: String?)

    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()
}
