package com.sangita.app.data.local

import com.sangita.app.data.local.dao.TrackDao
import com.sangita.app.data.local.database.SangitaDatabase
import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.data.local.entity.PlaylistTrackCrossRef
import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.data.local.entity.TrackEntity
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.data.remote.dto.PlaylistDto
import com.sangita.app.data.repository.findUnambiguousDefaultAccountPlaylistsToMigrate
import com.sangita.app.data.repository.MusicRepositoryImpl
import com.sangita.app.domain.model.PlaylistKind
import com.sangita.app.domain.model.PlaylistSyncState
import com.sangita.app.domain.model.SyncActionType
import com.sangita.app.domain.repository.MusicRepository
import com.sangita.app.domain.repository.SyncRepository
import com.sangita.app.download.SangitaDownloadManager
import com.sangita.app.player.PlaybackController
import com.sangita.app.sync.DeviceSyncManager
import com.sangita.app.sync.NetworkConnectivityObserver
import com.sangita.app.ui.screens.library.LibraryViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

@OptIn(ExperimentalCoroutinesApi::class)
class AccountScopingTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private lateinit var connection: Connection

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { stmt ->
            // Create playlists table according to Room schema v3
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS playlists (
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
            """.trimIndent())
            stmt.execute("CREATE INDEX IF NOT EXISTS index_playlists_account_key ON playlists(account_key)")
            stmt.execute("CREATE INDEX IF NOT EXISTS index_playlists_server_id ON playlists(server_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS index_playlists_account_key_server_id ON playlists(account_key, server_id)")

            // Create sync_queue table according to Room schema v4
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sync_queue (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    account_key TEXT NOT NULL DEFAULT 'default_account',
                    action_type TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    last_error TEXT,
                    is_failed INTEGER NOT NULL DEFAULT 0,
                    failure_reason TEXT,
                    failed_at INTEGER
                )
            """.trimIndent())
            stmt.execute("CREATE INDEX IF NOT EXISTS index_sync_queue_account_key ON sync_queue(account_key)")

            // Create playlist_track_cross_ref table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS playlist_track_cross_ref (
                    playlist_local_key TEXT NOT NULL,
                    track_id TEXT NOT NULL,
                    position INTEGER NOT NULL DEFAULT 0,
                    is_available INTEGER NOT NULL DEFAULT 1,
                    added_at INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(playlist_local_key, track_id)
                )
            """.trimIndent())
            stmt.execute("CREATE INDEX IF NOT EXISTS index_playlist_track_cross_ref_playlist_local_key ON playlist_track_cross_ref(playlist_local_key)")
            stmt.execute("CREATE INDEX IF NOT EXISTS index_playlist_track_cross_ref_track_id ON playlist_track_cross_ref(track_id)")

            // Create tracks table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tracks (
                    id TEXT PRIMARY KEY NOT NULL,
                    playlist_name TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    duration_seconds REAL,
                    remote_url TEXT NOT NULL,
                    local_file_path TEXT,
                    is_downloaded INTEGER NOT NULL DEFAULT 0,
                    download_status TEXT NOT NULL DEFAULT 'IDLE',
                    download_progress INTEGER NOT NULL DEFAULT 0,
                    file_size_bytes INTEGER NOT NULL DEFAULT 0,
                    is_favorite INTEGER NOT NULL DEFAULT 0,
                    downloaded_at INTEGER,
                    last_played_at INTEGER,
                    album TEXT NOT NULL DEFAULT ''
                )
            """.trimIndent())

            // Create favorites table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS favorites (
                    track_id TEXT PRIMARY KEY NOT NULL,
                    added_at INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (!connection.isClosed) {
            connection.close()
        }
    }

    // =========================================================================
    // 1. UserPreferences: Account Key Derivation & Flow
    // =========================================================================

    @Test
    fun `different serverUrl and username pairs produce distinct accountKeys`() {
        val prefs1 = UserPreferences(null)
        prefs1.setServerUrl("https://server-a.sangita.com")
        prefs1.setUsername("alice")

        val prefs2 = UserPreferences(null)
        prefs2.setServerUrl("https://server-a.sangita.com")
        prefs2.setUsername("bob")

        val prefs3 = UserPreferences(null)
        prefs3.setServerUrl("https://server-b.sangita.com")
        prefs3.setUsername("alice")

        val key1 = prefs1.getAccountKey()
        val key2 = prefs2.getAccountKey()
        val key3 = prefs3.getAccountKey()

        assertEquals("https://server-a.sangita.com|alice", key1)
        assertEquals("https://server-a.sangita.com|bob", key2)
        assertEquals("https://server-b.sangita.com|alice", key3)

        assertNotEquals(key1, key2)
        assertNotEquals(key1, key3)
        assertNotEquals(key2, key3)
    }

    @Test
    fun `accountKey normalizes trailing slashes and username whitespace and casing`() {
        val prefs = UserPreferences(null)
        prefs.setServerUrl("https://music.example.com///")
        prefs.setUsername("  AliceUser  ")

        assertEquals("https://music.example.com|aliceuser", prefs.getAccountKey())
    }

    @Test
    fun `accountKey defaults to default_account when serverUrl or username is missing`() {
        val prefs = UserPreferences(null)
        assertEquals("default_account", prefs.getAccountKey())

        prefs.setServerUrl("https://music.example.com")
        // username is still null
        assertEquals("default_account", prefs.getAccountKey())

        prefs.setUsername("charlie")
        assertEquals("https://music.example.com|charlie", prefs.getAccountKey())

        // clearAuth removes username
        prefs.clearAuth()
        assertEquals("default_account", prefs.getAccountKey())
    }

    @Test
    fun `observeAccountKey emits updated keys on credential changes`() = runBlocking {
        val prefs = UserPreferences(null)
        val initialKey = prefs.observeAccountKey().first()
        assertEquals("default_account", initialKey)

        prefs.setServerUrl("https://cluster.sangita.net")
        prefs.setUsername("dan")
        val updatedKey = prefs.observeAccountKey().first()
        assertEquals("https://cluster.sangita.net|dan", updatedKey)

        prefs.clearAuth()
        val loggedOutKey = prefs.observeAccountKey().first()
        assertEquals("default_account", loggedOutKey)
    }

    // =========================================================================
    // 2. Room Database: Playlist Account Isolation
    // =========================================================================

    @Test
    fun `querying playlists for Account A does not return playlists created under Account B`() {
        val accountA = "https://server-one.com|alice"
        val accountB = "https://server-two.com|bob"

        // Insert playlists for Account A
        insertTestPlaylist(
            localKey = "loc_a1",
            serverId = "srv_a1",
            accountKey = accountA,
            title = "Alice Workout",
            kind = PlaylistKind.PERSONAL.name
        )
        insertTestPlaylist(
            localKey = "loc_a2",
            serverId = null,
            accountKey = accountA,
            title = "Favorites",
            kind = PlaylistKind.FAVORITES.name
        )

        // Insert playlists for Account B (including one with identical title "Alice Workout")
        insertTestPlaylist(
            localKey = "loc_b1",
            serverId = "srv_b1",
            accountKey = accountB,
            title = "Alice Workout", // Same title, different account!
            kind = PlaylistKind.PERSONAL.name
        )
        insertTestPlaylist(
            localKey = "loc_b2",
            serverId = "srv_b2",
            accountKey = accountB,
            title = "Bob Chill Vibes",
            kind = PlaylistKind.PERSONAL.name
        )

        // 1. Test getAllPlaylists partitioned by account_key
        val playlistsA = queryAllPlaylists(accountA)
        assertEquals(2, playlistsA.size)
        // Order: FAVORITES first, then alphabetical
        assertEquals("loc_a2", playlistsA[0].localKey)
        assertEquals("Favorites", playlistsA[0].title)
        assertEquals("loc_a1", playlistsA[1].localKey)
        assertEquals("Alice Workout", playlistsA[1].title)
        assertTrue(playlistsA.none { it.accountKey == accountB })
        assertTrue(playlistsA.none { it.localKey == "loc_b1" || it.localKey == "loc_b2" })

        val playlistsB = queryAllPlaylists(accountB)
        assertEquals(2, playlistsB.size)
        assertEquals("loc_b1", playlistsB[0].localKey)
        assertEquals("loc_b2", playlistsB[1].localKey)
        assertTrue(playlistsB.none { it.accountKey == accountA })
        assertTrue(playlistsB.none { it.localKey == "loc_a1" || it.localKey == "loc_a2" })

        // 2. Test getPlaylistByKey partitioned by account_key
        val aOwnKey = queryPlaylistByKey(accountA, "loc_a1")
        assertNotNull(aOwnKey)
        assertEquals("loc_a1", aOwnKey?.localKey)

        // Account A querying Account B's key -> MUST RETURN NULL
        val crossQueryKey = queryPlaylistByKey(accountA, "loc_b1")
        assertNull("Account A must never see Account B's playlist by localKey", crossQueryKey)

        val bCrossQueryKey = queryPlaylistByKey(accountB, "loc_a1")
        assertNull("Account B must never see Account A's playlist by localKey", bCrossQueryKey)

        // 3. Test getPlaylistByTitle partitioned by account_key
        // Both accounts have "Alice Workout"
        val titleMatchA = queryPlaylistByTitle(accountA, "Alice Workout")
        assertNotNull(titleMatchA)
        assertEquals("loc_a1", titleMatchA?.localKey)
        assertEquals(accountA, titleMatchA?.accountKey)

        val titleMatchB = queryPlaylistByTitle(accountB, "Alice Workout")
        assertNotNull(titleMatchB)
        assertEquals("loc_b1", titleMatchB?.localKey)
        assertEquals(accountB, titleMatchB?.accountKey)

        // 4. Test getPlaylistByServerId partitioned by account_key
        val serverMatchA = queryPlaylistByServerId(accountA, "srv_a1")
        assertNotNull(serverMatchA)
        assertEquals("loc_a1", serverMatchA?.localKey)

        val crossServerMatch = queryPlaylistByServerId(accountA, "srv_b1")
        assertNull("Account A must never see Account B's playlist by serverId", crossServerMatch)

        // 5. Test deletePlaylistByKey partitioned by account_key
        // Attempting to delete Account B's playlist while acting as Account A
        deletePlaylistByKey(accountA, "loc_b1")
        // Account B's playlist must STILL exist!
        val bKeyStillExists = queryPlaylistByKey(accountB, "loc_b1")
        assertNotNull("Deleting with wrong accountKey must not delete another account's playlist", bKeyStillExists)

        // Deleting with correct accountKey succeeds
        deletePlaylistByKey(accountA, "loc_a1")
        assertNull(queryPlaylistByKey(accountA, "loc_a1"))
    }

    // =========================================================================
    // 3. Logout & Cache Isolation in LibraryViewModel
    // =========================================================================

    @Test
    fun `logout clears memory cache and disconnects deviceSyncManager`() {
        val mockMusicRepo = mockk<MusicRepository>(relaxed = true)
        val mockSyncRepo = mockk<SyncRepository>(relaxed = true)
        val mockNetworkClient = mockk<com.sangita.app.data.remote.api.NetworkClient>(relaxed = true)
        val mockPlayback = mockk<PlaybackController>(relaxed = true)
        val mockDownload = mockk<SangitaDownloadManager>(relaxed = true)
        val mockDeviceSync = mockk<DeviceSyncManager>(relaxed = true)
        val mockConnectivity = mockk<NetworkConnectivityObserver>(relaxed = true)

        every { mockConnectivity.observe() } returns flowOf(true)
        every { mockConnectivity.isOnline } returns true
        every { mockSyncRepo.getSyncQueueSize() } returns flowOf(0)
        every { mockMusicRepo.observePlaylists() } returns flowOf(emptyList())
        every { mockMusicRepo.observeDownloadedTracks() } returns flowOf(emptyList())
        every { mockMusicRepo.observeAllTracks() } returns flowOf(emptyList())

        val userPrefs = UserPreferences(null)
        userPrefs.setServerUrl("https://sangita.test")
        userPrefs.setUsername("tester")
        userPrefs.setToken("valid_token_xyz")

        assertEquals("https://sangita.test|tester", userPrefs.getAccountKey())

        val viewModel = LibraryViewModel(
            musicRepository = mockMusicRepo,
            syncRepository = mockSyncRepo,
            networkClient = mockNetworkClient,
            userPreferences = userPrefs,
            playbackController = mockPlayback,
            downloadManager = mockDownload,
            deviceSyncManager = mockDeviceSync,
            connectivityObserver = mockConnectivity
        )

        var loggedOutCalled = false
        viewModel.logout {
            loggedOutCalled = true
        }

        assertTrue("onSuccess callback must be called", loggedOutCalled)
        verify(exactly = 1) { mockMusicRepo.clearMemoryCache() }
        verify(exactly = 1) { mockDeviceSync.disconnect() }
        verify(exactly = 1) { mockDeviceSync.resetSyncState() }

        // Credentials cleared
        assertNull(userPrefs.getToken())
        assertNull(userPrefs.getUsername())
        assertEquals("default_account", userPrefs.getAccountKey())
    }

    // =========================================================================
    // Helpers executing Room queries directly on SQLite engine
    // =========================================================================

    private fun insertTestPlaylist(
        localKey: String,
        serverId: String?,
        accountKey: String,
        title: String,
        kind: String,
        owner: String? = null
    ) {
        val sql = """
            INSERT OR REPLACE INTO playlists (
                local_key, server_id, account_key, title, kind,
                owner, visibility, description, track_count, cover_track_path,
                unavailable_count, created_at, last_synced_at, sync_state
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, localKey)
            stmt.setString(2, serverId)
            stmt.setString(3, accountKey)
            stmt.setString(4, title)
            stmt.setString(5, kind)
            stmt.setString(6, owner)
            stmt.setString(7, "private")
            stmt.setString(8, null)
            stmt.setInt(9, 0)
            stmt.setString(10, null)
            stmt.setInt(11, 0)
            stmt.setLong(12, System.currentTimeMillis())
            stmt.setLong(13, 0L)
            stmt.setString(14, PlaylistSyncState.SYNCED.name)
            stmt.executeUpdate()
        }
    }

    private fun queryAllPlaylists(accountKey: String): List<PlaylistEntity> {
        val sql = "SELECT * FROM playlists WHERE account_key = ? ORDER BY CASE WHEN kind = 'FAVORITES' THEN 0 ELSE 1 END, title ASC"
        val results = mutableListOf<PlaylistEntity>()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountKey)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(
                        PlaylistEntity(
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
                    )
                }
            }
        }
        return results
    }

    private fun queryPlaylistByKey(accountKey: String, localKey: String): PlaylistEntity? {
        val sql = "SELECT * FROM playlists WHERE account_key = ? AND local_key = ? LIMIT 1"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountKey)
            stmt.setString(2, localKey)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) {
                    PlaylistEntity(
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
                } else null
            }
        }
    }

    private fun queryPlaylistByTitle(accountKey: String, title: String): PlaylistEntity? {
        val sql = "SELECT * FROM playlists WHERE account_key = ? AND title = ? LIMIT 1"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountKey)
            stmt.setString(2, title)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) {
                    PlaylistEntity(
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
                } else null
            }
        }
    }

    private fun queryPlaylistByServerId(accountKey: String, serverId: String): PlaylistEntity? {
        val sql = "SELECT * FROM playlists WHERE account_key = ? AND server_id = ? LIMIT 1"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountKey)
            stmt.setString(2, serverId)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) {
                    PlaylistEntity(
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
                } else null
            }
        }
    }

    private fun deletePlaylistByKey(accountKey: String, localKey: String) {
        val sql = "DELETE FROM playlists WHERE account_key = ? AND local_key = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountKey)
            stmt.setString(2, localKey)
            stmt.executeUpdate()
        }
    }

    // =========================================================================
    // 4. Default Account Migration & Integrity
    // =========================================================================

    @Test
    fun `unambiguous default_account migration to active accountKey preserves localKey track memberships and download links`() {
        val activeAccountKey = "https://sangita.live|alice"

        // Setup offline personal playlist created before login under default_account
        val offlineGymKey = "loc_gym_offline"
        insertTestPlaylist(
            localKey = offlineGymKey,
            serverId = null,
            accountKey = "default_account",
            title = "Gym Mix",
            kind = PlaylistKind.PERSONAL.name,
            owner = "alice"
        )

        // Add downloaded tracks and cross refs to this offline playlist
        insertTestTrack(
            id = "track_gym_1",
            playlistName = "Gym Mix",
            title = "Power Workout",
            isDownloaded = true,
            localPath = "/data/music/gym1.mp3"
        )
        insertTestTrack(
            id = "track_gym_2",
            playlistName = "Gym Mix",
            title = "Fast Pace",
            isDownloaded = true,
            localPath = "/data/music/gym2.mp3"
        )
        insertTestCrossRef(playlistLocalKey = offlineGymKey, trackId = "track_gym_1", position = 0)
        insertTestCrossRef(playlistLocalKey = offlineGymKey, trackId = "track_gym_2", position = 1)

        // Enqueue pending sync action for this playlist under default_account
        insertTestSyncAction(
            accountKey = "default_account",
            actionType = SyncActionType.CREATE_PLAYLIST.name,
            payloadJson = """{"localKey":"$offlineGymKey","title":"Gym Mix"}"""
        )

        // Active account already has its own existing playlist on server/local
        insertTestPlaylist(
            localKey = "loc_alice_existing",
            serverId = "srv_alice_99",
            accountKey = activeAccountKey,
            title = "Alice Existing",
            kind = PlaylistKind.PERSONAL.name
        )

        val defaultPlaylists = queryAllPlaylists("default_account")
        val activePlaylists = queryAllPlaylists(activeAccountKey)

        // Find unambiguous candidates to migrate
        val toMigrate = findUnambiguousDefaultAccountPlaylistsToMigrate(
            defaultPlaylists = defaultPlaylists,
            activeAccountPlaylists = activePlaylists,
            remotePlaylists = emptyList(),
            targetAccountKey = activeAccountKey,
            targetUsername = "alice"
        )

        assertEquals(1, toMigrate.size)
        val candidate = toMigrate.first()
        assertEquals(offlineGymKey, candidate.localKey)
        assertEquals(activeAccountKey, candidate.accountKey)
        assertEquals("Gym Mix", candidate.title)

        // Apply migration
        updatePlaylistAccountKey(candidate.localKey, candidate.accountKey)
        migrateSyncActions(candidate.localKey, activeAccountKey)

        // Verify: Gym Mix is now visible under activeAccountKey
        val activeList = queryAllPlaylists(activeAccountKey)
        assertEquals(2, activeList.size)
        val migratedGym = activeList.find { it.localKey == offlineGymKey }
        assertNotNull(migratedGym)
        assertEquals(activeAccountKey, migratedGym?.accountKey)
        assertEquals("Gym Mix", migratedGym?.title)
        assertEquals(offlineGymKey, migratedGym?.localKey)

        // Verify: default_account no longer has Gym Mix
        val defaultList = queryAllPlaylists("default_account")
        assertTrue("default_account must no longer have migrated Gym Mix", defaultList.none { it.localKey == offlineGymKey })

        // Verify: Track memberships in playlist_track_cross_ref are fully preserved
        val tracksForGym = queryTracksForPlaylist(offlineGymKey)
        assertEquals(2, tracksForGym.size)
        assertEquals("track_gym_1", tracksForGym[0].id)
        assertEquals("track_gym_2", tracksForGym[1].id)

        // Verify: Download links are fully preserved
        assertTrue(tracksForGym[0].isDownloaded)
        assertEquals("/data/music/gym1.mp3", tracksForGym[0].localFilePath)
        assertTrue(tracksForGym[1].isDownloaded)
        assertEquals("/data/music/gym2.mp3", tracksForGym[1].localFilePath)

        // Verify: Pending sync queue actions were migrated to activeAccountKey
        val activeQueue = queryPendingActions(activeAccountKey)
        assertEquals(1, activeQueue.size)
        assertEquals(activeAccountKey, activeQueue[0].accountKey)
        assertTrue(activeQueue[0].payloadJson.contains(offlineGymKey))
    }

    @Test
    fun `default_account migration does NOT migrate ambiguous playlists or favorites`() {
        val activeAccountKey = "https://sangita.live|alice"

        // 1. Favorites playlist under default_account
        insertTestPlaylist(
            localKey = "loc_fav_def",
            serverId = null,
            accountKey = "default_account",
            title = "Favorites",
            kind = PlaylistKind.FAVORITES.name
        )

        // 2. Duplicate titles in default_account (ambiguous)
        insertTestPlaylist(
            localKey = "loc_dup1",
            serverId = null,
            accountKey = "default_account",
            title = "Rock Classics",
            kind = PlaylistKind.PERSONAL.name
        )
        insertTestPlaylist(
            localKey = "loc_dup2",
            serverId = null,
            accountKey = "default_account",
            title = "Rock Classics",
            kind = PlaylistKind.PERSONAL.name
        )

        // 3. Collision with server playlist (ambiguous: server already has it)
        insertTestPlaylist(
            localKey = "loc_collision",
            serverId = null,
            accountKey = "default_account",
            title = "Lo-Fi Beats",
            kind = PlaylistKind.PERSONAL.name
        )

        // 4. Collision with existing active account playlist (ambiguous)
        insertTestPlaylist(
            localKey = "loc_active_collision",
            serverId = null,
            accountKey = "default_account",
            title = "Morning Coffee",
            kind = PlaylistKind.PERSONAL.name
        )
        insertTestPlaylist(
            localKey = "loc_alice_coffee",
            serverId = null,
            accountKey = activeAccountKey,
            title = "Morning Coffee",
            kind = PlaylistKind.PERSONAL.name
        )

        // 5. One clear unambiguous playlist
        insertTestPlaylist(
            localKey = "loc_clear",
            serverId = null,
            accountKey = "default_account",
            title = "Deep Focus",
            kind = PlaylistKind.PERSONAL.name,
            owner = "alice"
        )

        val defaultPlaylists = queryAllPlaylists("default_account")
        val activePlaylists = queryAllPlaylists(activeAccountKey)
        val remotePlaylists = listOf(
            PlaylistDto(id = "srv_lofi_1", title = "Lo-Fi Beats", owner = "alice")
        )

        val toMigrate = findUnambiguousDefaultAccountPlaylistsToMigrate(
            defaultPlaylists = defaultPlaylists,
            activeAccountPlaylists = activePlaylists,
            remotePlaylists = remotePlaylists,
            targetAccountKey = activeAccountKey,
            targetUsername = "alice"
        )

        // ONLY "Deep Focus" must be migrated!
        assertEquals(1, toMigrate.size)
        assertEquals("Deep Focus", toMigrate[0].title)
        assertEquals("loc_clear", toMigrate[0].localKey)
        assertEquals(activeAccountKey, toMigrate[0].accountKey)

        // None of the ambiguous playlists or favorites are migrated
        assertFalse(toMigrate.any { it.kind == PlaylistKind.FAVORITES.name })
        assertFalse(toMigrate.any { it.title == "Rock Classics" })
        assertFalse(toMigrate.any { it.title == "Lo-Fi Beats" })
        assertFalse(toMigrate.any { it.title == "Morning Coffee" })
    }

    // =========================================================================
    // 5. SyncQueueDao: Account Isolation
    // =========================================================================

    @Test
    fun `SyncQueueDao pending actions and queue size isolation between Account A and Account B`() {
        val accountA = "https://server-one.com|alice"
        val accountB = "https://server-two.com|bob"
        val defaultAccount = "default_account"

        // Enqueue actions for Account A
        val idA1 = insertTestSyncAction(accountA, SyncActionType.CREATE_PLAYLIST.name, """{"title":"A1"}""")
        val idA2 = insertTestSyncAction(accountA, SyncActionType.ADD_TRACK.name, """{"track":"A2"}""")

        // Enqueue actions for Account B
        val idB1 = insertTestSyncAction(accountB, SyncActionType.CREATE_PLAYLIST.name, """{"title":"B1"}""")

        // Enqueue action for default_account
        val idD1 = insertTestSyncAction(defaultAccount, SyncActionType.TOGGLE_FAVORITE.name, """{"track":"D1"}""")

        // 1. Pending actions partition
        val pendingA = queryPendingActions(accountA)
        assertEquals(2, pendingA.size)
        assertEquals(listOf(idA1, idA2), pendingA.map { it.id })
        assertTrue(pendingA.all { it.accountKey == accountA })

        val pendingB = queryPendingActions(accountB)
        assertEquals(1, pendingB.size)
        assertEquals(idB1, pendingB[0].id)
        assertEquals(accountB, pendingB[0].accountKey)

        val pendingDefault = queryPendingActions(defaultAccount)
        assertEquals(1, pendingDefault.size)
        assertEquals(idD1, pendingDefault[0].id)
        assertEquals(defaultAccount, pendingDefault[0].accountKey)

        // 2. Queue size partition
        assertEquals(2, queryQueueSize(accountA))
        assertEquals(1, queryQueueSize(accountB))
        assertEquals(1, queryQueueSize(defaultAccount))

        // 3. Deleting an action from Account A does not affect Account B or default_account
        deleteSyncAction(idA1)
        assertEquals(1, queryQueueSize(accountA))
        assertEquals(1, queryQueueSize(accountB))
        assertEquals(1, queryQueueSize(defaultAccount))

        // 4. Deleting by local key partitioned by accountKey
        insertTestSyncAction(accountA, SyncActionType.ADD_TRACK.name, """{"localKey":"shared_loc_key","track":"t1"}""")
        insertTestSyncAction(accountB, SyncActionType.ADD_TRACK.name, """{"localKey":"shared_loc_key","track":"t2"}""")

        assertEquals(2, queryQueueSize(accountA))
        assertEquals(2, queryQueueSize(accountB))

        deleteSyncActionsByLocalKey(accountA, "shared_loc_key")
        // Account A had shared_loc_key removed
        assertEquals(1, queryQueueSize(accountA))
        // Account B MUST STILL HAVE shared_loc_key!
        assertEquals(2, queryQueueSize(accountB))
        val bActions = queryPendingActions(accountB)
        assertTrue(bActions.any { it.payloadJson.contains("shared_loc_key") })

        // 5. Clear all for Account A
        clearSyncQueue(accountA)
        assertEquals(0, queryQueueSize(accountA))
        // Account B and default_account are intact
        assertEquals(2, queryQueueSize(accountB))
        assertEquals(1, queryQueueSize(defaultAccount))
    }

    @Test
    fun `matchAndRetargetPlaylistAction prevents SQL wildcard false matches, retargets renamed collisions, and preserves ambiguous actions`() {
        // 1. Wildcard safety: "A_B" must NOT match "A1B"
        val actionA1B = SyncQueueEntity(
            id = 1L,
            accountKey = "default_account",
            actionType = SyncActionType.CREATE_PLAYLIST,
            payloadJson = """{"name":"A1B","visibility":"private"}"""
        )
        val resultA_B = com.sangita.app.sync.SyncPayloadSerialization.matchAndRetargetPlaylistAction(
            action = actionA1B,
            localKey = "loc_a_b",
            oldTitle = "A_B",
            newTitle = "A_B"
        )
        assertNull("A_B should not match A1B", resultA_B)

        // 2. Exact match with special characters: "A_B" matches "A_B" exactly
        val actionA_B = SyncQueueEntity(
            id = 2L,
            accountKey = "default_account",
            actionType = SyncActionType.CREATE_PLAYLIST,
            payloadJson = """{"name":"A_B","visibility":"private"}"""
        )
        val matchedA_B = com.sangita.app.sync.SyncPayloadSerialization.matchAndRetargetPlaylistAction(
            action = actionA_B,
            localKey = "loc_a_b",
            oldTitle = "A_B",
            newTitle = "A_B"
        )
        assertNotNull(matchedA_B)
        val decodedA_B = com.sangita.app.sync.SyncPayloadSerialization.decodeCreatePlaylist(matchedA_B!!.payloadJson)
        assertEquals("A_B", decodedA_B.title)
        assertEquals("loc_a_b", decodedA_B.localKey)

        // 3. Collision retargeting: "Old Rock" renamed to "Old Rock (Recovered)"
        val legacyAddTrack = SyncQueueEntity(
            id = 3L,
            accountKey = "default_account",
            actionType = SyncActionType.ADD_TRACK,
            payloadJson = """{"playlist":"Old Rock","track_path":"rock/song1.mp3"}"""
        )
        val retargeted = com.sangita.app.sync.SyncPayloadSerialization.matchAndRetargetPlaylistAction(
            action = legacyAddTrack,
            localKey = "loc_recovered_99",
            oldTitle = "Old Rock",
            newTitle = "Old Rock (Recovered)"
        )
        assertNotNull(retargeted)
        val decodedAdd = com.sangita.app.sync.SyncPayloadSerialization.decodeAddTrack(retargeted!!.payloadJson)
        assertEquals("Old Rock (Recovered)", decodedAdd.playlistName)
        assertEquals("loc_recovered_99", decodedAdd.localKey)

        // 4. Ambiguity safety: when isTitleAmbiguous=true, title-only action is rejected
        val ambiguousResult = com.sangita.app.sync.SyncPayloadSerialization.matchAndRetargetPlaylistAction(
            action = legacyAddTrack,
            localKey = "loc_ambiguous",
            oldTitle = "Old Rock",
            newTitle = "Old Rock",
            isTitleAmbiguous = true
        )
        assertNull("Ambiguous title-only action should be left for review", ambiguousResult)
    }

    @Test
    fun `logout and clearMemoryCache cleanly resets favorites cache and active playback`() {
        val mockMusicRepo = mockk<MusicRepository>(relaxed = true)
        val mockPlayback = mockk<PlaybackController>(relaxed = true)
        val mockDeviceSync = mockk<DeviceSyncManager>(relaxed = true)
        val mockSyncRepo = mockk<SyncRepository>(relaxed = true)
        val mockNetworkClient = mockk<com.sangita.app.data.remote.api.NetworkClient>(relaxed = true)
        val mockDownload = mockk<SangitaDownloadManager>(relaxed = true)
        val mockConnectivity = mockk<NetworkConnectivityObserver>(relaxed = true)

        val userPrefs = UserPreferences(null)
        userPrefs.setServerUrl("https://server.com")
        userPrefs.setUsername("alice")

        val viewModel = LibraryViewModel(
            musicRepository = mockMusicRepo,
            syncRepository = mockSyncRepo,
            networkClient = mockNetworkClient,
            userPreferences = userPrefs,
            playbackController = mockPlayback,
            downloadManager = mockDownload,
            deviceSyncManager = mockDeviceSync,
            connectivityObserver = mockConnectivity
        )

        var loggedOut = false
        viewModel.logout {
            loggedOut = true
        }

        assertTrue(loggedOut)
        verify(exactly = 1) { mockMusicRepo.clearMemoryCache() }
        verify(exactly = 1) { mockPlayback.resetPlayback() }
        verify(exactly = 1) { mockDeviceSync.disconnect() }
        verify(exactly = 1) { mockDeviceSync.resetSyncState() }
        assertEquals("default_account", userPrefs.getAccountKey())
    }

    // =========================================================================
    // 6. Track & Download Account Scoping
    // =========================================================================

    @Test
    fun `tracks, downloads, search, and favorites queries are strictly partitioned by accountKey`() {
        val accountA = "https://server-a.com|alice"
        val accountB = "https://server-b.com|bob"

        // Create playlists for Account A
        insertTestPlaylist(
            localKey = "loc_a_rock",
            serverId = "srv_a_rock",
            accountKey = accountA,
            title = "Alice Rock",
            kind = PlaylistKind.PERSONAL.name
        )
        insertTestPlaylist(
            localKey = "loc_a_fav",
            serverId = null,
            accountKey = accountA,
            title = "Favorites",
            kind = PlaylistKind.FAVORITES.name
        )

        // Create playlists for Account B
        insertTestPlaylist(
            localKey = "loc_b_jazz",
            serverId = "srv_b_jazz",
            accountKey = accountB,
            title = "Bob Jazz",
            kind = PlaylistKind.PERSONAL.name
        )

        // Insert tracks into shared database
        // Track 1: Belongs to Alice only (downloaded)
        insertTestTrack(
            id = "t1",
            playlistName = "Alice Rock",
            title = "Stairway to Heaven",
            artist = "Led Zeppelin",
            album = "Led Zeppelin IV",
            isDownloaded = true,
            localPath = "/music/stairway.mp3",
            downloadedAt = 1000L
        )
        // Track 2: Belongs to Alice only (not downloaded)
        insertTestTrack(
            id = "t2",
            playlistName = "Alice Rock",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            isDownloaded = false,
            localPath = null
        )
        // Track 3: Belongs to Bob only (downloaded)
        insertTestTrack(
            id = "t3",
            playlistName = "Bob Jazz",
            title = "Take Five",
            artist = "Dave Brubeck",
            album = "Time Out",
            isDownloaded = true,
            localPath = "/music/takefive.mp3",
            downloadedAt = 3000L
        )
        // Track 4: Shared track linked to both Alice and Bob playlists (downloaded)
        insertTestTrack(
            id = "t4",
            playlistName = "Shared",
            title = "Hotel California",
            artist = "Eagles",
            album = "Hotel California",
            isDownloaded = true,
            localPath = "/music/hotel.mp3",
            downloadedAt = 2000L
        )
        // Track 5: Orphan cached track (downloaded) - not linked to any playlist
        insertTestTrack(
            id = "t5",
            playlistName = "Old Cache",
            title = "Old Stale Track",
            artist = "Unknown",
            album = "Forgotten",
            isDownloaded = true,
            localPath = "/music/stale.mp3",
            downloadedAt = 500L
        )
        // Track 6: In Alice's Favorites playlist
        insertTestTrack(
            id = "t6",
            playlistName = "Favorites",
            title = "Alice Special Song",
            artist = "Special Artist",
            album = "Special Album",
            isFavorite = false
        )
        // Track 7: In Alice's rock playlist with isFavorite = true
        insertTestTrack(
            id = "t7",
            playlistName = "Alice Rock",
            title = "Comfortably Numb",
            artist = "Pink Floyd",
            album = "The Wall",
            isFavorite = true
        )

        // Cross refs
        insertTestCrossRef("loc_a_rock", "t1", 0)
        insertTestCrossRef("loc_a_rock", "t2", 1)
        insertTestCrossRef("loc_a_rock", "t4", 2)
        insertTestCrossRef("loc_a_fav", "t6", 0)
        insertTestCrossRef("loc_a_rock", "t7", 3)

        insertTestCrossRef("loc_b_jazz", "t3", 0)
        insertTestCrossRef("loc_b_jazz", "t4", 1)

        // 1. observeTracksForAccount
        val tracksA = queryTracksForAccount(accountA)
        val trackIdsA = tracksA.map { it.id }.toSet()
        assertEquals(setOf("t1", "t2", "t4", "t6", "t7"), trackIdsA)
        assertFalse("Bob's track must not leak to Alice", trackIdsA.contains("t3"))
        assertFalse("Stale orphan track must not leak to Alice", trackIdsA.contains("t5"))

        val tracksB = queryTracksForAccount(accountB)
        val trackIdsB = tracksB.map { it.id }.toSet()
        assertEquals(setOf("t3", "t4"), trackIdsB)
        assertFalse("Alice's track must not leak to Bob", trackIdsB.contains("t1"))
        assertFalse("Alice's track must not leak to Bob", trackIdsB.contains("t2"))
        assertFalse("Alice's favorite must not leak to Bob", trackIdsB.contains("t6"))
        assertFalse("Alice's favorite must not leak to Bob", trackIdsB.contains("t7"))
        assertFalse("Stale orphan track must not leak to Bob", trackIdsB.contains("t5"))

        // 2. observeDownloadedTracksForAccount
        val downloadedA = queryDownloadedTracksForAccount(accountA)
        val downloadedIdsA = downloadedA.map { it.id }
        // Ordered by downloaded_at DESC: t4 (2000), t1 (1000)
        assertEquals(listOf("t4", "t1"), downloadedIdsA)
        assertTrue(downloadedA.all { it.isDownloaded })
        assertFalse("Bob's download must not leak to Alice", downloadedIdsA.contains("t3"))
        assertFalse("Orphan download must not leak to Alice", downloadedIdsA.contains("t5"))

        val downloadedB = queryDownloadedTracksForAccount(accountB)
        val downloadedIdsB = downloadedB.map { it.id }
        // Ordered by downloaded_at DESC: t3 (3000), t4 (2000)
        assertEquals(listOf("t3", "t4"), downloadedIdsB)
        assertTrue(downloadedB.all { it.isDownloaded })
        assertFalse("Alice's download must not leak to Bob", downloadedIdsB.contains("t1"))
        assertFalse("Orphan download must not leak to Bob", downloadedIdsB.contains("t5"))

        // 3. searchTracksForAccount
        val searchQueenA = querySearchTracksForAccount(accountA, "Queen")
        assertEquals(1, searchQueenA.size)
        assertEquals("t2", searchQueenA[0].id)

        val searchQueenB = querySearchTracksForAccount(accountB, "Queen")
        assertTrue("Account B must not see search results from Account A", searchQueenB.isEmpty())

        val searchHotelA = querySearchTracksForAccount(accountA, "Hotel")
        assertEquals(1, searchHotelA.size)
        assertEquals("t4", searchHotelA[0].id)

        val searchHotelB = querySearchTracksForAccount(accountB, "Hotel")
        assertEquals(1, searchHotelB.size)
        assertEquals("t4", searchHotelB[0].id)

        val searchStaleA = querySearchTracksForAccount(accountA, "Stale")
        assertTrue("Orphan stale tracks must never appear in search for Account A", searchStaleA.isEmpty())

        val searchStaleB = querySearchTracksForAccount(accountB, "Stale")
        assertTrue("Orphan stale tracks must never appear in search for Account B", searchStaleB.isEmpty())

        // 4. observeFavoriteTracksForAccount
        val favsA = queryFavoriteTracksForAccount(accountA)
        val favIdsA = favsA.map { it.id }.toSet()
        assertEquals(setOf("t6", "t7"), favIdsA)

        val favsB = queryFavoriteTracksForAccount(accountB)
        assertTrue("Account B has no favorites and must not see Account A favorites", favsB.isEmpty())
    }

    @Test
    fun `switching from account A to account B ensures account B cannot see account A cached tracks downloads or favorites`() = runBlocking {
        val prefs = UserPreferences(null)
        prefs.setServerUrl("https://server-a.com")
        prefs.setUsername("alice")

        val accountA = "https://server-a.com|alice"
        val accountB = "https://server-b.com|bob"

        val trackA = TrackEntity(id = "ta1", playlistName = "P1", title = "Alice Track", artist = "Artist", isDownloaded = true, downloadedAt = 100L, isFavorite = true)
        val trackB = TrackEntity(id = "tb1", playlistName = "P2", title = "Bob Track", artist = "Artist", isDownloaded = true, downloadedAt = 200L, isFavorite = false)

        val mockContext = mockk<android.content.Context>(relaxed = true)
        val mockDb = mockk<com.sangita.app.data.local.database.SangitaDatabase>(relaxed = true)
        val mockTrackDao = mockk<TrackDao>(relaxed = true)
        val mockPlaylistDao = mockk<com.sangita.app.data.local.dao.PlaylistDao>(relaxed = true)
        val mockFavoriteDao = mockk<com.sangita.app.data.local.dao.FavoriteDao>(relaxed = true)
        val mockSyncQueueDao = mockk<com.sangita.app.data.local.dao.SyncQueueDao>(relaxed = true)
        val mockStorage = mockk<com.sangita.app.data.local.storage.LocalStorageManager>(relaxed = true)
        val mockNetwork = mockk<com.sangita.app.data.remote.api.NetworkClient>(relaxed = true)

        every { mockTrackDao.observeTracksForAccount(accountA) } returns flowOf(listOf(trackA))
        every { mockTrackDao.observeTracksForAccount(accountB) } returns flowOf(listOf(trackB))

        every { mockTrackDao.observeDownloadedTracksForAccount(accountA) } returns flowOf(listOf(trackA))
        every { mockTrackDao.observeDownloadedTracksForAccount(accountB) } returns flowOf(listOf(trackB))

        every { mockTrackDao.searchTracksForAccount(accountA, "Alice") } returns flowOf(listOf(trackA))
        every { mockTrackDao.searchTracksForAccount(accountB, "Alice") } returns flowOf(emptyList())

        every { mockTrackDao.observeFavoriteTracksForAccount(accountA) } returns flowOf(listOf(trackA))
        every { mockTrackDao.observeFavoriteTracksForAccount(accountB) } returns flowOf(emptyList())

        val repo = MusicRepositoryImpl(
            context = mockContext,
            database = mockDb,
            trackDao = mockTrackDao,
            playlistDao = mockPlaylistDao,
            favoriteDao = mockFavoriteDao,
            syncQueueDao = mockSyncQueueDao,
            localStorageManager = mockStorage,
            userPreferences = prefs,
            networkClient = mockNetwork
        )

        // While logged into Account A
        assertEquals("ta1", repo.observeAllTracks().first().first().id)
        assertEquals("ta1", repo.observeDownloadedTracks().first().first().id)
        assertEquals(1, repo.searchTracks("Alice").first().size)
        assertEquals("ta1", repo.observeFavoriteTracks().first().first().id)

        // Switch to Account B
        prefs.setServerUrl("https://server-b.com")
        prefs.setUsername("bob")

        assertEquals("tb1", repo.observeAllTracks().first().first().id)
        assertEquals("tb1", repo.observeDownloadedTracks().first().first().id)
        assertTrue(repo.searchTracks("Alice").first().isEmpty())
        assertTrue(repo.observeFavoriteTracks().first().isEmpty())
    }

    @Test
    fun `default_account migration verifies owner and does not adopt playlists belonging to other users`() {
        val targetAccount = "https://server.com|alice"

        // Candidate 1: owner is null (unassigned offline playlist) -> can migrate
        val p1 = PlaylistEntity(
            localKey = "loc_unassigned",
            serverId = null,
            accountKey = "default_account",
            title = "Unassigned Offline",
            kind = PlaylistKind.PERSONAL.name,
            owner = null
        )

        // Candidate 2: owner is "alice" -> matches target user -> can migrate
        val p2 = PlaylistEntity(
            localKey = "loc_alice_owned",
            serverId = null,
            accountKey = "default_account",
            title = "Alice Offline",
            kind = PlaylistKind.PERSONAL.name,
            owner = "alice"
        )

        // Candidate 3: owner is "bob" -> belongs to someone else -> MUST NOT MIGRATE!
        val p3 = PlaylistEntity(
            localKey = "loc_bob_owned",
            serverId = null,
            accountKey = "default_account",
            title = "Bob Stale Offline",
            kind = PlaylistKind.PERSONAL.name,
            owner = "bob"
        )

        val defaultList = listOf(p1, p2, p3)
        val migrated = findUnambiguousDefaultAccountPlaylistsToMigrate(
            defaultPlaylists = defaultList,
            activeAccountPlaylists = emptyList(),
            remotePlaylists = emptyList(),
            targetAccountKey = targetAccount,
            targetUsername = "alice"
        )

        assertEquals(1, migrated.size)
        val migratedKeys = migrated.map { it.localKey }.toSet()
        assertFalse("Cannot assume ownership of unassigned playlist by title alone", migratedKeys.contains("loc_unassigned"))
        assertTrue("Migrates playlist explicitly owned by alice", migratedKeys.contains("loc_alice_owned"))
        assertFalse("Cannot adopt playlist owned by bob", migratedKeys.contains("loc_bob_owned"))
    }

    @Test
    fun `default_account migration only runs once initially and flag prevents re-migration on account switch`() = runBlocking {
        val prefs = UserPreferences(null)
        assertFalse(prefs.isInitialDefaultAccountMigrated())

        val mockContext = mockk<android.content.Context>(relaxed = true)
        val mockDb = mockk<com.sangita.app.data.local.database.SangitaDatabase>(relaxed = true)
        val mockTrackDao = mockk<TrackDao>(relaxed = true)
        val mockPlaylistDao = mockk<com.sangita.app.data.local.dao.PlaylistDao>(relaxed = true)
        val mockFavoriteDao = mockk<com.sangita.app.data.local.dao.FavoriteDao>(relaxed = true)
        val mockSyncQueueDao = mockk<com.sangita.app.data.local.dao.SyncQueueDao>(relaxed = true)
        val mockStorage = mockk<com.sangita.app.data.local.storage.LocalStorageManager>(relaxed = true)
        val mockNetwork = mockk<com.sangita.app.data.remote.api.NetworkClient>(relaxed = true)

        val defaultPlaylists = listOf(
            PlaylistEntity(
                localKey = "loc_offline",
                serverId = null,
                accountKey = "default_account",
                title = "Offline Mix",
                kind = PlaylistKind.PERSONAL.name,
                owner = "alice"
            )
        )
        coEvery { mockPlaylistDao.getAllPlaylists("default_account") } returns defaultPlaylists
        coEvery { mockPlaylistDao.getAllPlaylists("https://server-a.com|alice") } returns emptyList()
        coEvery { mockPlaylistDao.getAllPlaylists("https://server-b.com|bob") } returns emptyList()

        prefs.setServerUrl("https://server-a.com")
        prefs.setUsername("alice")

        val repo = MusicRepositoryImpl(
            context = mockContext,
            database = mockDb,
            trackDao = mockTrackDao,
            playlistDao = mockPlaylistDao,
            favoriteDao = mockFavoriteDao,
            syncQueueDao = mockSyncQueueDao,
            localStorageManager = mockStorage,
            userPreferences = prefs,
            networkClient = mockNetwork
        )

        // First migration for Alice runs
        val firstMigrationResult = repo.migrateDefaultAccountPlaylists("https://server-a.com|alice")
        assertTrue(firstMigrationResult.isSuccess)
        assertEquals(1, firstMigrationResult.getOrNull())
        assertTrue("isInitialDefaultAccountMigrated should be true after first migration", prefs.isInitialDefaultAccountMigrated())

        // Clear auth (logout) does NOT reset isInitialDefaultAccountMigrated
        prefs.clearAuth()
        assertTrue("Logout must preserve isInitialDefaultAccountMigrated flag", prefs.isInitialDefaultAccountMigrated())

        // Now Bob logs in on a different server
        prefs.setServerUrl("https://server-b.com")
        prefs.setUsername("bob")

        // Bob attempting migration must return 0 because migration has already occurred
        val bobMigrationResult = repo.migrateDefaultAccountPlaylists("https://server-b.com|bob")
        assertTrue(bobMigrationResult.isSuccess)
        assertEquals("Subsequent account switch must NOT migrate default_account playlists", 0, bobMigrationResult.getOrNull())
    }

    // =========================================================================
    // Additional SQLite Helpers
    // =========================================================================

    private fun insertTestSyncAction(accountKey: String, actionType: String, payloadJson: String): Long {
        val sql = "INSERT INTO sync_queue (account_key, action_type, payload_json, created_at, retry_count, is_failed) VALUES (?, ?, ?, ?, ?, ?)"
        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setString(1, accountKey)
            stmt.setString(2, actionType)
            stmt.setString(3, payloadJson)
            stmt.setLong(4, System.currentTimeMillis())
            stmt.setInt(5, 0)
            stmt.setInt(6, 0)
            stmt.executeUpdate()
            stmt.generatedKeys.use { rs ->
                rs.next()
                return rs.getLong(1)
            }
        }
    }

    private fun queryPendingActions(accountKey: String): List<SyncQueueEntity> {
        val sql = "SELECT * FROM sync_queue WHERE account_key = ? AND is_failed = 0 ORDER BY created_at ASC, id ASC"
        val results = mutableListOf<SyncQueueEntity>()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountKey)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(
                        SyncQueueEntity(
                            id = rs.getLong("id"),
                            accountKey = rs.getString("account_key"),
                            actionType = SyncActionType.valueOf(rs.getString("action_type")),
                            payloadJson = rs.getString("payload_json"),
                            createdAt = rs.getLong("created_at"),
                            retryCount = rs.getInt("retry_count"),
                            lastError = rs.getString("last_error")
                        )
                    )
                }
            }
        }
        return results
    }

    private fun queryQueueSize(accountKey: String): Int {
        val sql = "SELECT COUNT(*) FROM sync_queue WHERE account_key = ? AND is_failed = 0"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountKey)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getInt(1) else 0
            }
        }
    }

    private fun deleteSyncAction(actionId: Long) {
        val sql = "DELETE FROM sync_queue WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, actionId)
            stmt.executeUpdate()
        }
    }

    private fun deleteSyncActionsByLocalKey(accountKey: String, localKey: String) {
        val sql = "DELETE FROM sync_queue WHERE account_key = ? AND payload_json LIKE '%' || ? || '%'"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountKey)
            stmt.setString(2, localKey)
            stmt.executeUpdate()
        }
    }

    private fun clearSyncQueue(accountKey: String) {
        val sql = "DELETE FROM sync_queue WHERE account_key = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountKey)
            stmt.executeUpdate()
        }
    }

    private fun migrateSyncActions(localKey: String, targetAccountKey: String) {
        val sql = "UPDATE sync_queue SET account_key = ? WHERE account_key = 'default_account' AND payload_json LIKE '%' || ? || '%'"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, targetAccountKey)
            stmt.setString(2, localKey)
            stmt.executeUpdate()
        }
    }

    private fun updatePlaylistAccountKey(localKey: String, targetAccountKey: String) {
        val sql = "UPDATE playlists SET account_key = ? WHERE local_key = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, targetAccountKey)
            stmt.setString(2, localKey)
            stmt.executeUpdate()
        }
    }

    private fun insertTestTrack(
        id: String,
        playlistName: String,
        title: String,
        isDownloaded: Boolean = false,
        localPath: String? = null,
        artist: String = "Artist",
        album: String = "Album",
        downloadedAt: Long? = null,
        isFavorite: Boolean = false
    ) {
        val sql = "INSERT OR REPLACE INTO tracks (id, playlist_name, title, artist, remote_url, local_file_path, is_downloaded, download_status, download_progress, file_size_bytes, is_favorite, album, downloaded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.setString(2, playlistName)
            stmt.setString(3, title)
            stmt.setString(4, artist)
            stmt.setString(5, "https://remote/$id")
            stmt.setString(6, localPath)
            stmt.setInt(7, if (isDownloaded) 1 else 0)
            stmt.setString(8, if (isDownloaded) "COMPLETED" else "IDLE")
            stmt.setInt(9, if (isDownloaded) 100 else 0)
            stmt.setLong(10, 1024L)
            stmt.setInt(11, if (isFavorite) 1 else 0)
            stmt.setString(12, album)
            if (downloadedAt != null) stmt.setLong(13, downloadedAt) else stmt.setNull(13, java.sql.Types.BIGINT)
            stmt.executeUpdate()
        }
    }

    private fun insertTestCrossRef(playlistLocalKey: String, trackId: String, position: Int) {
        val sql = "INSERT OR REPLACE INTO playlist_track_cross_ref (playlist_local_key, track_id, position, is_available, added_at) VALUES (?, ?, ?, ?, ?)"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, playlistLocalKey)
            stmt.setString(2, trackId)
            stmt.setInt(3, position)
            stmt.setInt(4, 1)
            stmt.setLong(5, System.currentTimeMillis())
            stmt.executeUpdate()
        }
    }

    private fun queryTracksForPlaylist(playlistLocalKey: String): List<TrackEntity> {
        val sql = """
            SELECT t.* FROM tracks t
            INNER JOIN playlist_track_cross_ref r ON t.id = r.track_id
            WHERE r.playlist_local_key = ?
            ORDER BY r.position ASC
        """.trimIndent()
        val results = mutableListOf<TrackEntity>()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, playlistLocalKey)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(mapResultSetToTrackEntity(rs))
                }
            }
        }
        return results
    }

    private fun queryTracksForAccount(accountKey: String): List<TrackEntity> {
        val sql = """
            SELECT DISTINCT t.* FROM tracks t
            INNER JOIN playlist_track_cross_ref xref ON t.id = xref.track_id
            INNER JOIN playlists p ON xref.playlist_local_key = p.local_key
            WHERE p.account_key = ?
            ORDER BY t.title ASC
        """.trimIndent()
        val results = mutableListOf<TrackEntity>()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountKey)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(mapResultSetToTrackEntity(rs))
                }
            }
        }
        return results
    }

    private fun queryDownloadedTracksForAccount(accountKey: String): List<TrackEntity> {
        val sql = """
            SELECT DISTINCT t.* FROM tracks t
            INNER JOIN playlist_track_cross_ref xref ON t.id = xref.track_id
            INNER JOIN playlists p ON xref.playlist_local_key = p.local_key
            WHERE p.account_key = ? AND t.is_downloaded = 1
            ORDER BY t.downloaded_at DESC, t.title ASC
        """.trimIndent()
        val results = mutableListOf<TrackEntity>()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountKey)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(mapResultSetToTrackEntity(rs))
                }
            }
        }
        return results
    }

    private fun querySearchTracksForAccount(accountKey: String, query: String): List<TrackEntity> {
        val sql = """
            SELECT DISTINCT t.* FROM tracks t
            INNER JOIN playlist_track_cross_ref xref ON t.id = xref.track_id
            INNER JOIN playlists p ON xref.playlist_local_key = p.local_key
            WHERE p.account_key = ?
              AND (t.title LIKE '%' || ? || '%' OR t.artist LIKE '%' || ? || '%' OR t.album LIKE '%' || ? || '%')
            ORDER BY t.title ASC
        """.trimIndent()
        val results = mutableListOf<TrackEntity>()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountKey)
            stmt.setString(2, query)
            stmt.setString(3, query)
            stmt.setString(4, query)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(mapResultSetToTrackEntity(rs))
                }
            }
        }
        return results
    }

    private fun queryFavoriteTracksForAccount(accountKey: String): List<TrackEntity> {
        val sql = """
            SELECT DISTINCT t.* FROM tracks t
            INNER JOIN playlist_track_cross_ref xref ON t.id = xref.track_id
            INNER JOIN playlists p ON xref.playlist_local_key = p.local_key
            WHERE p.account_key = ? AND (t.is_favorite = 1 OR p.kind = 'FAVORITES')
            ORDER BY t.title ASC
        """.trimIndent()
        val results = mutableListOf<TrackEntity>()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountKey)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(mapResultSetToTrackEntity(rs))
                }
            }
        }
        return results
    }

    private fun mapResultSetToTrackEntity(rs: java.sql.ResultSet): TrackEntity {
        return TrackEntity(
            id = rs.getString("id"),
            playlistName = rs.getString("playlist_name"),
            title = rs.getString("title"),
            artist = rs.getString("artist"),
            album = rs.getString("album"),
            durationSeconds = null,
            remoteUrl = rs.getString("remote_url"),
            localFilePath = rs.getString("local_file_path"),
            isDownloaded = rs.getInt("is_downloaded") == 1,
            downloadStatus = com.sangita.app.domain.model.DownloadStatus.valueOf(rs.getString("download_status")),
            downloadProgress = rs.getInt("download_progress"),
            fileSizeBytes = rs.getLong("file_size_bytes"),
            isFavorite = rs.getInt("is_favorite") == 1,
            downloadedAt = rs.getLong("downloaded_at").takeIf { !rs.wasNull() }
        )
    }
}
