package com.sangita.app.ui.screens.playlist

import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.PlaylistKind
import com.sangita.app.domain.model.PlaylistSyncState
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.MusicRepository
import com.sangita.app.sync.NetworkConnectivityObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeMusicRepository
    private lateinit var fakePreferences: FakeUserPreferences
    private lateinit var fakeConnectivityObserver: FakeConnectivityObserver

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeMusicRepository()
        fakePreferences = FakeUserPreferences(testUsername = "current_user", testRole = "user")
        fakeConnectivityObserver = FakeConnectivityObserver(initialOnline = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(playlistKey: String = "playlist-123"): PlaylistViewModel {
        val viewModel = PlaylistViewModel(
            playlistKey = playlistKey,
            musicRepository = fakeRepository,
            userPreferences = fakePreferences,
            playbackController = null,
            downloadManager = null,
            deviceSyncManager = null,
            connectivityObserver = fakeConnectivityObserver
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.tracks.collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.playlist.collect {}
        }
        return viewModel
    }

    @Test
    fun loadsPlaylistAndTracks_updatesStateFlows() = runTest(testDispatcher) {
        val testPlaylist = Playlist(
            id = "playlist-123",
            title = "Chill Vibes",
            owner = "current_user",
            description = "Relaxing tunes"
        )
        val track1 = Track(id = "1", title = "Track 1", isAvailable = true)
        val track2 = Track(id = "2", title = "Track 2", isAvailable = true)

        fakeRepository.setPlaylist("playlist-123", testPlaylist)
        fakeRepository.setTracks("playlist-123", listOf(track1, track2))

        val viewModel = createViewModel("playlist-123")
        advanceUntilIdle()

        assertEquals("Chill Vibes", viewModel.playlistName)
        assertEquals(testPlaylist, viewModel.uiState.value.playlist)
        assertEquals(listOf(track1, track2), viewModel.uiState.value.tracks)
        assertEquals(0, viewModel.uiState.value.unavailableCount)
        assertTrue(viewModel.uiState.value.isOwner)
    }

    @Test
    fun isOwner_personalPlaylistOwnedByCurrentUser_returnsTrue() = runTest(testDispatcher) {
        val playlist = Playlist(
            id = "p-1",
            title = "My Playlist",
            kind = PlaylistKind.PERSONAL,
            owner = "current_user"
        )
        fakeRepository.setPlaylist("p-1", playlist)

        val viewModel = createViewModel("p-1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOwner)
        assertTrue(viewModel.isOwner)
    }

    @Test
    fun isOwner_folderPlaylist_isNeverOwner() = runTest(testDispatcher) {
        val folderPlaylist = Playlist(
            id = "folder-1",
            title = "Rock Folder",
            kind = PlaylistKind.FOLDER,
            owner = "current_user" // Even if owner matches
        )
        fakeRepository.setPlaylist("folder-1", folderPlaylist)

        val viewModel = createViewModel("folder-1")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isOwner)
        assertFalse(viewModel.isOwner)
    }

    @Test
    fun isOwner_publicPlaylistOwnedByOther_isNotOwner() = runTest(testDispatcher) {
        val otherPlaylist = Playlist(
            id = "other-1",
            title = "Someone Else's Mix",
            kind = PlaylistKind.PERSONAL,
            owner = "another_user",
            visibility = "public"
        )
        fakeRepository.setPlaylist("other-1", otherPlaylist)

        val viewModel = createViewModel("other-1")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isOwner)
        assertFalse(viewModel.isOwner)
    }

    @Test
    fun isOwner_adminRole_alwaysOwner() = runTest(testDispatcher) {
        fakePreferences.setRole("admin")
        val folderPlaylist = Playlist(
            id = "folder-admin",
            title = "System Folder",
            kind = PlaylistKind.FOLDER,
            owner = "root"
        )
        fakeRepository.setPlaylist("folder-admin", folderPlaylist)

        val viewModel = createViewModel("folder-admin")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOwner)
        assertTrue(viewModel.isOwner)
    }

    @Test
    fun isOwner_favoritesPlaylist_returnsTrue() = runTest(testDispatcher) {
        fakeRepository.setPlaylist("Favorites", null)
        val viewModel = createViewModel("Favorites")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOwner)
    }

    @Test
    fun unavailableCount_computesMaxOfPlaylistAndTracks() = runTest(testDispatcher) {
        val track1 = Track(id = "1", title = "Available 1", isAvailable = true)
        val track2 = Track(id = "2", title = "Unavailable 1", isAvailable = false)
        val track3 = Track(id = "3", title = "Unavailable 2", isAvailable = false)

        // Case A: tracks have 2 unavailable, playlist unavailableCount is 0 -> count = 2
        val playlistA = Playlist(
            id = "count-test-a",
            title = "Count Test A",
            unavailableCount = 0
        )
        fakeRepository.setPlaylist("count-test-a", playlistA)
        fakeRepository.setTracks("count-test-a", listOf(track1, track2, track3))

        val vmA = createViewModel("count-test-a")
        advanceUntilIdle()
        assertEquals(2, vmA.uiState.value.unavailableCount)

        // Case B: playlist unavailableCount is 5, tracks only show 2 -> count = 5
        val playlistB = Playlist(
            id = "count-test-b",
            title = "Count Test B",
            unavailableCount = 5
        )
        fakeRepository.setPlaylist("count-test-b", playlistB)
        fakeRepository.setTracks("count-test-b", listOf(track1, track2, track3))

        val vmB = createViewModel("count-test-b")
        advanceUntilIdle()
        assertEquals(5, vmB.uiState.value.unavailableCount)
    }

    @Test
    fun playAll_onlyQueuesAvailableTracks() = runTest(testDispatcher) {
        val track1 = Track(id = "1", title = "Good Song 1", isAvailable = true)
        val track2 = Track(id = "2", title = "Deleted Song", isAvailable = false)
        val track3 = Track(id = "3", title = "Good Song 2", isAvailable = true)

        fakeRepository.setPlaylist("pl", Playlist(id = "pl", title = "My List"))
        fakeRepository.setTracks("pl", listOf(track1, track2, track3))

        val viewModel = createViewModel("pl")
        advanceUntilIdle()

        var playedTrack: Track? = null
        var queuedTracks: List<Track>? = null
        viewModel.onPlayTracksListener = { track, list ->
            playedTrack = track
            queuedTracks = list
        }

        viewModel.playAll()

        assertEquals(track1, playedTrack)
        assertEquals(listOf(track1, track3), queuedTracks)
        assertFalse(queuedTracks!!.contains(track2))
    }

    @Test
    fun shufflePlay_onlyQueuesAvailableTracks() = runTest(testDispatcher) {
        val track1 = Track(id = "1", title = "Good Song 1", isAvailable = true)
        val track2 = Track(id = "2", title = "Unavailable Song", isAvailable = false)
        val track3 = Track(id = "3", title = "Good Song 2", isAvailable = true)

        fakeRepository.setPlaylist("pl", Playlist(id = "pl", title = "My List"))
        fakeRepository.setTracks("pl", listOf(track1, track2, track3))

        val viewModel = createViewModel("pl")
        advanceUntilIdle()

        var playedTrack: Track? = null
        var queuedTracks: List<Track>? = null
        viewModel.onPlayTracksListener = { track, list ->
            playedTrack = track
            queuedTracks = list
        }

        viewModel.shufflePlay()

        assertTrue(playedTrack == track1 || playedTrack == track3)
        assertEquals(2, queuedTracks?.size)
        assertTrue(queuedTracks!!.contains(track1))
        assertTrue(queuedTracks!!.contains(track3))
        assertFalse(queuedTracks!!.contains(track2))
    }

    @Test
    fun playTrack_availableTrack_playsWithAvailableQueue() = runTest(testDispatcher) {
        val track1 = Track(id = "1", title = "Good 1", isAvailable = true)
        val track2 = Track(id = "2", title = "Bad 1", isAvailable = false)
        val track3 = Track(id = "3", title = "Good 2", isAvailable = true)

        fakeRepository.setPlaylist("pl", Playlist(id = "pl", title = "List"))
        fakeRepository.setTracks("pl", listOf(track1, track2, track3))

        val viewModel = createViewModel("pl")
        advanceUntilIdle()

        var playedTrack: Track? = null
        var queuedTracks: List<Track>? = null
        viewModel.onPlayTracksListener = { track, list ->
            playedTrack = track
            queuedTracks = list
        }

        viewModel.playTrack(track3)

        assertEquals(track3, playedTrack)
        assertEquals(listOf(track1, track3), queuedTracks)
    }

    @Test
    fun playTrack_unavailableTrack_doesNothing() = runTest(testDispatcher) {
        val track1 = Track(id = "1", title = "Good 1", isAvailable = true)
        val track2 = Track(id = "2", title = "Bad 1", isAvailable = false)

        fakeRepository.setPlaylist("pl", Playlist(id = "pl", title = "List"))
        fakeRepository.setTracks("pl", listOf(track1, track2))

        val viewModel = createViewModel("pl")
        advanceUntilIdle()

        var called = false
        viewModel.onPlayTracksListener = { _, _ -> called = true }

        viewModel.playTrack(track2)

        assertFalse(called)
    }

    @Test
    fun downloadAll_onlyDownloadsAvailableTracks() = runTest(testDispatcher) {
        val track1 = Track(id = "1", title = "Song 1", isAvailable = true)
        val track2 = Track(id = "2", title = "Song 2", isAvailable = false)
        val track3 = Track(id = "3", title = "Song 3", isAvailable = true)

        fakeRepository.setPlaylist("pl", Playlist(id = "pl", title = "List"))
        fakeRepository.setTracks("pl", listOf(track1, track2, track3))

        val viewModel = createViewModel("pl")
        advanceUntilIdle()

        var downloadedTracks: List<Track>? = null
        viewModel.onDownloadTracksListener = { list ->
            downloadedTracks = list
        }

        viewModel.downloadAll()

        assertEquals(listOf(track1, track3), downloadedTracks)
    }

    @Test
    fun updatePlaylist_callsRepositoryAndNotifiesCallback() = runTest(testDispatcher) {
        val viewModel = createViewModel("pl-update")
        advanceUntilIdle()

        var successResult: Boolean? = null
        viewModel.updatePlaylist(
            title = "Updated Title",
            description = "Updated Desc",
            visibility = "public"
        ) { success ->
            successResult = success
        }
        advanceUntilIdle()

        assertEquals(true, successResult)
        assertEquals("pl-update", fakeRepository.lastUpdatedKey)
        assertEquals("Updated Title", fakeRepository.lastUpdatedTitle)
        assertEquals("Updated Desc", fakeRepository.lastUpdatedDescription)
        assertEquals("public", fakeRepository.lastUpdatedVisibility)
    }

    @Test
    fun deletePlaylist_callsRepositoryAndNotifiesCallback() = runTest(testDispatcher) {
        val viewModel = createViewModel("pl-delete")
        advanceUntilIdle()

        var successResult: Boolean? = null
        viewModel.deletePlaylist { success ->
            successResult = success
        }
        advanceUntilIdle()

        assertEquals(true, successResult)
        assertEquals("pl-delete", fakeRepository.lastDeletedKey)
    }

    @Test
    fun clonePlaylist_callsRepositoryAndNotifiesCallback() = runTest(testDispatcher) {
        val viewModel = createViewModel("pl-clone")
        advanceUntilIdle()

        var clonedResult: Playlist? = null
        viewModel.clonePlaylist(
            title = "Cloned Playlist",
            description = "Cloned from original",
            isPublic = false
        ) { playlist ->
            clonedResult = playlist
        }
        advanceUntilIdle()

        assertEquals("cloned-id-123", clonedResult?.id)
        assertEquals("Cloned Playlist", clonedResult?.title)
        assertEquals("pl-clone", fakeRepository.lastClonedKey)
    }

    @Test
    fun addTrack_and_removeTrack_callsRepository() = runTest(testDispatcher) {
        val viewModel = createViewModel("pl-tracks")
        advanceUntilIdle()

        val track = Track(id = "track-10", title = "Track 10")
        viewModel.addTrack(track)
        advanceUntilIdle()
        assertEquals("pl-tracks", fakeRepository.lastAddedTrackPlaylistKey)
        assertEquals(track, fakeRepository.lastAddedTrack)

        viewModel.removeTrack(track)
        advanceUntilIdle()
        assertEquals("pl-tracks", fakeRepository.lastRemovedTrackPlaylistKey)
        assertEquals(track, fakeRepository.lastRemovedTrack)
    }

    @Test
    fun offlineMode_filtersUndownloadedTracks() = runTest(testDispatcher) {
        val downloaded = Track(id = "d1", title = "Downloaded", isDownloaded = true)
        val notDownloaded = Track(id = "nd1", title = "Stream Only", isDownloaded = false)

        fakeRepository.setPlaylist("offline-pl", Playlist(id = "offline-pl", title = "Offline Test"))
        fakeRepository.setTracks("offline-pl", listOf(downloaded, notDownloaded))

        fakeConnectivityObserver.setOnline(false)
        val viewModel = createViewModel("offline-pl")
        advanceUntilIdle()

        assertEquals(listOf(downloaded), viewModel.tracks.value)
    }
}

