package com.sangita.app.ui.screens.library

import com.sangita.app.data.local.dao.PlaylistDao
import com.sangita.app.data.local.dao.TrackDao
import com.sangita.app.data.local.entity.TrackEntity
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.data.remote.dto.ArtistDto
import com.sangita.app.domain.model.DownloadStatus
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.PlaylistKind
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.MusicRepository
import com.sangita.app.domain.repository.SyncRepository
import com.sangita.app.download.SangitaDownloadManager
import com.sangita.app.player.PlaybackController
import com.sangita.app.sync.NetworkConnectivityObserver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val musicRepository: MusicRepository = mockk(relaxed = true)
    private val syncRepository: SyncRepository = mockk(relaxed = true)
    private val networkClient: NetworkClient = mockk(relaxed = true)
    private val playbackController: PlaybackController = mockk(relaxed = true)
    private val downloadManager: SangitaDownloadManager = mockk(relaxed = true)
    private val connectivityObserver: NetworkConnectivityObserver = mockk(relaxed = true)
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val trackDao: TrackDao = mockk(relaxed = true)
    private lateinit var userPreferences: UserPreferences

    private val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    private val allTracksFlow = MutableStateFlow<List<Track>>(emptyList())
    private val downloadedTracksFlow = MutableStateFlow<List<Track>>(emptyList())
    private val downloadedEntitiesFlow = MutableStateFlow<List<TrackEntity>>(emptyList())
    private val savedTracksFlow = MutableStateFlow<List<Track>>(emptyList())
    private val savedArtistsFlow = MutableStateFlow<List<ArtistDto>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        userPreferences = spyk(UserPreferences(null))
        userPreferences.setUsername("testuser")
        userPreferences.setServerUrl("http://localhost:8080")

        every { connectivityObserver.observe() } returns flowOf(true)
        every { connectivityObserver.isOnline } returns true

        every { syncRepository.getSyncQueueSize() } returns flowOf(0)

        every { musicRepository.observePlaylists() } returns playlistsFlow
        every { musicRepository.observeAllTracks() } returns allTracksFlow
        every { musicRepository.observeDownloadedTracks() } returns downloadedTracksFlow
        every { musicRepository.observeSavedArtists() } returns savedArtistsFlow
        every { musicRepository.observeSavedTracks() } returns savedTracksFlow
        every { trackDao.observeDownloadedTracks() } returns downloadedEntitiesFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LibraryViewModel {
        return LibraryViewModel(
            musicRepository = musicRepository,
            syncRepository = syncRepository,
            networkClient = networkClient,
            userPreferences = userPreferences,
            playbackController = playbackController,
            downloadManager = downloadManager,
            deviceSyncManager = null,
            connectivityObserver = connectivityObserver,
            playlistDao = playlistDao,
            trackDao = trackDao
        )
    }

    @Test
    fun `initial selectedFilter tab defaults to Playlists`() {
        val viewModel = createViewModel()
        assertEquals("Playlists", viewModel.uiState.value.selectedFilter)
    }

    @Test
    fun `selectFilter changes active tab across Playlists, Artists, Songs, Downloads`() {
        val viewModel = createViewModel()

        viewModel.selectFilter("Artists")
        assertEquals("Artists", viewModel.uiState.value.selectedFilter)

        viewModel.selectFilter("Songs")
        assertEquals("Songs", viewModel.uiState.value.selectedFilter)

        viewModel.selectFilter("Downloads")
        assertEquals("Downloads", viewModel.uiState.value.selectedFilter)

        viewModel.selectFilter("Playlists")
        assertEquals("Playlists", viewModel.uiState.value.selectedFilter)
    }

    @Test
    fun `empty personal library results in empty playlists, artists, and songs`() = runTest {
        playlistsFlow.value = emptyList()
        allTracksFlow.value = emptyList()
        savedArtistsFlow.value = emptyList()

        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.libraryPlaylists.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.libraryArtists.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.librarySongs.collect() }
        advanceUntilIdle()

        assertTrue(viewModel.libraryPlaylists.value.isEmpty())
        assertTrue(viewModel.libraryArtists.value.isEmpty())
        assertTrue(viewModel.librarySongs.value.isEmpty())
    }

    @Test
    fun `libraryPlaylists contains user personal and saved playlists`() = runTest {
        val personalPl = Playlist(
            id = "pl-1",
            title = "My Beats",
            owner = "testuser",
            kind = PlaylistKind.PERSONAL
        )
        val savedPl = Playlist(
            id = "pl-2",
            title = "Saved Hits",
            owner = "otheruser",
            isSaved = true
        )
        val otherPl = Playlist(
            id = "pl-3",
            title = "Unsaved Other",
            owner = "stranger",
            isSaved = false,
            kind = PlaylistKind.PUBLIC
        )

        playlistsFlow.value = listOf(personalPl, savedPl, otherPl)

        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.libraryPlaylists.collect() }
        advanceUntilIdle()

        val result = viewModel.libraryPlaylists.value
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "pl-1" })
        assertTrue(result.any { it.id == "pl-2" })
        assertFalse(result.any { it.id == "pl-3" })
    }

    @Test
    fun `downloadedTracks tab displays account-scoped offline downloads`() = runTest {
        val downloadedEntity = TrackEntity(
            id = "offline_song.mp3",
            playlistName = "Rock",
            title = "Offline Song",
            artist = "Pink Floyd",
            isDownloaded = true,
            downloadStatus = DownloadStatus.COMPLETED
        )
        downloadedEntitiesFlow.value = listOf(downloadedEntity)
        downloadedTracksFlow.value = listOf(downloadedEntity.toDomain())

        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.downloadedTracks.collect() }
        advanceUntilIdle()

        val tracks = viewModel.downloadedTracks.value
        assertEquals(1, tracks.size)
        assertEquals("offline_song.mp3", tracks.first().id)
        assertEquals("Offline Song", tracks.first().title)
        assertTrue(tracks.first().isDownloaded)
    }

    @Test
    fun `libraryArtists exposes only explicitly saved artists`() = runTest {
        val savedArtist = ArtistDto(id = "art-1", name = "Daft Punk", trackCount = 10)
        savedArtistsFlow.value = listOf(savedArtist)

        val libraryTrack = Track(
            id = "queen_song.mp3",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            playlist = "Favorites"
        )
        allTracksFlow.value = listOf(libraryTrack)

        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.libraryArtists.collect() }
        advanceUntilIdle()

        val artists = viewModel.libraryArtists.value
        assertEquals(1, artists.size)
        assertTrue(artists.any { it.name.equals("Daft Punk", ignoreCase = true) })
    }

    @Test
    fun `searchQuery filters songs and playlists`() = runTest {
        savedTracksFlow.value = listOf(
            Track(id = "1", title = "Bohemian Rhapsody", artist = "Queen", playlist = "Rock"),
            Track(id = "2", title = "Another One", artist = "Queen", playlist = "Rock"),
            Track(id = "3", title = "Billie Jean", artist = "Michael Jackson", playlist = "Pop")
        )

        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.librarySongs.collect() }
        advanceUntilIdle()

        assertEquals(3, viewModel.librarySongs.value.size)

        viewModel.onSearchQueryChange("Billie")
        advanceUntilIdle()

        val filtered = viewModel.librarySongs.value
        assertEquals(1, filtered.size)
        assertEquals("Billie Jean", filtered.first().title)
    }

    @Test
    fun `downloadTrack invokes downloadManager downloadTrack`() = runTest {
        val viewModel = createViewModel()
        val track = Track(id = "track_100", title = "Test Song", artist = "Artist")

        viewModel.downloadTrack(track)

        verify { downloadManager.downloadTrack(track) }
    }

    @Test
    fun `deleteDownloadedTrack invokes downloadManager removeDownloadedTrack`() = runTest {
        val track = Track(id = "track_100", title = "Test Song", artist = "Artist")
        coEvery { downloadManager.removeDownloadedTrack(track.id) } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteDownloadedTrack(track)
        advanceUntilIdle()

        coVerify { downloadManager.removeDownloadedTrack(track.id) }
    }

    @Test
    fun `toggleFavorite updates repository`() = runTest {
        val track = Track(id = "track_100", title = "Test Song", artist = "Artist")
        coEvery { musicRepository.toggleFavorite(track) } returns Result.success(true)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleFavorite(track)
        advanceUntilIdle()

        coVerify { musicRepository.toggleFavorite(track) }
    }

    @Test
    fun `addTrackToPlaylist delegates to repository and invokes callback`() = runTest {
        val playlist = Playlist(id = "pl-1", name = "My Playlist")
        val track = Track(id = "tr-1", title = "Song", artist = "Artist")
        coEvery { musicRepository.addTrackToPlaylist("pl-1", track) } returns Result.success(Unit)

        val viewModel = createViewModel()
        var callbackSuccess: Boolean? = null
        viewModel.addTrackToPlaylist(playlist, track) { success ->
            callbackSuccess = success
        }
        advanceUntilIdle()

        coVerify { musicRepository.addTrackToPlaylist("pl-1", track) }
        assertEquals(true, callbackSuccess)
    }
}
