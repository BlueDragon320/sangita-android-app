package com.sangita.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sangita.app.SangitaApplication
import com.sangita.app.data.local.dao.PlaylistDao
import com.sangita.app.data.local.dao.SyncQueueDao
import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.data.remote.api.SangitaApiService
import com.sangita.app.data.remote.dto.AddTrackRequest
import com.sangita.app.data.remote.dto.ClonePlaylistRequest
import com.sangita.app.data.remote.dto.CreatePlaylistRequest
import com.sangita.app.data.remote.dto.PlaylistTrackActionRequest
import com.sangita.app.data.remote.dto.RecordDataUsageRequest
import com.sangita.app.data.remote.dto.SetFavoriteRequest
import com.sangita.app.data.remote.dto.StatsPingRequest
import com.sangita.app.data.remote.dto.UpdatePlaylistRequest
import com.sangita.app.domain.model.PlaylistSyncState
import com.sangita.app.domain.model.SyncActionType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

sealed class ActionExecutionResult {
    object Success : ActionExecutionResult()
    object Skip : ActionExecutionResult()
    data class Failure(val isPermanent: Boolean, val message: String) : ActionExecutionResult() {
        constructor(message: String) : this(isPermanent = false, message = message)
        val errorMessage: String get() = message
    }
}

data class QueueExecutionSummary(
    val processed: Int,
    val failed: Int,
    val skipped: Int,
    val totalRemaining: Int
)

class IncompleteSyncException(message: String) : Exception(message)

class SangitaSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SangitaApplication ?: return Result.failure()
        val syncRepo = app.container.syncRepository
        val musicRepo = app.container.musicRepository

        return try {
            // 1. Flush pending outbox actions
            syncRepo.syncPendingActions().getOrThrow()

            // 2. Fetch remote library updates
            musicRepo.refreshPlaylists().getOrThrow()

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val SYNC_WORK_NAME = "sangita_cloud_sync_worker"
        private const val PERIODIC_SYNC_WORK_NAME = "sangita_periodic_sync_worker"

        fun enqueueOneTime(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SangitaSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                SYNC_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SangitaSyncWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Checks whether an AddTrack error/status is considered successful (e.g., 409 conflict
         * where track already exists on server).
         */
        fun isAddTrackConflictSuccess(code: Int, errorBody: String? = null): Boolean {
            return SyncPayloadSerialization.isAddTrackConflictSuccess(code, errorBody)
        }

        /**
         * Processes a single pending sync queue entity using the API service and Room database.
         */
        suspend fun executeAction(
            action: SyncQueueEntity,
            apiService: SangitaApiService,
            playlistDao: PlaylistDao?,
            syncQueueDao: SyncQueueDao
        ): ActionExecutionResult {
            return try {
                when (action.actionType) {
                    SyncActionType.CREATE_PLAYLIST -> {
                        val payload = try {
                            SyncPayloadSerialization.decodeCreatePlaylist(action.payloadJson)
                        } catch (e: Exception) {
                            return ActionExecutionResult.Failure(isPermanent = true, message = "Corrupt CREATE_PLAYLIST payload: ${e.message}")
                        }

                        // If legacy payload didn't have localKey, recover by querying playlistDao.getPlaylistByTitle
                        var effectiveLocalKey = payload.localKey
                        if (playlistDao != null) {
                            val existingByTitle = playlistDao.getPlaylistByTitle(action.accountKey, payload.title)
                                ?: playlistDao.getPlaylistByTitle("default_account", payload.title)
                            if (existingByTitle != null && playlistDao.getPlaylistByKey(payload.localKey) == null) {
                                effectiveLocalKey = existingByTitle.localKey
                            }
                        }

                        var newServerId: String? = null
                        var isSuccess = false
                        var failureReason: String? = null
                        var isPermanentFailure = false

                        // Check local cached serverId first
                        val localEntity = if (playlistDao != null) {
                            playlistDao.getPlaylistByKey(effectiveLocalKey)
                                ?: playlistDao.getPlaylistByTitle(action.accountKey, payload.title)
                                ?: playlistDao.getPlaylistByTitle("default_account", payload.title)
                        } else null

                        if (!localEntity?.serverId.isNullOrBlank()) {
                            newServerId = localEntity?.serverId
                            isSuccess = true
                        }

                        if (!isSuccess) {
                            val response = apiService.createPlaylist(
                                CreatePlaylistRequest(
                                    title = payload.title,
                                    description = payload.description,
                                    visibility = payload.visibility,
                                    idempotencyKey = payload.idempotencyKey
                                )
                            )
                            if (response.isSuccessful) {
                                newServerId = response.body()?.id
                                isSuccess = true
                            } else if (response.code() == 409) {
                                isPermanentFailure = true
                                failureReason = response.errorBody()?.string() ?: "HTTP 409 Conflict"
                            } else if (response.code() == 404) {
                                val body = buildMap {
                                    put("name", payload.title)
                                    put("visibility", payload.visibility)
                                    payload.description?.let { put("description", it) }
                                }
                                val legacyResp = apiService.createLegacyPlaylist(body)
                                if (legacyResp.isSuccessful) {
                                    newServerId = legacyResp.body()?.id ?: payload.title
                                    isSuccess = true
                                } else if (legacyResp.code() == 409) {
                                    isPermanentFailure = true
                                    failureReason = legacyResp.errorBody()?.string() ?: "HTTP 409 Conflict"
                                } else {
                                    val code = legacyResp.code()
                                    isPermanentFailure = code in 400..499 && code != 408 && code != 429
                                    failureReason = legacyResp.errorBody()?.string() ?: "HTTP ${legacyResp.code()}"
                                }
                            } else {
                                val code = response.code()
                                isPermanentFailure = code in 400..499 && code != 408 && code != 429
                                failureReason = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                            }
                        }

                        if (isSuccess) {
                            val effectiveServerId = newServerId ?: payload.title
                            if (playlistDao != null) {
                                val existing = playlistDao.getPlaylistByKey(effectiveLocalKey)
                                    ?: playlistDao.getPlaylistByTitle("default_account", payload.title)
                                if (existing != null) {
                                    playlistDao.insertOrReplace(
                                        existing.copy(
                                            serverId = effectiveServerId,
                                            syncState = "SYNCED",
                                            lastSyncedAt = System.currentTimeMillis()
                                        )
                                    )
                                } else {
                                    playlistDao.insertOrReplace(
                                        PlaylistEntity(
                                            localKey = effectiveLocalKey,
                                            accountKey = action.accountKey,
                                            serverId = effectiveServerId,
                                            title = payload.title,
                                            description = payload.description,
                                            visibility = payload.visibility,
                                            syncState = "SYNCED",
                                            lastSyncedAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                            // Scan pending sync actions for this localKey (e.g. ADD_TRACK or UPDATE_PLAYLIST)
                            // and update their payloads with the new serverId
                            val pending = syncQueueDao.getPendingActions()
                            for (item in pending) {
                                if (item.id == action.id) continue
                                val updatedJson = SyncPayloadSerialization.updateServerIdInPayload(
                                    actionType = item.actionType,
                                    payloadJson = item.payloadJson,
                                    localKey = effectiveLocalKey,
                                    newServerId = effectiveServerId,
                                    playlistTitle = payload.title
                                )
                                if (updatedJson != null && updatedJson != item.payloadJson) {
                                    syncQueueDao.updatePayload(item.id, updatedJson)
                                }
                            }
                            ActionExecutionResult.Success
                        } else {
                            ActionExecutionResult.Failure(isPermanent = isPermanentFailure, message = failureReason ?: "Create playlist failed")
                        }
                    }

                    SyncActionType.UPDATE_PLAYLIST -> {
                        val payload = try {
                            SyncPayloadSerialization.decodeUpdatePlaylist(action.payloadJson)
                        } catch (e: Exception) {
                            return ActionExecutionResult.Failure(isPermanent = true, message = "Corrupt UPDATE_PLAYLIST payload: ${e.message}")
                        }

                        var effectiveLocalKey = payload.localKey
                        if (effectiveLocalKey.isBlank() && playlistDao != null) {
                            val titleQuery = payload.title ?: ""
                            val matched = if (titleQuery.isNotBlank()) {
                                playlistDao.getPlaylistByTitle(action.accountKey, titleQuery)
                                    ?: playlistDao.getPlaylistByTitle("default_account", titleQuery)
                            } else null
                            if (matched != null) {
                                effectiveLocalKey = matched.localKey
                            } else {
                                val genKey = java.util.UUID.randomUUID().toString()
                                playlistDao.insertOrReplace(
                                    PlaylistEntity(
                                        localKey = genKey,
                                        accountKey = action.accountKey,
                                        title = if (titleQuery.isNotBlank()) titleQuery else "Untitled Playlist",
                                        syncState = "PENDING_CREATE",
                                        lastSyncedAt = System.currentTimeMillis()
                                    )
                                )
                                effectiveLocalKey = genKey
                            }
                            val updatedPayload = payload.copy(localKey = effectiveLocalKey)
                            syncQueueDao.updatePayload(action.id, SyncPayloadSerialization.encodeUpdatePlaylist(updatedPayload))
                        }

                        val playlistEntity = if (playlistDao != null && effectiveLocalKey.isNotBlank()) playlistDao.getPlaylistByKey(effectiveLocalKey) else null
                        var serverId = payload.serverId
                        if (serverId.isNullOrBlank()) {
                            serverId = playlistEntity?.serverId
                        }

                        if (serverId.isNullOrBlank()) {
                            // If still null, leave pending or skip
                            ActionExecutionResult.Skip
                        } else {
                            val playlistTitle = playlistEntity?.title ?: payload.title ?: serverId
                            val response = apiService.updatePlaylist(
                                serverId,
                                UpdatePlaylistRequest(
                                    title = payload.title,
                                    description = payload.description,
                                    visibility = payload.visibility,
                                    tracks = payload.trackPaths
                                )
                            )
                            var isSuccess = false
                            var failureReason: String? = null
                            var isPermanentFailure = false

                            if (response.isSuccessful) {
                                isSuccess = true
                            } else if (response.code() == 409) {
                                if (playlistDao != null && effectiveLocalKey.isNotBlank()) {
                                    val existing = playlistDao.getPlaylistByKey(effectiveLocalKey)
                                    if (existing != null) {
                                        playlistDao.insertOrReplace(
                                            existing.copy(
                                                syncState = PlaylistSyncState.CONFLICT.name
                                            )
                                        )
                                    }
                                }
                                isPermanentFailure = true
                                failureReason = response.errorBody()?.string() ?: "HTTP 409 Conflict"
                            } else if (response.code() == 404) {
                                val body = buildMap {
                                    payload.title?.let { put("new_name", it) }
                                    payload.visibility?.let { put("visibility", it) }
                                    payload.description?.let { put("description", it) }
                                }
                                val legacyResp = apiService.updateLegacyPlaylist(playlistTitle, body)
                                if (legacyResp.isSuccessful) {
                                    isSuccess = true
                                } else if (legacyResp.code() == 409) {
                                    if (playlistDao != null && effectiveLocalKey.isNotBlank()) {
                                        val existing = playlistDao.getPlaylistByKey(effectiveLocalKey)
                                        if (existing != null) {
                                            playlistDao.insertOrReplace(
                                                existing.copy(
                                                    syncState = PlaylistSyncState.CONFLICT.name
                                                )
                                            )
                                        }
                                    }
                                    isPermanentFailure = true
                                    failureReason = legacyResp.errorBody()?.string() ?: "HTTP 409 Conflict"
                                } else {
                                    val code = legacyResp.code()
                                    isPermanentFailure = code in 400..499 && code != 408 && code != 429
                                    failureReason = legacyResp.errorBody()?.string() ?: "HTTP ${legacyResp.code()}"
                                }
                            } else {
                                val code = response.code()
                                isPermanentFailure = code in 400..499 && code != 408 && code != 429
                                failureReason = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                            }

                            if (isSuccess) {
                                if (playlistDao != null && effectiveLocalKey.isNotBlank()) {
                                    val existing = playlistDao.getPlaylistByKey(effectiveLocalKey)
                                    if (existing != null) {
                                        playlistDao.insertOrReplace(
                                            existing.copy(
                                                title = payload.title ?: existing.title,
                                                description = payload.description ?: existing.description,
                                                visibility = payload.visibility ?: existing.visibility,
                                                syncState = "SYNCED",
                                                lastSyncedAt = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                }
                                ActionExecutionResult.Success
                            } else {
                                ActionExecutionResult.Failure(isPermanent = isPermanentFailure, message = failureReason ?: "HTTP ${response.code()}")
                            }
                        }
                    }

                    SyncActionType.DELETE_PLAYLIST -> {
                        val payload = try {
                            SyncPayloadSerialization.decodeDeletePlaylist(action.payloadJson)
                        } catch (e: Exception) {
                            return ActionExecutionResult.Failure(isPermanent = true, message = "Corrupt DELETE_PLAYLIST payload: ${e.message}")
                        }

                        var effectiveLocalKey = payload.localKey
                        if (effectiveLocalKey.isBlank() && playlistDao != null) {
                            val titleQuery = payload.playlistName ?: ""
                            val matched = if (titleQuery.isNotBlank()) {
                                playlistDao.getPlaylistByTitle(action.accountKey, titleQuery)
                                    ?: playlistDao.getPlaylistByTitle("default_account", titleQuery)
                            } else null
                            if (matched != null) {
                                effectiveLocalKey = matched.localKey
                            }
                        }

                        val playlistEntity = if (playlistDao != null && effectiveLocalKey.isNotBlank()) playlistDao.getPlaylistByKey(effectiveLocalKey) else null
                        var serverId = payload.serverId ?: playlistEntity?.serverId
                        val playlistTitle = playlistEntity?.title ?: serverId ?: effectiveLocalKey

                        val isSuccess = if (!serverId.isNullOrBlank()) {
                            val response = apiService.deletePlaylist(serverId)
                            if (response.isSuccessful) {
                                true
                            } else if (response.code() == 404) {
                                val legacyResp = apiService.deleteLegacyPlaylist(playlistTitle)
                                legacyResp.isSuccessful || legacyResp.code() == 404
                            } else {
                                false
                            }
                        } else if (playlistTitle.isNotBlank()) {
                            val legacyResp = apiService.deleteLegacyPlaylist(playlistTitle)
                            legacyResp.isSuccessful || legacyResp.code() == 404
                        } else {
                            // Never created on server, local deletion is sufficient
                            true
                        }

                        if (isSuccess) {
                            if (playlistDao != null && effectiveLocalKey.isNotBlank()) {
                                playlistDao.clearTracksForPlaylist(effectiveLocalKey)
                                playlistDao.deletePlaylistByKey(effectiveLocalKey)
                            }
                            ActionExecutionResult.Success
                        } else {
                            ActionExecutionResult.Failure(isPermanent = false, message = "HTTP delete failed for ${serverId ?: playlistTitle}")
                        }
                    }

                    SyncActionType.ADD_TRACK -> {
                        val payload = try {
                            SyncPayloadSerialization.decodeAddTrack(action.payloadJson)
                        } catch (e: Exception) {
                            return ActionExecutionResult.Failure(isPermanent = true, message = "Corrupt ADD_TRACK payload: ${e.message}")
                        }

                        var effectiveLocalKey = payload.localKey
                        val playlistTitleQuery = payload.playlistName ?: ""
                        if (effectiveLocalKey.isBlank() && playlistDao != null) {
                            val matched = if (playlistTitleQuery.isNotBlank()) {
                                playlistDao.getPlaylistByTitle(action.accountKey, playlistTitleQuery)
                                    ?: playlistDao.getPlaylistByTitle("default_account", playlistTitleQuery)
                            } else null
                            if (matched != null) {
                                effectiveLocalKey = matched.localKey
                            } else {
                                val genKey = java.util.UUID.randomUUID().toString()
                                playlistDao.insertOrReplace(
                                    PlaylistEntity(
                                        localKey = genKey,
                                        accountKey = action.accountKey,
                                        title = if (playlistTitleQuery.isNotBlank()) playlistTitleQuery else "Untitled Playlist",
                                        syncState = "PENDING_CREATE",
                                        lastSyncedAt = System.currentTimeMillis()
                                    )
                                )
                                effectiveLocalKey = genKey
                            }
                            val updatedPayload = payload.copy(localKey = effectiveLocalKey)
                            syncQueueDao.updatePayload(action.id, SyncPayloadSerialization.encodeAddTrack(updatedPayload))
                        }

                        val playlistEntity = if (playlistDao != null && effectiveLocalKey.isNotBlank()) playlistDao.getPlaylistByKey(effectiveLocalKey) else null
                        var serverId = payload.serverId ?: playlistEntity?.serverId ?: playlistEntity?.title
                        val playlistTitle = playlistEntity?.title ?: playlistTitleQuery.ifBlank { null } ?: serverId ?: effectiveLocalKey

                        if (serverId.isNullOrBlank() && playlistTitle.isBlank()) {
                            // If still null, wait/retry
                            ActionExecutionResult.Skip
                        } else {
                            val targetId = serverId ?: playlistTitle
                            val response = apiService.addTrackToPlaylist(targetId, AddTrackRequest(payload.trackPath))
                            if (response.isSuccessful) {
                                ActionExecutionResult.Success
                            } else if (response.code() == 404) {
                                val legacyResp = apiService.addLegacyTrack(
                                    playlistTitle,
                                    mapOf("track_path" to payload.trackPath)
                                )
                                if (legacyResp.isSuccessful) {
                                    ActionExecutionResult.Success
                                } else {
                                    val errorBody = try { legacyResp.errorBody()?.string() } catch (_: Exception) { null }
                                    if (SyncPayloadSerialization.isAddTrackConflictSuccess(legacyResp.code(), errorBody)) {
                                        ActionExecutionResult.Success
                                    } else {
                                        val isPermanent = legacyResp.code() in 400..499 && legacyResp.code() != 408 && legacyResp.code() != 429
                                        ActionExecutionResult.Failure(isPermanent = isPermanent, message = errorBody ?: "HTTP ${legacyResp.code()}")
                                    }
                                }
                            } else {
                                val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
                                if (SyncPayloadSerialization.isAddTrackConflictSuccess(response.code(), errorBody)) {
                                    ActionExecutionResult.Success
                                } else {
                                    val isPermanent = response.code() in 400..499 && response.code() != 408 && response.code() != 429
                                    ActionExecutionResult.Failure(isPermanent = isPermanent, message = errorBody ?: "HTTP ${response.code()}")
                                }
                            }
                        }
                    }

                    SyncActionType.REMOVE_TRACK -> {
                        val payload = try {
                            SyncPayloadSerialization.decodeRemoveTrack(action.payloadJson)
                        } catch (e: Exception) {
                            return ActionExecutionResult.Failure(isPermanent = true, message = "Corrupt REMOVE_TRACK payload: ${e.message}")
                        }

                        var effectiveLocalKey = payload.localKey
                        val playlistTitleQuery = payload.playlistName ?: ""
                        if (effectiveLocalKey.isBlank() && playlistDao != null) {
                            val matched = if (playlistTitleQuery.isNotBlank()) {
                                playlistDao.getPlaylistByTitle(action.accountKey, playlistTitleQuery)
                                    ?: playlistDao.getPlaylistByTitle("default_account", playlistTitleQuery)
                            } else null
                            if (matched != null) {
                                effectiveLocalKey = matched.localKey
                            }
                        }

                        val playlistEntity = if (playlistDao != null && effectiveLocalKey.isNotBlank()) playlistDao.getPlaylistByKey(effectiveLocalKey) else null
                        var serverId = payload.serverId ?: playlistEntity?.serverId ?: playlistEntity?.title
                        val playlistTitle = playlistEntity?.title ?: playlistTitleQuery.ifBlank { null } ?: serverId ?: effectiveLocalKey

                        if (serverId.isNullOrBlank() && playlistTitle.isBlank()) {
                            // Playlist doesn't exist on server, local removal was already performed
                            ActionExecutionResult.Success
                        } else {
                            val targetId = serverId ?: playlistTitle
                            val response = apiService.removeTrackFromPlaylist(targetId, payload.trackPath)
                            if (response.isSuccessful) {
                                ActionExecutionResult.Success
                            } else if (response.code() == 404) {
                                val legacyResp = apiService.removeLegacyTrack(playlistTitle, payload.trackPath)
                                if (legacyResp.isSuccessful || legacyResp.code() == 404) {
                                    ActionExecutionResult.Success
                                } else {
                                    val isPermanent = legacyResp.code() in 400..499 && legacyResp.code() != 408 && legacyResp.code() != 429
                                    val err = legacyResp.errorBody()?.string() ?: "HTTP ${legacyResp.code()}"
                                    ActionExecutionResult.Failure(isPermanent = isPermanent, message = err)
                                }
                            } else {
                                val isPermanent = response.code() in 400..499 && response.code() != 408 && response.code() != 429
                                val err = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                                ActionExecutionResult.Failure(isPermanent = isPermanent, message = err)
                            }
                        }
                    }

                    SyncActionType.CLONE_PLAYLIST -> {
                        val payload = try {
                            SyncPayloadSerialization.decodeClonePlaylist(action.payloadJson)
                        } catch (e: Exception) {
                            return ActionExecutionResult.Failure(isPermanent = true, message = "Corrupt CLONE_PLAYLIST payload: ${e.message}")
                        }

                        var newServerId: String? = null
                        var isSuccess = false
                        var failureReason: String? = null
                        var isPermanent = false

                        val localEntity = if (playlistDao != null) {
                            playlistDao.getPlaylistByKey(action.accountKey, payload.newLocalKey)
                                ?: playlistDao.getPlaylistByKey(payload.newLocalKey)
                                ?: (payload.title?.let {
                                    playlistDao.getPlaylistByTitle(action.accountKey, it)
                                        ?: playlistDao.getPlaylistByTitle("default_account", it)
                                })
                        } else null

                        if (!localEntity?.serverId.isNullOrBlank()) {
                            newServerId = localEntity?.serverId
                            isSuccess = true
                        }

                        if (!isSuccess) {
                            val response = apiService.clonePlaylist(
                                payload.sourceServerId,
                                ClonePlaylistRequest(
                                    title = payload.title,
                                    description = payload.description,
                                    visibility = payload.visibility,
                                    idempotencyKey = payload.idempotencyKey
                                )
                            )
                            if (response.isSuccessful) {
                                newServerId = response.body()?.id
                                isSuccess = true
                            } else if (response.code() == 404) {
                                isPermanent = true
                                failureReason = "This feature requires Sangita Server V2 or newer."
                            } else if (response.code() == 409) {
                                isPermanent = true
                                failureReason = response.errorBody()?.string() ?: "HTTP 409 Conflict"
                            } else {
                                isPermanent = response.code() in 400..499 && response.code() != 408 && response.code() != 429
                                failureReason = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                            }
                        }

                        if (isSuccess && newServerId != null) {
                            if (playlistDao != null) {
                                val existing = playlistDao.getPlaylistByKey(action.accountKey, payload.newLocalKey)
                                    ?: playlistDao.getPlaylistByKey(payload.newLocalKey)
                                if (existing != null) {
                                    playlistDao.insertOrReplace(
                                        existing.copy(
                                            serverId = newServerId,
                                            syncState = "SYNCED",
                                            lastSyncedAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                            val pending = syncQueueDao.getPendingActions()
                            for (item in pending) {
                                if (item.id == action.id) continue
                                val updatedJson = SyncPayloadSerialization.updateServerIdInPayload(
                                    actionType = item.actionType,
                                    payloadJson = item.payloadJson,
                                    localKey = payload.newLocalKey,
                                    newServerId = newServerId,
                                    playlistTitle = payload.title
                                )
                                if (updatedJson != null && updatedJson != item.payloadJson) {
                                    syncQueueDao.updatePayload(item.id, updatedJson)
                                }
                            }
                            ActionExecutionResult.Success
                        } else if (isSuccess) {
                            ActionExecutionResult.Success
                        } else {
                            ActionExecutionResult.Failure(isPermanent = isPermanent, message = failureReason ?: "Clone playlist failed")
                        }
                    }

                    SyncActionType.TOGGLE_FAVORITE -> {
                        val payload = try {
                            SyncPayloadSerialization.decodeToggleFavorite(action.payloadJson)
                        } catch (e: Exception) {
                            return ActionExecutionResult.Failure(isPermanent = true, message = "Corrupt TOGGLE_FAVORITE payload: ${e.message}")
                        }
                        val response = apiService.setFavorite(
                            SetFavoriteRequest(
                                trackPath = payload.trackId,
                                isFavorite = payload.isFavorite
                            )
                        )
                        if (response.isSuccessful) {
                            ActionExecutionResult.Success
                        } else {
                            val isPermanent = response.code() in 400..499 && response.code() != 408 && response.code() != 429
                            val err = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                            ActionExecutionResult.Failure(isPermanent = isPermanent, message = err)
                        }
                    }

                    // Legacy actions
                    SyncActionType.SET_FAVORITE -> {
                        val req = try {
                            SyncPayloadSerialization.json.decodeFromString<SetFavoriteRequest>(action.payloadJson)
                        } catch (e: Exception) {
                            return ActionExecutionResult.Failure(isPermanent = true, message = "Corrupt SET_FAVORITE: ${e.message}")
                        }
                        val response = apiService.setFavorite(req)
                        if (response.isSuccessful) ActionExecutionResult.Success
                        else {
                            val isPermanent = response.code() in 400..499 && response.code() != 408 && response.code() != 429
                            ActionExecutionResult.Failure(isPermanent = isPermanent, message = response.errorBody()?.string() ?: "HTTP ${response.code()}")
                        }
                    }
                    SyncActionType.ADD_TRACK_TO_PLAYLIST -> {
                        val obj = try {
                            SyncPayloadSerialization.json.parseToJsonElement(action.payloadJson).jsonObject
                        } catch (e: Exception) {
                            return ActionExecutionResult.Failure(isPermanent = true, message = "Corrupt ADD_TRACK_TO_PLAYLIST: ${e.message}")
                        }
                        val localKey = obj["local_key"]?.jsonPrimitive?.content ?: obj["localKey"]?.jsonPrimitive?.content
                        val localEntity = if (!localKey.isNullOrBlank() && playlistDao != null) {
                            playlistDao.getPlaylistByKey(action.accountKey, localKey) ?: playlistDao.getPlaylistByKey(localKey)
                        } else null
                        val pl = localEntity?.title ?: obj["playlist"]?.jsonPrimitive?.content ?: obj["name"]?.jsonPrimitive?.content ?: ""
                        val track = obj["track_path"]?.jsonPrimitive?.content ?: obj["track_id"]?.jsonPrimitive?.content ?: ""
                        val response = apiService.addTrackToUserPlaylist(pl, PlaylistTrackActionRequest(trackPath = track))
                        if (response.isSuccessful) ActionExecutionResult.Success
                        else {
                            val isPermanent = response.code() in 400..499 && response.code() != 408 && response.code() != 429
                            ActionExecutionResult.Failure(isPermanent = isPermanent, message = response.errorBody()?.string() ?: "HTTP ${response.code()}")
                        }
                    }
                    SyncActionType.REMOVE_TRACK_FROM_PLAYLIST -> {
                        val obj = try {
                            SyncPayloadSerialization.json.parseToJsonElement(action.payloadJson).jsonObject
                        } catch (e: Exception) {
                            return ActionExecutionResult.Failure(isPermanent = true, message = "Corrupt REMOVE_TRACK_FROM_PLAYLIST: ${e.message}")
                        }
                        val localKey = obj["local_key"]?.jsonPrimitive?.content ?: obj["localKey"]?.jsonPrimitive?.content
                        val localEntity = if (!localKey.isNullOrBlank() && playlistDao != null) {
                            playlistDao.getPlaylistByKey(action.accountKey, localKey) ?: playlistDao.getPlaylistByKey(localKey)
                        } else null
                        val pl = localEntity?.title ?: obj["playlist"]?.jsonPrimitive?.content ?: obj["name"]?.jsonPrimitive?.content ?: ""
                        val track = obj["track_path"]?.jsonPrimitive?.content ?: obj["track_id"]?.jsonPrimitive?.content ?: ""
                        val response = apiService.removeTrackFromUserPlaylist(pl, PlaylistTrackActionRequest(trackPath = track))
                        if (response.isSuccessful) ActionExecutionResult.Success
                        else {
                            val isPermanent = response.code() in 400..499 && response.code() != 408 && response.code() != 429
                            ActionExecutionResult.Failure(isPermanent = isPermanent, message = response.errorBody()?.string() ?: "HTTP ${response.code()}")
                        }
                    }
                    SyncActionType.PLAY_EVENT -> {
                        val req = try {
                            SyncPayloadSerialization.json.decodeFromString<StatsPingRequest>(action.payloadJson)
                        } catch (e: Exception) {
                            return ActionExecutionResult.Failure(isPermanent = true, message = "Corrupt PLAY_EVENT: ${e.message}")
                        }
                        val response = apiService.pingStats(req)
                        if (response.isSuccessful) ActionExecutionResult.Success
                        else {
                            val isPermanent = response.code() in 400..499 && response.code() != 408 && response.code() != 429
                            ActionExecutionResult.Failure(isPermanent = isPermanent, message = response.errorBody()?.string() ?: "HTTP ${response.code()}")
                        }
                    }
                    SyncActionType.DATA_CONSUMPTION -> {
                        val req = try {
                            SyncPayloadSerialization.json.decodeFromString<RecordDataUsageRequest>(action.payloadJson)
                        } catch (e: Exception) {
                            return ActionExecutionResult.Failure(isPermanent = true, message = "Corrupt DATA_CONSUMPTION: ${e.message}")
                        }
                        val response = apiService.recordDataUsage(req)
                        if (response.isSuccessful) ActionExecutionResult.Success
                        else {
                            val isPermanent = response.code() in 400..499 && response.code() != 408 && response.code() != 429
                            ActionExecutionResult.Failure(isPermanent = isPermanent, message = response.errorBody()?.string() ?: "HTTP ${response.code()}")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val isPermanent = e is kotlinx.serialization.SerializationException || e is IllegalArgumentException
                ActionExecutionResult.Failure(isPermanent = isPermanent, message = e.localizedMessage ?: e.message ?: "Unknown error")
            }
        }

        /**
         * Executes the pending actions queue sequentially.
         * Returns a [QueueExecutionSummary] containing counts of processed, failed, skipped, and remaining actions.
         */
        suspend fun executeSyncQueue(
            apiService: SangitaApiService,
            playlistDao: PlaylistDao?,
            syncQueueDao: SyncQueueDao,
            accountKey: String = "default_account"
        ): QueueExecutionSummary {
            val pending = syncQueueDao.getPendingActions(accountKey)
            if (pending.isEmpty()) {
                return QueueExecutionSummary(processed = 0, failed = 0, skipped = 0, totalRemaining = 0)
            }

            var processedCount = 0
            var failedCount = 0
            var skippedCount = 0

            for (action in pending) {
                when (val result = executeAction(action, apiService, playlistDao, syncQueueDao)) {
                    is ActionExecutionResult.Success -> {
                        syncQueueDao.deleteAction(action.id)
                        processedCount++
                    }
                    is ActionExecutionResult.Skip -> {
                        // Action dependency is not yet resolved; leave it pending
                        skippedCount++
                    }
                    is ActionExecutionResult.Failure -> {
                        failedCount++
                        syncQueueDao.incrementRetry(action.id, result.message)
                        if (result.isPermanent) {
                            // Permanent failure: mark failed in recoverable state; do NOT delete or crash!
                            // Allow UNRELATED subsequent actions to continue processing.
                            syncQueueDao.markActionFailed(
                                actionId = action.id,
                                failureReason = result.message,
                                failedAt = System.currentTimeMillis()
                            )
                        } else {
                            // Transient failure: stop processing further actions
                            break
                        }
                    }
                }
            }
            val totalRemaining = syncQueueDao.getQueueSize(accountKey)
            return QueueExecutionSummary(
                processed = processedCount,
                failed = failedCount,
                skipped = skippedCount,
                totalRemaining = totalRemaining
            )
        }
    }
}