// -------------------------------------------------------------
// Fakes for testing
// -------------------------------------------------------------

class FakeUserPreferences(
    private var testUsername: String? = null,
    private var testRole: String = "user",
    private var testServerUrl: String = "https://example.com",
    private var testToken: String? = "test-token"
) : UserPreferences(null) {
    override fun getUsername(): String? = testUsername
    fun setTestUsername(name: String?) { testUsername = name }
    override fun getRole(): String = testRole
    override fun setRole(role: String) { testRole = role }
    override fun getServerUrl(): String = testServerUrl
    override fun getToken(): String? = testToken
}

class FakeConnectivityObserver(initialOnline: Boolean = true) : NetworkConnectivityObserver(null) {
    private val onlineFlow = MutableStateFlow(initialOnline)

    override val isOnline: Boolean get() = onlineFlow.value

    fun setOnline(online: Boolean) {
        onlineFlow.value = online
    }

    override fun observe(): Flow<Boolean> = onlineFlow.asStateFlow()
}

open class FakeMusicRepository : MusicRepository {

    private val playlistsMap = mutableMapOf<String, MutableStateFlow<Playlist?>>()
    private val tracksMap = mutableMapOf<String, MutableStateFlow<List<Track>>>()

    var lastUpdatedKey: String? = null
    var lastUpdatedTitle: String? = null
    var lastUpdatedDescription: String? = null
    var lastUpdatedVisibility: String? = null

