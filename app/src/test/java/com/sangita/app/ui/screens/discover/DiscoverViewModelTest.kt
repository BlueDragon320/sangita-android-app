package com.sangita.app.ui.screens.discover

import com.sangita.app.data.remote.dto.ArtistDto
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.PlaylistKind
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.MusicRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val musicRepository: MusicRepository = mockk(relaxed = true)
    private val playbackController: PlaybackController = mockk(relaxed = true)
    private val connectivityObserver: NetworkConnectivityObserver = mockk(relaxed = true)

    private val testArtists = listOf(
        ArtistDto(id = "artist_1", name = "Radiohead", trackCount = 12),
        ArtistDto(id = "artist_2", name = "Pink Floyd", trackCount = 20)
    )

    private val testFolderPlaylists = listOf(
        Playlist(
            id = "folder_1",
            title = "Rock Classics",
            kind = PlaylistKind.FOLDER,
            trackCount = 15
        )
    )

    private val testCommunityPlaylists = listOf(
        Playlist(
            id = "comm_1",
            title = "Chill Evening",
            kind = PlaylistKind.PUBLIC,
            owner = "alice",
            visibility = "public",
            trackCount = 8
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { connectivityObserver.isOnline } returns true
        every { playbackController.playbackState } returns MutableStateFlow(com.sangita.app.domain.model.PlaybackState())

        coEvery { musicRepository.getArtistsList() } returns Result.success(testArtists)
        coEvery { musicRepository.getFolderPlaylists() } returns Result.success(testFolderPlaylists)
        coEvery { musicRepository.getCommunityPlaylists() } returns Result.success(testCommunityPlaylists)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state triggers refresh and loads artists and playlists successfully`() = runTest {
        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertFalse(state.isOffline)
        assertEquals(2, state.artists.size)
        assertEquals("Radiohead", state.artists[0].name)
        assertEquals(1, state.folderPlaylists.size)
        assertEquals("Rock Classics", state.folderPlaylists[0].title)
        assertEquals(1, state.communityPlaylists.size)
        assertEquals("Chill Evening", state.communityPlaylists[0].title)
    }

    @Test
    fun `onSearchQueryChange filters artists by name`() = runTest {
        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        viewModel.onSearchQueryChange("Radio")

        val state = viewModel.uiState.value
        assertEquals("Radio", state.searchQuery)
        assertEquals(1, state.artists.size)
        assertEquals("Radiohead", state.artists[0].name)
    }

    @Test
    fun `onSearchQueryChange filters folder playlists and community playlists`() = runTest {
        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        // Filter for "Classics" matching folder playlist
        viewModel.onSearchQueryChange("Classics")
        assertEquals(1, viewModel.uiState.value.folderPlaylists.size)
        assertEquals(0, viewModel.uiState.value.communityPlaylists.size)

        // Filter for "alice" matching community playlist owner
        viewModel.onSearchQueryChange("alice")
        assertEquals(0, viewModel.uiState.value.folderPlaylists.size)
        assertEquals(1, viewModel.uiState.value.communityPlaylists.size)
    }

    @Test
    fun `search with no matches produces empty lists`() = runTest {
        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        viewModel.onSearchQueryChange("nonexistent_query_xyz")

        val state = viewModel.uiState.value
        assertTrue(state.artists.isEmpty())
        assertTrue(state.folderPlaylists.isEmpty())
        assertTrue(state.communityPlaylists.isEmpty())
    }

    @Test
    fun `refresh handles repository errors when all fail`() = runTest {
        coEvery { musicRepository.getArtistsList() } returns Result.failure(IOException("Server error"))
        coEvery { musicRepository.getArtists() } returns flowOf(emptyList())
        coEvery { musicRepository.getFolderPlaylists() } returns Result.failure(IOException("Server error"))
        coEvery { musicRepository.getCommunityPlaylists() } returns Result.failure(IOException("Server error"))
        coEvery { musicRepository.observePlaylists() } returns flowOf(emptyList())

        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertTrue(state.artists.isEmpty())
        assertTrue(state.folderPlaylists.isEmpty())
        assertTrue(state.communityPlaylists.isEmpty())
    }

    @Test
    fun `playPlaylist calls playbackController with tracks in playlist`() = runTest {
        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        val testTracks = listOf(
            Track(id = "track_1", title = "Karma Police", artist = "Radiohead", playlist = "Rock Classics")
        )
        val playlistWithTracks = testFolderPlaylists[0].copy(tracks = testTracks)

        viewModel.playPlaylist(playlistWithTracks)
        advanceUntilIdle()

        verify {
            playbackController.playTrack(testTracks[0], testTracks)
        }
    }

    @Test
    fun `playPlaylist fetches tracks from repository if playlist model tracks are empty`() = runTest {
        val testTracks = listOf(
            Track(id = "track_2", title = "Creep", artist = "Radiohead", playlist = "folder_1")
        )
        every { musicRepository.observeTracks("folder_1") } returns flowOf(testTracks)

        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        val emptyTracksPlaylist = testFolderPlaylists[0].copy(tracks = emptyList())
        viewModel.playPlaylist(emptyTracksPlaylist)
        advanceUntilIdle()

        verify {
            playbackController.playTrack(testTracks[0], testTracks)
        }
    }

    @Test
    fun `isOffline is set when connectivity observer reports offline and content is empty`() = runTest {
        every { connectivityObserver.isOnline } returns false
        coEvery { musicRepository.getArtistsList() } returns Result.failure(IOException("No network"))
        coEvery { musicRepository.getArtists() } returns flowOf(emptyList())
        coEvery { musicRepository.getFolderPlaylists() } returns Result.failure(IOException("No network"))
        coEvery { musicRepository.getCommunityPlaylists() } returns Result.failure(IOException("No network"))
        coEvery { musicRepository.observePlaylists() } returns flowOf(emptyList())

        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isOffline)
    }
}
