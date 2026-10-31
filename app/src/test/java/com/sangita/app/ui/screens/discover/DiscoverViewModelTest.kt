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

    private val testTracks = listOf(
        Track(
            id = "track_1",
            title = "Karma Police",
            artist = "Radiohead",
            album = "OK Computer",
            playlist = "Rock Classics"
        ),
        Track(
            id = "track_2",
            title = "Comfortably Numb",
            artist = "Pink Floyd",
            album = "The Wall",
            playlist = "Rock Classics"
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
        every { musicRepository.observeAllTracks() } returns flowOf(testTracks)
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

    @Test
    fun `onSearchQueryChange with exact substring matches song title and populates tracks`() = runTest {
        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        viewModel.onSearchQueryChange("Karma")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Karma", state.searchQuery)
        assertEquals(1, state.tracks.size)
        assertEquals("Karma Police", state.tracks[0].title)
    }

    @Test
    fun `onSearchQueryChange with sequential subsequence matches song and populates tracks`() = runTest {
        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        // "krma" is a subsequence of "Karma"
        viewModel.onSearchQueryChange("krma")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.tracks.size)
        assertEquals("Karma Police", state.tracks[0].title)
    }

    @Test
    fun `onSearchQueryChange with individual letters multiset matches song and populates tracks`() = runTest {
        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        // "rmak" contains characters of "karma" scrambled
        viewModel.onSearchQueryChange("rmak")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.tracks.size)
        assertEquals("Karma Police", state.tracks[0].title)
    }

    @Test
    fun `onSearchQueryChange with token matching matches across multiple fields`() = runTest {
        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        // "radio police": "radio" matches artist "Radiohead", "police" matches title "Karma Police"
        viewModel.onSearchQueryChange("radio police")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.tracks.size)
        assertEquals("Karma Police", state.tracks[0].title)

        // If one token does not match any field, whole query fails
        viewModel.onSearchQueryChange("radio nonexistent")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.tracks.isEmpty())
    }

    @Test
    fun `empty search state condition correctly respects song results`() = runTest {
        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        // "Numb" only matches the song "Comfortably Numb", not any artist or playlist
        viewModel.onSearchQueryChange("Numb")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.artists.isEmpty())
        assertTrue(state.folderPlaylists.isEmpty())
        assertTrue(state.communityPlaylists.isEmpty())
        assertEquals(1, state.tracks.size)
        assertEquals("Comfortably Numb", state.tracks[0].title)

        // The empty search state condition in DiscoverScreen evaluates to false
        val isEmptySearchState = state.searchQuery.isNotBlank() &&
            state.artists.isEmpty() &&
            state.folderPlaylists.isEmpty() &&
            state.communityPlaylists.isEmpty() &&
            state.tracks.isEmpty()
        assertFalse(isEmptySearchState)
    }

    @Test
    fun `blank search query resets tracks to empty`() = runTest {
        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        viewModel.onSearchQueryChange("Karma")
        assertEquals(1, viewModel.uiState.value.tracks.size)

        viewModel.onSearchQueryChange("")
        assertTrue(viewModel.uiState.value.tracks.isEmpty())
    }

    @Test
    fun `playTrack delegates to playbackController with queue`() = runTest {
        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        viewModel.onSearchQueryChange("Karma")
        advanceUntilIdle()

        val track = viewModel.uiState.value.tracks.first()
        viewModel.playTrack(track)
        advanceUntilIdle()

        verify {
            playbackController.playTrack(track, listOf(track))
        }
    }

    @Test
    fun `matchIndividualLetters behaves as expected across all matching strategies`() {
        // Blank query
        assertTrue(matchIndividualLetters("", "Radiohead"))
        assertTrue(matchIndividualLetters("   ", "Radiohead"))

        // Null/empty fields
        assertFalse(matchIndividualLetters("query", null, null))

        // a) Exact substring match (case-insensitive)
        assertTrue(matchIndividualLetters("Radio", "Radiohead"))
        assertTrue(matchIndividualLetters("head", "Radiohead"))
        assertTrue(matchIndividualLetters("dio", "Radiohead"))

        // b) Sequential subsequence match
        assertTrue(matchIndividualLetters("rdoh", "Radiohead"))
        assertTrue(matchIndividualLetters("krma", "Karma Police"))

        // c) Individual letter multiset match
        assertTrue(matchIndividualLetters("headradio", "Radiohead"))
        assertTrue(matchIndividualLetters("plkrma", "Karma Police"))
        assertTrue(matchIndividualLetters("aa", "Radiohead")) // "Radiohead" has two 'a's
        assertFalse(matchIndividualLetters("aaa", "Radiohead")) // "Radiohead" does NOT have three 'a's

        // d) Token matching
        assertTrue(matchIndividualLetters("radio police", "Karma Police", "Radiohead"))
        assertFalse(matchIndividualLetters("radio police queen", "Karma Police", "Radiohead"))
        // Token matching with separator/punctuation
        assertTrue(matchIndividualLetters("Radiohead - Karma Police", "Karma Police", "Radiohead"))
        assertTrue(matchIndividualLetters("Radiohead -", "Radiohead"))

        // Non-matching query
        assertFalse(matchIndividualLetters("xyz123", "Karma Police", "Radiohead"))
    }

    @Test
    fun `search query matching artist and playlist populates both and songs underneath`() = runTest {
        val viewModel = DiscoverViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            connectivityObserver = connectivityObserver
        )
        advanceUntilIdle()

        // "Radio" matches artist "Radiohead" and track "Karma Police" by "Radiohead"
        viewModel.onSearchQueryChange("Radio")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Artists populated first
        assertEquals(1, state.artists.size)
        assertEquals("Radiohead", state.artists[0].name)

        // Tracks populated underneath
        assertEquals(1, state.tracks.size)
        assertEquals("Karma Police", state.tracks[0].title)

        // "Rock" matches folder playlist "Rock Classics" and tracks with playlist "Rock Classics" or albums
        viewModel.onSearchQueryChange("Rock")
        advanceUntilIdle()

        val rockState = viewModel.uiState.value
        assertEquals(1, rockState.folderPlaylists.size)
        assertEquals("Rock Classics", rockState.folderPlaylists[0].title)
    }
}
