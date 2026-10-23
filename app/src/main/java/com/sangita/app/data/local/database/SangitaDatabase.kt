package com.sangita.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sangita.app.data.local.dao.FavoriteDao
import com.sangita.app.data.local.dao.LyricsDao
import com.sangita.app.data.local.dao.PlaybackEventDao
import com.sangita.app.data.local.dao.PlaylistDao
import com.sangita.app.data.local.dao.SyncQueueDao
import com.sangita.app.data.local.dao.TrackDao
import com.sangita.app.data.local.entity.FavoriteEntity
import com.sangita.app.data.local.entity.LyricsCacheEntity
import com.sangita.app.data.local.entity.PlaybackEventEntity
import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.data.local.entity.PlaylistTrackCrossRef
import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.data.local.entity.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        FavoriteEntity::class,
        SyncQueueEntity::class,
        PlaybackEventEntity::class,
        LyricsCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(SangitaTypeConverters::class)
abstract class SangitaDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun playbackEventDao(): PlaybackEventDao
    abstract fun lyricsDao(): LyricsDao

    companion object {
        @Volatile
        private var INSTANCE: SangitaDatabase? = null

        fun getInstance(context: Context): SangitaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SangitaDatabase::class.java,
                    "sangita.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
