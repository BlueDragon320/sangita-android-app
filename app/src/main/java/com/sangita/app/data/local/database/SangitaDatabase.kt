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
    version = 5,
    exportSchema = true
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
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN album TEXT NOT NULL DEFAULT ''")
                db.execSQL("""INSERT OR IGNORE INTO playlist_track_cross_ref
                    (playlist_name, track_id, position, added_at)
                    SELECT playlist_name, id, 0, 0 FROM tracks
                    WHERE playlist_name != 'Favorites'""")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create playlists_v3
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlists_v3 (
                        local_key TEXT PRIMARY KEY NOT NULL,
                        server_id TEXT,
                        account_key TEXT NOT NULL DEFAULT 'default_account',
                        title TEXT NOT NULL,
                        kind TEXT NOT NULL DEFAULT 'PERSONAL',
                        owner TEXT,
                        visibility TEXT NOT NULL DEFAULT 'private',
                        description TEXT,
                        track_count INTEGER NOT NULL DEFAULT 0,
                        cover_track_path TEXT,
                        unavailable_count INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        last_synced_at INTEGER NOT NULL DEFAULT 0,
                        sync_state TEXT NOT NULL DEFAULT 'SYNCED'
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlists_account_key ON playlists_v3(account_key)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlists_server_id ON playlists_v3(server_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlists_account_key_server_id ON playlists_v3(account_key, server_id)")

                // 2. Migrate existing records from playlists
                // Assign deterministic UUID for local_key using randomblob
                db.execSQL("""
                    INSERT INTO playlists_v3 (
                        local_key, server_id, account_key, title, kind,
                        owner, visibility, description, track_count, cover_track_path,
                        unavailable_count, created_at, last_synced_at, sync_state
                    )
                    SELECT
                        'legacy_' || lower(hex(randomblob(16))),
                        NULL,
                        'default_account',
                        name,
                        CASE 
                            WHEN is_favorites = 1 THEN 'FAVORITES'
                            WHEN is_virtual = 1 THEN 'LEGACY_PENDING'
                            ELSE 'FOLDER'
                        END,
                        NULL,
                        'private',
                        NULL,
                        track_count,
                        cover_track_path,
                        0,
                        created_at,
                        last_synced_at,
                        'SYNCED'
                    FROM playlists
                """)

                // 3. Create playlist_track_cross_ref_v3
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlist_track_cross_ref_v3 (
                        playlist_local_key TEXT NOT NULL,
                        track_id TEXT NOT NULL,
                        position INTEGER NOT NULL DEFAULT 0,
                        is_available INTEGER NOT NULL DEFAULT 1,
                        added_at INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(playlist_local_key, track_id)
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ref_local_key ON playlist_track_cross_ref_v3(playlist_local_key)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ref_track_id ON playlist_track_cross_ref_v3(track_id)")

                // 4. Populate cross refs by joining on title
                db.execSQL("""
                    INSERT OR IGNORE INTO playlist_track_cross_ref_v3 (playlist_local_key, track_id, position, is_available, added_at)
                    SELECT p.local_key, r.track_id, r.position, 1, r.added_at
                    FROM playlist_track_cross_ref r
                    INNER JOIN playlists_v3 p ON p.title = r.playlist_name
                """)

                // 5. Swap tables
                db.execSQL("DROP TABLE playlist_track_cross_ref")
                db.execSQL("ALTER TABLE playlist_track_cross_ref_v3 RENAME TO playlist_track_cross_ref")
                db.execSQL("DROP TABLE playlists")
                db.execSQL("ALTER TABLE playlists_v3 RENAME TO playlists")

                // Ensure Room-expected index names on playlist_track_cross_ref exist
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_track_cross_ref_playlist_local_key ON playlist_track_cross_ref(playlist_local_key)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_track_cross_ref_track_id ON playlist_track_cross_ref(track_id)")
                db.execSQL("DROP INDEX IF EXISTS index_ref_local_key")
                db.execSQL("DROP INDEX IF EXISTS index_ref_track_id")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN account_key TEXT NOT NULL DEFAULT 'default_account'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_account_key ON sync_queue(account_key)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN is_failed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN failure_reason TEXT")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN failed_at INTEGER")
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
