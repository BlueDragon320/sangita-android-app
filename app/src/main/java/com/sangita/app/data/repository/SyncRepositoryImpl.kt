package com.sangita.app.data.repository

import com.sangita.app.data.local.dao.PlaybackEventDao
import com.sangita.app.data.local.dao.PlaylistDao
import com.sangita.app.data.local.dao.SyncQueueDao
import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.data.remote.dto.StatsPingRequest
import com.sangita.app.domain.model.SyncActionType
import com.sangita.app.domain.repository.SyncRepository
import com.sangita.app.sync.ActionExecutionResult
import com.sangita.app.sync.AddTrackPayload
import com.sangita.app.sync.ClonePlaylistPayload
import com.sangita.app.sync.CreatePlaylistPayload
import com.sangita.app.sync.DeletePlaylistPayload
import com.sangita.app.sync.IncompleteSyncException
import com.sangita.app.sync.RemoveTrackPayload
import com.sangita.app.sync.SangitaSyncWorker
import com.sangita.app.sync.SyncPayloadSerialization
import com.sangita.app.sync.ToggleFavoritePayload
import com.sangita.app.sync.UpdatePlaylistPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SyncRepositoryImpl(
    private val syncQueueDao: SyncQueueDao,
    private val playbackEventDao: PlaybackEventDao,
    private val networkClient: NetworkClient,
    private val playlistDao: PlaylistDao? = null,
    private val userPreferences: UserPreferences? = null
) : SyncRepository {

    override suspend fun enqueueAction(type: SyncActionType, payloadJson: String) = withContext(Dispatchers.IO) {
        val accountKey = userPreferences?.getAccountKey() ?: "default_account"
        syncQueueDao.enqueueAction(
            SyncQueueEntity(
                accountKey = accountKey,
                actionType = type,
                payloadJson = payloadJson
            )
        )
        Unit
    }

    override suspend fun enqueueCreatePlaylist(payload: CreatePlaylistPayload) {
        enqueueAction(SyncActionType.CREATE_PLAYLIST, SyncPayloadSerialization.encodeCreatePlaylist(payload))
    }

    override suspend fun enqueueUpdatePlaylist(payload: UpdatePlaylistPayload) {
        enqueueAction(SyncActionType.UPDATE_PLAYLIST, SyncPayloadSerialization.encodeUpdatePlaylist(payload))
    }

    override suspend fun enqueueDeletePlaylist(payload: DeletePlaylistPayload) {
        enqueueAction(SyncActionType.DELETE_PLAYLIST, SyncPayloadSerialization.encodeDeletePlaylist(payload))
    }

    override suspend fun enqueueAddTrack(payload: AddTrackPayload) {
        enqueueAction(SyncActionType.ADD_TRACK, SyncPayloadSerialization.encodeAddTrack(payload))
    }

    override suspend fun enqueueRemoveTrack(payload: RemoveTrackPayload) {
        enqueueAction(SyncActionType.REMOVE_TRACK, SyncPayloadSerialization.encodeRemoveTrack(payload))
    }

    override suspend fun enqueueClonePlaylist(payload: ClonePlaylistPayload) {
        enqueueAction(SyncActionType.CLONE_PLAYLIST, SyncPayloadSerialization.encodeClonePlaylist(payload))
    }

    override suspend fun enqueueToggleFavorite(payload: ToggleFavoritePayload) {
        enqueueAction(SyncActionType.TOGGLE_FAVORITE, SyncPayloadSerialization.encodeToggleFavorite(payload))
    }

    override fun getSyncQueueSize(): Flow<Int> {
        val accountKey = userPreferences?.getAccountKey() ?: "default_account"
        return syncQueueDao.observeQueueSize(accountKey)
    }

    override suspend fun syncPendingActions(): Result<Int> = SyncSerialization.mutex.withLock { withContext(Dispatchers.IO) {
        try {
            val api = networkClient.createApiService()
            val accountKey = userPreferences?.getAccountKey() ?: "default_account"
            val summary = SangitaSyncWorker.executeSyncQueue(
                apiService = api,
                playlistDao = playlistDao,
                syncQueueDao = syncQueueDao,
                accountKey = accountKey
            )

            // Flush offline playback telemetry events
            val unsyncedEvents = playbackEventDao.getUnsyncedEvents()
            val syncedIds = mutableListOf<Long>()
            for (ev in unsyncedEvents) {
                try {
                    val pingResp = api.pingStats(
                        StatsPingRequest(
                            trackId = ev.trackId,
                            playlist = ev.playlist,
                            deviceId = ev.deviceId,
                            deviceName = ev.deviceName,
                            deviceType = ev.deviceType,
                            browser = ev.browser,
                            os = ev.os,
                            durationSec = ev.durationSec
                        )
                    )
                    if (pingResp.isSuccessful) {
                        syncedIds.add(ev.id)
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) {}
            }
            if (syncedIds.isNotEmpty()) {
                playbackEventDao.markSynced(syncedIds)
            }

            if (summary.failed > 0 || summary.skipped > 0 || summary.totalRemaining > 0) {
                Result.failure(
                    IncompleteSyncException(
                        "Sync incomplete: ${summary.failed} failed, ${summary.skipped} pending/skipped, ${summary.totalRemaining} remaining"
                    )
                )
            } else {
                Result.success(summary.processed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    } }

    override fun observeFailedActions(accountKey: String): Flow<List<SyncQueueEntity>> {
        return syncQueueDao.observeFailedActions(accountKey)
    }

    override suspend fun retryFailedAction(id: Long) = withContext(Dispatchers.IO) {
        syncQueueDao.retryFailedAction(id)
    }

    override suspend fun dismissFailedAction(id: Long) = withContext(Dispatchers.IO) {
        syncQueueDao.dismissFailedAction(id)
    }
}
