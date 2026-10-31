package com.sangita.app.data.repository

import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.sangita.app.data.local.dao.FavoriteDao
import com.sangita.app.data.local.dao.PlaylistDao
import com.sangita.app.data.local.dao.SyncQueueDao
import com.sangita.app.data.local.dao.TrackDao
import com.sangita.app.data.local.database.SangitaDatabase
import com.sangita.app.data.local.entity.FavoriteEntity
import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.data.local.entity.TrackEntity
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.data.remote.api.SangitaApiService
import com.sangita.app.data.remote.dto.PlaylistDetailDto
import com.sangita.app.data.remote.dto.PlaylistDto
import com.sangita.app.data.remote.dto.PlaylistsResponseDto
import com.sangita.app.domain.model.DownloadStatus
import com.sangita.app.domain.model.PlaylistKind
import com.sangita.app.domain.model.PlaylistSyncState
import com.sangita.app.domain.model.SyncActionType
import com.sangita.app.domain.model.Track
import com.sangita.app.download.SangitaDownloadManager
import com.sangita.app.player.PlaybackController
import com.sangita.app.ui.screens.downloads.DownloadsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class EmptyLibraryTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private val context = mockk<Context>(relaxed = true)
    private val database = mockk<SangitaDatabase>(relaxed = true)
    private val trackDao = mockk<TrackDao>(relaxed = true)
    private val playlistDao = mockk<PlaylistDao>(relaxed = true)
    private val favoriteDao = mockk<FavoriteDao>(relaxed = true)
    private val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
    private val localStorageManager = mockk<LocalStorageManager>(relaxed = true)
    private val downloadManager = mockk<SangitaDownloadManager>(relaxed = true)
    private val playbackController = mockk<PlaybackController>(relaxed = true)

    private lateinit var userPreferences: UserPreferences
    private lateinit var networkClient: NetworkClient
    private val apiService = mockk<SangitaApiService>(relaxed = true)

    private lateinit var repository: MusicRepositoryImpl

    // Simulated in-memory database storage
    private val playlistsInDb = mutableListOf<PlaylistEntity>()
    private val tracksInDb = mutableListOf<TrackEntity>()
    private val syncQueueInDb = mutableListOf<SyncQueueEntity>()
    private val favoritesInDb = mutableListOf<FavoriteEntity>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<RoomDatabase>().withTransaction(any<suspend () -> Any?>()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }

        mockkStatic(WorkManager::class)
        val workManager = mockk<WorkManager>(relaxed = true)
        every { WorkManager.getInstance(any()) } returns workManager

        playlistsInDb.clear()
        tracksInDb.clear()
        syncQueueInDb.clear()
        favoritesInDb.clear()

        // Real in-memory UserPreferences
        val realPrefs = UserPreferences(null)
        userPreferences = spyk(realPrefs)
        userPreferences.setServerUrl("http://localhost:8080")
        userPreferences.setUsername("fresh_user")

        networkClient = spyk(NetworkClient(userPreferences))
        every { networkClient.createApiService(any()) } returns apiService
        every { networkClient.createApiService() } returns apiService

        // PlaylistDao in-memory wiring
        coEvery { playlistDao.getAllPlaylists(any()) } answers {
            val acc = firstArg<String>()
            playlistsInDb.filter { it.accountKey == acc }
        }
        every { playlistDao.observeAllPlaylists(any()) } answers {
            val acc = firstArg<String>()
            flow { emit(playlistsInDb.filter { it.accountKey == acc }) }
        }
        coEvery { playlistDao.getPlaylistByKey(any(), any()) } answers {
            val acc = firstArg<String>()
            val key = secondArg<String>()
            playlistsInDb.find { it.accountKey == acc && (it.localKey == key || it.serverId == key || it.title == key) }
        }
        coEvery { playlistDao.getPlaylistByKey(any()) } answers {
            val key = firstArg<String>()
            playlistsInDb.find { it.localKey == key || it.serverId == key || it.title == key }
        }
        coEvery { playlistDao.insertOrReplaceAll(any()) } answers {
            val items = firstArg<List<PlaylistEntity>>()
            playlistsInDb.removeAll { existing -> items.any { it.localKey == existing.localKey } }
            playlistsInDb.addAll(items)
        }
        coEvery { playlistDao.insertOrReplace(any()) } answers {
            val item = firstArg<PlaylistEntity>()
            playlistsInDb.removeAll { it.localKey == item.localKey }
            playlistsInDb.add(item)
        }
        coEvery { playlistDao.deletePlaylistByKey(any(), any()) } answers {
            val acc = firstArg<String>()
            val key = secondArg<String>()
            playlistsInDb.removeAll { it.accountKey == acc && (it.localKey == key || it.serverId == key) }
        }
        coEvery { playlistDao.deletePlaylistByKey(any()) } answers {
            val key = firstArg<String>()
            playlistsInDb.removeAll { it.localKey == key || it.serverId == key }
        }
        coEvery { playlistDao.clearTracksForPlaylist(any()) } answers { Unit }

        // TrackDao in-memory wiring
        coEvery { trackDao.getTrackById(any()) } answers {
            val id = firstArg<String>()
            tracksInDb.find { it.id == id }
        }
        every { trackDao.observeDownloadedTracks() } answers {
            flowOf(tracksInDb.filter { it.isDownloaded })
        }
        every { trackDao.observeDownloadedTrackCount() } answers {
            flowOf(tracksInDb.count { it.isDownloaded })
        }
        coEvery { trackDao.deleteTrackById(any()) } answers {
            val id = firstArg<String>()
            tracksInDb.removeAll { it.id == id }
        }
        coEvery { trackDao.insertOrReplaceAll(any()) } answers {
            val items = firstArg<List<TrackEntity>>()
            tracksInDb.removeAll { existing -> items.any { it.id == existing.id } }
            tracksInDb.addAll(items)
        }
        coEvery { trackDao.clearFavoriteStatuses() } answers {
            val updated = tracksInDb.map { it.copy(isFavorite = false) }
            tracksInDb.clear()
            tracksInDb.addAll(updated)
        }

        // SyncQueueDao in-memory wiring
        coEvery { syncQueueDao.getPendingActions(any()) } answers {
            val acc = firstArg<String>()
            syncQueueInDb.filter { it.accountKey == acc && !it.isFailed }
        }
        every { syncQueueDao.observePendingActions(any()) } answers {
            val acc = firstArg<String>()
            flow { emit(syncQueueInDb.filter { it.accountKey == acc && !it.isFailed }) }
        }
        coEvery { syncQueueDao.getQueueSize(any()) } answers {
            val acc = firstArg<String>()
            syncQueueInDb.count { it.accountKey == acc && !it.isFailed }
        }
        every { syncQueueDao.observeQueueSize(any()) } answers {
            val acc = firstArg<String>()
            flow { emit(syncQueueInDb.count { it.accountKey == acc && !it.isFailed }) }
        }
        coEvery { syncQueueDao.enqueueAction(any()) } answers {
            val action = firstArg<SyncQueueEntity>()
            val nextId = (syncQueueInDb.maxOfOrNull { it.id } ?: 0L) + 1L
            val entity = action.copy(id = nextId)
            syncQueueInDb.add(entity)
            nextId
        }

        // FavoriteDao in-memory wiring
        coEvery { favoriteDao.clearAll() } answers { favoritesInDb.clear() }
        coEvery { favoriteDao.insert(any()) } answers {
            val fav = firstArg<FavoriteEntity>()
            favoritesInDb.add(fav)
        }

        // Default API service responses
        coEvery { apiService.getPlaylists(any()) } returns Response.success(PlaylistsResponseDto(emptyList()))
        coEvery { apiService.getPlaylists() } returns Response.success(PlaylistsResponseDto(emptyList()))
        coEvery { apiService.getDurations() } returns Response.success(emptyMap())
        coEvery { apiService.getFavorites() } returns Response.success(emptyList())
        coEvery { apiService.getTrackMetadata() } returns Response.success(emptyMap())

        repository = MusicRepositoryImpl(
            context = context,
            database = database,
            trackDao = trackDao,
            playlistDao = playlistDao,
            favoriteDao = favoriteDao,
            syncQueueDao = syncQueueDao,
            localStorageManager = localStorageManager,
            userPreferences = userPreferences,
            networkClient = networkClient
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // =========================================================================
    // Test 1: Empty Personal Library State
    // =========================================================================

    @Test
    fun `test1_onFreshLoginWithZeroPlaylists_syncLibraryLeavesPlaylistsTableEmpty`() = runTest {
        // Fresh login: 0 personal playlists on the server
        coEvery { apiService.getPlaylists() } returns Response.success(PlaylistsResponseDto(playlists = emptyList()))
        coEvery { apiService.getFavorites() } returns Response.success(emptyList())

        // Initial Room playlists table is completely empty
        val currentAccountKey = userPreferences.getAccountKey()
        assertEquals("http://localhost:8080|fresh_user", currentAccountKey)
        assertTrue(playlistsInDb.isEmpty())

        // Sync library from server
        val syncResult = repository.syncLibrary()
        assertTrue("syncLibrary should succeed on fresh login", syncResult.isSuccess)

        // Room playlists table must remain completely empty
        val localPlaylists = playlistDao.getAllPlaylists(currentAccountKey)
        assertTrue("Room playlists table must be empty for 0 server playlists", localPlaylists.isEmpty())

        // observePlaylists() must emit an empty list
        val observedPlaylists = repository.observePlaylists().first()
        assertTrue("observePlaylists() must emit empty list", observedPlaylists.isEmpty())
    }

    @Test
    fun `test1_onFreshLoginWithFavoritesOnly_syncLibraryLeavesOnlyFavoritesInPlaylistsTable`() = runTest {
        val currentAccountKey = userPreferences.getAccountKey()

        // Existing local Favorites playlist in Room database
        val favPlaylist = PlaylistEntity(
            localKey = "favorites_key",
            serverId = null,
            accountKey = currentAccountKey,
            title = "Favorites",
            kind = PlaylistKind.FAVORITES.name,
            syncState = PlaylistSyncState.SYNCED.name
        )
        playlistsInDb.add(favPlaylist)

        // Server has 0 personal playlists and empty favorites
        coEvery { apiService.getPlaylists() } returns Response.success(PlaylistsResponseDto(playlists = emptyList()))
        coEvery { apiService.getFavorites() } returns Response.success(emptyList())

        // Sync library
        val syncResult = repository.syncLibrary()
        assertTrue(syncResult.isSuccess)

        // Verify Room playlists table contains ONLY Favorites
        val localPlaylists = playlistDao.getAllPlaylists(currentAccountKey)
        assertEquals(1, localPlaylists.size)
        assertEquals(PlaylistKind.FAVORITES.name, localPlaylists.first().kind)
        assertEquals("Favorites", localPlaylists.first().title)

        // observePlaylists() emits only Favorites
        val observed = repository.observePlaylists().first()
        assertEquals(1, observed.size)
        assertEquals(PlaylistKind.FAVORITES, observed.first().kind)
        assertTrue(observed.first().isFavorites)
    }

    @Test
    fun `test1_unsavedFolderPlaylistsFromServer_areNotInsertedIntoPersonalPlaylistsTable`() = runTest {
        val currentAccountKey = userPreferences.getAccountKey()

        // Server has public folder playlists available on discover/community endpoint
        val folderPlaylistsOnServer = listOf(
            PlaylistDto(
                id = "folder_rock_90s",
                title = "90s Rock Classics",
                description = "Classic rock hits",
                owner = "curator_bob",
                visibility = "public",
                trackCount = 25,
                isFolderPlaylist = true,
                isSaved = false
            ),
            PlaylistDto(
                id = "folder_electronic",
                title = "Electronic Beats",
                description = "Synth and EDM",
                owner = "curator_alice",
                visibility = "public",
                trackCount = 18,
                isFolderPlaylist = true,
                isSaved = false
            )
        )

        // Discovery endpoint returns the folder playlists
        coEvery { apiService.getPlaylists(discover = true) } returns Response.success(
            PlaylistsResponseDto(playlists = folderPlaylistsOnServer)
        )
        // Library sync endpoint returns 0 personal playlists
        coEvery { apiService.getPlaylists(discover = null) } returns Response.success(
            PlaylistsResponseDto(playlists = emptyList())
        )
        coEvery { apiService.getPlaylists() } returns Response.success(
            PlaylistsResponseDto(playlists = emptyList())
        )

        // 1. Browsing discover/community playlists
        val discoverResult = repository.getCommunityPlaylists()
        assertTrue(discoverResult.isSuccess)
        val discovered = discoverResult.getOrNull().orEmpty()
        assertEquals(2, discovered.size)
        assertTrue(discovered.all { it.isFolder })
        assertFalse("Un-saved folder playlists must have isSaved=false", discovered.any { it.isSaved })

        // 2. Verify folder playlists were NOT inserted into Room playlists table
        coVerify(exactly = 0) { playlistDao.insertOrReplace(any()) }
        assertTrue("Room playlists table must remain empty after browsing community folders", playlistsInDb.isEmpty())

        // 3. Run syncLibrary() and verify un-saved folder playlists are NOT inserted into personal playlists
        val syncResult = repository.syncLibrary()
        assertTrue(syncResult.isSuccess)
        val localPlaylists = playlistDao.getAllPlaylists(currentAccountKey)
        assertTrue("Personal playlists table must remain empty after syncLibrary()", localPlaylists.isEmpty())

        // 4. observePlaylists() must emit an empty list
        val libraryPlaylists = repository.observePlaylists().first()
        assertTrue("Personal library observePlaylists() must emit empty list", libraryPlaylists.isEmpty())
    }

    // =========================================================================
    // Test 2: Saving and Removing Playlists from Library
    // =========================================================================

    @Test
    fun `test2_savePlaylistToLibrary_updatesPreferencesAndSyncsToPersonalLibrary`() = runTest {
        val currentAccountKey = userPreferences.getAccountKey()
        val playlistId = "folder_synthwave_2024"

        // Initial state: not saved in preferences, not in DB
        assertFalse(userPreferences.isPlaylistSaved(playlistId))
        assertFalse(repository.isPlaylistSaved(playlistId).first())
        assertTrue(playlistsInDb.isEmpty())

        // Server mocks for save and subsequent refresh
        coEvery { apiService.savePlaylistToLibrary(playlistId) } returns Response.success(mapOf("status" to "saved"))

        val savedDto = PlaylistDto(
            id = playlistId,
            title = "Synthwave 2024",
            description = "Retro synth grooves",
            owner = "neon_rider",
            visibility = "public",
            trackCount = 12,
            isFolderPlaylist = true,
            isSaved = true
        )
        coEvery { apiService.getPlaylists() } returns Response.success(
            PlaylistsResponseDto(playlists = listOf(savedDto))
        )
        coEvery { apiService.getPlaylistDetail(playlistId) } returns Response.success(
            PlaylistDetailDto(
                id = playlistId,
                title = "Synthwave 2024",
                description = "Retro synth grooves",
                owner = "neon_rider",
                visibility = "public",
                trackCount = 12,
                tracks = emptyList(),
                isFolderPlaylist = true
            )
        )

        // Action: save playlist to personal library
        val saveResult = repository.savePlaylistToLibrary(playlistId)
        assertTrue("savePlaylistToLibrary must succeed", saveResult.isSuccess)

        // 1. Verify UserPreferences is updated
        assertTrue("UserPreferences.isPlaylistSaved must be true", userPreferences.isPlaylistSaved(playlistId))
        assertTrue("repository.isPlaylistSaved must emit true", repository.isPlaylistSaved(playlistId).first())
        assertTrue("observeSavedPlaylistIds must contain saved id", repository.observeSavedPlaylistIds().first().contains(playlistId))

        // 2. Verify network API call was made
        coVerify(exactly = 1) { apiService.savePlaylistToLibrary(playlistId) }

        // 3. Verify it was synced into Room playlists table
        coVerify(atLeast = 1) { playlistDao.insertOrReplaceAll(any()) }
        val localList = playlistDao.getAllPlaylists(currentAccountKey)
        assertEquals(1, localList.size)
        assertEquals("Synthwave 2024", localList.first().title)
        assertEquals(playlistId, localList.first().serverId)

        // 4. Verify observePlaylists() emits it with isSaved = true
        val observedList = repository.observePlaylists().first()
        assertEquals(1, observedList.size)
        val savedPlaylist = observedList.first()
        assertEquals(playlistId, savedPlaylist.serverId)
        assertEquals("Synthwave 2024", savedPlaylist.title)
        assertTrue("Playlist in personal library must have isSaved=true", savedPlaylist.isSaved)
    }

    @Test
    fun `test2_removePlaylistFromLibrary_clearsFromPreferencesAndLibraryDatabase`() = runTest {
        val currentAccountKey = userPreferences.getAccountKey()
        val playlistId = "folder_ambient_space"
        val localKey = "local_key_ambient"

        // Set up playlist as already saved in preferences and present in Room
        userPreferences.setPlaylistSaved(playlistId, true)
        assertTrue(userPreferences.isPlaylistSaved(playlistId))

        val existingEntity = PlaylistEntity(
            localKey = localKey,
            serverId = playlistId,
            accountKey = currentAccountKey,
            title = "Ambient Space",
            kind = PlaylistKind.FOLDER.name,
            syncState = PlaylistSyncState.SYNCED.name
        )
        playlistsInDb.add(existingEntity)

        // Verify initial state has 1 playlist in library
        assertEquals(1, playlistDao.getAllPlaylists(currentAccountKey).size)
        val initialObserved = repository.observePlaylists().first()
        assertEquals(1, initialObserved.size)
        assertTrue(initialObserved.first().isSaved)

        // Server mocks for removal: server returns 200, then getPlaylists returns empty
        coEvery { apiService.removePlaylistFromLibrary(playlistId) } returns Response.success(mapOf("status" to "removed"))
        coEvery { apiService.getPlaylists() } returns Response.success(PlaylistsResponseDto(playlists = emptyList()))

        // Action: remove playlist from library
        val removeResult = repository.removePlaylistFromLibrary(playlistId)
        assertTrue("removePlaylistFromLibrary must succeed", removeResult.isSuccess)

        // 1. Verify UserPreferences is updated
        assertFalse("UserPreferences.isPlaylistSaved must be false", userPreferences.isPlaylistSaved(playlistId))
        assertFalse("repository.isPlaylistSaved must emit false", repository.isPlaylistSaved(playlistId).first())
        assertFalse("observeSavedPlaylistIds must not contain removed id", repository.observeSavedPlaylistIds().first().contains(playlistId))

        // 2. Verify network API call was made
        coVerify(exactly = 1) { apiService.removePlaylistFromLibrary(playlistId) }

        // 3. Verify it was cleared from Room playlists table
        coVerify(atLeast = 1) { playlistDao.deletePlaylistByKey(currentAccountKey, localKey) }
        val remainingInDb = playlistDao.getAllPlaylists(currentAccountKey)
        assertTrue("Room playlists table must be empty after removal", remainingInDb.isEmpty())

        // 4. Verify observePlaylists() emits empty list
        val remainingObserved = repository.observePlaylists().first()
        assertTrue("observePlaylists() must emit empty list after removal", remainingObserved.isEmpty())
    }

    @Test
    fun `test2_savePlaylistToLibrary_revertsStateOnServerError`() = runTest {
        val playlistId = "folder_fail_500"
        assertFalse(userPreferences.isPlaylistSaved(playlistId))

        coEvery { apiService.savePlaylistToLibrary(playlistId) } returns Response.error(
            500,
            "{\"error\":\"Internal Server Error\"}".toResponseBody()
        )

        val result = repository.savePlaylistToLibrary(playlistId)
        assertTrue(result.isFailure)
        assertFalse("Preference must revert to false when server returns 500", userPreferences.isPlaylistSaved(playlistId))
    }

    @Test
    fun `test2_removePlaylistFromLibrary_revertsStateOnNetworkError`() = runTest {
        val playlistId = "folder_offline_revert"
        userPreferences.setPlaylistSaved(playlistId, true)
        assertTrue(userPreferences.isPlaylistSaved(playlistId))

        coEvery { apiService.removePlaylistFromLibrary(playlistId) } throws IOException("Network unreachable")

        val result = repository.removePlaylistFromLibrary(playlistId)
        assertTrue(result.isFailure)
        assertTrue("Preference must revert to true when network call fails", userPreferences.isPlaylistSaved(playlistId))
    }

    // =========================================================================
    // Test 3: Offline Downloads Preservation
    // =========================================================================

    @Test
    fun `test3_offlineDownloadedTracks_preservedWhenPersonalLibraryHasZeroPlaylists`() = runTest {
        val currentAccountKey = userPreferences.getAccountKey()

        // 1. Personal library has 0 playlists and 0 saved songs
        assertTrue("Personal library playlists must be empty", playlistDao.getAllPlaylists(currentAccountKey).isEmpty())
        assertTrue("Personal library observed must be empty", repository.observePlaylists().first().isEmpty())

        // 2. Room tracks table contains downloaded tracks with is_downloaded == true
        val downloadedTrack1 = TrackEntity(
            id = "track_offline_01",
            playlistName = "Album Alpha",
            title = "Midnight Horizon",
            artist = "Solar Drive",
            durationSeconds = 240.0,
            remoteUrl = "http://localhost:8080/api/stream/track_offline_01",
            localFilePath = "/data/user/0/com.sangita.app/files/downloads/track_offline_01.mp3",
            isDownloaded = true,
            downloadStatus = DownloadStatus.COMPLETED,
            downloadProgress = 100,
            fileSizeBytes = 4500000L,
            downloadedAt = 1000L
        )
        val downloadedTrack2 = TrackEntity(
            id = "track_offline_02",
            playlistName = "Album Beta",
            title = "Neon Highway",
            artist = "Retro Synth",
            durationSeconds = 195.0,
            remoteUrl = "http://localhost:8080/api/stream/track_offline_02",
            localFilePath = "/data/user/0/com.sangita.app/files/downloads/track_offline_02.mp3",
            isDownloaded = true,
            downloadStatus = DownloadStatus.COMPLETED,
            downloadProgress = 100,
            fileSizeBytes = 3800000L,
            downloadedAt = 2000L
        )
        val streamOnlyTrack = TrackEntity(
            id = "track_stream_only",
            playlistName = "Online Album",
            title = "Stream Anthem",
            artist = "Cloud Artist",
            durationSeconds = 180.0,
            remoteUrl = "http://localhost:8080/api/stream/track_stream_only",
            localFilePath = null,
            isDownloaded = false,
            downloadStatus = DownloadStatus.IDLE
        )

        tracksInDb.addAll(listOf(downloadedTrack1, downloadedTrack2, streamOnlyTrack))

        // 3. Run syncLibrary() with 0 server playlists
        coEvery { apiService.getPlaylists() } returns Response.success(PlaylistsResponseDto(emptyList()))
        coEvery { apiService.getFavorites() } returns Response.success(emptyList())

        val syncResult = repository.syncLibrary()
        assertTrue(syncResult.isSuccess)

        // 4. Verify downloaded tracks were NOT deleted or altered during library sync
        coVerify(exactly = 0) { trackDao.deleteTrackById(any()) }
        val remainingTracks = tracksInDb.filter { it.isDownloaded }
        assertEquals(2, remainingTracks.size)
        assertTrue(remainingTracks.any { it.id == "track_offline_01" })
        assertTrue(remainingTracks.any { it.id == "track_offline_02" })

        // 5. Verify trackDao.observeDownloadedTracks() emits downloaded tracks
        val observedDownloaded = trackDao.observeDownloadedTracks().first()
        assertEquals(2, observedDownloaded.size)
        assertEquals("track_offline_01", observedDownloaded[0].id)
        assertEquals("track_offline_02", observedDownloaded[1].id)
        assertTrue(observedDownloaded.all { it.isDownloaded })

        // 6. Verify trackDao.observeDownloadedTrackCount() emits 2
        val downloadedCount = trackDao.observeDownloadedTrackCount().first()
        assertEquals(2, downloadedCount)
    }

    @Test
    fun `test3_offlinePlayback_worksRegardlessOfPersonalLibraryContents`() = runTest {
        // Personal library has 0 playlists
        assertTrue(playlistsInDb.isEmpty())

        // Mock local audio file existing on device
        val mockFile = mockk<File>(relaxed = true) {
            every { exists() } returns true
            every { isFile } returns true
            every { length() } returns 4500000L
            every { absolutePath } returns "/data/user/0/com.sangita.app/files/downloads/track_offline_01.mp3"
        }
        every { localStorageManager.getExistingAudioFile(any(), any()) } returns mockFile

        val trackEntity = TrackEntity(
            id = "track_offline_01",
            playlistName = "Album Alpha",
            title = "Midnight Horizon",
            artist = "Solar Drive",
            album = "Album Alpha",
            durationSeconds = 240.0,
            remoteUrl = "http://localhost:8080/api/stream/track_offline_01",
            localFilePath = "/data/user/0/com.sangita.app/files/downloads/track_offline_01.mp3",
            isDownloaded = true,
            downloadStatus = DownloadStatus.COMPLETED
        )
        tracksInDb.add(trackEntity)

        // DownloadsViewModel initializes and observes downloaded tracks directly from trackDao
        val downloadsViewModel = DownloadsViewModel(
            trackDao = trackDao,
            localStorageManager = localStorageManager,
            downloadManager = downloadManager,
            playbackController = playbackController,
            userPreferences = userPreferences
        )

        advanceUntilIdle()

        // 1. DownloadsViewModel.downloadedTracks contains the offline track despite 0 playlists
        val downloadedList = downloadsViewModel.downloadedTracks.first { it.isNotEmpty() }
        assertEquals(1, downloadedList.size)
        assertEquals("track_offline_01", downloadedList.first().id)
        assertTrue(downloadedList.first().isDownloaded)

        // 2. Play downloaded track offline via ViewModel
        downloadsViewModel.playTrack(downloadedList.first())

        // 3. Verify PlaybackController.playTrack was invoked with forceLocal = true
        verify(exactly = 1) {
            playbackController.playTrack(
                match { it.id == "track_offline_01" },
                match { it.size == 1 && it.first().id == "track_offline_01" },
                forceLocal = true
            )
        }
    }

    // =========================================================================
    // Test 4: Account Isolation
    // =========================================================================

    @Test
    fun `test4_accountSwitch_isolatesPersonalPlaylistsAndSyncQueueBetweenUsers`() = runTest {
        // --- STEP 1: Set up User A with personal playlists and sync queue actions ---
        userPreferences.setServerUrl("https://music.sangita.app")
        userPreferences.setUsername("userA")

        val userAKey = userPreferences.getAccountKey()
        assertEquals("https://music.sangita.app|usera", userAKey)

        // User A's playlists in Room
        val userAPlaylist1 = PlaylistEntity(
            localKey = "user_a_pl_1",
            serverId = "srv_a_1",
            accountKey = userAKey,
            title = "User A Workout Mix",
            kind = PlaylistKind.PERSONAL.name,
            syncState = PlaylistSyncState.SYNCED.name
        )
        val userAPlaylist2 = PlaylistEntity(
            localKey = "user_a_pl_2",
            serverId = "srv_a_2",
            accountKey = userAKey,
            title = "User A Chillout",
            kind = PlaylistKind.PERSONAL.name,
            syncState = PlaylistSyncState.SYNCED.name
        )
        playlistsInDb.addAll(listOf(userAPlaylist1, userAPlaylist2))

        // User A's sync queue actions in Room
        val actionA1 = SyncQueueEntity(
            id = 1L,
            accountKey = userAKey,
            actionType = SyncActionType.CREATE_PLAYLIST,
            payloadJson = "{\"local_key\":\"user_a_pl_1\",\"title\":\"User A Workout Mix\"}",
            createdAt = 1000L
        )
        val actionA2 = SyncQueueEntity(
            id = 2L,
            accountKey = userAKey,
            actionType = SyncActionType.ADD_TRACK,
            payloadJson = "{\"local_key\":\"user_a_pl_1\",\"track_path\":\"song_01.mp3\"}",
            createdAt = 1100L
        )
        syncQueueInDb.addAll(listOf(actionA1, actionA2))

        // Verify User A sees User A's playlists and actions
        val userAPlaylists = playlistDao.getAllPlaylists(userAKey)
        assertEquals(2, userAPlaylists.size)
        assertEquals(2, repository.observePlaylists().first().size)

        val userAActions = syncQueueDao.getPendingActions(userAKey)
        assertEquals(2, userAActions.size)
        assertEquals(2, syncQueueDao.getQueueSize(userAKey))
        assertEquals(2, syncQueueDao.observePendingActions(userAKey).first().size)

        // --- STEP 2: Switch to User B (Fresh Account) ---
        userPreferences.setUsername("userB")
        val userBKey = userPreferences.getAccountKey()
        assertEquals("https://music.sangita.app|userb", userBKey)

        // 1. User B does NOT see User A's playlists
        val userBPlaylists = playlistDao.getAllPlaylists(userBKey)
        assertTrue("User B must have 0 playlists in Room", userBPlaylists.isEmpty())

        val userBObservedPlaylists = repository.observePlaylists().first()
        assertTrue("repository.observePlaylists() for User B must be empty", userBObservedPlaylists.isEmpty())
        assertTrue("User B must not see User A's playlists", userBObservedPlaylists.none { it.id == "user_a_pl_1" || it.id == "user_a_pl_2" })

        // 2. User B does NOT see User A's sync queue actions
        val userBActions = syncQueueDao.getPendingActions(userBKey)
        assertTrue("User B must have 0 pending sync actions", userBActions.isEmpty())
        assertTrue("User B must not see User A's sync actions", userBActions.none { it.accountKey == userAKey })

        val userBObservedActions = syncQueueDao.observePendingActions(userBKey).first()
        assertTrue("User B observePendingActions() must emit empty list", userBObservedActions.isEmpty())

        val userBQueueSize = syncQueueDao.getQueueSize(userBKey)
        assertEquals("User B queue size must be 0", 0, userBQueueSize)

        val userBObservedQueueSize = syncQueueDao.observeQueueSize(userBKey).first()
        assertEquals("User B observed queue size must be 0", 0, userBObservedQueueSize)

        // --- STEP 3: User B creates their own personal playlist ---
        val userBPlaylist = PlaylistEntity(
            localKey = "user_b_pl_1",
            serverId = null,
            accountKey = userBKey,
            title = "User B Acoustic",
            kind = PlaylistKind.PERSONAL.name,
            syncState = PlaylistSyncState.PENDING_CREATE.name
        )
        playlistsInDb.add(userBPlaylist)

        val actionB1 = SyncQueueEntity(
            id = 3L,
            accountKey = userBKey,
            actionType = SyncActionType.CREATE_PLAYLIST,
            payloadJson = "{\"local_key\":\"user_b_pl_1\",\"title\":\"User B Acoustic\"}",
            createdAt = 2000L
        )
        syncQueueInDb.add(actionB1)

        // Verify User B sees only their playlist
        val userBUpdatedPlaylists = playlistDao.getAllPlaylists(userBKey)
        assertEquals(1, userBUpdatedPlaylists.size)
        assertEquals("User B Acoustic", userBUpdatedPlaylists.first().title)

        val userBUpdatedActions = syncQueueDao.getPendingActions(userBKey)
        assertEquals(1, userBUpdatedActions.size)
        assertEquals(3L, userBUpdatedActions.first().id)

        // --- STEP 4: Switch back to User A ---
        userPreferences.setUsername("userA")
        assertEquals(userAKey, userPreferences.getAccountKey())

        // User A sees only User A's playlists and queue actions, and does NOT see User B's
        val userARestoredPlaylists = playlistDao.getAllPlaylists(userAKey)
        assertEquals(2, userARestoredPlaylists.size)
        assertTrue(userARestoredPlaylists.none { it.localKey == "user_b_pl_1" })

        val userARestoredObserved = repository.observePlaylists().first()
        assertEquals(2, userARestoredObserved.size)
        assertTrue(userARestoredObserved.none { it.title == "User B Acoustic" })

        val userARestoredActions = syncQueueDao.getPendingActions(userAKey)
        assertEquals(2, userARestoredActions.size)
        assertTrue(userARestoredActions.none { it.id == 3L })
        assertEquals(2, syncQueueDao.getQueueSize(userAKey))
    }
}
