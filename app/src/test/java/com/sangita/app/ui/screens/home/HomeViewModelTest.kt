package com.sangita.app.ui.screens.home

import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.remote.api.NetworkClient
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
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userPreferences: UserPreferences = mockk(relaxed = true)
    private val networkClient: NetworkClient = mockk(relaxed = true)
    private val musicRepository: MusicRepository = mockk(relaxed = true)
    private val syncRepository: SyncRepository = mockk(relaxed = true)
    private val playbackController: PlaybackController = mockk(relaxed = true)
    private val downloadManager: SangitaDownloadManager = mockk(relaxed = true)
    private val connectivityObserver: NetworkConnectivityObserver = mockk(relaxed = true)

    private val testTrack1 = Track(id = "track_1", title = "Paranoid Android", artist = "Radiohead", playlist = "OK Computer")
    private val testTrack2 = Track(id = "track_2", title = "Karma Police", artist = "Radiohead", playlist = "OK Computer")
    private val testPlaylist = Playlist(id = "pl_1", title = "Favorites", kind = PlaylistKind.PERSONAL, tracks = listOf(testTrack1, testTrack2))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { userPreferences.getUsername() } returns "TestUser"
        every { userPreferences.getServerUrl() } returns "https://music.sangita.test"
        every { userPreferences.getTheme() } returns UserPreferences.THEME_AMOLED
        every { userPreferences.themeFlow } returns MutableStateFlow(UserPreferences.THEME_AMOLED)
        every { connectivityObserver.observe() } returns flowOf(false)
        every { connectivityObserver.isOnline } returns false
        every { syncRepository.getSyncQueueSize() } returns flowOf(0)
        every { musicRepository.observeAllTracks() } returns flowOf(listOf(testTrack1, testTrack2))
        every { musicRepository.observePlaylists() } returns flowOf(listOf(testPlaylist))
        every { playbackController.playbackState } returns MutableStateFlow(com.sangita.app.domain.model.PlaybackState())
        every { downloadManager.activeDownloads } returns MutableStateFlow(emptyList())
        every { downloadManager.activeDownloadsCount } returns MutableStateFlow(0)
        every { downloadManager.totalSpeedBytesPerSec } returns MutableStateFlow(0L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `formatListeningTime formats seconds properly`() {
        assertEquals("0m", formatListeningTime(0L))
        assertEquals("0m", formatListeningTime(45L))
        assertEquals("2m", formatListeningTime(125L))
        assertEquals("1h 1m", formatListeningTime(3665L))
        assertEquals("2h 30m", formatListeningTime(9000L))
    }

    @Test
    fun `quickShuffle selects and plays tracks from available playlists`() = runTest {
        val viewModel = HomeViewModel(
            userPreferences = userPreferences,
            networkClient = networkClient,
            musicRepository = musicRepository,
            syncRepository = syncRepository,
            playbackController = playbackController,
            downloadManager = downloadManager,
            deviceSyncManager = null,
            connectivityObserver = connectivityObserver
        )

        advanceUntilIdle()

        viewModel.quickShuffle()
        advanceUntilIdle()

        verify {
            playbackController.playTrack(
                match { it.id == "track_1" || it.id == "track_2" },
                any()
            )
        }
    }

    @Test
    fun `settings sheet open and close toggles state`() = runTest {
        val viewModel = HomeViewModel(
            userPreferences = userPreferences,
            networkClient = networkClient,
            musicRepository = musicRepository,
            syncRepository = syncRepository,
            playbackController = playbackController,
            downloadManager = downloadManager,
            deviceSyncManager = null,
            connectivityObserver = connectivityObserver
        )

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showSettingsSheet)
        viewModel.openSettingsSheet()
        assertTrue(viewModel.uiState.value.showSettingsSheet)
        viewModel.closeSettingsSheet()
        assertFalse(viewModel.uiState.value.showSettingsSheet)
    }

    @Test
    fun `setTheme updates preferences and state`() = runTest {
        val viewModel = HomeViewModel(
            userPreferences = userPreferences,
            networkClient = networkClient,
            musicRepository = musicRepository,
            syncRepository = syncRepository,
            playbackController = playbackController,
            downloadManager = downloadManager,
            deviceSyncManager = null,
            connectivityObserver = connectivityObserver
        )

        advanceUntilIdle()

        viewModel.setTheme(UserPreferences.THEME_LIGHT)
        advanceUntilIdle()

        assertEquals(UserPreferences.THEME_LIGHT, viewModel.uiState.value.currentTheme)
        verify { userPreferences.setTheme(UserPreferences.THEME_LIGHT) }
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            userPreferences = userPreferences,
            networkClient = networkClient,
            musicRepository = musicRepository,
            syncRepository = syncRepository,
            playbackController = playbackController,
            downloadManager = downloadManager,
            deviceSyncManager = null,
            connectivityObserver = connectivityObserver
        )
    }

    @Test
    fun `downloadTrack invokes downloadManager downloadTrack`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.downloadTrack(testTrack1)

        verify { downloadManager.downloadTrack(testTrack1) }
    }

    @Test
    fun `deleteDownloadedTrack invokes downloadManager removeDownloadedTrack`() = runTest {
        coEvery { downloadManager.removeDownloadedTrack(testTrack1.id) } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteDownloadedTrack(testTrack1)
        advanceUntilIdle()

        coVerify { downloadManager.removeDownloadedTrack(testTrack1.id) }
    }

    @Test
    fun `toggleFavorite updates repository`() = runTest {
        coEvery { musicRepository.toggleFavorite(testTrack1) } returns Result.success(true)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleFavorite(testTrack1)
        advanceUntilIdle()

        coVerify { musicRepository.toggleFavorite(testTrack1) }
    }

    @Test
    fun `addTrackToPlaylist adds track and returns success`() = runTest {
        coEvery { musicRepository.addTrackToPlaylist(testPlaylist.id, testTrack1) } returns Result.success(Unit)

        val viewModel = createViewModel()
        advanceUntilIdle()

        var callbackResult: Boolean? = null
        viewModel.addTrackToPlaylist(testPlaylist, testTrack1) { success ->
            callbackResult = success
        }
        advanceUntilIdle()

        coVerify { musicRepository.addTrackToPlaylist(testPlaylist.id, testTrack1) }
        assertEquals(true, callbackResult)
    }

    @Test
    fun `createPlaylist creates playlist and triggers callback`() = runTest {
        val createdPlaylist = Playlist(id = "new_pl", title = "New Pl", kind = PlaylistKind.PERSONAL)
        coEvery { musicRepository.createPlaylist(title = "New Pl", description = null, isPublic = false) } returns Result.success(createdPlaylist)

        val viewModel = createViewModel()
        advanceUntilIdle()

        var resultPl: Playlist? = null
        viewModel.createPlaylist(title = "New Pl", description = null, isPublic = false) { pl ->
            resultPl = pl
        }
        advanceUntilIdle()

        coVerify { musicRepository.createPlaylist(title = "New Pl", description = null, isPublic = false) }
        assertEquals("new_pl", resultPl?.id)
    }

    @Test
    fun `playlists and userPlaylists flows expose repository playlists`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf(testPlaylist), viewModel.playlists.value)
        assertEquals(listOf(testPlaylist), viewModel.userPlaylists.value)
    }
}
