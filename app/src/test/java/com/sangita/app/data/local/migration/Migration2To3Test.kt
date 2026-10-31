package com.sangita.app.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import com.sangita.app.data.local.database.SangitaDatabase
import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.PlaylistKind
import com.sangita.app.domain.model.PlaylistSyncState
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

class Migration2To3Test {

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

    @Test
    fun `migration from v2 to v3 transforms schema and preserves playlists and memberships`() {
        // 1. Create Version 2 tables
        connection.createStatement().use { stmt ->
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

            stmt.execute("""
                CREATE TABLE playlist_track_cross_ref (
                    playlist_name TEXT NOT NULL,
                    track_id TEXT NOT NULL,
                    position INTEGER NOT NULL DEFAULT 0,
                    added_at INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(playlist_name, track_id)
                )
            """.trimIndent())

            // 2. Insert representative V2 data:
            // - Favorites playlist
            // - Virtual/legacy pending playlist
            // - Regular folder playlist
            stmt.execute("""
                INSERT INTO playlists (name, is_virtual, is_favorites, track_count, cover_track_path, created_at, last_synced_at)
                VALUES ('Favorites', 0, 1, 2, 'covers/fav.png', 1000, 2000)
            """.trimIndent())

            stmt.execute("""
                INSERT INTO playlists (name, is_virtual, is_favorites, track_count, cover_track_path, created_at, last_synced_at)
                VALUES ('Pending Mix', 1, 0, 1, NULL, 3000, 0)
            """.trimIndent())

            stmt.execute("""
                INSERT INTO playlists (name, is_virtual, is_favorites, track_count, cover_track_path, created_at, last_synced_at)
                VALUES ('Rock Classics', 0, 0, 3, 'covers/rock.png', 4000, 5000)
            """.trimIndent())

            // Insert cross references
            stmt.execute("INSERT INTO playlist_track_cross_ref VALUES ('Favorites', 'track_f1.mp3', 0, 1010)")
            stmt.execute("INSERT INTO playlist_track_cross_ref VALUES ('Favorites', 'track_f2.mp3', 1, 1020)")
            stmt.execute("INSERT INTO playlist_track_cross_ref VALUES ('Pending Mix', 'track_p1.mp3', 0, 3010)")
            stmt.execute("INSERT INTO playlist_track_cross_ref VALUES ('Rock Classics', 'track_r1.mp3', 0, 4010)")
            stmt.execute("INSERT INTO playlist_track_cross_ref VALUES ('Rock Classics', 'track_r2.mp3', 1, 4020)")
            stmt.execute("INSERT INTO playlist_track_cross_ref VALUES ('Rock Classics', 'track_r3.mp3', 2, 4030)")
        }

        // 3. Execute MIGRATION_2_3
        SangitaDatabase.MIGRATION_2_3.migrate(supportDb)

        // 4. Verify intermediate tables were cleaned up and final tables exist
        connection.createStatement().use { stmt ->
            val rsTables = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('playlists_v3', 'playlist_track_cross_ref_v3')"
            )
            assertFalse("Intermediate temporary tables should be dropped/renamed", rsTables.next())
        }

        // 5. Verify playlists table schema and migrated data
        data class MigratedPlaylist(
            val localKey: String,
            val serverId: String?,
            val accountKey: String,
            val title: String,
            val kind: String,
            val owner: String?,
            val visibility: String,
            val description: String?,
            val trackCount: Int,
            val coverTrackPath: String?,
            val unavailableCount: Int,
            val createdAt: Long,
            val lastSyncedAt: Long,
            val syncState: String
        )

        val playlists = mutableMapOf<String, MigratedPlaylist>()
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("""
                SELECT local_key, server_id, account_key, title, kind,
                       owner, visibility, description, track_count, cover_track_path,
                       unavailable_count, created_at, last_synced_at, sync_state
                FROM playlists
            """.trimIndent())

