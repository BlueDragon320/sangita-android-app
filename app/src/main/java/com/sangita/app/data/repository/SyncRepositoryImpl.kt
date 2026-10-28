package com.sangita.app.data.repository

import com.sangita.app.data.local.dao.PlaybackEventDao
import com.sangita.app.data.local.dao.SyncQueueDao
import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.data.remote.dto.CreateUserPlaylistRequest
import com.sangita.app.data.remote.dto.PlaylistTrackActionRequest
import com.sangita.app.data.remote.dto.RecordDataUsageRequest
import com.sangita.app.data.remote.dto.StatsPingRequest
import com.sangita.app.data.remote.dto.ToggleFavoriteRequest
import com.sangita.app.data.remote.dto.SetFavoriteRequest
import com.sangita.app.domain.model.SyncActionType
import com.sangita.app.domain.repository.SyncRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SyncRepositoryImpl(
    private val syncQueueDao: SyncQueueDao,
    private val playbackEventDao: PlaybackEventDao,
    private val networkClient: NetworkClient
) : SyncRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun enqueueAction(type: SyncActionType, payloadJson: String) = withContext(Dispatchers.IO) {
        syncQueueDao.enqueueAction(
            SyncQueueEntity(
                actionType = type,
                payloadJson = payloadJson
            )
        )
        Unit
    }

    override fun getSyncQueueSize(): Flow<Int> {
        return syncQueueDao.observeQueueSize()
    }

    override suspend fun syncPendingActions(): Result<Int> = SyncSerialization.mutex.withLock { withContext(Dispatchers.IO) {
        try {
            val pending = syncQueueDao.getPendingActions()
            if (pending.isEmpty()) return@withContext Result.success(0)

            val api = networkClient.createApiService()
            var successCount = 0

            for (action in pending) {
                try {
                    val isSuccess = when (action.actionType) {
                        SyncActionType.TOGGLE_FAVORITE -> {
                            val req = json.decodeFromString<ToggleFavoriteRequest>(action.payloadJson)
                            api.toggleFavorite(req).isSuccessful
                        }
                        SyncActionType.SET_FAVORITE -> {
                            val req = json.decodeFromString<SetFavoriteRequest>(action.payloadJson)
                            api.setFavorite(req).isSuccessful
                        }
                        SyncActionType.CREATE_PLAYLIST -> {
                            val req = json.decodeFromString<CreateUserPlaylistRequest>(action.payloadJson)
                            api.createUserPlaylist(req).isSuccessful
                        }
                        SyncActionType.ADD_TRACK_TO_PLAYLIST -> {
                            val obj = json.parseToJsonElement(action.payloadJson).jsonObject
                            val pl = obj["playlist"]?.jsonPrimitive?.content ?: ""
                            val track = obj["track_path"]?.jsonPrimitive?.content ?: ""
                            api.addTrackToUserPlaylist(pl, PlaylistTrackActionRequest(trackPath = track)).isSuccessful
                        }
                        SyncActionType.REMOVE_TRACK_FROM_PLAYLIST -> {
                            val obj = json.parseToJsonElement(action.payloadJson).jsonObject
                            val pl = obj["playlist"]?.jsonPrimitive?.content ?: ""
                            val track = obj["track_path"]?.jsonPrimitive?.content ?: ""
                            api.removeTrackFromUserPlaylist(pl, PlaylistTrackActionRequest(trackPath = track)).isSuccessful
                        }
                        SyncActionType.PLAY_EVENT -> {
                            val req = json.decodeFromString<StatsPingRequest>(action.payloadJson)
                            api.pingStats(req).isSuccessful
                        }
                        SyncActionType.DATA_CONSUMPTION -> {
                            val req = json.decodeFromString<RecordDataUsageRequest>(action.payloadJson)
                            api.recordDataUsage(req).isSuccessful
                        }
                    }

                    if (isSuccess) {
                        syncQueueDao.deleteAction(action.id)
                        successCount++
                    } else {
                        syncQueueDao.incrementRetry(action.id, "HTTP Unsuccessful")
                        break
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    syncQueueDao.incrementRetry(action.id, e.localizedMessage)
                    break
                }
            }

            // Also flush offline playback telemetry events
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

            if (syncQueueDao.getQueueSize() > 0) Result.failure(IllegalStateException("Pending sync actions remain"))
            else Result.success(successCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    } }
}
