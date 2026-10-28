package com.sangita.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.sangita.app.data.local.database.SangitaDatabase
import com.sangita.app.sync.SangitaSyncWorker
import com.sangita.app.data.local.dao.FavoriteDao
import com.sangita.app.data.local.dao.PlaylistDao
import com.sangita.app.data.local.dao.SyncQueueDao
import com.sangita.app.data.local.dao.TrackDao
import com.sangita.app.data.local.entity.FavoriteEntity
import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.data.local.entity.PlaylistTrackCrossRef
import com.sangita.app.data.local.entity.SyncQueueEntity
import com.sangita.app.data.local.entity.TrackEntity
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.data.remote.dto.CreateUserPlaylistRequest
import com.sangita.app.data.remote.dto.PlaylistTrackActionRequest
import com.sangita.app.data.remote.dto.ToggleFavoriteRequest
import com.sangita.app.data.remote.dto.SetFavoriteRequest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.SyncActionType
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MusicRepositoryImpl(
    private val context: Context,
    private val database: SangitaDatabase,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val favoriteDao: FavoriteDao,
    private val syncQueueDao: SyncQueueDao,
    private val localStorageManager: LocalStorageManager,
    private val userPreferences: UserPreferences,
    private val networkClient: NetworkClient
) : MusicRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun observeAllTracks(): Flow<List<Track>> {
        return trackDao.observeAllTracks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observePlaylists(): Flow<List<Playlist>> {
        return playlistDao.observeAllPlaylists().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeTracks(playlistName: String): Flow<List<Track>> {
        return if (playlistName == "Favorites") {
            trackDao.observeFavoriteTracks().map { entities -> entities.map { it.toDomain() } }
        } else {
            playlistDao.observeTracksForPlaylist(playlistName).map { entities ->
                entities.map { it.toDomain() }
            }
        }
    }

    override fun observeFavorites(): Flow<List<Track>> {
        return trackDao.observeFavoriteTracks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeDownloadedTracks(): Flow<List<Track>> {
        return trackDao.observeDownloadedTracks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun toggleFavorite(track: Track): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val newFavStatus = database.withTransaction {
                val desired = !(trackDao.getTrackById(track.id)?.isFavorite ?: track.isFavorite)
                trackDao.updateFavoriteStatus(track.id, desired)
                if (desired) favoriteDao.insert(FavoriteEntity(trackId = track.id)) else favoriteDao.delete(track.id)
                val payload = json.encodeToString(SetFavoriteRequest(trackPath = track.id, isFavorite = desired))
                syncQueueDao.enqueueAction(SyncQueueEntity(actionType = SyncActionType.SET_FAVORITE, payloadJson = payload))
                desired
            }
            SangitaSyncWorker.enqueueOneTime(context)

            Result.success(newFavStatus)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createPlaylist(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                playlistDao.insertOrReplace(PlaylistEntity(name = name, isVirtual = true, isFavorites = false, trackCount = 0))
                val payload = json.encodeToString(CreateUserPlaylistRequest(name = name))
                syncQueueDao.enqueueAction(SyncQueueEntity(actionType = SyncActionType.CREATE_PLAYLIST, payloadJson = payload))
            }
            SangitaSyncWorker.enqueueOneTime(context)

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addTrackToPlaylist(playlistName: String, track: Track): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                playlistDao.insertCrossRef(PlaylistTrackCrossRef(
                    playlistName = playlistName, trackId = track.id,
                    position = playlistDao.nextTrackPosition(playlistName)))
                val payload = json.encodeToString(mapOf("playlist" to playlistName, "track_path" to track.id))
                syncQueueDao.enqueueAction(SyncQueueEntity(actionType = SyncActionType.ADD_TRACK_TO_PLAYLIST, payloadJson = payload))
            }
            SangitaSyncWorker.enqueueOneTime(context)

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeTrackFromPlaylist(playlistName: String, track: Track): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                playlistDao.deleteCrossRef(playlistName, track.id)
                val payload = json.encodeToString(mapOf("playlist" to playlistName, "track_path" to track.id))
                syncQueueDao.enqueueAction(SyncQueueEntity(actionType = SyncActionType.REMOVE_TRACK_FROM_PLAYLIST, payloadJson = payload))
            }
            SangitaSyncWorker.enqueueOneTime(context)

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshPlaylists(): Result<Unit> = SyncSerialization.mutex.withLock { withContext(Dispatchers.IO) {
        try {
            val api = networkClient.createApiService()

            // 1. Fetch remote playlists map & durations
            val playlistsResp = api.getPlaylists()
            val durationsResp = api.getDurations()
            val favoritesResp = api.getFavorites()

            if (!playlistsResp.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch playlists: ${playlistsResp.code()}"))
            }

            val playlistsMap = playlistsResp.body() ?: emptyMap()
            val durationsMap = durationsResp.body() ?: emptyMap()
            val favoritesAvailable = favoritesResp.isSuccessful
            val remoteFavorites = (favoritesResp.body() ?: emptyList()).toSet()
            // Metadata is optional so this client can still connect to older servers.
            val metadata = try {
                val response = api.getTrackMetadata()
                if (response.isSuccessful) response.body() ?: emptyMap() else emptyMap()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }

            database.withTransaction {
                val pending = syncQueueDao.getPendingActions()
                val pendingFavorites = mutableMapOf<String, Boolean>()
                val pendingPlaylists = mutableSetOf<String>()
                for (action in pending) {
                    try {
                        val payload = json.parseToJsonElement(action.payloadJson).jsonObject
                        when (action.actionType) {
                            SyncActionType.SET_FAVORITE -> {
                                val request = json.decodeFromString<SetFavoriteRequest>(action.payloadJson)
                                pendingFavorites[request.trackPath] = request.isFavorite
                            }
                            SyncActionType.TOGGLE_FAVORITE -> {
                                val path = payload["track_path"]?.jsonPrimitive?.content
                                if (path != null) pendingFavorites[path] = trackDao.getTrackById(path)?.isFavorite ?: false
                            }
                            SyncActionType.ADD_TRACK_TO_PLAYLIST, SyncActionType.REMOVE_TRACK_FROM_PLAYLIST ->
                                payload["playlist"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            SyncActionType.CREATE_PLAYLIST -> payload["name"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            else -> Unit
                        }
                    } catch (_: Exception) { /* Keep existing local state for malformed pending actions. */ }
                }
                val desiredFavorites = reconcileFavorites(remoteFavorites, pendingFavorites)
                if (favoritesAvailable) {
                    favoriteDao.clearAll()
                    trackDao.clearFavoriteStatuses()
                    desiredFavorites.forEach { favoriteDao.insert(FavoriteEntity(trackId = it)) }
                }
                val playlistEntities = mutableListOf<PlaylistEntity>()
                val trackEntities = linkedMapOf<String, TrackEntity>()
                playlistsMap.forEach { (plName, trackPaths) ->
                    val isFav = plName == "Favorites"
                    playlistEntities.add(PlaylistEntity(name = plName, isVirtual = isFav,
                        isFavorites = isFav, trackCount = trackPaths.size,
                        coverTrackPath = trackPaths.take(4).joinToString(",").ifBlank { null },
                        lastSyncedAt = System.currentTimeMillis()))
                    if (!isFav && plName !in pendingPlaylists) {
                        playlistDao.clearTracksForPlaylist(plName)
                        playlistDao.insertCrossRefs(playlistMemberships(plName, trackPaths))
                    }
                    trackPaths.forEach { path ->
                        if (path !in trackEntities) {
                            val existing = trackDao.getTrackById(path)
                            val (fallbackTitle, fallbackArtist) = parseTrackMetadata(path)
                            val tags = metadata[path]
                            val audioFile = localStorageManager.getExistingAudioFile(path, existing?.localFilePath)
                            val isDownloadedLocally = audioFile != null
                            val sourcePlaylist = path.substringBeforeLast('/', "Library")
                            trackEntities[path] = TrackEntity(
                                id = path, playlistName = sourcePlaylist,
                                title = tags?.title?.takeIf { it.isNotBlank() } ?: existing?.title ?: fallbackTitle,
                                artist = tags?.artist?.takeIf { it.isNotBlank() } ?: existing?.artist ?: fallbackArtist,
                                album = tags?.album?.takeIf { it.isNotBlank() } ?: existing?.album.orEmpty(),
                                durationSeconds = durationsMap[path] ?: existing?.durationSeconds,
                                remoteUrl = "${userPreferences.getServerUrl()}/api/stream/$path",
                                localFilePath = audioFile?.absolutePath ?: existing?.localFilePath,
                                isDownloaded = isDownloadedLocally,
                                downloadStatus = existing?.downloadStatus ?: com.sangita.app.domain.model.DownloadStatus.IDLE,
                                downloadProgress = if (isDownloadedLocally) 100 else (existing?.downloadProgress ?: 0),
                                fileSizeBytes = audioFile?.length() ?: (existing?.fileSizeBytes ?: 0L),
                                isFavorite = if (favoritesAvailable) path in desiredFavorites else existing?.isFavorite ?: false,
                                downloadedAt = existing?.downloadedAt)
                        }
                    }
                }
                playlistDao.insertOrReplaceAll(playlistEntities)
                trackDao.insertOrReplaceAll(trackEntities.values.toList())
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    } }

    private fun parseTrackMetadata(path: String): Pair<String, String> {
        val filename = path.substringAfterLast('/').substringBeforeLast('.')
        return if (filename.contains(" - ")) {
            val parts = filename.split(" - ", limit = 2)
            parts[1].trim() to parts[0].trim()
        } else {
            filename.trim() to "Unknown Artist"
        }
    }
}
