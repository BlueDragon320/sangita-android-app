package com.sangita.app.data.repository

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
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.SyncActionType
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MusicRepositoryImpl(
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
            trackDao.observeTracksByPlaylist(playlistName).map { entities ->
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
            val newFavStatus = !track.isFavorite
            // 1. Optimistic Local Update
            trackDao.updateFavoriteStatus(track.id, newFavStatus)
            if (newFavStatus) {
                favoriteDao.insert(FavoriteEntity(trackId = track.id))
            } else {
                favoriteDao.delete(track.id)
            }

            // 2. Queue into Sync Outbox
            val payload = json.encodeToString(ToggleFavoriteRequest(trackPath = track.id))
            val actionId = syncQueueDao.enqueueAction(
                SyncQueueEntity(
                    actionType = SyncActionType.TOGGLE_FAVORITE,
                    payloadJson = payload
                )
            )

            // 3. Try immediate flush if token & network available
            try {
                val api = networkClient.createApiService()
                val resp = api.toggleFavorite(ToggleFavoriteRequest(trackPath = track.id))
                if (resp.isSuccessful) {
                    syncQueueDao.deleteAction(actionId)
                }
            } catch (_: Exception) {
                // Kept in syncQueue for background sync worker
            }

            Result.success(newFavStatus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createPlaylist(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = PlaylistEntity(
                name = name,
                isVirtual = true,
                isFavorites = false,
                trackCount = 0
            )
            playlistDao.insertOrReplace(entity)

            val payload = json.encodeToString(CreateUserPlaylistRequest(name = name))
            val actionId = syncQueueDao.enqueueAction(
                SyncQueueEntity(
                    actionType = SyncActionType.CREATE_PLAYLIST,
                    payloadJson = payload
                )
            )

            try {
                val api = networkClient.createApiService()
                val resp = api.createUserPlaylist(CreateUserPlaylistRequest(name = name))
                if (resp.isSuccessful) {
                    syncQueueDao.deleteAction(actionId)
                }
            } catch (_: Exception) {
                // Kept in queue
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addTrackToPlaylist(playlistName: String, track: Track): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            playlistDao.insertCrossRef(
                PlaylistTrackCrossRef(
                    playlistName = playlistName,
                    trackId = track.id
                )
            )

            val payload = json.encodeToString(
                mapOf("playlist" to playlistName, "track_path" to track.id)
            )
            val actionId = syncQueueDao.enqueueAction(
                SyncQueueEntity(
                    actionType = SyncActionType.ADD_TRACK_TO_PLAYLIST,
                    payloadJson = payload
                )
            )

            try {
                val api = networkClient.createApiService()
                val resp = api.addTrackToUserPlaylist(
                    name = playlistName,
                    request = PlaylistTrackActionRequest(trackPath = track.id)
                )
                if (resp.isSuccessful) {
                    syncQueueDao.deleteAction(actionId)
                }
            } catch (_: Exception) {
                // Kept in queue
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeTrackFromPlaylist(playlistName: String, track: Track): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            playlistDao.deleteCrossRef(playlistName, track.id)

            val payload = json.encodeToString(
                mapOf("playlist" to playlistName, "track_path" to track.id)
            )
            val actionId = syncQueueDao.enqueueAction(
                SyncQueueEntity(
                    actionType = SyncActionType.REMOVE_TRACK_FROM_PLAYLIST,
                    payloadJson = payload
                )
            )

            try {
                val api = networkClient.createApiService()
                val resp = api.removeTrackFromUserPlaylist(
                    name = playlistName,
                    request = PlaylistTrackActionRequest(trackPath = track.id)
                )
                if (resp.isSuccessful) {
                    syncQueueDao.deleteAction(actionId)
                }
            } catch (_: Exception) {
                // Kept in queue
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshPlaylists(): Result<Unit> = withContext(Dispatchers.IO) {
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
            val remoteFavorites = (favoritesResp.body() ?: emptyList()).toSet()

            // Update Favorites table
            remoteFavorites.forEach { favId ->
                favoriteDao.insert(FavoriteEntity(trackId = favId))
            }

            val playlistEntities = mutableListOf<PlaylistEntity>()
            val trackEntities = mutableListOf<TrackEntity>()

            playlistsMap.forEach { (plName, trackPaths) ->
                val isFav = plName == "Favorites"
                val coverTrack = if (trackPaths.isEmpty()) null else trackPaths.take(4).joinToString(",")

                playlistEntities.add(
                    PlaylistEntity(
                        name = plName,
                        isVirtual = isFav,
                        isFavorites = isFav,
                        trackCount = trackPaths.size,
                        coverTrackPath = coverTrack,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )

                trackPaths.forEach { path ->
                    val existing = trackDao.getTrackById(path)
                    val (title, artist) = parseTrackMetadata(path)
                    val duration = durationsMap[path] ?: existing?.durationSeconds
                    val isFavorite = remoteFavorites.contains(path) || (existing?.isFavorite == true)

                    val audioFile = localStorageManager.getAudioFile(path)
                    val isDownloadedLocally = audioFile.exists() && audioFile.length() > 0

                    trackEntities.add(
                        TrackEntity(
                            id = path,
                            playlistName = plName,
                            title = title,
                            artist = artist,
                            durationSeconds = duration,
                            remoteUrl = "${userPreferences.getServerUrl()}/api/stream/$path",
                            localFilePath = if (isDownloadedLocally) audioFile.absolutePath else existing?.localFilePath,
                            isDownloaded = isDownloadedLocally || (existing?.isDownloaded == true),
                            downloadStatus = existing?.downloadStatus ?: com.sangita.app.domain.model.DownloadStatus.IDLE,
                            downloadProgress = if (isDownloadedLocally) 100 else (existing?.downloadProgress ?: 0),
                            fileSizeBytes = if (isDownloadedLocally) audioFile.length() else (existing?.fileSizeBytes ?: 0L),
                            isFavorite = isFavorite,
                            downloadedAt = existing?.downloadedAt
                        )
                    )
                }
            }

            playlistDao.insertOrReplaceAll(playlistEntities)
            trackDao.insertOrReplaceAll(trackEntities)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
