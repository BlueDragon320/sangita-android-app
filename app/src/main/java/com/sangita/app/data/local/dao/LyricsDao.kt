package com.sangita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sangita.app.data.local.entity.LyricsCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics_cache WHERE track_id = :trackId LIMIT 1")
    fun observeLyrics(trackId: String): Flow<LyricsCacheEntity?>

    @Query("SELECT * FROM lyrics_cache WHERE track_id = :trackId LIMIT 1")
    suspend fun getLyrics(trackId: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE track_id = :trackId")
    suspend fun deleteLyrics(trackId: String)
}