    var lastDeletedKey: String? = null

    var lastClonedKey: String? = null

    var lastAddedTrackPlaylistKey: String? = null
    var lastAddedTrack: Track? = null

    var lastRemovedTrackPlaylistKey: String? = null
    var lastRemovedTrack: Track? = null

    var lastReorderedPlaylistKey: String? = null
    var lastReorderedTrackIds: List<String>? = null

    fun setPlaylist(key: String, playlist: Playlist?) {
        val flow = playlistsMap.getOrPut(key) { MutableStateFlow(null) }
        flow.value = playlist
    }

    fun setTracks(key: String, tracks: List<Track>) {
        val flow = tracksMap.getOrPut(key) { MutableStateFlow(emptyList()) }
        flow.value = tracks
    }

    override fun getPlaylist(playlistKey: String): Flow<Playlist?> {
        return playlistsMap.getOrPut(playlistKey) { MutableStateFlow(null) }
    }

    override fun observeTracks(playlistKey: String): Flow<List<Track>> {
        return tracksMap.getOrPut(playlistKey) { MutableStateFlow(emptyList()) }
    }

    override fun observeAllTracks(): Flow<List<Track>> = flowOf(emptyList())

    override fun observePlaylists(): Flow<List<Playlist>> = flowOf(emptyList())

