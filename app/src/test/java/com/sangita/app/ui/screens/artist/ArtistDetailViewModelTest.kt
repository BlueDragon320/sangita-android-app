package com.sangita.app.ui.screens.artist

import com.sangita.app.data.remote.dto.ArtistDetailDto
import com.sangita.app.data.remote.dto.ArtistTrackDto
import com.sangita.app.domain.model.PlaybackState
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.MusicRepository
import com.sangita.app.player.PlaybackController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
class ArtistDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val musicRepository: MusicRepository = mockk(relaxed = true)
    private val playbackController: PlaybackController = mockk(relaxed = true)

    private val testTracks = listOf(
        ArtistTrackDto(path = "Radiohead/OK Computer/01 Airbag.mp3", title = "Airbag", artist = "Radiohead", duration = 284.0),
        ArtistTrackDto(path = "Radiohead/OK Computer/02 Paranoid Android.mp3", title = "Paranoid Android", artist = "Radiohead", duration = 383.0),
        ArtistTrackDto(path = "Radiohead/OK Computer/03 Subterranean Homesick Alien.mp3", title = "Subterranean Homesick Alien", artist = "Radiohead", duration = 267.0)
    )

    private val testArtistDetail = ArtistDetailDto(
        id = "artist_radiohead",
        name = "Radiohead",
        bio = "English rock band formed in Abingdon, Oxfordshire, in 1985.",
        imageUrl = "/api/cover/radiohead.jpg",
        trackCount = 3,
        tracks = testTracks
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { playbackController.playbackState } returns MutableStateFlow(PlaybackState())
        every { musicRepository.observePlaylists() } returns flowOf(emptyList())
        coEvery { musicRepository.fetchArtistDetail("artist_radiohead") } returns Result.success(testArtistDetail)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state triggers load and updates artist detail and tracks successfully`() = runTest {
        val viewModel = ArtistDetailViewModel(
            artistId = "artist_radiohead",
            musicRepository = musicRepository,
            playbackController = playbackController
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertNotNull(state.artist)
        assertEquals("Radiohead", state.artist?.name)
        assertEquals(3, state.artist?.tracks?.size)
        assertEquals("Airbag", state.artist?.tracks?.get(0)?.title)
    }

    @Test
    fun `error loading artist sets errorMessage`() = runTest {
        coEvery { musicRepository.fetchArtistDetail("artist_unknown") } returns Result.failure(IOException("Artist not found"))
        coEvery { musicRepository.getArtistDetail("artist_unknown") } returns Result.failure(IOException("Artist not found"))

        val viewModel = ArtistDetailViewModel(
            artistId = "artist_unknown",
            musicRepository = musicRepository,
            playbackController = playbackController
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.artist)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Artist not found"))
    }

    @Test
    fun `playTrack plays selected track with all artist tracks as queue`() = runTest {
        val viewModel = ArtistDetailViewModel(
            artistId = "artist_radiohead",
            musicRepository = musicRepository,
            playbackController = playbackController
        )
        advanceUntilIdle()

        val selectedTrack = testTracks[1] // Paranoid Android
        viewModel.playTrack(selectedTrack)
        advanceUntilIdle()

        val trackSlot = slot<Track>()
        val queueSlot = slot<List<Track>>()
        verify {
            playbackController.playTrack(capture(trackSlot), capture(queueSlot))
        }

        assertEquals("Paranoid Android", trackSlot.captured.title)
        assertEquals(3, queueSlot.captured.size)
        assertEquals("Airbag", queueSlot.captured[0].title)
        assertEquals("Paranoid Android", queueSlot.captured[1].title)
        assertEquals("Subterranean Homesick Alien", queueSlot.captured[2].title)
    }

    @Test
    fun `playAll plays first track with all tracks in queue`() = runTest {
        val viewModel = ArtistDetailViewModel(
            artistId = "artist_radiohead",
            musicRepository = musicRepository,
            playbackController = playbackController
        )
        advanceUntilIdle()

        viewModel.playAll()
        advanceUntilIdle()

        val trackSlot = slot<Track>()
        val queueSlot = slot<List<Track>>()
        verify {
            playbackController.playTrack(capture(trackSlot), capture(queueSlot))
        }

        assertEquals("Airbag", trackSlot.captured.title)
        assertEquals(3, queueSlot.captured.size)
    }

    @Test
    fun `shuffle plays a track with full shuffled queue`() = runTest {
        val viewModel = ArtistDetailViewModel(
            artistId = "artist_radiohead",
            musicRepository = musicRepository,
            playbackController = playbackController
        )
        advanceUntilIdle()

        viewModel.shuffle()
        advanceUntilIdle()

        val trackSlot = slot<Track>()
        val queueSlot = slot<List<Track>>()
        verify {
            playbackController.playTrack(capture(trackSlot), capture(queueSlot))
        }

        assertNotNull(trackSlot.captured)
        assertEquals(3, queueSlot.captured.size)
        // Ensure all original tracks are in the shuffled queue
        val titles = queueSlot.captured.map { it.title }.toSet()
        assertEquals(setOf("Airbag", "Paranoid Android", "Subterranean Homesick Alien"), titles)
    }

    @Test
    fun `addTrackToPlaylist delegates to musicRepository and reports completion`() = runTest {
        coEvery { musicRepository.addTrackToPlaylist("pl_test", any()) } returns Result.success(Unit)

        val viewModel = ArtistDetailViewModel(
            artistId = "artist_radiohead",
            musicRepository = musicRepository,
            playbackController = playbackController
        )
        advanceUntilIdle()

        var callbackSuccess: Boolean? = null
        viewModel.addTrackToPlaylist("pl_test", testTracks[0]) { success ->
            callbackSuccess = success
        }
        advanceUntilIdle()

        coVerify {
            musicRepository.addTrackToPlaylist("pl_test", match { it.id == testTracks[0].path })
        }
        assertEquals(true, callbackSuccess)
    }

    @Test
    fun `alternative constructor order initializes correctly`() = runTest {
        val viewModel = ArtistDetailViewModel(
            musicRepository = musicRepository,
            playbackController = playbackController,
            artistId = "artist_radiohead"
        )
        advanceUntilIdle()

        assertEquals("artist_radiohead", viewModel.artistId)
        assertNotNull(viewModel.uiState.value.artist)
    }
}
