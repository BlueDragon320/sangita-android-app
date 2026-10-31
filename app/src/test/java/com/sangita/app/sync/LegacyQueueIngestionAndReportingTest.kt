package com.sangita.app.sync

import com.sangita.app.data.local.dao.PlaybackEventDao
import com.sangita.app.data.local.dao.PlaylistDao
import com.sangita.app.data.local.dao.SyncQueueDao
import com.sangita.app.data.local.entity.PlaybackEventEntity
import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.data.local.entity.PlaylistTrackCrossRef
import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.data.local.entity.TrackEntity
import com.sangita.app.data.remote.api.NetworkClient
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
import com.sangita.app.data.repository.SyncRepositoryImpl
import com.sangita.app.domain.model.SyncActionType
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class LegacyQueueIngestionAndReportingTest {

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

        override fun observeRecoverableLegacyPlaylists(): Flow<List<PlaylistEntity>> =
            flowOf(playlists.values.filter { it.accountKey == "default_account" && it.serverId.isNullOrBlank() && it.kind != "FAVORITES" })
        override suspend fun getRecoverableLegacyPlaylists(): List<PlaylistEntity> =
            playlists.values.filter { it.accountKey == "default_account" && it.serverId.isNullOrBlank() && it.kind != "FAVORITES" }

        override fun observeAllPlaylists(accountKey: String): Flow<List<PlaylistEntity>> = flowOf(playlists.values.filter { it.accountKey == accountKey })
        override suspend fun getAllPlaylists(accountKey: String): List<PlaylistEntity> = playlists.values.filter { it.accountKey == accountKey }
        override suspend fun getPlaylistByKey(accountKey: String, localKey: String): PlaylistEntity? =
            playlists[localKey]?.takeIf { it.accountKey == accountKey }
        override suspend fun getPlaylistByKey(localKey: String): PlaylistEntity? = playlists[localKey]
        override suspend fun getPlaylistByServerId(accountKey: String, serverId: String): PlaylistEntity? =
            playlists.values.firstOrNull { it.accountKey == accountKey && it.serverId == serverId }
        override suspend fun getPlaylistByTitle(accountKey: String, title: String): PlaylistEntity? =
            playlists.values.firstOrNull { it.accountKey == accountKey && it.title == title }
        override fun observePlaylistByKeyOrTitle(accountKey: String, keyOrTitle: String): Flow<PlaylistEntity?> =
            flowOf(playlists[keyOrTitle]?.takeIf { it.accountKey == accountKey } ?: playlists.values.firstOrNull { it.accountKey == accountKey && it.title == keyOrTitle })
        override fun observePlaylistByKeyOrTitle(keyOrTitle: String): Flow<PlaylistEntity?> =
            flowOf(playlists[keyOrTitle] ?: playlists.values.firstOrNull { it.title == keyOrTitle })
        override fun observePlaylistByKey(accountKey: String, localKey: String): Flow<PlaylistEntity?> =
            flowOf(playlists[localKey]?.takeIf { it.accountKey == accountKey })
        override fun observePlaylistByKey(localKey: String): Flow<PlaylistEntity?> = flowOf(playlists[localKey])
        override fun observePlaylistByName(accountKey: String, name: String): Flow<PlaylistEntity?> =
            flowOf(playlists.values.firstOrNull { it.accountKey == accountKey && it.title == name })
        override fun observePlaylistByName(name: String): Flow<PlaylistEntity?> =
            flowOf(playlists.values.firstOrNull { it.title == name })
        override suspend fun insertOrReplace(playlist: PlaylistEntity) {
            playlists[playlist.localKey] = playlist
        }
        override suspend fun insertOrReplaceAll(playlists: List<PlaylistEntity>) {
            playlists.forEach { insertOrReplace(it) }
        }
        override suspend fun deletePlaylistByKey(accountKey: String, localKey: String) {
            if (playlists[localKey]?.accountKey == accountKey) playlists.remove(localKey)
        }
        override suspend fun deletePlaylistByKey(localKey: String) {
            playlists.remove(localKey)
        }
        override suspend fun deletePlaylistByName(accountKey: String, name: String) {
            playlists.values.firstOrNull { it.accountKey == accountKey && it.title == name }?.let { playlists.remove(it.localKey) }
        }
        override suspend fun deletePlaylistByName(name: String) {
            playlists.values.firstOrNull { it.title == name }?.let { playlists.remove(it.localKey) }
        }
        override suspend fun nextTrackPosition(playlistLocalKey: String): Int = 0
        override suspend fun insertCrossRef(ref: PlaylistTrackCrossRef) {}
        override suspend fun clearTracksForPlaylist(playlistLocalKey: String) {}
        override suspend fun insertCrossRefs(refs: List<PlaylistTrackCrossRef>) {}
        override suspend fun deleteCrossRef(playlistLocalKey: String, trackId: String) {}
        override suspend fun updateTrackPosition(playlistLocalKey: String, trackId: String, position: Int) {}
        override suspend fun getTracksForPlaylist(playlistLocalKey: String): List<TrackEntity> = emptyList()
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
    }

    private class FakePlaybackEventDao : PlaybackEventDao {
        val events = mutableListOf<PlaybackEventEntity>()
        override suspend fun insertEvent(event: PlaybackEventEntity) { events.add(event) }
        override suspend fun getUnsyncedEvents(): List<PlaybackEventEntity> = events.filter { !it.synced }
        override suspend fun markSynced(eventIds: List<Long>) {
            events.replaceAll { if (it.id in eventIds) it.copy(synced = true) else it }
        }
        override suspend fun purgeSyncedEvents() { events.removeAll { it.synced } }
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
        override suspend fun getSavedArtists(): Response<com.sangita.app.data.remote.dto.LibraryArtistsResponseDto> = throw UnsupportedOperationException()
        override suspend fun saveArtist(artistId: String): Response<Map<String, String>> = Response.success(emptyMap())
        override suspend fun removeArtist(artistId: String): Response<Map<String, String>> = Response.success(emptyMap())
        override suspend fun getSavedTracks(): Response<com.sangita.app.data.remote.dto.LibraryTracksResponseDto> = throw UnsupportedOperationException()
        override suspend fun saveTrack(request: com.sangita.app.data.remote.dto.SaveTrackRequest): Response<com.sangita.app.data.remote.dto.SaveActionResponseDto> = Response.success(com.sangita.app.data.remote.dto.SaveActionResponseDto())
        override suspend fun removeTrack(trackPath: String): Response<Map<String, String>> = Response.success(emptyMap())
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
        override suspend fun addTrackToUserPlaylist(name: String, request: PlaylistTrackActionRequest): Response<Map<String, String>> = throw UnsupportedOperationException()
        override suspend fun removeTrackFromUserPlaylist(name: String, request: PlaylistTrackActionRequest): Response<Map<String, String>> = throw UnsupportedOperationException()
        override suspend fun pingStats(request: StatsPingRequest): Response<Map<String, String>> = Response.success(emptyMap())
        override suspend fun getUserStats(): Response<UserStatsResponse> = throw UnsupportedOperationException()
        override suspend fun getDataUsage(): Response<DataUsageResponse> = throw UnsupportedOperationException()
        override suspend fun recordDataUsage(request: RecordDataUsageRequest): Response<Map<String, String>> = throw UnsupportedOperationException()
    }

    private fun createMockNetworkClient(api: SangitaApiService): NetworkClient {
        val userPreferences = mockk<com.sangita.app.data.local.preferences.UserPreferences>(relaxed = true)
        every { userPreferences.getServerUrl() } returns "http://localhost:8080"
        val networkClient = spyk(NetworkClient(userPreferences))
        every { networkClient.createApiService(any()) } returns api
        return networkClient
    }

    // =========================================================================
    // 1. Legacy Payload Deserialization Tests
    // =========================================================================

    @Test
    fun deserializeLegacyPayload_addTrack_withNameAndTrackPath() {
        val legacyJson = """{"name": "Summer Hits", "track_path": "a.mp3"}"""

        // Test direct decode
        val payload = SyncPayloadSerialization.decodeAddTrack(legacyJson)
        assertEquals("a.mp3", payload.trackPath)
        assertEquals("Summer Hits", payload.playlistName)
        assertEquals("", payload.localKey)
        assertNull(payload.serverId)

        // Test generic deserializePayload
        val genericResult = SyncPayloadSerialization.deserializePayload(SyncActionType.ADD_TRACK, legacyJson)
        assertTrue(genericResult is AddTrackPayload)
        val genericPayload = genericResult as AddTrackPayload
        assertEquals("a.mp3", genericPayload.trackPath)
        assertEquals("Summer Hits", genericPayload.playlistName)
    }

    @Test
    fun deserializeLegacyPayload_createPlaylist_generatesLocalKeyWhenMissing() {
        val legacyJson = """{"name": "Summer Hits", "description": "Sunny vibes"}"""

        val payload = SyncPayloadSerialization.decodeCreatePlaylist(legacyJson)
        assertEquals("Summer Hits", payload.title)
        assertEquals("Sunny vibes", payload.description)
        assertTrue("Expected generated localKey to not be blank", payload.localKey.isNotBlank())

        val genericResult = SyncPayloadSerialization.deserializePayload(SyncActionType.CREATE_PLAYLIST, legacyJson)
        assertTrue(genericResult is CreatePlaylistPayload)
        val genericPayload = genericResult as CreatePlaylistPayload
        assertEquals("Summer Hits", genericPayload.title)
        assertTrue(genericPayload.localKey.isNotBlank())
    }

    @Test
    fun deserializePayload_corruptJson_returnsNullWithoutThrowing() {
        val corruptJson = "{invalid_json: true,,"
        val resultAdd = SyncPayloadSerialization.deserializePayload(SyncActionType.ADD_TRACK, corruptJson)
        assertNull(resultAdd)

        val resultCreate = SyncPayloadSerialization.deserializePayload(SyncActionType.CREATE_PLAYLIST, corruptJson)
        assertNull(resultCreate)
    }

    // =========================================================================
    // 2. SangitaSyncWorker Graceful Recovery with Legacy Payloads
    // =========================================================================

    @Test
    fun executeAction_legacyAddTrackWithoutLocalKey_recoversExistingPlaylistByTitle() = runBlocking {
        val fakeApi = FakeApiService()
        var apiCalledWithServerId: String? = null
        var apiCalledWithTrackPath: String? = null
        fakeApi.addTrackHandler = { serverId, req ->
            apiCalledWithServerId = serverId
            apiCalledWithTrackPath = req.trackPath
            Response.success(201, AddTrackResponseDto(status = "added", playlistId = serverId, trackPath = req.trackPath))
        }

        val playlistDao = FakePlaylistDao()
        playlistDao.insertOrReplace(
            PlaylistEntity(
                localKey = "local_summer_uuid",
                serverId = "srv_summer_100",
                title = "Summer Hits",
                syncState = "SYNCED"
            )
        )

        val syncQueueDao = FakeSyncQueueDao()
        val legacyAction = SyncQueueEntity(
            id = 1L,
            actionType = SyncActionType.ADD_TRACK,
            payloadJson = """{"name": "Summer Hits", "track_path": "a.mp3"}"""
        )
        syncQueueDao.enqueueAction(legacyAction)

        val result = SangitaSyncWorker.executeAction(legacyAction, fakeApi, playlistDao, syncQueueDao)
        assertTrue("Expected Success result, got $result", result is ActionExecutionResult.Success)
        assertEquals("srv_summer_100", apiCalledWithServerId)
        assertEquals("a.mp3", apiCalledWithTrackPath)

        // Verify action payload in queue was updated with the recovered localKey
        val updatedAction = syncQueueDao.getActionById(1L)
        assertNotNull(updatedAction)
        val decoded = SyncPayloadSerialization.decodeAddTrack(updatedAction!!.payloadJson)
        assertEquals("local_summer_uuid", decoded.localKey)
    }

    // =========================================================================
    // 3. SyncRepository Reporting of Incomplete Synchronization
    // =========================================================================

    @Test
    fun syncPendingActions_returnsFailureWhenActionSkipped() = runBlocking {
        val fakeApi = FakeApiService()
        val playlistDao = FakePlaylistDao()
        val syncQueueDao = FakeSyncQueueDao()
        val playbackEventDao = FakePlaybackEventDao()

        // Unresolved action: missing local playlist and missing server ID
        val skipAction = SyncQueueEntity(
            id = 1L,
            actionType = SyncActionType.UPDATE_PLAYLIST,
            payloadJson = SyncPayloadSerialization.encodeUpdatePlaylist(
                UpdatePlaylistPayload(localKey = "non_existent_key", serverId = null, title = "New Title")
            )
        )
        syncQueueDao.enqueueAction(skipAction)

        val networkClient = createMockNetworkClient(fakeApi)

        val syncRepo = SyncRepositoryImpl(
            syncQueueDao = syncQueueDao,
            playbackEventDao = playbackEventDao,
            networkClient = networkClient,
            playlistDao = playlistDao
        )

        val result = syncRepo.syncPendingActions()
        assertTrue("Expected syncPendingActions to return failure when action is skipped", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Expected IncompleteSyncException, got $exception", exception is IncompleteSyncException)
        assertTrue(
            "Exception message should mention skipped/pending actions",
            exception?.message?.contains("Sync incomplete") == true &&
                exception.message?.contains("skipped") == true
        )
    }

    @Test
    fun syncPendingActions_returnsFailureWhenActionFails() = runBlocking {
        val fakeApi = FakeApiService()
        fakeApi.setFavoriteHandler = { _ ->
            Response.error(500, "Server Error".toResponseBody())
        }

        val playlistDao = FakePlaylistDao()
        val syncQueueDao = FakeSyncQueueDao()
        val playbackEventDao = FakePlaybackEventDao()

        val failAction = SyncQueueEntity(
            id = 10L,
            actionType = SyncActionType.TOGGLE_FAVORITE,
            payloadJson = SyncPayloadSerialization.encodeToggleFavorite(
                ToggleFavoritePayload(trackId = "Song.mp3", isFavorite = true)
            )
        )
        syncQueueDao.enqueueAction(failAction)

        val networkClient = createMockNetworkClient(fakeApi)

        val syncRepo = SyncRepositoryImpl(
            syncQueueDao = syncQueueDao,
            playbackEventDao = playbackEventDao,
            networkClient = networkClient,
            playlistDao = playlistDao
        )

        val result = syncRepo.syncPendingActions()
        assertTrue("Expected syncPendingActions to return failure when action fails", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Expected IncompleteSyncException, got $exception", exception is IncompleteSyncException)
        assertTrue(
            "Exception message should mention failed action",
            exception?.message?.contains("Sync incomplete") == true &&
                exception.message?.contains("1 failed") == true
        )
    }

    @Test
    fun syncPendingActions_returnsSuccessWhenAllActionsSucceed() = runBlocking {
        val fakeApi = FakeApiService()
        fakeApi.setFavoriteHandler = { req ->
            Response.success(ToggleFavoriteResponse(isFavorite = req.isFavorite, trackPath = req.trackPath))
        }

        val playlistDao = FakePlaylistDao()
        val syncQueueDao = FakeSyncQueueDao()
        val playbackEventDao = FakePlaybackEventDao()

        val action = SyncQueueEntity(
            id = 20L,
            actionType = SyncActionType.TOGGLE_FAVORITE,
            payloadJson = SyncPayloadSerialization.encodeToggleFavorite(
                ToggleFavoritePayload(trackId = "Song.mp3", isFavorite = true)
            )
        )
        syncQueueDao.enqueueAction(action)

        val networkClient = createMockNetworkClient(fakeApi)

        val syncRepo = SyncRepositoryImpl(
            syncQueueDao = syncQueueDao,
            playbackEventDao = playbackEventDao,
            networkClient = networkClient,
            playlistDao = playlistDao
        )

        val result = syncRepo.syncPendingActions()
        assertTrue("Expected syncPendingActions to succeed", result.isSuccess)
        assertEquals(1, result.getOrNull())
        assertEquals(0, syncQueueDao.getPendingActions().size)
    }

    @Test
    fun migrateActionsForPlaylist_movesLegacyQueuedEditsMatchingPlaylistTitleToTargetAccount() = runBlocking {
        val syncQueueDao = FakeSyncQueueDao()
        val targetAccountKey = "https://sangita.live|alice"

        // Enqueue legacy actions under default_account that do not contain a local_key
        val legacyCreate = SyncQueueEntity(
            id = 1L,
            accountKey = "default_account",
            actionType = SyncActionType.CREATE_PLAYLIST,
            payloadJson = """{"name":"Old Rock","visibility":"private"}"""
        )
        val legacyAddTrack1 = SyncQueueEntity(
            id = 2L,
            accountKey = "default_account",
            actionType = SyncActionType.ADD_TRACK,
            payloadJson = """{"playlist":"Old Rock","track_path":"rock/song1.mp3"}"""
        )
        val legacyAddTrack2 = SyncQueueEntity(
            id = 3L,
            accountKey = "default_account",
            actionType = SyncActionType.ADD_TRACK,
            payloadJson = """{"playlist_name":"Old Rock","track_path":"rock/song2.mp3"}"""
        )
        val otherPlaylistAction = SyncQueueEntity(
            id = 4L,
            accountKey = "default_account",
            actionType = SyncActionType.ADD_TRACK,
            payloadJson = """{"playlist":"Pop Hits","track_path":"pop/song1.mp3"}"""
        )

        syncQueueDao.enqueueAction(legacyCreate)
        syncQueueDao.enqueueAction(legacyAddTrack1)
        syncQueueDao.enqueueAction(legacyAddTrack2)
        syncQueueDao.enqueueAction(otherPlaylistAction)

        // When playlist is recovered with a new localKey e.g. "legacy_pl_uuid"
        syncQueueDao.migrateActionsForPlaylist("legacy_pl_uuid", "Old Rock", targetAccountKey)

        val targetActions = syncQueueDao.getPendingActions(targetAccountKey)
        assertEquals(3, targetActions.size)
        assertTrue(targetActions.any { it.id == 1L && it.accountKey == targetAccountKey })
        assertTrue(targetActions.any { it.id == 2L && it.accountKey == targetAccountKey })
        assertTrue(targetActions.any { it.id == 3L && it.accountKey == targetAccountKey })

        val createAction = targetActions.first { it.id == 1L }
        val decodedCreate = SyncPayloadSerialization.decodeCreatePlaylist(createAction.payloadJson)
        assertEquals("legacy_pl_uuid", decodedCreate.localKey)
        assertEquals("Old Rock", decodedCreate.title)

        val addTrackAction = targetActions.first { it.id == 2L }
        val decodedAdd = SyncPayloadSerialization.decodeAddTrack(addTrackAction.payloadJson)
        assertEquals("legacy_pl_uuid", decodedAdd.localKey)
        assertEquals("Old Rock", decodedAdd.playlistName)

        val remainingDefault = syncQueueDao.getPendingActions("default_account")
        assertEquals(1, remainingDefault.size)
        assertEquals(4L, remainingDefault[0].id)
    }
}