    override fun observeFavorites(): Flow<List<Track>> = flowOf(emptyList())

    override fun observeDownloadedTracks(): Flow<List<Track>> = flowOf(emptyList())

    override suspend fun toggleFavorite(track: Track): Result<Boolean> = Result.success(true)

    override suspend fun createPlaylist(title: String, description: String?, isPublic: Boolean): Result<Playlist> {
        val created = Playlist(
            id = "new-id",
            title = title,
            description = description,
            visibility = if (isPublic) "public" else "private"
        )
        return Result.success(created)
    }

    override suspend fun updatePlaylist(
        playlistKey: String,
        title: String?,
        description: String?,
        visibility: String?
    ): Result<Unit> {
        lastUpdatedKey = playlistKey
        lastUpdatedTitle = title
        lastUpdatedDescription = description
        lastUpdatedVisibility = visibility
        return Result.success(Unit)
    }

    override suspend fun deletePlaylist(playlistKey: String): Result<Unit> {
        lastDeletedKey = playlistKey
        return Result.success(Unit)
    }

    override suspend fun clonePlaylist(
        playlistKey: String,
        title: String?,
        description: String?,
        isPublic: Boolean
    ): Result<Playlist> {
        lastClonedKey = playlistKey
        val cloned = Playlist(
            id = "cloned-id-123",
            title = title ?: "Cloned",
            description = description,
            visibility = if (isPublic) "public" else "private"
        )
        return Result.success(cloned)
    }

    override suspend fun addTrackToPlaylist(playlistKey: String, track: Track): Result<Unit> {
        lastAddedTrackPlaylistKey = playlistKey
        lastAddedTrack = track
        return Result.success(Unit)
    }

    override suspend fun removeTrackFromPlaylist(playlistKey: String, track: Track): Result<Unit> {
        lastRemovedTrackPlaylistKey = playlistKey
        lastRemovedTrack = track
        return Result.success(Unit)
    }

    override suspend fun reorderPlaylistTracks(
        playlistKey: String,
        newTrackIds: List<String>
    ): Result<Unit> {
        lastReorderedPlaylistKey = playlistKey
        lastReorderedTrackIds = newTrackIds
        val currentTracks = tracksMap[playlistKey]?.value ?: emptyList()
        val trackMap = currentTracks.associateBy { it.id }
        val reordered = newTrackIds.mapNotNull { trackMap[it] }
        tracksMap[playlistKey]?.value = reordered
        return Result.success(Unit)
    }

    override suspend fun refreshPlaylists(): Result<Unit> = Result.success(Unit)
}
