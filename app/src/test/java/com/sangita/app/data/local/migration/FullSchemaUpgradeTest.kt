package com.sangita.app.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import com.sangita.app.data.local.database.SangitaDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * Priority 0 Item 3 & Priority 1 Verification:
 * Test upgrading Android Room database schema step-by-step from v1 through v5
 * using representative populated fixtures in in-memory SQLite.
 *
 * Verifies:
 * - Downloaded audio files and local track paths are completely preserved.
 * - Playlist membership and track order positions are intact.
 * - Account scoping (`account_key`) defaults safely to `default_account` without data loss.
 * - Failure tracking columns (`is_failed`, `failure_reason`, `failed_at`) in v5 are available and defaulted.
 * - Destructive migration is never used as a recovery shortcut.
 */
class FullSchemaUpgradeTest {

    private lateinit var connection: Connection
    private lateinit var supportDb: SupportSQLiteDatabase

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        supportDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            when (method.name) {
                "execSQL" -> {
                    val sql = args[0] as String
                    connection.createStatement().use { it.execute(sql) }
                    null
                }
                else -> throw UnsupportedOperationException("Method ${method.name} not implemented")
            }
        } as SupportSQLiteDatabase
    }

    @After
    fun tearDown() {
        if (!connection.isClosed) {
            connection.close()
        }
    }

    private fun createV1Schema() {
        connection.createStatement().use { stmt ->
            // 1. tracks table in v1 (does NOT have 'album' column)
            stmt.execute("""
                CREATE TABLE tracks (
                    id TEXT PRIMARY KEY NOT NULL,
                    playlist_name TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    duration_seconds REAL,
                    remote_url TEXT NOT NULL,
                    local_file_path TEXT,
                    is_downloaded INTEGER NOT NULL,
                    download_status TEXT NOT NULL,
                    download_progress INTEGER NOT NULL,
                    file_size_bytes INTEGER NOT NULL,
                    is_favorite INTEGER NOT NULL,
                    downloaded_at INTEGER,
                    last_played_at INTEGER
                )
            """.trimIndent())

            // 2. playlists table in v1 (name as PK, is_virtual, is_favorites)
            stmt.execute("""
                CREATE TABLE playlists (
                    name TEXT PRIMARY KEY NOT NULL,
                    is_virtual INTEGER NOT NULL DEFAULT 0,
                    is_favorites INTEGER NOT NULL DEFAULT 0,
                    track_count INTEGER NOT NULL DEFAULT 0,
                    cover_track_path TEXT,
                    created_at INTEGER NOT NULL DEFAULT 0,
                    last_synced_at INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            // 3. playlist_track_cross_ref in v1 (playlist_name, track_id, position, added_at)
            stmt.execute("""
                CREATE TABLE playlist_track_cross_ref (
                    playlist_name TEXT NOT NULL,
                    track_id TEXT NOT NULL,
                    position INTEGER NOT NULL DEFAULT 0,
                    added_at INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(playlist_name, track_id)
                )
            """.trimIndent())

            // 4. favorites table in v1
            stmt.execute("""
                CREATE TABLE favorites (
                    track_id TEXT NOT NULL,
                    added_at INTEGER NOT NULL,
                    PRIMARY KEY(track_id)
                )
            """.trimIndent())

            // 5. sync_queue table in v1 (no account_key, no failure tracking)
            stmt.execute("""
                CREATE TABLE sync_queue (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    action_type TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    retry_count INTEGER NOT NULL,
                    last_error TEXT
                )
            """.trimIndent())

            // 6. playback_events table in v1
            stmt.execute("""
                CREATE TABLE playback_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    track_id TEXT NOT NULL,
                    playlist TEXT NOT NULL,
                    duration_sec INTEGER NOT NULL,
                    device_id TEXT NOT NULL,
                    device_name TEXT NOT NULL,
                    device_type TEXT NOT NULL,
                    browser TEXT NOT NULL,
                    os TEXT NOT NULL,
                    timestamp TEXT NOT NULL,
                    synced INTEGER NOT NULL
                )
            """.trimIndent())

            // 7. lyrics_cache table in v1
            stmt.execute("""
                CREATE TABLE lyrics_cache (
                    track_id TEXT NOT NULL,
                    raw_lrc TEXT NOT NULL,
                    has_lyrics INTEGER NOT NULL,
                    cached_at INTEGER NOT NULL,
                    PRIMARY KEY(track_id)
                )
            """.trimIndent())
        }
    }

    private fun populateV1RepresentativeFixtures() {
        connection.createStatement().use { stmt ->
            // Insert representative tracks: downloaded lossless, downloaded lossy, stream-only
            stmt.execute("""
                INSERT INTO tracks (id, playlist_name, title, artist, duration_seconds, remote_url, local_file_path, is_downloaded, download_status, download_progress, file_size_bytes, is_favorite, downloaded_at, last_played_at)
                VALUES (
                    'Classical/Beethoven_Symphony5.flac',
                    'Symphonies',
                    'Symphony No. 5 in C minor, Op. 67',
                    'Ludwig van Beethoven',
                    484.5,
                    'http://stream.sangita.local/audio/Classical/Beethoven_Symphony5.flac',
                    '/data/user/0/com.sangita.app/files/downloads/Classical_Beethoven_Symphony5.flac',
                    1,
                    'COMPLETED',
                    100,
                    52428800,
                    1,
                    1600000000000,
                    1600050000000
                )
            """.trimIndent())

            stmt.execute("""
                INSERT INTO tracks (id, playlist_name, title, artist, duration_seconds, remote_url, local_file_path, is_downloaded, download_status, download_progress, file_size_bytes, is_favorite, downloaded_at, last_played_at)
                VALUES (
                    'Rock/StairwayToHeaven.mp3',
                    'Rock Classics',
                    'Stairway to Heaven',
                    'Led Zeppelin',
                    482.0,
                    'http://stream.sangita.local/audio/Rock/StairwayToHeaven.mp3',
                    '/data/user/0/com.sangita.app/files/downloads/Rock_StairwayToHeaven.mp3',
                    1,
                    'COMPLETED',
                    100,
                    11500000,
                    1,
                    1600200000000,
                    1600250000000
                )
            """.trimIndent())

            stmt.execute("""
                INSERT INTO tracks (id, playlist_name, title, artist, duration_seconds, remote_url, local_file_path, is_downloaded, download_status, download_progress, file_size_bytes, is_favorite, downloaded_at, last_played_at)
                VALUES (
                    'Jazz/Miles_SoWhat.mp3',
                    'Rock Classics',
                    'So What',
                    'Miles Davis',
                    562.0,
                    'http://stream.sangita.local/audio/Jazz/Miles_SoWhat.mp3',
                    '/data/user/0/com.sangita.app/files/downloads/Jazz_Miles_SoWhat.mp3',
                    1,
                    'COMPLETED',
                    100,
                    13500000,
                    0,
                    1600100000000,
                    1600150000000
                )
            """.trimIndent())

            stmt.execute("""
                INSERT INTO tracks (id, playlist_name, title, artist, duration_seconds, remote_url, local_file_path, is_downloaded, download_status, download_progress, file_size_bytes, is_favorite, downloaded_at, last_played_at)
                VALUES (
                    'Ambient/Weightless.mp3',
                    'Pending Mix',
                    'Weightless',
                    'Marconi Union',
                    485.0,
                    'http://stream.sangita.local/audio/Ambient/Weightless.mp3',
                    NULL,
                    0,
                    'IDLE',
                    0,
                    0,
                    0,
                    NULL,
                    NULL
                )
            """.trimIndent())

            // Insert playlists: Favorites (is_favorites=1), Pending Mix (is_virtual=1), Rock Classics, Symphonies
            stmt.execute("""
                INSERT INTO playlists (name, is_virtual, is_favorites, track_count, cover_track_path, created_at, last_synced_at)
                VALUES ('Favorites', 0, 1, 2, 'covers/fav.png', 1590000000000, 1595000000000)
            """.trimIndent())

            stmt.execute("""
                INSERT INTO playlists (name, is_virtual, is_favorites, track_count, cover_track_path, created_at, last_synced_at)
                VALUES ('Pending Mix', 1, 0, 1, NULL, 1592000000000, 0)
            """.trimIndent())

            stmt.execute("""
                INSERT INTO playlists (name, is_virtual, is_favorites, track_count, cover_track_path, created_at, last_synced_at)
                VALUES ('Rock Classics', 0, 0, 2, 'covers/rock.jpg', 1593000000000, 1597000000000)
            """.trimIndent())

            stmt.execute("""
                INSERT INTO playlists (name, is_virtual, is_favorites, track_count, cover_track_path, created_at, last_synced_at)
                VALUES ('Symphonies', 0, 0, 1, 'covers/symphony.jpg', 1594000000000, 1598000000000)
            """.trimIndent())

            // Insert playlist cross references with distinct ordering positions (0, 1)
            stmt.execute("""
                INSERT INTO playlist_track_cross_ref (playlist_name, track_id, position, added_at)
                VALUES ('Favorites', 'Classical/Beethoven_Symphony5.flac', 0, 1595000010000)
            """.trimIndent())
            stmt.execute("""
                INSERT INTO playlist_track_cross_ref (playlist_name, track_id, position, added_at)
                VALUES ('Favorites', 'Rock/StairwayToHeaven.mp3', 1, 1595000020000)
            """.trimIndent())

            stmt.execute("""
                INSERT INTO playlist_track_cross_ref (playlist_name, track_id, position, added_at)
                VALUES ('Rock Classics', 'Rock/StairwayToHeaven.mp3', 0, 1597000010000)
            """.trimIndent())
            stmt.execute("""
                INSERT INTO playlist_track_cross_ref (playlist_name, track_id, position, added_at)
                VALUES ('Rock Classics', 'Jazz/Miles_SoWhat.mp3', 1, 1597000020000)
            """.trimIndent())

            stmt.execute("""
                INSERT INTO playlist_track_cross_ref (playlist_name, track_id, position, added_at)
                VALUES ('Pending Mix', 'Ambient/Weightless.mp3', 0, 1592000010000)
            """.trimIndent())
            // Note: 'Symphonies' intentionally has no cross-ref in v1 to verify MIGRATION_1_2 auto-populates it

            // Insert favorites records
            stmt.execute("INSERT INTO favorites (track_id, added_at) VALUES ('Classical/Beethoven_Symphony5.flac', 1595000010000)")
            stmt.execute("INSERT INTO favorites (track_id, added_at) VALUES ('Rock/StairwayToHeaven.mp3', 1595000020000)")

            // Insert pre-existing sync queue items in FIFO creation order
            stmt.execute("""
                INSERT INTO sync_queue (action_type, payload_json, created_at, retry_count, last_error)
                VALUES ('FAVORITE_TRACK', '{"trackId":"Classical/Beethoven_Symphony5.flac"}', 1600001000000, 0, NULL)
            """.trimIndent())
            stmt.execute("""
                INSERT INTO sync_queue (action_type, payload_json, created_at, retry_count, last_error)
                VALUES ('CREATE_PLAYLIST', '{"title":"Late Night Jazz"}', 1600002000000, 2, 'Connection refused')
            """.trimIndent())
            stmt.execute("""
                INSERT INTO sync_queue (action_type, payload_json, created_at, retry_count, last_error)
                VALUES ('ADD_TRACK_TO_PLAYLIST', '{"playlistId":"pl-42","trackId":"Rock/StairwayToHeaven.mp3"}', 1600003000000, 1, 'Socket timeout')
            """.trimIndent())

            // Insert playback events (one synced, one pending offline playback report)
            stmt.execute("""
                INSERT INTO playback_events (track_id, playlist, duration_sec, device_id, device_name, device_type, browser, os, timestamp, synced)
                VALUES ('Rock/StairwayToHeaven.mp3', 'Rock Classics', 482, 'pixel-7-device', 'Pixel 7', 'mobile', 'none', 'Android 14', '2026-10-15T12:00:00Z', 1)
            """.trimIndent())
            stmt.execute("""
                INSERT INTO playback_events (track_id, playlist, duration_sec, device_id, device_name, device_type, browser, os, timestamp, synced)
                VALUES ('Classical/Beethoven_Symphony5.flac', 'Favorites', 240, 'pixel-7-device', 'Pixel 7', 'mobile', 'none', 'Android 14', '2026-10-16T14:30:00Z', 0)
            """.trimIndent())

            // Insert lyrics cache
            stmt.execute("""
                INSERT INTO lyrics_cache (track_id, raw_lrc, has_lyrics, cached_at)
                VALUES ('Rock/StairwayToHeaven.mp3', '[00:15.00]There is a lady who is sure...', 1, 1600005000000)
            """.trimIndent())
        }
    }

    @Test
    fun `step-by-step schema upgrade from v1 through v5 preserves downloads queue ordering and account isolation`() {
        // 1. Initialize v1 schema and populated fixtures
        createV1Schema()
        populateV1RepresentativeFixtures()

        // -------------------------------------------------------------
        // STEP 1 -> 2: MIGRATION_1_2
        // - Adds album column to tracks
        // - Backfills playlist_track_cross_ref from tracks where playlist_name != 'Favorites'
        // -------------------------------------------------------------
        SangitaDatabase.MIGRATION_1_2.migrate(supportDb)

        // Verify MIGRATION_1_2 schema changes
        connection.createStatement().use { stmt ->
            // Check album column exists on tracks and defaults to empty string
            stmt.executeQuery("SELECT id, album FROM tracks").use { rs ->
                var count = 0
                while (rs.next()) {
                    count++
                    assertEquals("", rs.getString("album"))
                }
                assertEquals(4, count)
            }

            // Check Symphonies track was backfilled into playlist_track_cross_ref
            stmt.executeQuery("SELECT track_id, position FROM playlist_track_cross_ref WHERE playlist_name = 'Symphonies'").use { rs ->
                assertTrue("Symphonies track must be backfilled by MIGRATION_1_2", rs.next())
                assertEquals("Classical/Beethoven_Symphony5.flac", rs.getString("track_id"))
                assertEquals(0, rs.getInt("position"))
            }

            // Check existing cross-refs were preserved without duplicate corruption
            stmt.executeQuery("SELECT COUNT(*) FROM playlist_track_cross_ref WHERE playlist_name = 'Rock Classics'").use { rs ->
                assertTrue(rs.next())
                assertEquals(2, rs.getInt(1))
            }
        }

        // -------------------------------------------------------------
        // STEP 2 -> 3: MIGRATION_2_3
        // - Creates playlists_v3 with local_key UUIDs, account_key, and kind mapping
        // - Migrates cross_ref to join on playlist_local_key
        // - Renames tables and sets up indices
        // -------------------------------------------------------------
        SangitaDatabase.MIGRATION_2_3.migrate(supportDb)

        // Map titles to new local keys
        val playlistLocalKeys = mutableMapOf<String, String>()
        val playlistKinds = mutableMapOf<String, String>()

        connection.createStatement().use { stmt ->
            // Verify intermediate tables were cleaned up
            stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('playlists_v3', 'playlist_track_cross_ref_v3')"
            ).use { rs ->
                assertFalse("Temporary migration tables must be dropped", rs.next())
            }

            // Read playlists table after MIGRATION_2_3
            stmt.executeQuery("""
                SELECT local_key, server_id, account_key, title, kind,
                       owner, visibility, description, track_count, cover_track_path,
                       unavailable_count, created_at, last_synced_at, sync_state
                FROM playlists
            """.trimIndent()).use { rs ->
                var playlistCount = 0
                while (rs.next()) {
                    playlistCount++
                    val title = rs.getString("title")
                    val key = rs.getString("local_key")
                    val kind = rs.getString("kind")
                    playlistLocalKeys[title] = key
                    playlistKinds[title] = kind

                    assertTrue("local_key must start with legacy_", key.startsWith("legacy_"))
                    assertEquals("default_account", rs.getString("account_key"))
                    assertNull("server_id must be null for pre-migration playlists", rs.getString("server_id"))
                    assertEquals("private", rs.getString("visibility"))
                    assertEquals(0, rs.getInt("unavailable_count"))
                    assertEquals("SYNCED", rs.getString("sync_state"))
                }
                assertEquals(4, playlistCount)
            }

            // Verify kind mappings
            assertEquals("FAVORITES", playlistKinds["Favorites"])
            assertEquals("LEGACY_PENDING", playlistKinds["Pending Mix"])
            assertEquals("FOLDER", playlistKinds["Rock Classics"])
            assertEquals("FOLDER", playlistKinds["Symphonies"])

            // Verify playlist_track_cross_ref joined properly to local_key
            val favKey = playlistLocalKeys["Favorites"]!!
            val rockKey = playlistLocalKeys["Rock Classics"]!!
            val pendingKey = playlistLocalKeys["Pending Mix"]!!
            val symphKey = playlistLocalKeys["Symphonies"]!!

            // Verify Rock Classics ordering: position 0 -> Stairway, position 1 -> So What
            stmt.executeQuery("""
                SELECT track_id, position, is_available, added_at
                FROM playlist_track_cross_ref
                WHERE playlist_local_key = '$rockKey'
                ORDER BY position ASC
            """.trimIndent()).use { rs ->
                assertTrue(rs.next())
                assertEquals("Rock/StairwayToHeaven.mp3", rs.getString("track_id"))
                assertEquals(0, rs.getInt("position"))
                assertEquals(1, rs.getInt("is_available"))
                assertEquals(1597000010000L, rs.getLong("added_at"))

                assertTrue(rs.next())
                assertEquals("Jazz/Miles_SoWhat.mp3", rs.getString("track_id"))
                assertEquals(1, rs.getInt("position"))
                assertEquals(1, rs.getInt("is_available"))
                assertEquals(1597000020000L, rs.getLong("added_at"))

                assertFalse(rs.next())
            }

            // Verify Favorites ordering: position 0 -> Beethoven, position 1 -> Stairway
            stmt.executeQuery("""
                SELECT track_id, position, is_available, added_at
                FROM playlist_track_cross_ref
                WHERE playlist_local_key = '$favKey'
                ORDER BY position ASC
            """.trimIndent()).use { rs ->
                assertTrue(rs.next())
                assertEquals("Classical/Beethoven_Symphony5.flac", rs.getString("track_id"))
                assertEquals(0, rs.getInt("position"))

                assertTrue(rs.next())
                assertEquals("Rock/StairwayToHeaven.mp3", rs.getString("track_id"))
                assertEquals(1, rs.getInt("position"))

                assertFalse(rs.next())
            }

            // Verify Pending Mix
            stmt.executeQuery("SELECT track_id, position FROM playlist_track_cross_ref WHERE playlist_local_key = '$pendingKey'").use { rs ->
                assertTrue(rs.next())
                assertEquals("Ambient/Weightless.mp3", rs.getString("track_id"))
                assertEquals(0, rs.getInt("position"))
                assertFalse(rs.next())
            }

            // Verify Symphonies
            stmt.executeQuery("SELECT track_id, position FROM playlist_track_cross_ref WHERE playlist_local_key = '$symphKey'").use { rs ->
                assertTrue(rs.next())
                assertEquals("Classical/Beethoven_Symphony5.flac", rs.getString("track_id"))
                assertEquals(0, rs.getInt("position"))
                assertFalse(rs.next())
            }

            // Verify indices on playlists and playlist_track_cross_ref
            stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index'").use { rs ->
                val indices = mutableSetOf<String>()
                while (rs.next()) {
                    indices.add(rs.getString("name"))
                }
                assertTrue(indices.contains("index_playlists_account_key"))
                assertTrue(indices.contains("index_playlists_server_id"))
                assertTrue(indices.contains("index_playlists_account_key_server_id"))
                assertTrue(indices.contains("index_playlist_track_cross_ref_playlist_local_key"))
                assertTrue(indices.contains("index_playlist_track_cross_ref_track_id"))
            }
        }

        // -------------------------------------------------------------
        // STEP 3 -> 4: MIGRATION_3_4
        // - Adds account_key column to sync_queue
        // - Creates index on sync_queue(account_key)
        // -------------------------------------------------------------
        SangitaDatabase.MIGRATION_3_4.migrate(supportDb)

        connection.createStatement().use { stmt ->
            // Verify existing sync_queue items have account_key defaulted to default_account
            stmt.executeQuery("SELECT id, account_key, action_type, retry_count FROM sync_queue ORDER BY id ASC").use { rs ->
                assertTrue(rs.next())
                assertEquals(1L, rs.getLong("id"))
                assertEquals("default_account", rs.getString("account_key"))
                assertEquals("FAVORITE_TRACK", rs.getString("action_type"))
                assertEquals(0, rs.getInt("retry_count"))

                assertTrue(rs.next())
                assertEquals(2L, rs.getLong("id"))
                assertEquals("default_account", rs.getString("account_key"))
                assertEquals("CREATE_PLAYLIST", rs.getString("action_type"))
                assertEquals(2, rs.getInt("retry_count"))

                assertTrue(rs.next())
                assertEquals(3L, rs.getLong("id"))
                assertEquals("default_account", rs.getString("account_key"))
                assertEquals("ADD_TRACK_TO_PLAYLIST", rs.getString("action_type"))
                assertEquals(1, rs.getInt("retry_count"))

                assertFalse(rs.next())
            }

            // Verify index on sync_queue(account_key) exists
            stmt.executeQuery("PRAGMA index_list('sync_queue')").use { rs ->
                val queueIndices = mutableListOf<String>()
                while (rs.next()) {
                    queueIndices.add(rs.getString("name"))
                }
                assertTrue(queueIndices.contains("index_sync_queue_account_key"))
            }
        }

        // -------------------------------------------------------------
        // STEP 4 -> 5: MIGRATION_4_5
        // - Adds is_failed (default 0), failure_reason, failed_at to sync_queue
        // -------------------------------------------------------------
        SangitaDatabase.MIGRATION_4_5.migrate(supportDb)

        // -------------------------------------------------------------
        // COMPREHENSIVE FINAL ASSERTIONS ON v5 DATABASE
        // -------------------------------------------------------------

        // 1. Downloaded audio files and local track paths are completely preserved
        connection.createStatement().use { stmt ->
            data class VerifiedTrack(
                val id: String,
                val title: String,
                val artist: String,
                val durationSeconds: Double,
                val localFilePath: String?,
                val isDownloaded: Boolean,
                val downloadStatus: String,
                val downloadProgress: Int,
                val fileSizeBytes: Long,
                val isFavorite: Boolean,
                val downloadedAt: Long?,
                val lastPlayedAt: Long?,
                val album: String
            )

            val tracks = mutableMapOf<String, VerifiedTrack>()
            stmt.executeQuery("""
                SELECT id, title, artist, duration_seconds, local_file_path, is_downloaded,
                       download_status, download_progress, file_size_bytes, is_favorite,
                       downloaded_at, last_played_at, album
                FROM tracks
            """.trimIndent()).use { rs ->
                while (rs.next()) {
                    val id = rs.getString("id")
                    tracks[id] = VerifiedTrack(
                        id = id,
                        title = rs.getString("title"),
                        artist = rs.getString("artist"),
                        durationSeconds = rs.getDouble("duration_seconds"),
                        localFilePath = rs.getString("local_file_path"),
                        isDownloaded = rs.getInt("is_downloaded") == 1,
                        downloadStatus = rs.getString("download_status"),
                        downloadProgress = rs.getInt("download_progress"),
                        fileSizeBytes = rs.getLong("file_size_bytes"),
                        isFavorite = rs.getInt("is_favorite") == 1,
                        downloadedAt = rs.getLong("downloaded_at").takeIf { !rs.wasNull() },
                        lastPlayedAt = rs.getLong("last_played_at").takeIf { !rs.wasNull() },
                        album = rs.getString("album")
                    )
                }
            }

            assertEquals(4, tracks.size)

            // Beethoven lossless FLAC download check
            val beethoven = tracks["Classical/Beethoven_Symphony5.flac"]
            assertNotNull("Beethoven track must exist", beethoven)
            assertEquals("/data/user/0/com.sangita.app/files/downloads/Classical_Beethoven_Symphony5.flac", beethoven!!.localFilePath)
            assertTrue("Downloaded flag must remain true", beethoven.isDownloaded)
            assertEquals("COMPLETED", beethoven.downloadStatus)
            assertEquals(100, beethoven.downloadProgress)
            assertEquals(52428800L, beethoven.fileSizeBytes)
            assertTrue("Favorite status must be preserved", beethoven.isFavorite)
            assertEquals(1600000000000L, beethoven.downloadedAt)
            assertEquals(1600050000000L, beethoven.lastPlayedAt)
            assertEquals("", beethoven.album)

            // Led Zeppelin MP3 download check
            val stairway = tracks["Rock/StairwayToHeaven.mp3"]
            assertNotNull(stairway)
            assertEquals("/data/user/0/com.sangita.app/files/downloads/Rock_StairwayToHeaven.mp3", stairway!!.localFilePath)
            assertTrue(stairway.isDownloaded)
            assertEquals("COMPLETED", stairway.downloadStatus)
            assertEquals(100, stairway.downloadProgress)
            assertEquals(11500000L, stairway.fileSizeBytes)
            assertTrue(stairway.isFavorite)
            assertEquals(1600200000000L, stairway.downloadedAt)

            // Miles Davis MP3 download check
            val soWhat = tracks["Jazz/Miles_SoWhat.mp3"]
            assertNotNull(soWhat)
            assertEquals("/data/user/0/com.sangita.app/files/downloads/Jazz_Miles_SoWhat.mp3", soWhat!!.localFilePath)
            assertTrue(soWhat.isDownloaded)
            assertEquals("COMPLETED", soWhat.downloadStatus)
            assertEquals(100, soWhat.downloadProgress)
            assertEquals(13500000L, soWhat.fileSizeBytes)
            assertFalse(soWhat.isFavorite)

            // Streaming track (not downloaded) check
            val weightless = tracks["Ambient/Weightless.mp3"]
            assertNotNull(weightless)
            assertNull("Stream track must not have localFilePath", weightless!!.localFilePath)
            assertFalse(weightless.isDownloaded)
            assertEquals("IDLE", weightless.downloadStatus)
            assertEquals(0, weightless.downloadProgress)
            assertEquals(0L, weightless.fileSizeBytes)
            assertNull(weightless.downloadedAt)
        }

        // 2. Playlist membership and track order positions are intact
        connection.createStatement().use { stmt ->
            val rockKey = playlistLocalKeys["Rock Classics"]!!
            stmt.executeQuery("""
                SELECT c.position, c.track_id, t.title, t.local_file_path, t.is_downloaded
                FROM playlist_track_cross_ref c
                INNER JOIN tracks t ON t.id = c.track_id
                WHERE c.playlist_local_key = '$rockKey'
                ORDER BY c.position ASC
            """.trimIndent()).use { rs ->
                // First in order: Stairway
                assertTrue(rs.next())
                assertEquals(0, rs.getInt("position"))
                assertEquals("Rock/StairwayToHeaven.mp3", rs.getString("track_id"))
                assertEquals("Stairway to Heaven", rs.getString("title"))
                assertEquals("/data/user/0/com.sangita.app/files/downloads/Rock_StairwayToHeaven.mp3", rs.getString("local_file_path"))
                assertEquals(1, rs.getInt("is_downloaded"))

                // Second in order: So What
                assertTrue(rs.next())
                assertEquals(1, rs.getInt("position"))
                assertEquals("Jazz/Miles_SoWhat.mp3", rs.getString("track_id"))
                assertEquals("So What", rs.getString("title"))
                assertEquals("/data/user/0/com.sangita.app/files/downloads/Jazz_Miles_SoWhat.mp3", rs.getString("local_file_path"))
                assertEquals(1, rs.getInt("is_downloaded"))

                assertFalse(rs.next())
            }
        }

        // 3. Account scoping (`account_key`) defaults safely to `default_account` without data loss
        connection.createStatement().use { stmt ->
            // All 4 migrated playlists have account_key = 'default_account'
            stmt.executeQuery("SELECT COUNT(*) FROM playlists WHERE account_key = 'default_account'").use { rs ->
                assertTrue(rs.next())
                assertEquals(4, rs.getInt(1))
            }

            // All 3 migrated sync_queue items have account_key = 'default_account'
            stmt.executeQuery("SELECT COUNT(*) FROM sync_queue WHERE account_key = 'default_account'").use { rs ->
                assertTrue(rs.next())
                assertEquals(3, rs.getInt(1))
            }
        }

        // 4. Failure tracking columns (`is_failed`, `failure_reason`, `failed_at`) in v5 are available and defaulted
        connection.createStatement().use { stmt ->
            stmt.executeQuery("""
                SELECT id, account_key, action_type, payload_json, retry_count, last_error,
                       is_failed, failure_reason, failed_at
                FROM sync_queue
                ORDER BY id ASC
            """.trimIndent()).use { rs ->
                // Item 1
                assertTrue(rs.next())
                assertEquals(1L, rs.getLong("id"))
                assertEquals("default_account", rs.getString("account_key"))
                assertEquals("FAVORITE_TRACK", rs.getString("action_type"))
                assertEquals(0, rs.getInt("is_failed"))
                assertNull(rs.getString("failure_reason"))
                assertNull(rs.getString("failed_at"))

                // Item 2
                assertTrue(rs.next())
                assertEquals(2L, rs.getLong("id"))
                assertEquals("default_account", rs.getString("account_key"))
                assertEquals("CREATE_PLAYLIST", rs.getString("action_type"))
                assertEquals("Connection refused", rs.getString("last_error"))
                assertEquals(0, rs.getInt("is_failed"))
                assertNull(rs.getString("failure_reason"))
                assertNull(rs.getString("failed_at"))

                // Item 3
                assertTrue(rs.next())
                assertEquals(3L, rs.getLong("id"))
                assertEquals("default_account", rs.getString("account_key"))
                assertEquals("ADD_TRACK_TO_PLAYLIST", rs.getString("action_type"))
                assertEquals("Socket timeout", rs.getString("last_error"))
                assertEquals(0, rs.getInt("is_failed"))
                assertNull(rs.getString("failure_reason"))
                assertNull(rs.getString("failed_at"))

                assertFalse(rs.next())
            }

            // Verify inserting a v5 failure-tracked item
            stmt.execute("""
                INSERT INTO sync_queue (account_key, action_type, payload_json, created_at, retry_count, last_error, is_failed, failure_reason, failed_at)
                VALUES ('user_alice', 'DELETE_PLAYLIST', '{"playlist_id":"pl-dead"}', 1600004000000, 5, 'HTTP 404', 1, 'Resource not found', 1600004500000)
            """.trimIndent())

            stmt.executeQuery("SELECT is_failed, failure_reason, failed_at FROM sync_queue WHERE id = 4").use { rs ->
                assertTrue(rs.next())
                assertEquals(1, rs.getInt("is_failed"))
                assertEquals("Resource not found", rs.getString("failure_reason"))
                assertEquals(1600004500000L, rs.getLong("failed_at"))
            }

            // Verify inserting a normal item on v5 uses default failure values
            stmt.execute("""
                INSERT INTO sync_queue (account_key, action_type, payload_json, created_at, retry_count)
                VALUES ('user_alice', 'UPDATE_PLAYLIST', '{"title":"Alice Vibes"}', 1600005000000, 0)
            """.trimIndent())

            stmt.executeQuery("SELECT is_failed, failure_reason, failed_at FROM sync_queue WHERE id = 5").use { rs ->
                assertTrue(rs.next())
                assertEquals(0, rs.getInt("is_failed"))
                assertNull(rs.getString("failure_reason"))
                assertNull(rs.getString("failed_at"))
            }
        }

        // 5. Verify favorites, playback_events, and lyrics_cache remain completely intact
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM favorites").use { rs ->
                assertTrue(rs.next())
                assertEquals(2, rs.getInt(1))
            }

            stmt.executeQuery("SELECT COUNT(*) FROM playback_events").use { rs ->
                assertTrue(rs.next())
                assertEquals(2, rs.getInt(1))
            }

            stmt.executeQuery("SELECT track_id, raw_lrc, has_lyrics FROM lyrics_cache WHERE track_id = 'Rock/StairwayToHeaven.mp3'").use { rs ->
                assertTrue(rs.next())
                assertEquals(1, rs.getInt("has_lyrics"))
                assertTrue(rs.getString("raw_lrc").contains("There is a lady who is sure"))
            }
        }
    }

    @Test
    fun `destructive migration is never used as recovery shortcut - data integrity is preserved across schema changes`() {
        createV1Schema()
        populateV1RepresentativeFixtures()

        // Count all rows before migration
        var v1TracksCount = 0
        var v1PlaylistsCount = 0
        var v1CrossRefsCount = 0
        var v1SyncQueueCount = 0
        var v1FavoritesCount = 0
        var v1EventsCount = 0
        var v1LyricsCount = 0

        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM tracks").use { if (it.next()) v1TracksCount = it.getInt(1) }
            stmt.executeQuery("SELECT COUNT(*) FROM playlists").use { if (it.next()) v1PlaylistsCount = it.getInt(1) }
            stmt.executeQuery("SELECT COUNT(*) FROM playlist_track_cross_ref").use { if (it.next()) v1CrossRefsCount = it.getInt(1) }
            stmt.executeQuery("SELECT COUNT(*) FROM sync_queue").use { if (it.next()) v1SyncQueueCount = it.getInt(1) }
            stmt.executeQuery("SELECT COUNT(*) FROM favorites").use { if (it.next()) v1FavoritesCount = it.getInt(1) }
            stmt.executeQuery("SELECT COUNT(*) FROM playback_events").use { if (it.next()) v1EventsCount = it.getInt(1) }
            stmt.executeQuery("SELECT COUNT(*) FROM lyrics_cache").use { if (it.next()) v1LyricsCount = it.getInt(1) }
        }

        assertEquals(4, v1TracksCount)
        assertEquals(4, v1PlaylistsCount)
        assertEquals(5, v1CrossRefsCount)
        assertEquals(3, v1SyncQueueCount)
        assertEquals(2, v1FavoritesCount)
        assertEquals(2, v1EventsCount)
        assertEquals(1, v1LyricsCount)

        // Apply all migrations sequentially
        SangitaDatabase.MIGRATION_1_2.migrate(supportDb)
        SangitaDatabase.MIGRATION_2_3.migrate(supportDb)
        SangitaDatabase.MIGRATION_3_4.migrate(supportDb)
        SangitaDatabase.MIGRATION_4_5.migrate(supportDb)

        // Verify no rows were destroyed or lost
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM tracks").use {
                assertTrue(it.next())
                assertEquals("Tracks table must never lose rows during migration", v1TracksCount, it.getInt(1))
            }
            stmt.executeQuery("SELECT COUNT(*) FROM playlists").use {
                assertTrue(it.next())
                assertEquals("Playlists table must never lose rows during migration", v1PlaylistsCount, it.getInt(1))
            }
            stmt.executeQuery("SELECT COUNT(*) FROM playlist_track_cross_ref").use {
                assertTrue(it.next())
                // Cross refs increases by 1 because MIGRATION_1_2 backfills missing Symphonies cross ref
                assertEquals("Playlist cross refs must not lose entries", v1CrossRefsCount + 1, it.getInt(1))
            }
            stmt.executeQuery("SELECT COUNT(*) FROM sync_queue").use {
                assertTrue(it.next())
                assertEquals("Sync queue must never drop pending offline actions", v1SyncQueueCount, it.getInt(1))
            }
            stmt.executeQuery("SELECT COUNT(*) FROM favorites").use {
                assertTrue(it.next())
                assertEquals("Favorites must never be dropped", v1FavoritesCount, it.getInt(1))
            }
            stmt.executeQuery("SELECT COUNT(*) FROM playback_events").use {
                assertTrue(it.next())
                assertEquals("Playback events must never be lost", v1EventsCount, it.getInt(1))
            }
            stmt.executeQuery("SELECT COUNT(*) FROM lyrics_cache").use {
                assertTrue(it.next())
                assertEquals("Lyrics cache must never be lost", v1LyricsCount, it.getInt(1))
            }
        }
    }

    @Test
    fun `offline queue FIFO order and account isolation are preserved on upgraded v5 database`() {
        createV1Schema()
        populateV1RepresentativeFixtures()

        // Apply full upgrade to v5
        SangitaDatabase.MIGRATION_1_2.migrate(supportDb)
        SangitaDatabase.MIGRATION_2_3.migrate(supportDb)
        SangitaDatabase.MIGRATION_3_4.migrate(supportDb)
        SangitaDatabase.MIGRATION_4_5.migrate(supportDb)

        connection.createStatement().use { stmt ->
            // Enqueue additional actions for a distinct authenticated user
            stmt.execute("""
                INSERT INTO sync_queue (account_key, action_type, payload_json, created_at, retry_count)
                VALUES ('http://localhost:8080|bob', 'FAVORITE_TRACK', '{"trackId":"Jazz/Miles_SoWhat.mp3"}', 1600010000000, 0)
            """.trimIndent())

            stmt.execute("""
                INSERT INTO sync_queue (account_key, action_type, payload_json, created_at, retry_count)
                VALUES ('http://localhost:8080|bob', 'CREATE_PLAYLIST', '{"title":"Bobs Chill"}', 1600020000000, 0)
            """.trimIndent())

            // Assert default_account queue items remain in strict creation order
            stmt.executeQuery("""
                SELECT id, action_type, created_at
                FROM sync_queue
                WHERE account_key = 'default_account' AND is_failed = 0
                ORDER BY id ASC
            """.trimIndent()).use { rs ->
                assertTrue(rs.next())
                assertEquals("FAVORITE_TRACK", rs.getString("action_type"))
                assertEquals(1600001000000L, rs.getLong("created_at"))

                assertTrue(rs.next())
                assertEquals("CREATE_PLAYLIST", rs.getString("action_type"))
                assertEquals(1600002000000L, rs.getLong("created_at"))

                assertTrue(rs.next())
                assertEquals("ADD_TRACK_TO_PLAYLIST", rs.getString("action_type"))
                assertEquals(1600003000000L, rs.getLong("created_at"))

                assertFalse(rs.next())
            }

            // Assert Bob's queue items are completely isolated
            stmt.executeQuery("""
                SELECT id, action_type, created_at
                FROM sync_queue
                WHERE account_key = 'http://localhost:8080|bob'
                ORDER BY id ASC
            """.trimIndent()).use { rs ->
                assertTrue(rs.next())
                assertEquals("FAVORITE_TRACK", rs.getString("action_type"))
                assertEquals(1600010000000L, rs.getLong("created_at"))

                assertTrue(rs.next())
                assertEquals("CREATE_PLAYLIST", rs.getString("action_type"))
                assertEquals(1600020000000L, rs.getLong("created_at"))

                assertFalse(rs.next())
            }
        }
    }
}
