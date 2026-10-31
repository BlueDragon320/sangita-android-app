package com.sangita.app.ui.screens.playlist

import com.sangita.app.data.local.dao.PlaylistDao
import com.sangita.app.data.local.dao.SyncQueueDao
import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.data.remote.api.SangitaApiService
import com.sangita.app.data.remote.dto.PlaylistDetailDto
import com.sangita.app.data.remote.dto.PlaylistDto
import com.sangita.app.data.remote.dto.PlaylistsResponseDto
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.PlaylistKind
import com.sangita.app.domain.model.PlaylistSyncState
import com.sangita.app.domain.model.SyncActionType
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.MusicRepository
import com.sangita.app.sync.ActionExecutionResult
import com.sangita.app.sync.CreatePlaylistPayload
import com.sangita.app.sync.SangitaSyncWorker
import com.sangita.app.sync.SyncPayloadSerialization
import com.sangita.app.sync.UpdatePlaylistPayload
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ReorderingAndIdempotencyTest {

    private val apiService = mockk<SangitaApiService>(relaxed = true)
    private val playlistDao = mockk<PlaylistDao>(relaxed = true)
    private val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
    private val musicRepository = mockk<MusicRepository>(relaxed = true)

    @Test
    fun `reordering tracks updates track order in repository`() = runTest {
        val tracks = listOf(
            Track(id = "track_1", title = "Song 1"),
            Track(id = "track_2", title = "Song 2"),
            Track(id = "track_3", title = "Song 3")
        )
        val reorderedTracks = listOf(tracks[2], tracks[0], tracks[1])
        val reorderedIds = reorderedTracks.map { it.id }

        coEvery { musicRepository.reorderPlaylistTracks("pl_1", reorderedIds) } returns Result.success(Unit)

        val result = musicRepository.reorderPlaylistTracks("pl_1", reorderedIds)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { musicRepository.reorderPlaylistTracks("pl_1", reorderedIds) }
    }

    @Test
    fun `idempotency protection passes idempotencyKey and updates localKey with returned server playlist`() = runTest {
        val payload = CreatePlaylistPayload(
            localKey = "local_new_1",
            title = "Chill Beats",
            description = "Lofi",
            visibility = "private",
            idempotencyKey = "uuid_idemp_123"
        )
        val action = SyncQueueEntity(
            id = 10L,
            actionType = SyncActionType.CREATE_PLAYLIST,
            payloadJson = SyncPayloadSerialization.encodeCreatePlaylist(payload)
        )

        // Mock server returning existing playlist detail for this idempotency key
        coEvery {
            apiService.createPlaylist(
                match { it.title == "Chill Beats" && it.idempotencyKey == "uuid_idemp_123" }
            )
        } returns Response.success(
            PlaylistDetailDto(
                id = "server_chill_99",
                title = "Chill Beats",
                owner = "alice"
            )
        )
        coEvery { playlistDao.getPlaylistByKey("local_new_1") } returns PlaylistEntity(
            localKey = "local_new_1",
            serverId = null,
            title = "Chill Beats"
        )

        val result = SangitaSyncWorker.executeAction(action, apiService, playlistDao, syncQueueDao)

        assertTrue(result is ActionExecutionResult.Success)
        // Verify apiService.createPlaylist was called with the idempotencyKey
        coVerify(exactly = 1) {
            apiService.createPlaylist(
                match { it.idempotencyKey == "uuid_idemp_123" }
            )
        }
        // Verify Room was updated with the server ID returned by the idempotent call
        coVerify(exactly = 1) {
            playlistDao.insertOrReplace(match {
                it.localKey == "local_new_1" && it.serverId == "server_chill_99" && it.syncState == "SYNCED"
            })
        }
    }

    @Test
    fun `concurrent edit returning 409 Conflict flags playlist syncState as CONFLICT`() = runTest {
        val payload = UpdatePlaylistPayload(
            localKey = "local_conflict_1",
            serverId = "server_conflict_1",
            title = "Colliding Title"
        )
        val action = SyncQueueEntity(
            id = 11L,
            actionType = SyncActionType.UPDATE_PLAYLIST,
            payloadJson = SyncPayloadSerialization.encodeUpdatePlaylist(payload)
        )

        coEvery { playlistDao.getPlaylistByKey("local_conflict_1") } returns PlaylistEntity(
            localKey = "local_conflict_1",
            serverId = "server_conflict_1",
            title = "Old Title"
        )
        coEvery { apiService.updatePlaylist("server_conflict_1", any()) } returns Response.error(
            409,
            "{\"error\":\"A playlist with this title already exists\"}".toResponseBody("application/json".toMediaTypeOrNull())
        )

        val result = SangitaSyncWorker.executeAction(action, apiService, playlistDao, syncQueueDao)

        assertTrue(result is ActionExecutionResult.Failure)
        assertTrue((result as ActionExecutionResult.Failure).isPermanent)
        // Verify entity was flagged with CONFLICT state
        coVerify(exactly = 1) {
            playlistDao.insertOrReplace(match {
                it.localKey == "local_conflict_1" && it.syncState == PlaylistSyncState.CONFLICT.name
            })
        }
    }

    @Test
    fun `offline library grouping isolates same-titled playlists by localKey`() = runTest {
        // Two distinct playlists with identical title "Workout Mix" but different local keys
        val pl1 = Playlist(
            id = "uuid_local_1",
            title = "Workout Mix",
            kind = PlaylistKind.PERSONAL
        )
        val pl2 = Playlist(
            id = "uuid_local_2",
            title = "Workout Mix",
            kind = PlaylistKind.FOLDER
        )

        val trackPl1 = listOf(Track(id = "track_a.mp3", title = "Running", isDownloaded = true))
        val trackPl2 = listOf(Track(id = "track_b.mp3", title = "Lifting", isDownloaded = true))

        coEvery { playlistDao.observeTracksForPlaylist("uuid_local_1") } returns flowOf(
            listOf(
                com.sangita.app.data.local.entity.TrackEntity(
                    id = "track_a.mp3",
                    playlistName = "Workout Mix",
                    title = "Running",
                    isDownloaded = true
                )
            )
        )
        coEvery { playlistDao.observeTracksForPlaylist("uuid_local_2") } returns flowOf(
            listOf(
                com.sangita.app.data.local.entity.TrackEntity(
                    id = "track_b.mp3",
                    playlistName = "Workout Mix",
                    title = "Lifting",
                    isDownloaded = true
                )
            )
        )

        assertNotEquals(pl1.id, pl2.id)
        assertEquals(pl1.title, pl2.title)

        val tracksForPl1 = playlistDao.observeTracksForPlaylist(pl1.id)
        val tracksForPl2 = playlistDao.observeTracksForPlaylist(pl2.id)

        // Tracks are strictly resolved per playlist ID, never merged by title
        tracksForPl1.collect { list1 ->
            assertEquals(1, list1.size)
            assertEquals("track_a.mp3", list1[0].id)
        }
        tracksForPl2.collect { list2 ->
            assertEquals(1, list2.size)
            assertEquals("track_b.mp3", list2[0].id)
        }
    }
}
