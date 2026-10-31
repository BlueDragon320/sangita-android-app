package com.sangita.app.sync

import com.sangita.app.data.local.dao.PlaylistDao
import com.sangita.app.data.local.dao.SyncQueueDao
import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.data.remote.api.SangitaApiService
import com.sangita.app.data.remote.dto.LegacyPlaylistMutationDto
import com.sangita.app.domain.model.SyncActionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class OlderServerLegacyFallbackTest {

    private val apiService = mockk<SangitaApiService>(relaxed = true)
    private val playlistDao = mockk<PlaylistDao>(relaxed = true)
    private val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)

    private val notFoundResponse = Response.error<Any>(
        404,
        "{\"error\":\"Not Found\"}".toResponseBody("application/json".toMediaTypeOrNull())
    )

    @Before
    fun setUp() {
        coEvery { apiService.getPlaylists(discover = any()) } returns Response.success(null)
    }

    @Test
    fun `create playlist falls back to legacy endpoint when V2 endpoint returns 404`() = runTest {
        val payload = CreatePlaylistPayload(
            localKey = "local_123",
            title = "Oldies Classics",
            description = "Songs from 70s",
            visibility = "public"
        )
        val action = SyncQueueEntity(
            id = 1L,
            actionType = SyncActionType.CREATE_PLAYLIST,
            payloadJson = SyncPayloadSerialization.encodeCreatePlaylist(payload)
        )

        coEvery { apiService.createPlaylist(any()) } returns Response.error(
            404,
            "{\"error\":\"Not Found\"}".toResponseBody("application/json".toMediaTypeOrNull())
        )
        coEvery { apiService.createLegacyPlaylist(any()) } returns Response.success(
            LegacyPlaylistMutationDto(status = "created", id = "legacy_server_123", name = "Oldies Classics")
        )

        val result = SangitaSyncWorker.executeAction(action, apiService, playlistDao, syncQueueDao)

        assertTrue(result is ActionExecutionResult.Success)
        coVerify(exactly = 1) { apiService.createPlaylist(any()) }
        coVerify(exactly = 1) {
            apiService.createLegacyPlaylist(match {
                it["name"] == "Oldies Classics" && it["visibility"] == "public" && it["description"] == "Songs from 70s"
            })
        }
    }

    @Test
    fun `add track falls back to legacy endpoint when V2 endpoint returns 404`() = runTest {
        val payload = AddTrackPayload(
            localKey = "local_123",
            serverId = "server_123",
            trackPath = "Rock/Song.mp3"
        )
        val action = SyncQueueEntity(
            id = 2L,
            actionType = SyncActionType.ADD_TRACK,
            payloadJson = SyncPayloadSerialization.encodeAddTrack(payload)
        )

        coEvery { playlistDao.getPlaylistByKey("local_123") } returns PlaylistEntity(
            localKey = "local_123",
            serverId = "server_123",
            title = "My Rock Playlist"
        )
        coEvery { apiService.addTrackToPlaylist("server_123", any()) } returns Response.error(
            404,
            "{\"error\":\"Not Found\"}".toResponseBody("application/json".toMediaTypeOrNull())
        )
        coEvery { apiService.addLegacyTrack("My Rock Playlist", any()) } returns Response.success(
            LegacyPlaylistMutationDto(status = "added")
        )

        val result = SangitaSyncWorker.executeAction(action, apiService, playlistDao, syncQueueDao)

        assertTrue(result is ActionExecutionResult.Success)
        coVerify(exactly = 1) { apiService.addTrackToPlaylist("server_123", any()) }
        coVerify(exactly = 1) {
            apiService.addLegacyTrack("My Rock Playlist", match { it["track_path"] == "Rock/Song.mp3" })
        }
    }

    @Test
    fun `remove track falls back to legacy endpoint when V2 endpoint returns 404`() = runTest {
        val payload = RemoveTrackPayload(
            localKey = "local_123",
            serverId = "server_123",
            trackPath = "Rock/Song.mp3"
        )
        val action = SyncQueueEntity(
            id = 3L,
            actionType = SyncActionType.REMOVE_TRACK,
            payloadJson = SyncPayloadSerialization.encodeRemoveTrack(payload)
        )

        coEvery { playlistDao.getPlaylistByKey("local_123") } returns PlaylistEntity(
            localKey = "local_123",
            serverId = "server_123",
            title = "My Rock Playlist"
        )
        coEvery { apiService.removeTrackFromPlaylist("server_123", "Rock/Song.mp3") } returns Response.error(
            404,
            "{\"error\":\"Not Found\"}".toResponseBody("application/json".toMediaTypeOrNull())
        )
        coEvery { apiService.removeLegacyTrack("My Rock Playlist", "Rock/Song.mp3") } returns Response.success(
            LegacyPlaylistMutationDto(status = "removed")
        )

        val result = SangitaSyncWorker.executeAction(action, apiService, playlistDao, syncQueueDao)

        assertTrue(result is ActionExecutionResult.Success)
        coVerify(exactly = 1) { apiService.removeTrackFromPlaylist("server_123", "Rock/Song.mp3") }
        coVerify(exactly = 1) { apiService.removeLegacyTrack("My Rock Playlist", "Rock/Song.mp3") }
    }

    @Test
    fun `delete playlist falls back to legacy endpoint when V2 endpoint returns 404`() = runTest {
        val payload = DeletePlaylistPayload(
            localKey = "local_123",
            serverId = "server_123"
        )
        val action = SyncQueueEntity(
            id = 4L,
            actionType = SyncActionType.DELETE_PLAYLIST,
            payloadJson = SyncPayloadSerialization.encodeDeletePlaylist(payload)
        )

        coEvery { playlistDao.getPlaylistByKey("local_123") } returns PlaylistEntity(
            localKey = "local_123",
            serverId = "server_123",
            title = "Delete Me"
        )
        coEvery { apiService.deletePlaylist("server_123") } returns Response.error(
            404,
            "{\"error\":\"Not Found\"}".toResponseBody("application/json".toMediaTypeOrNull())
        )
        coEvery { apiService.deleteLegacyPlaylist("Delete Me") } returns Response.success(
            LegacyPlaylistMutationDto(status = "deleted")
        )

        val result = SangitaSyncWorker.executeAction(action, apiService, playlistDao, syncQueueDao)

        assertTrue(result is ActionExecutionResult.Success)
        coVerify(exactly = 1) { apiService.deletePlaylist("server_123") }
        coVerify(exactly = 1) { apiService.deleteLegacyPlaylist("Delete Me") }
        coVerify(exactly = 1) { playlistDao.deletePlaylistByKey("local_123") }
    }

    @Test
    fun `update playlist falls back to legacy endpoint when V2 endpoint returns 404`() = runTest {
        val payload = UpdatePlaylistPayload(
            localKey = "local_123",
            serverId = "server_123",
            title = "Renamed Playlist",
            description = "Updated description"
        )
        val action = SyncQueueEntity(
            id = 5L,
            actionType = SyncActionType.UPDATE_PLAYLIST,
            payloadJson = SyncPayloadSerialization.encodeUpdatePlaylist(payload)
        )

        coEvery { playlistDao.getPlaylistByKey("local_123") } returns PlaylistEntity(
            localKey = "local_123",
            serverId = "server_123",
            title = "Original Playlist"
        )
        coEvery { apiService.updatePlaylist("server_123", any()) } returns Response.error(
            404,
            "{\"error\":\"Not Found\"}".toResponseBody("application/json".toMediaTypeOrNull())
        )
        coEvery { apiService.updateLegacyPlaylist("Original Playlist", any()) } returns Response.success(
            LegacyPlaylistMutationDto(status = "updated")
        )

        val result = SangitaSyncWorker.executeAction(action, apiService, playlistDao, syncQueueDao)

        assertTrue(result is ActionExecutionResult.Success)
        coVerify(exactly = 1) { apiService.updatePlaylist("server_123", any()) }
        coVerify(exactly = 1) {
            apiService.updateLegacyPlaylist("Original Playlist", match {
                it["new_name"] == "Renamed Playlist" && it["description"] == "Updated description"
            })
        }
    }
}
