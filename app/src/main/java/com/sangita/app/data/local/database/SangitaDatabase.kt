package com.sangita.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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
        // Preserve existing offline libraries when introducing playlist membership links.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN album TEXT NOT NULL DEFAULT ''")
                db.execSQL("""INSERT OR IGNORE INTO playlist_track_cross_ref
                    (playlist_name, track_id, position, added_at)
                    SELECT playlist_name, id, 0, 0 FROM tracks
                    WHERE playlist_name != 'Favorites'""")
            }
        }

        @Volatile
        private var INSTANCE: SangitaDatabase? = null

        fun getInstance(context: Context): SangitaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SangitaDatabase::class.java,
                    "sangita.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
