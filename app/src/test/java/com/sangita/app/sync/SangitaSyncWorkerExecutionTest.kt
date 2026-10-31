package com.sangita.app.sync

import com.sangita.app.data.local.dao.PlaylistDao
import com.sangita.app.data.local.dao.SyncQueueDao
import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.data.local.entity.PlaylistTrackCrossRef
import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.data.local.entity.TrackEntity
import com.sangita.app.data.remote.api.SangitaApiService
import com.sangita.app.data.remote.dto.AddArtistCollectionRequest
import com.sangita.app.data.remote.dto.AddTrackRequest
import com.sangita.app.data.remote.dto.AddTrackResponseDto
import com.sangita.app.data.remote.dto.ArtistAddCollectionResponseDto
import com.sangita.app.data.remote.dto.ArtistDetailDto
import com.sangita.app.data.remote.dto.ArtistsResponseDto
import com.sangita.app.data.remote.dto.ClonePlaylistRequest
import com.sangita.app.data.remote.dto.CreatePlaylistRequest
import com.sangita.app.data.remote.dto.CreateUserPlaylistRequest
import com.sangita.app.data.remote.dto.DataUsageResponse
import com.sangita.app.data.remote.dto.DeletePlaylistResponseDto
import com.sangita.app.data.remote.dto.LegacyPlaylistMutationDto
import com.sangita.app.data.remote.dto.LoginRequest
import com.sangita.app.data.remote.dto.LoginResponse
import com.sangita.app.data.remote.dto.LyricsResponse
import com.sangita.app.data.remote.dto.MeResponse
import com.sangita.app.data.remote.dto.PlaylistDetailDto
import com.sangita.app.data.remote.dto.PlaylistDto
import com.sangita.app.data.remote.dto.PlaylistTrackActionRequest
import com.sangita.app.data.remote.dto.PlaylistsResponseDto
import com.sangita.app.data.remote.dto.RecordDataUsageRequest
import com.sangita.app.data.remote.dto.RemoveTrackResponseDto
import com.sangita.app.data.remote.dto.SetFavoriteRequest
import com.sangita.app.data.remote.dto.SourcesResponseDto
import com.sangita.app.data.remote.dto.StatsPingRequest
import com.sangita.app.data.remote.dto.ToggleFavoriteRequest
import com.sangita.app.data.remote.dto.ToggleFavoriteResponse
import com.sangita.app.data.remote.dto.TrackMetadataDto
import com.sangita.app.data.remote.dto.UpdatePlaylistRequest
import com.sangita.app.data.remote.dto.UserPlaylistsResponse
import com.sangita.app.data.remote.dto.UserStatsResponse
import com.sangita.app.domain.model.SyncActionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SangitaSyncWorkerExecutionTest {

    private class FakeSyncQueueDao : SyncQueueDao {
        val actions = mutableListOf<SyncQueueEntity>()
        val retryCounts = mutableMapOf<Long, Int>()

        override fun observePendingActions(accountKey: String): Flow<List<SyncQueueEntity>> =
            flowOf(actions.filter { it.accountKey == accountKey })
        override fun observePendingActions(): Flow<List<SyncQueueEntity>> = flowOf(actions)
        override suspend fun getPendingActions(accountKey: String): List<SyncQueueEntity> =
            actions.filter { it.accountKey == accountKey }
        override suspend fun getPendingActions(): List<SyncQueueEntity> = actions.toList()
        override fun observeQueueSize(accountKey: String): Flow<Int> =
            flowOf(actions.count { it.accountKey == accountKey })
        override fun observeQueueSize(): Flow<Int> = flowOf(actions.size)
        override suspend fun getQueueSize(accountKey: String): Int =
            actions.count { it.accountKey == accountKey }
        override suspend fun getQueueSize(): Int = actions.size
        override suspend fun getActionCount(accountKey: String): Int =
            actions.count { it.accountKey == accountKey }
        override suspend fun getActionCount(): Int = actions.size
        override fun observeFailedActions(accountKey: String): Flow<List<SyncQueueEntity>> =
            flowOf(actions.filter { it.is_failed && it.accountKey == accountKey })
        override fun observeFailedActions(): Flow<List<SyncQueueEntity>> =
            flowOf(actions.filter { it.is_failed })
        override suspend fun retryFailedAction(actionId: Long) {
            val idx = actions.indexOfFirst { it.id == actionId }
            if (idx != -1) actions[idx] = actions[idx].copy(is_failed = false, failure_reason = null, failed_at = null, retryCount = 0)
        }
        override suspend fun dismissFailedAction(actionId: Long) {
            actions.removeAll { it.id == actionId }
        }
        override suspend fun markActionFailed(actionId: Long, failureReason: String?, failedAt: Long) {
            val idx = actions.indexOfFirst { it.id == actionId }
            if (idx != -1) actions[idx] = actions[idx].copy(is_failed = true, failure_reason = failureReason, failed_at = failedAt)
        }
        override suspend fun enqueueAction(action: SyncQueueEntity): Long {
            val id = if (action.id == 0L) (actions.size + 1).toLong() else action.id
            val entity = action.copy(id = id)
            actions.add(entity)
            return id
        }
        override suspend fun deleteAction(actionId: Long) {
            actions.removeAll { it.id == actionId }
        }
        override suspend fun incrementRetry(actionId: Long, error: String?) {
            retryCounts[actionId] = (retryCounts[actionId] ?: 0) + 1
        }
        override suspend fun clearAll(accountKey: String) {
            actions.removeAll { it.accountKey == accountKey }
        }
        override suspend fun clearAll() {
            actions.clear()
        }
        override suspend fun getActionById(actionId: Long): SyncQueueEntity? =
            actions.firstOrNull { it.id == actionId }
        override suspend fun updateAction(action: SyncQueueEntity) {
            val idx = actions.indexOfFirst { it.id == action.id }
            if (idx != -1) actions[idx] = action
        }
        override suspend fun updatePayload(actionId: Long, payloadJson: String) {
            val idx = actions.indexOfFirst { it.id == actionId }
            if (idx != -1) actions[idx] = actions[idx].copy(payloadJson = payloadJson)
        }
        override suspend fun deleteActionsByLocalKey(accountKey: String, localKey: String) {
            actions.removeAll { it.accountKey == accountKey && it.payloadJson.contains(localKey) }
        }
        override suspend fun deleteActionsByLocalKey(localKey: String) {
            actions.removeAll { it.payloadJson.contains(localKey) }
        }
        override suspend fun getAllActionsForAccount(accountKey: String): List<SyncQueueEntity> =
            actions.filter { it.accountKey == accountKey }
        override suspend fun migrateActionsToAccount(localKey: String, targetAccountKey: String) {
            actions.replaceAll { if (it.accountKey == "default_account" && it.payloadJson.contains(localKey)) it.copy(accountKey = targetAccountKey) else it }
        }
    }

    private class FakePlaylistDao : PlaylistDao {
        val playlists = mutableMapOf<String, PlaylistEntity>()
        val clearedPlaylists = mutableListOf<String>()

        override fun observeRecoverableLegacyPlaylists(): Flow<List<PlaylistEntity>> =
            flowOf(playlists.values.filter { it.accountKey == "default_account" && it.serverId.isNullOrBlank() && it.kind != "FAVORITES" })
        override suspend fun getRecoverableLegacyPlaylists(): List<PlaylistEntity> =
            playlists.values.filter { it.accountKey == "default_account" && it.serverId.isNullOrBlank() && it.kind != "FAVORITES" }

        override fun observeAllPlaylists(accountKey: String): Flow<List<PlaylistEntity>> = flowOf(playlists.values.toList())
        override suspend fun getAllPlaylists(accountKey: String): List<PlaylistEntity> = playlists.values.toList()
        override suspend fun getPlaylistByKey(accountKey: String, localKey: String): PlaylistEntity? = playlists[localKey]
        override suspend fun getPlaylistByKey(localKey: String): PlaylistEntity? = playlists[localKey]
        override suspend fun getPlaylistByServerId(accountKey: String, serverId: String): PlaylistEntity? =
            playlists.values.firstOrNull { it.serverId == serverId }
        override suspend fun getPlaylistByTitle(accountKey: String, title: String): PlaylistEntity? =
            playlists.values.firstOrNull { it.title == title }
        override fun observePlaylistByKeyOrTitle(accountKey: String, keyOrTitle: String): Flow<PlaylistEntity?> =
            flowOf(playlists[keyOrTitle] ?: playlists.values.firstOrNull { it.title == keyOrTitle })
        override fun observePlaylistByKeyOrTitle(keyOrTitle: String): Flow<PlaylistEntity?> =
            flowOf(playlists[keyOrTitle] ?: playlists.values.firstOrNull { it.title == keyOrTitle })
        override fun observePlaylistByKey(accountKey: String, localKey: String): Flow<PlaylistEntity?> = flowOf(playlists[localKey])
        override fun observePlaylistByKey(localKey: String): Flow<PlaylistEntity?> = flowOf(playlists[localKey])
        override fun observePlaylistByName(accountKey: String, name: String): Flow<PlaylistEntity?> =
            flowOf(playlists.values.firstOrNull { it.title == name })
        override fun observePlaylistByName(name: String): Flow<PlaylistEntity?> =
            flowOf(playlists.values.firstOrNull { it.title == name })
        override suspend fun insertOrReplace(playlist: PlaylistEntity) {
            playlists[playlist.localKey] = playlist
        }
        override suspend fun insertOrReplaceAll(playlists: List<PlaylistEntity>) {
            playlists.forEach { insertOrReplace(it) }
        }
        override suspend fun deletePlaylistByKey(accountKey: String, localKey: String) {
            playlists.remove(localKey)
        }
        override suspend fun deletePlaylistByKey(localKey: String) {
            playlists.remove(localKey)
        }
        override suspend fun deletePlaylistByName(accountKey: String, name: String) {
            playlists.values.firstOrNull { it.title == name }?.let { playlists.remove(it.localKey) }
        }
        override suspend fun deletePlaylistByName(name: String) {
            playlists.values.firstOrNull { it.title == name }?.let { playlists.remove(it.localKey) }
        }
        override suspend fun nextTrackPosition(playlistLocalKey: String): Int = 0
        override suspend fun insertCrossRef(ref: PlaylistTrackCrossRef) {}
        override suspend fun clearTracksForPlaylist(playlistLocalKey: String) {
            clearedPlaylists.add(playlistLocalKey)
        }
        override suspend fun insertCrossRefs(refs: List<PlaylistTrackCrossRef>) {}
        override suspend fun deleteCrossRef(playlistLocalKey: String, trackId: String) {}
        override fun observeTracksForPlaylist(playlistLocalKey: String): Flow<List<TrackEntity>> = flowOf(emptyList())
        override fun observeTracksForPlaylistWithAvailability(accountKey: String, playlistLocalKey: String): Flow<List<com.sangita.app.data.local.dao.PlaylistTrackItem>> = flowOf(emptyList())
        override fun observeTracksForPlaylistWithAvailability(playlistLocalKey: String): Flow<List<com.sangita.app.data.local.dao.PlaylistTrackItem>> = flowOf(emptyList())
        override suspend fun updateSyncInfo(localKey: String, serverId: String, syncState: String, lastSyncedAt: Long) {
            playlists[localKey]?.let {
                playlists[localKey] = it.copy(serverId = serverId, syncState = syncState, lastSyncedAt = lastSyncedAt)
            }
        }
        override suspend fun updateSyncState(localKey: String, syncState: String, lastSyncedAt: Long) {
            playlists[localKey]?.let {
                playlists[localKey] = it.copy(syncState = syncState, lastSyncedAt = lastSyncedAt)
            }
        }
        override suspend fun updateTrackPosition(playlistLocalKey: String, trackId: String, position: Int) {}
        override suspend fun getTracksForPlaylist(playlistLocalKey: String): List<TrackEntity> = emptyList()
    }

    private open class FakeApiService : SangitaApiService {
        var createPlaylistHandler: ((CreatePlaylistRequest) -> Response<PlaylistDetailDto>)? = null
        var updatePlaylistHandler: ((String, UpdatePlaylistRequest) -> Response<PlaylistDto>)? = null
        var deletePlaylistHandler: ((String) -> Response<DeletePlaylistResponseDto>)? = null
        var addTrackHandler: ((String, AddTrackRequest) -> Response<AddTrackResponseDto>)? = null
        var removeTrackHandler: ((String, String) -> Response<RemoveTrackResponseDto>)? = null
        var clonePlaylistHandler: ((String, ClonePlaylistRequest) -> Response<PlaylistDetailDto>)? = null
        var setFavoriteHandler: ((SetFavoriteRequest) -> Response<ToggleFavoriteResponse>)? = null
        var createLegacyPlaylistHandler: ((Map<String, String>) -> Response<LegacyPlaylistMutationDto>)? = null
        var addLegacyTrackHandler: ((String, Map<String, String>) -> Response<LegacyPlaylistMutationDto>)? = null
        var removeLegacyTrackHandler: ((String, String) -> Response<LegacyPlaylistMutationDto>)? = null
        var updateLegacyPlaylistHandler: ((String, Map<String, String>) -> Response<LegacyPlaylistMutationDto>)? = null
        var deleteLegacyPlaylistHandler: ((String) -> Response<LegacyPlaylistMutationDto>)? = null
        var getPlaylistsHandler: ((Boolean?) -> Response<PlaylistsResponseDto>)? = null
        var addTrackToUserPlaylistHandler: ((String, PlaylistTrackActionRequest) -> Response<Map<String, String>>)? = null

        override suspend fun createPlaylist(request: CreatePlaylistRequest): Response<PlaylistDetailDto> =
            createPlaylistHandler?.invoke(request) ?: Response.error(500, "Not implemented".toResponseBody())
        override suspend fun updatePlaylist(playlistId: String, request: UpdatePlaylistRequest): Response<PlaylistDto> =
            updatePlaylistHandler?.invoke(playlistId, request) ?: Response.error(500, "Not implemented".toResponseBody())
        override suspend fun deletePlaylist(playlistId: String): Response<DeletePlaylistResponseDto> =
            deletePlaylistHandler?.invoke(playlistId) ?: Response.error(500, "Not implemented".toResponseBody())
        override suspend fun addTrackToPlaylist(playlistId: String, request: AddTrackRequest): Response<AddTrackResponseDto> =
            addTrackHandler?.invoke(playlistId, request) ?: Response.error(500, "Not implemented".toResponseBody())
        override suspend fun removeTrackFromPlaylist(playlistId: String, trackPath: String): Response<RemoveTrackResponseDto> =
            removeTrackHandler?.invoke(playlistId, trackPath) ?: Response.error(500, "Not implemented".toResponseBody())
        override suspend fun clonePlaylist(playlistId: String, request: ClonePlaylistRequest): Response<PlaylistDetailDto> =
            clonePlaylistHandler?.invoke(playlistId, request) ?: Response.error(500, "Not implemented".toResponseBody())
        override suspend fun setFavorite(request: SetFavoriteRequest): Response<ToggleFavoriteResponse> =
            setFavoriteHandler?.invoke(request) ?: Response.error(500, "Not implemented".toResponseBody())

        override suspend fun createLegacyPlaylist(body: Map<String, String>): Response<LegacyPlaylistMutationDto> =
            createLegacyPlaylistHandler?.invoke(body) ?: Response.error(500, "Not implemented".toResponseBody())
        override suspend fun addLegacyTrack(name: String, body: Map<String, String>): Response<LegacyPlaylistMutationDto> =
            addLegacyTrackHandler?.invoke(name, body) ?: Response.error(500, "Not implemented".toResponseBody())
        override suspend fun removeLegacyTrack(name: String, trackPath: String): Response<LegacyPlaylistMutationDto> =
            removeLegacyTrackHandler?.invoke(name, trackPath) ?: Response.error(404, "Not found".toResponseBody())
        override suspend fun updateLegacyPlaylist(name: String, body: Map<String, String>): Response<LegacyPlaylistMutationDto> =
            updateLegacyPlaylistHandler?.invoke(name, body) ?: Response.error(500, "Not implemented".toResponseBody())
        override suspend fun deleteLegacyPlaylist(name: String): Response<LegacyPlaylistMutationDto> =
            deleteLegacyPlaylistHandler?.invoke(name) ?: Response.error(404, "Not implemented".toResponseBody())

        override suspend fun login(request: LoginRequest): Response<LoginResponse> = throw UnsupportedOperationException()
        override suspend fun getMe(): Response<MeResponse> = throw UnsupportedOperationException()
        override suspend fun getLegacyPlaylists(): Response<Map<String, List<String>>> = throw UnsupportedOperationException()
        override suspend fun getCatalogSources(): Response<SourcesResponseDto> = throw UnsupportedOperationException()
        override suspend fun getArtists(): Response<ArtistsResponseDto> = throw UnsupportedOperationException()
        override suspend fun getArtistDetail(artistId: String): Response<ArtistDetailDto> = throw UnsupportedOperationException()
        override suspend fun addArtistCollectionToPlaylist(artistId: String, request: AddArtistCollectionRequest): Response<ArtistAddCollectionResponseDto> = Response.success(ArtistAddCollectionResponseDto())
        override suspend fun getPlaylists(discover: Boolean?): Response<PlaylistsResponseDto> =
            getPlaylistsHandler?.invoke(discover) ?: Response.success(PlaylistsResponseDto(emptyList()))
        override suspend fun savePlaylistToLibrary(playlistId: String): Response<Map<String, String>> = Response.success(emptyMap())
        override suspend fun removePlaylistFromLibrary(playlistId: String): Response<Map<String, String>> = Response.success(emptyMap())
        override suspend fun getPlaylistDetail(playlistId: String): Response<PlaylistDetailDto> = throw UnsupportedOperationException()
        override suspend fun getDurations(): Response<Map<String, Double?>> = throw UnsupportedOperationException()
        override suspend fun getTrackMetadata(): Response<Map<String, TrackMetadataDto>> = throw UnsupportedOperationException()
        override suspend fun streamAudio(filepath: String): Response<ResponseBody> = throw UnsupportedOperationException()
        override suspend fun getCoverArt(filepath: String): Response<ResponseBody> = throw UnsupportedOperationException()
        override suspend fun getLyrics(filepath: String): Response<LyricsResponse> = throw UnsupportedOperationException()
        override suspend fun getFavorites(): Response<List<String>> = throw UnsupportedOperationException()
        override suspend fun toggleFavorite(request: ToggleFavoriteRequest): Response<ToggleFavoriteResponse> = throw UnsupportedOperationException()
        override suspend fun getUserPlaylists(): Response<UserPlaylistsResponse> = throw UnsupportedOperationException()
        override suspend fun createUserPlaylist(request: CreateUserPlaylistRequest): Response<Map<String, String>> = throw UnsupportedOperationException()
        override suspend fun addTrackToUserPlaylist(name: String, request: PlaylistTrackActionRequest): Response<Map<String, String>> =
            addTrackToUserPlaylistHandler?.invoke(name, request) ?: Response.success(mapOf("status" to "ok"))
        override suspend fun removeTrackFromUserPlaylist(name: String, request: PlaylistTrackActionRequest): Response<Map<String, String>> = throw UnsupportedOperationException()
        override suspend fun pingStats(request: StatsPingRequest): Response<Map<String, String>> = throw UnsupportedOperationException()
        override suspend fun getUserStats(): Response<UserStatsResponse> = throw UnsupportedOperationException()
        override suspend fun getDataUsage(): Response<DataUsageResponse> = throw UnsupportedOperationException()
        override suspend fun recordDataUsage(request: RecordDataUsageRequest): Response<Map<String, String>> = throw UnsupportedOperationException()
    }

    // =========================================================================
    // Tests for executeAction across all 7 types
    // =========================================================================

    @Test
    fun executeAction_createPlaylist_success_updatesRoomAndPendingActions() = runBlocking {
        val fakeApi = FakeApiService()
        fakeApi.createPlaylistHandler = { req ->
            Response.success(
                201,
                PlaylistDetailDto(id = "srv_created_123", title = req.title, visibility = req.visibility)
            )
        }

        val playlistDao = FakePlaylistDao()
        playlistDao.insertOrReplace(
            PlaylistEntity(
                localKey = "local_pl_1",
                serverId = null,
                title = "Lo-Fi Beats",
                syncState = "PENDING_CREATE"
            )
        )

        val syncQueueDao = FakeSyncQueueDao()
        val createAction = SyncQueueEntity(
            id = 1L,
            actionType = SyncActionType.CREATE_PLAYLIST,
            payloadJson = SyncPayloadSerialization.encodeCreatePlaylist(
                CreatePlaylistPayload(localKey = "local_pl_1", title = "Lo-Fi Beats", visibility = "private")
            )
        )
        val dependentAddTrackAction = SyncQueueEntity(
            id = 2L,
            actionType = SyncActionType.ADD_TRACK,
            payloadJson = SyncPayloadSerialization.encodeAddTrack(
                AddTrackPayload(localKey = "local_pl_1", serverId = null, trackPath = "TrackA.mp3")
            )
        )
        syncQueueDao.actions.add(createAction)
        syncQueueDao.actions.add(dependentAddTrackAction)

        val result = SangitaSyncWorker.executeAction(createAction, fakeApi, playlistDao, syncQueueDao)
        assertTrue(result is ActionExecutionResult.Success)

        // Verify Room entity updated with serverId and SYNCED state
        val updatedPl = playlistDao.getPlaylistByKey("local_pl_1")
        assertNotNull(updatedPl)
        assertEquals("srv_created_123", updatedPl?.serverId)
        assertEquals("SYNCED", updatedPl?.syncState)

        // Verify dependent ADD_TRACK action in queue had its payload updated with new serverId
        val updatedAction = syncQueueDao.getActionById(2L)
        assertNotNull(updatedAction)
        val decodedPayload = SyncPayloadSerialization.decodeAddTrack(updatedAction!!.payloadJson)
        assertEquals("srv_created_123", decodedPayload.serverId)
    }

    @Test
    fun executeAction_updatePlaylist_nullServerId_skipsIfNotFoundInRoom() = runBlocking {
        val fakeApi = FakeApiService()
        val playlistDao = FakePlaylistDao()
        val syncQueueDao = FakeSyncQueueDao()

        val action = SyncQueueEntity(
            id = 10L,
            actionType = SyncActionType.UPDATE_PLAYLIST,
            payloadJson = SyncPayloadSerialization.encodeUpdatePlaylist(
                UpdatePlaylistPayload(localKey = "local_unmapped", serverId = null, title = "New Title")
            )
        )

        val result = SangitaSyncWorker.executeAction(action, fakeApi, playlistDao, syncQueueDao)
        assertTrue(result is ActionExecutionResult.Skip)
    }

    @Test
    fun executeAction_updatePlaylist_success_updatesRoomSyncState() = runBlocking {
        val fakeApi = FakeApiService()
        fakeApi.updatePlaylistHandler = { id, req ->
            Response.success(PlaylistDto(id = id, title = req.title ?: ""))
        }
        val playlistDao = FakePlaylistDao()
        playlistDao.insertOrReplace(
            PlaylistEntity(
                localKey = "local_pl_2",
                serverId = "srv_pl_2",
                title = "Old Title",
                syncState = "PENDING_UPDATE"
            )
        )
        val syncQueueDao = FakeSyncQueueDao()

        val action = SyncQueueEntity(
            id = 11L,
            actionType = SyncActionType.UPDATE_PLAYLIST,
            payloadJson = SyncPayloadSerialization.encodeUpdatePlaylist(
                UpdatePlaylistPayload(localKey = "local_pl_2", serverId = "srv_pl_2", title = "New Title")
            )
        )

        val result = SangitaSyncWorker.executeAction(action, fakeApi, playlistDao, syncQueueDao)
        assertTrue(result is ActionExecutionResult.Success)

        val updatedPl = playlistDao.getPlaylistByKey("local_pl_2")
        assertEquals("New Title", updatedPl?.title)
        assertEquals("SYNCED", updatedPl?.syncState)
    }

    @Test
    fun executeAction_deletePlaylist_200or404_deletesFromRoom() = runBlocking {
        val fakeApi = FakeApiService()
        fakeApi.deletePlaylistHandler = { id ->
            Response.error(404, "Playlist not found".toResponseBody("application/json".toMediaType()))
        }
        val playlistDao = FakePlaylistDao()
        playlistDao.insertOrReplace(
            PlaylistEntity(
                localKey = "local_to_delete",
                serverId = "srv_del",
                title = "To Delete"
            )
        )
        val syncQueueDao = FakeSyncQueueDao()

        val action = SyncQueueEntity(
            id = 20L,
            actionType = SyncActionType.DELETE_PLAYLIST,
            payloadJson = SyncPayloadSerialization.encodeDeletePlaylist(
                DeletePlaylistPayload(localKey = "local_to_delete", serverId = "srv_del")
            )
        )

        val result = SangitaSyncWorker.executeAction(action, fakeApi, playlistDao, syncQueueDao)
        assertTrue(result is ActionExecutionResult.Success)
        assertNull(playlistDao.getPlaylistByKey("local_to_delete"))
        assertTrue(playlistDao.clearedPlaylists.contains("local_to_delete"))
    }

    @Test
    fun executeAction_addTrack_409Conflict_treatedAsSuccess() = runBlocking {
        val fakeApi = FakeApiService()
        fakeApi.addTrackHandler = { _, _ ->
            val jsonErr = """{"error": "Track already exists in playlist", "status": "exists"}"""
            Response.error(409, jsonErr.toResponseBody("application/json".toMediaType()))
        }
        val playlistDao = FakePlaylistDao()
        val syncQueueDao = FakeSyncQueueDao()

        val action = SyncQueueEntity(
            id = 30L,
            actionType = SyncActionType.ADD_TRACK,
            payloadJson = SyncPayloadSerialization.encodeAddTrack(
                AddTrackPayload(localKey = "local_pl_3", serverId = "srv_pl_3", trackPath = "Rock/Song.mp3")
            )
        )

        val result = SangitaSyncWorker.executeAction(action, fakeApi, playlistDao, syncQueueDao)
        assertTrue(result is ActionExecutionResult.Success)
    }

    @Test
    fun executeAction_addTrack_201Created_success() = runBlocking {
        val fakeApi = FakeApiService()
        fakeApi.addTrackHandler = { plId, req ->
            Response.success(201, AddTrackResponseDto(status = "added", playlistId = plId, trackPath = req.trackPath))
        }
        val playlistDao = FakePlaylistDao()
        val syncQueueDao = FakeSyncQueueDao()

        val action = SyncQueueEntity(
            id = 31L,
            actionType = SyncActionType.ADD_TRACK,
            payloadJson = SyncPayloadSerialization.encodeAddTrack(
                AddTrackPayload(localKey = "local_pl_3", serverId = "srv_pl_3", trackPath = "Rock/New.mp3")
            )
        )

        val result = SangitaSyncWorker.executeAction(action, fakeApi, playlistDao, syncQueueDao)
        assertTrue(result is ActionExecutionResult.Success)
    }

    @Test
    fun executeAction_removeTrack_404_treatedAsSuccess() = runBlocking {
        val fakeApi = FakeApiService()
        fakeApi.removeTrackHandler = { _, _ ->
            Response.error(404, "Not found".toResponseBody("application/json".toMediaType()))
        }
        val playlistDao = FakePlaylistDao()
        val syncQueueDao = FakeSyncQueueDao()

        val action = SyncQueueEntity(
            id = 40L,
            actionType = SyncActionType.REMOVE_TRACK,
            payloadJson = SyncPayloadSerialization.encodeRemoveTrack(
                RemoveTrackPayload(localKey = "local_pl_4", serverId = "srv_pl_4", trackPath = "Rock/Song.mp3")
            )
        )

        val result = SangitaSyncWorker.executeAction(action, fakeApi, playlistDao, syncQueueDao)
        assertTrue(result is ActionExecutionResult.Success)
    }

    @Test
    fun executeAction_clonePlaylist_updatesNewLocalPlaylistServerId() = runBlocking {
        val fakeApi = FakeApiService()
        fakeApi.clonePlaylistHandler = { srcId, req ->
            Response.success(
                PlaylistDetailDto(id = "srv_new_cloned_id", title = req.title ?: "Cloned", visibility = req.visibility)
            )
        }
        val playlistDao = FakePlaylistDao()
        playlistDao.insertOrReplace(
            PlaylistEntity(
                localKey = "local_cloned_key",
                serverId = null,
                title = "Cloned Copy",
                syncState = "PENDING"
            )
        )
        val syncQueueDao = FakeSyncQueueDao()

        val action = SyncQueueEntity(
            id = 50L,
            actionType = SyncActionType.CLONE_PLAYLIST,
            payloadJson = SyncPayloadSerialization.encodeClonePlaylist(
                ClonePlaylistPayload(sourceServerId = "srv_src_id", newLocalKey = "local_cloned_key", title = "Cloned Copy")
            )
        )

        val result = SangitaSyncWorker.executeAction(action, fakeApi, playlistDao, syncQueueDao)
        assertTrue(result is ActionExecutionResult.Success)

        val updated = playlistDao.getPlaylistByKey("local_cloned_key")
        assertEquals("srv_new_cloned_id", updated?.serverId)
        assertEquals("SYNCED", updated?.syncState)
    }

    @Test
    fun executeAction_toggleFavorite_callsSetFavoriteIdempotently() = runBlocking {
        var calledPath: String? = null
        var calledIsFav: Boolean? = null
        val fakeApi = FakeApiService()
        fakeApi.setFavoriteHandler = { req ->
            calledPath = req.trackPath
            calledIsFav = req.isFavorite
            Response.success(ToggleFavoriteResponse(isFavorite = req.isFavorite, trackPath = req.trackPath))
        }
        val playlistDao = FakePlaylistDao()
        val syncQueueDao = FakeSyncQueueDao()

        val action = SyncQueueEntity(
            id = 60L,
            actionType = SyncActionType.TOGGLE_FAVORITE,
            payloadJson = SyncPayloadSerialization.encodeToggleFavorite(
                ToggleFavoritePayload(trackId = "Jazz/Song.mp3", isFavorite = true)
            )
        )

        val result = SangitaSyncWorker.executeAction(action, fakeApi, playlistDao, syncQueueDao)
        assertTrue(result is ActionExecutionResult.Success)
        assertEquals("Jazz/Song.mp3", calledPath)
        assertEquals(true, calledIsFav)
    }

    @Test
    fun executeSyncQueue_processesSequentialActionsAndSkipsUnresolved() = runBlocking {
        val fakeApi = FakeApiService()
        fakeApi.setFavoriteHandler = { req ->
            Response.success(ToggleFavoriteResponse(isFavorite = req.isFavorite, trackPath = req.trackPath))
        }

        val playlistDao = FakePlaylistDao()
        val syncQueueDao = FakeSyncQueueDao()

        // 1. Action that will skip (unresolved serverId for update)
        val skipAction = SyncQueueEntity(
            id = 1L,
            actionType = SyncActionType.UPDATE_PLAYLIST,
            payloadJson = SyncPayloadSerialization.encodeUpdatePlaylist(
                UpdatePlaylistPayload(localKey = "missing_local", serverId = null, title = "New Title")
            )
        )
        // 2. Action that will succeed
        val favAction = SyncQueueEntity(
            id = 2L,
            actionType = SyncActionType.TOGGLE_FAVORITE,
            payloadJson = SyncPayloadSerialization.encodeToggleFavorite(
                ToggleFavoritePayload(trackId = "Song.mp3", isFavorite = true)
            )
        )

        syncQueueDao.enqueueAction(skipAction)
        syncQueueDao.enqueueAction(favAction)

        val summary = SangitaSyncWorker.executeSyncQueue(fakeApi, playlistDao, syncQueueDao)
        assertEquals(1, summary.processed)
        assertEquals(1, summary.skipped)
        assertEquals(1, summary.totalRemaining)

        // Skipped action remains in queue, completed action was deleted
        val remaining = syncQueueDao.getPendingActions()
        assertEquals(1, remaining.size)
        assertEquals(1L, remaining[0].id)
    }

    @Test
    fun executeAction_createPlaylist_usesIdempotencyAndDoesNotStealExistingSameNamedServerPlaylist() = runBlocking {
        val fakeApi = FakeApiService()
        fakeApi.getPlaylistsHandler = { _ ->
            Response.success(
                PlaylistsResponseDto(
                    listOf(
                        PlaylistDto(
                            id = "srv_other_user_playlist_99",
                            title = "Chill Vibes",
                            owner = "bob",
                            visibility = "public"
                        )
                    )
                )
            )
        }

        var capturedRequest: CreatePlaylistRequest? = null
        fakeApi.createPlaylistHandler = { req ->
            capturedRequest = req
            Response.success(
                201,
                PlaylistDetailDto(
                    id = "srv_my_new_playlist_101",
                    title = req.title,
                    visibility = req.visibility
                )
            )
        }

        val playlistDao = FakePlaylistDao()
        playlistDao.insertOrReplace(
            PlaylistEntity(
                localKey = "local_chill_vibes",
                serverId = null,
                title = "Chill Vibes",
                syncState = "PENDING_CREATE"
            )
        )

        val syncQueueDao = FakeSyncQueueDao()
        val createAction = SyncQueueEntity(
            id = 50L,
            actionType = SyncActionType.CREATE_PLAYLIST,
            payloadJson = SyncPayloadSerialization.encodeCreatePlaylist(
                CreatePlaylistPayload(
                    localKey = "local_chill_vibes",
                    title = "Chill Vibes",
                    visibility = "private",
                    idempotencyKey = "idemp_cv_123"
                )
            )
        )
        syncQueueDao.enqueueAction(createAction)

        val result = SangitaSyncWorker.executeAction(createAction, fakeApi, playlistDao, syncQueueDao)
        assertTrue(result is ActionExecutionResult.Success)

        // Must have sent CreatePlaylistRequest with the idempotency key rather than stealing srv_other_user_playlist_99
        assertNotNull(capturedRequest)
        assertEquals("Chill Vibes", capturedRequest?.title)
        assertEquals("idemp_cv_123", capturedRequest?.idempotencyKey)

        val localPl = playlistDao.getPlaylistByKey("local_chill_vibes")
        assertNotNull(localPl)
        assertEquals("srv_my_new_playlist_101", localPl?.serverId)
    }

    @Test
    fun executeAction_addTrackToUserPlaylist_usesRetargetedTitleAndLocalKeyInsteadOfOriginalCollisionTitle() = runBlocking {
        val fakeApi = FakeApiService()
        var calledPlaylistName: String? = null
        var calledTrackPath: String? = null
        fakeApi.addTrackToUserPlaylistHandler = { plName, req ->
            calledPlaylistName = plName
            calledTrackPath = req.trackPath
            Response.success(mapOf("status" to "ok"))
        }

        val playlistDao = FakePlaylistDao()
        // Playlist in Room was renamed upon collision to "Old Rock (Recovered)"
        playlistDao.insertOrReplace(
            PlaylistEntity(
                localKey = "local_rec_1",
                accountKey = "https://sangita.live|alice",
                title = "Old Rock (Recovered)",
                syncState = "SYNCED"
            )
        )

        val syncQueueDao = FakeSyncQueueDao()
        val action = SyncQueueEntity(
            id = 60L,
            accountKey = "https://sangita.live|alice",
            actionType = SyncActionType.ADD_TRACK_TO_PLAYLIST,
            payloadJson = """{"local_key":"local_rec_1","playlist":"Old Rock (Recovered)","track_path":"classic/song.mp3"}"""
        )
        syncQueueDao.enqueueAction(action)

        val result = SangitaSyncWorker.executeAction(action, fakeApi, playlistDao, syncQueueDao)
        assertTrue(result is ActionExecutionResult.Success)

        // Verifies the retargeted title "Old Rock (Recovered)" was sent, NOT the collision original "Old Rock"
        assertEquals("Old Rock (Recovered)", calledPlaylistName)
        assertEquals("classic/song.mp3", calledTrackPath)
    }

    @Test
    fun executeAction_clonePlaylist_usesIdempotencyAndDoesNotStealExistingSameNamedServerPlaylist() = runBlocking {
        val fakeApi = FakeApiService()
        fakeApi.getPlaylistsHandler = { _ ->
            Response.success(
                PlaylistsResponseDto(
                    listOf(
                        PlaylistDto(
                            id = "srv_stranger_clone_99",
                            title = "Pop Hits (Copy)",
                            owner = "bob",
                            visibility = "public"
                        )
                    )
                )
            )
        }

        var capturedRequest: ClonePlaylistRequest? = null
        var capturedSourceId: String? = null
        fakeApi.clonePlaylistHandler = { sourceId, req ->
            capturedSourceId = sourceId
            capturedRequest = req
            Response.success(
                201,
                PlaylistDetailDto(
                    id = "srv_my_cloned_playlist_202",
                    title = req.title ?: "Pop Hits (Copy)",
                    visibility = req.visibility
                )
            )
        }

        val playlistDao = FakePlaylistDao()
        playlistDao.insertOrReplace(
            PlaylistEntity(
                localKey = "local_clone_pop",
                serverId = null,
                accountKey = "https://sangita.live|alice",
                title = "Pop Hits (Copy)",
                syncState = "PENDING_CREATE"
            )
        )

        val syncQueueDao = FakeSyncQueueDao()
        val cloneAction = SyncQueueEntity(
            id = 70L,
            accountKey = "https://sangita.live|alice",
            actionType = SyncActionType.CLONE_PLAYLIST,
            payloadJson = SyncPayloadSerialization.encodeClonePlaylist(
                ClonePlaylistPayload(
                    sourceServerId = "source_pl_original",
                    newLocalKey = "local_clone_pop",
                    title = "Pop Hits (Copy)",
                    visibility = "private",
                    idempotencyKey = "clone_idem_777"
                )
            )
        )
        syncQueueDao.enqueueAction(cloneAction)

        val result = SangitaSyncWorker.executeAction(cloneAction, fakeApi, playlistDao, syncQueueDao)
        assertTrue(result is ActionExecutionResult.Success)

        // Must have called apiService.clonePlaylist with sourceServerId and idempotencyKey
        assertEquals("source_pl_original", capturedSourceId)
        assertNotNull(capturedRequest)
        assertEquals("Pop Hits (Copy)", capturedRequest?.title)
        assertEquals("clone_idem_777", capturedRequest?.idempotencyKey)

        // Must NOT have stolen srv_stranger_clone_99
        val localPl = playlistDao.getPlaylistByKey("local_clone_pop")
        assertNotNull(localPl)
        assertEquals("srv_my_cloned_playlist_202", localPl?.serverId)
    }
}