            while (rs.next()) {
                val pl = MigratedPlaylist(
                    localKey = rs.getString("local_key"),
                    serverId = rs.getString("server_id"),
                    accountKey = rs.getString("account_key"),
                    title = rs.getString("title"),
                    kind = rs.getString("kind"),
                    owner = rs.getString("owner"),
                    visibility = rs.getString("visibility"),
                    description = rs.getString("description"),
                    trackCount = rs.getInt("track_count"),
                    coverTrackPath = rs.getString("cover_track_path"),
                    unavailableCount = rs.getInt("unavailable_count"),
                    createdAt = rs.getLong("created_at"),
                    lastSyncedAt = rs.getLong("last_synced_at"),
                    syncState = rs.getString("sync_state")
                )
                playlists[pl.title] = pl
            }
        }

        assertEquals(3, playlists.size)

        // Verify Favorites
        val fav = playlists["Favorites"]
        assertNotNull("Favorites playlist must exist", fav)
        assertTrue("localKey should start with legacy_", fav!!.localKey.startsWith("legacy_"))
        assertEquals("default_account", fav.accountKey)
        assertNull(fav.serverId)
        assertEquals("FAVORITES", fav.kind)
        assertNull(fav.owner)
        assertEquals("private", fav.visibility)
        assertNull(fav.description)
        assertEquals(2, fav.trackCount)
        assertEquals("covers/fav.png", fav.coverTrackPath)
        assertEquals(0, fav.unavailableCount)
        assertEquals(1000L, fav.createdAt)
        assertEquals(2000L, fav.lastSyncedAt)
        assertEquals("SYNCED", fav.syncState)

        // Verify Pending Mix (is_virtual = 1 -> LEGACY_PENDING)
        val pending = playlists["Pending Mix"]
        assertNotNull("Pending Mix playlist must exist", pending)
        assertTrue(pending!!.localKey.startsWith("legacy_"))
        assertEquals("LEGACY_PENDING", pending.kind)
        assertEquals(1, pending.trackCount)
        assertNull(pending.coverTrackPath)
        assertEquals(3000L, pending.createdAt)
        assertEquals(0L, pending.lastSyncedAt)

        // Verify Rock Classics (regular -> FOLDER)
        val rock = playlists["Rock Classics"]
        assertNotNull("Rock Classics playlist must exist", rock)
        assertTrue(rock!!.localKey.startsWith("legacy_"))
        assertEquals("FOLDER", rock.kind)
        assertEquals(3, rock.trackCount)
        assertEquals("covers/rock.png", rock.coverTrackPath)
        assertEquals(4000L, rock.createdAt)
        assertEquals(5000L, rock.lastSyncedAt)

        // All localKeys must be distinct UUIDs
        val allKeys = setOf(fav.localKey, pending.localKey, rock.localKey)
        assertEquals(3, allKeys.size)

        // 6. Verify cross references migration and relationship preservation
        data class MigratedCrossRef(
            val playlistLocalKey: String,
            val trackId: String,
            val position: Int,
            val isAvailable: Boolean,
            val addedAt: Long
        )

        val crossRefs = mutableListOf<MigratedCrossRef>()
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("""
                SELECT playlist_local_key, track_id, position, is_available, added_at
                FROM playlist_track_cross_ref
                ORDER BY playlist_local_key ASC, position ASC
            """.trimIndent())

            while (rs.next()) {
                crossRefs.add(
                    MigratedCrossRef(
                        playlistLocalKey = rs.getString("playlist_local_key"),
                        trackId = rs.getString("track_id"),
                        position = rs.getInt("position"),
                        isAvailable = rs.getInt("is_available") == 1,
                        addedAt = rs.getLong("added_at")
                    )
                )
            }
        }

        assertEquals(6, crossRefs.size)

        // Verify Rock Classics memberships linked correctly to the new Rock Classics localKey
        val rockRefs = crossRefs.filter { it.playlistLocalKey == rock.localKey }
        assertEquals(3, rockRefs.size)
        assertEquals(listOf("track_r1.mp3", "track_r2.mp3", "track_r3.mp3"), rockRefs.map { it.trackId })
        assertEquals(listOf(0, 1, 2), rockRefs.map { it.position })
        assertEquals(listOf(4010L, 4020L, 4030L), rockRefs.map { it.addedAt })
        assertTrue(rockRefs.all { it.isAvailable })

        // Verify Favorites memberships
        val favRefs = crossRefs.filter { it.playlistLocalKey == fav.localKey }
        assertEquals(2, favRefs.size)
        assertEquals(listOf("track_f1.mp3", "track_f2.mp3"), favRefs.map { it.trackId })
        assertEquals(listOf(0, 1), favRefs.map { it.position })
        assertEquals(listOf(1010L, 1020L), favRefs.map { it.addedAt })

        // Verify Pending Mix memberships
        val pendingRefs = crossRefs.filter { it.playlistLocalKey == pending.localKey }
        assertEquals(1, pendingRefs.size)
        assertEquals("track_p1.mp3", pendingRefs[0].trackId)
        assertEquals(0, pendingRefs[0].position)
        assertEquals(3010L, pendingRefs[0].addedAt)

        // 7. Verify indices exist on migrated tables
        val indexNames = mutableSetOf<String>()
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index'")
            while (rs.next()) {
                indexNames.add(rs.getString("name"))
            }
        }

        assertTrue("index_playlists_account_key must exist", indexNames.contains("index_playlists_account_key"))
        assertTrue("index_playlists_server_id must exist", indexNames.contains("index_playlists_server_id"))
        assertTrue("index_playlists_account_key_server_id must exist", indexNames.contains("index_playlists_account_key_server_id"))
        assertTrue(
            "index_playlist_track_cross_ref_playlist_local_key must exist",
            indexNames.contains("index_playlist_track_cross_ref_playlist_local_key")
        )
        assertTrue(
            "index_playlist_track_cross_ref_track_id must exist",
            indexNames.contains("index_playlist_track_cross_ref_track_id")
        )
    }

    @Test
    fun `domain model and entity mapping preserve all fields correctly`() {
        val domain = Playlist(
            id = "test-uuid-123",
            serverId = "srv-456",
            title = "My Synthwave Mix",
            kind = PlaylistKind.PERSONAL,
            owner = "user1",
            visibility = "public",
            description = "Best 80s synth vibes",
            trackCount = 42,
            coverTrackPath = "covers/synth.png",
            unavailableCount = 2,
            isAvailable = true,
            syncState = PlaylistSyncState.PENDING_UPDATE,
            createdAt = 123456789L,
            updatedAt = 987654321L
        )

        assertTrue(domain.isPersonal)
        assertTrue(domain.isPublic)
        assertFalse(domain.isFavorites)
        assertFalse(domain.isFolder)
        assertEquals("My Synthwave Mix", domain.name)

        val entity = PlaylistEntity.fromDomain(domain, accountKey = "acc-789")
        assertEquals("test-uuid-123", entity.localKey)
        assertEquals("srv-456", entity.serverId)
        assertEquals("acc-789", entity.accountKey)
        assertEquals("My Synthwave Mix", entity.title)
        assertEquals("PERSONAL", entity.kind)
        assertEquals("user1", entity.owner)
        assertEquals("public", entity.visibility)
        assertEquals("Best 80s synth vibes", entity.description)
        assertEquals(42, entity.trackCount)
        assertEquals("covers/synth.png", entity.coverTrackPath)
        assertEquals(2, entity.unavailableCount)
        assertEquals(123456789L, entity.createdAt)
        assertEquals(987654321L, entity.lastSyncedAt)
        assertEquals("PENDING_UPDATE", entity.syncState)

        val mappedBack = entity.toDomain()
        assertEquals(domain.id, mappedBack.id)
        assertEquals(domain.serverId, mappedBack.serverId)
        assertEquals(domain.title, mappedBack.title)
        assertEquals(domain.kind, mappedBack.kind)
        assertEquals(domain.owner, mappedBack.owner)
        assertEquals(domain.visibility, mappedBack.visibility)
        assertEquals(domain.description, mappedBack.description)
        assertEquals(domain.trackCount, mappedBack.trackCount)
        assertEquals(domain.coverTrackPath, mappedBack.coverTrackPath)
        assertEquals(domain.unavailableCount, mappedBack.unavailableCount)
        assertEquals(domain.syncState, mappedBack.syncState)
        assertEquals(domain.createdAt, mappedBack.createdAt)
        assertEquals(domain.updatedAt, mappedBack.updatedAt)
    }
}
