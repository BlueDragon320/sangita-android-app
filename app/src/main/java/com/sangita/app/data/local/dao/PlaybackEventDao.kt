package com.sangita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sangita.app.data.local.entity.PlaybackEventEntity

@Dao
interface PlaybackEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: PlaybackEventEntity)

    @Query("SELECT * FROM playback_events WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedEvents(): List<PlaybackEventEntity>

    @Query("UPDATE playback_events SET synced = 1 WHERE id IN (:eventIds)")
    suspend fun markSynced(eventIds: List<Long>)

    @Query("DELETE FROM playback_events WHERE synced = 1")
    suspend fun purgeSyncedEvents()
}
