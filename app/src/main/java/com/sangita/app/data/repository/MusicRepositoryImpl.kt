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
import com.sangita.app.data.remote.api.SangitaApiService
import com.sangita.app.data.remote.dto.AddArtistCollectionRequest
import com.sangita.app.data.remote.dto.ArtistDetailDto
import com.sangita.app.data.remote.dto.ArtistDto
import com.sangita.app.data.remote.dto.CatalogSourceDto
import com.sangita.app.data.remote.dto.PlaylistDetailDto
import com.sangita.app.data.remote.dto.PlaylistDto
import com.sangita.app.data.remote.dto.SaveTrackRequest
import com.sangita.app.data.remote.dto.SetFavoriteRequest
import com.sangita.app.domain.model.DownloadStatus
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.PlaylistKind
import com.sangita.app.domain.model.PlaylistSyncState
import com.sangita.app.domain.model.SyncActionType
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.ArtistAddCollectionResult
import com.sangita.app.domain.repository.MusicRepository
import com.sangita.app.sync.AddTrackPayload
import com.sangita.app.sync.ClonePlaylistPayload
import com.sangita.app.sync.CreatePlaylistPayload
import com.sangita.app.sync.DeletePlaylistPayload
import com.sangita.app.sync.RemoveTrackPayload
import com.sangita.app.sync.SyncPayloadSerialization
import com.sangita.app.sync.UpdatePlaylistPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class MusicRepositoryImpl(
    private val context: Context,
    private val database: SangitaDatabase,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val favoriteDao: FavoriteDao,
    private val syncQueueDao: SyncQueueDao,
    private val localStorageManager: LocalStorageManager,
    private val userPreferences: UserPreferences,
    private val networkClient: NetworkClient,
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : MusicRepository {

    private val json = Json { ignoreUnknownKeys = true }

    internal var v2Supported: Boolean = true

    override suspend fun checkV2Support(): Boolean = withContext(Dispatchers.IO) {
        try {
            val api = networkClient.createApiService()
            val response = api.getPlaylists()
            if (response.code() == 200 || response.isSuccessful) {
                v2Supported = true
            } else if (response.code() == 404) {
                v2Supported = false
            }
            // If returns 401 or network error / timeout: do NOT assume V2 is absent (keep current capability state)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep current capability state on network error or timeout
        }
        v2Supported
    }

    // In-memory StateFlows for discovery UI
    private val _catalogSources = MutableStateFlow<List<CatalogSourceDto>>(emptyList())
    private val _artists = MutableStateFlow<List<ArtistDto>>(emptyList())
    private val _savedArtists = MutableStateFlow<List<ArtistDto>>(emptyList())
    private val _savedTracks = MutableStateFlow<List<Track>>(emptyList())

    init {
        externalScope.launch {
            var previousAccountKey = userPreferences.getAccountKey()
            userPreferences.observeAccountKey().collect { currentAccountKey ->
                if (currentAccountKey != previousAccountKey) {
                    _catalogSources.value = emptyList()
                    _artists.value = emptyList()
                    _savedArtists.value = emptyList()
                    _savedTracks.value = emptyList()
                    try {
                        favoriteDao.clearAll()
                        trackDao.clearFavoriteStatuses()
                    } catch (_: Exception) {}
                    if (currentAccountKey != "default_account" && previousAccountKey == "default_account" && !userPreferences.isInitialDefaultAccountMigrated()) {
                        try {
                            migrateDefaultAccountPlaylistsInternal(currentAccountKey)
                        } catch (_: Exception) {}
                    }
                    previousAccountKey = currentAccountKey
                } else if (currentAccountKey != "default_account" && !userPreferences.isInitialDefaultAccountMigrated()) {
                    try {
                        migrateDefaultAccountPlaylistsInternal(currentAccountKey)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun clearMemoryCache() {
        _catalogSources.value = emptyList()
        _artists.value = emptyList()
        _savedArtists.value = emptyList()
        _savedTracks.value = emptyList()
        externalScope.launch {
            try {
                favoriteDao.clearAll()
                trackDao.clearFavoriteStatuses()
            } catch (_: Exception) {}
        }
    }

    suspend fun resetFavoritesCache() = withContext(Dispatchers.IO) {
        favoriteDao.clearAll()
        trackDao.clearFavoriteStatuses()
    }

    suspend fun migrateDefaultAccountPlaylistsInternal(
        accountKey: String,
        remotePlaylists: List<PlaylistDto> = emptyList()
    ): List<PlaylistEntity> = withContext(Dispatchers.IO) {
        if (accountKey == "default_account" || accountKey.isBlank()) {
            return@withContext emptyList()
        }
        if (userPreferences.isInitialDefaultAccountMigrated()) {
            return@withContext emptyList()
        }
        val defaultPlaylists = playlistDao.getAllPlaylists("default_account")
        if (defaultPlaylists.isEmpty()) {
            userPreferences.setInitialDefaultAccountMigrated(true)
            return@withContext emptyList()
        }
        val activeLocals = playlistDao.getAllPlaylists(accountKey)
        val toMigrate = findUnambiguousDefaultAccountPlaylistsToMigrate(
            defaultPlaylists = defaultPlaylists,
            activeAccountPlaylists = activeLocals,
            remotePlaylists = remotePlaylists,
            targetAccountKey = accountKey,
            targetUsername = userPreferences.getUsername()
        )
        for (entity in toMigrate) {
            playlistDao.insertOrReplace(entity)
            val matchingTitleCount = defaultPlaylists.count { it.title.equals(entity.title, ignoreCase = true) }
            val isTitleAmbiguous = matchingTitleCount > 1
            syncQueueDao.migrateActionsForPlaylist(
                localKey = entity.localKey,
                playlistTitle = entity.title,
                targetAccountKey = accountKey,
                newTitle = entity.title,
                isTitleAmbiguous = isTitleAmbiguous
            )
            syncQueueDao.migrateActionsToAccount(entity.localKey, accountKey)
        }
        userPreferences.setInitialDefaultAccountMigrated(true)
        toMigrate
    }

    override suspend fun migrateDefaultAccountPlaylists(accountKey: String): Result<Int> {
        return try {
            val migrated = migrateDefaultAccountPlaylistsInternal(accountKey, emptyList())
            Result.success(migrated.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeRecoverableLegacyPlaylists(): Flow<List<Playlist>> {
        return playlistDao.observeRecoverableLegacyPlaylists().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getRecoverableLegacyPlaylists(): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        try {
            val list = playlistDao.getRecoverableLegacyPlaylists().map { it.toDomain() }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recoverLegacyPlaylist(localKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accountKey = userPreferences.getAccountKey()
            if (accountKey == "default_account" || accountKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("No active account to recover into"))
            }
            val username = userPreferences.getUsername()
            database.withTransaction {
                val entity = playlistDao.getPlaylistByKey("default_account", localKey)
                    ?: return@withTransaction Result.failure(NoSuchElementException("Playlist not found in default_account"))

                val defaultPlaylists = playlistDao.getAllPlaylists("default_account")
                val matchingTitleCount = defaultPlaylists.count { it.title.equals(entity.title, ignoreCase = true) }
                val isTitleAmbiguous = matchingTitleCount > 1

                val existingActive = playlistDao.getPlaylistByTitle(accountKey, entity.title)
                val targetTitle = if (existingActive != null) "${entity.title} (Recovered)" else entity.title

                val recovered = entity.copy(
                    accountKey = accountKey,
                    title = targetTitle,
                    owner = username ?: entity.owner,
                    kind = PlaylistKind.PERSONAL.name
                )
                playlistDao.insertOrReplace(recovered)
                syncQueueDao.migrateActionsForPlaylist(
                    localKey = entity.localKey,
                    playlistTitle = entity.title,
                    targetAccountKey = accountKey,
                    newTitle = targetTitle,
                    isTitleAmbiguous = isTitleAmbiguous
                )
                syncQueueDao.migrateActionsToAccount(entity.localKey, accountKey)
                Result.success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recoverAllLegacyPlaylists(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val accountKey = userPreferences.getAccountKey()
            if (accountKey == "default_account" || accountKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("No active account to recover into"))
            }
            val username = userPreferences.getUsername()
            val unassigned = playlistDao.getRecoverableLegacyPlaylists()
            var recoveredCount = 0
            database.withTransaction {
                val defaultPlaylists = playlistDao.getAllPlaylists("default_account")
                for (entity in unassigned) {
                    val matchingTitleCount = defaultPlaylists.count { it.title.equals(entity.title, ignoreCase = true) }
                    val isTitleAmbiguous = matchingTitleCount > 1

                    val existingActive = playlistDao.getPlaylistByTitle(accountKey, entity.title)
                    val targetTitle = if (existingActive != null) "${entity.title} (Recovered)" else entity.title
                    val recovered = entity.copy(
                        accountKey = accountKey,
                        title = targetTitle,
                        owner = username ?: entity.owner,
                        kind = PlaylistKind.PERSONAL.name
                    )
                    playlistDao.insertOrReplace(recovered)
                    syncQueueDao.migrateActionsForPlaylist(
                        localKey = entity.localKey,
                        playlistTitle = entity.title,
                        targetAccountKey = accountKey,
                        newTitle = targetTitle,
                        isTitleAmbiguous = isTitleAmbiguous
                    )
                    syncQueueDao.migrateActionsToAccount(entity.localKey, accountKey)
                    recoveredCount++
                }
            }
            Result.success(recoveredCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getCatalogSources(): Flow<List<CatalogSourceDto>> = _catalogSources.asStateFlow()

    override suspend fun refreshCatalogSources(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val api = networkClient.createApiService()
            val resp = api.getCatalogSources()
            if (resp.isSuccessful) {
                _catalogSources.value = resp.body()?.sources ?: emptyList()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to fetch catalog sources: ${resp.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getArtists(): Flow<List<ArtistDto>> = _artists.asStateFlow()

    override suspend fun refreshArtists(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val api = networkClient.createApiService()
            val resp = api.getArtists()
            if (resp.isSuccessful) {
                _artists.value = resp.body()?.artists ?: emptyList()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to fetch artists: ${resp.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchArtistDetail(artistId: String): Result<ArtistDetailDto> = withContext(Dispatchers.IO) {
        try {
            val api = networkClient.createApiService()
            val resp = api.getArtistDetail(artistId)
            val body = resp.body()
            if (resp.isSuccessful && body != null) {
                Result.success(body)
            } else {
                Result.failure(Exception("Failed to fetch artist detail: ${resp.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getArtistsList(): Result<List<ArtistDto>> = withContext(Dispatchers.IO) {
        if (_artists.value.isNotEmpty()) {
            Result.success(_artists.value)
        } else {
            val refreshed = refreshArtists()
            if (refreshed.isSuccess) Result.success(_artists.value) else Result.success(emptyList())
        }
    }

    override suspend fun getArtistDetail(artistId: String): Result<ArtistDetailDto> = fetchArtistDetail(artistId)

    override suspend fun getFolderPlaylists(): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        try {
            val accountKey = userPreferences.getAccountKey()
            val folders = playlistDao.getAllPlaylists(accountKey)
                .filter { it.kind == PlaylistKind.FOLDER.name }
                .map { it.toDomain() }
            Result.success(folders)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCommunityPlaylists(): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        val accountKey = userPreferences.getAccountKey()
        suspend fun getLocalFallback(): List<Playlist> {
            return playlistDao.getAllPlaylists(accountKey)
                .filter { it.visibility.equals("public", ignoreCase = true) && it.kind != PlaylistKind.FAVORITES.name }
                .map { entity ->
                    val domain = entity.toDomain()
                    val isSaved = userPreferences.isPlaylistSaved(domain.id) ||
                        (domain.serverId != null && userPreferences.isPlaylistSaved(domain.serverId)) ||
                        domain.isSaved
                    domain.copy(isSaved = isSaved)
                }
        }

        try {
            val api = networkClient.createApiService()
            val resp = api.getPlaylists(discover = true)
            if (resp.isSuccessful && resp.body() != null) {
                val dtos = resp.body()!!.playlists
                val mapped = dtos.map { dto ->
                    val isSaved = userPreferences.isPlaylistSaved(dto.id) || dto.isSaved
                    Playlist(
                        id = dto.id,
                        serverId = dto.id,
                        title = dto.title,
                        kind = if (dto.isFolderPlaylist) PlaylistKind.FOLDER else PlaylistKind.PUBLIC,
                        owner = dto.owner,
                        visibility = dto.visibility,
                        description = dto.description,
                        trackCount = dto.trackCount,
                        coverTrackPath = null,
                        unavailableCount = 0,
                        isAvailable = true,
                        syncState = PlaylistSyncState.SYNCED,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        isSaved = isSaved
                    )
                }
                Result.success(mapped)
            } else {
                Result.success(getLocalFallback())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                Result.success(getLocalFallback())
            } catch (dbError: Exception) {
                if (dbError is CancellationException) throw dbError
                Result.failure(e)
            }
        }
    }

    override suspend fun getPlaylistDetail(playlistId: String): Result<PlaylistDetailDto> = withContext(Dispatchers.IO) {
        try {
            val api = networkClient.createApiService()
            val resp = api.getPlaylistDetail(playlistId)
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch playlist detail: ${resp.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAllTracks(): Flow<List<Track>> {
        return userPreferences.observeAccountKey().flatMapLatest { accountKey ->
            trackDao.observeTracksForAccount(accountKey).map { entities ->
                entities.map { it.toDomain() }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePlaylists(): Flow<List<Playlist>> {
        return userPreferences.observeAccountKey().flatMapLatest { accountKey ->
            combine(
                playlistDao.observeAllPlaylists(accountKey),
                userPreferences.observeSavedPlaylistIds()
            ) { entities, savedIds ->
                entities.filter { it.syncState != PlaylistSyncState.PENDING_DELETE.name }
                    .map { entity ->
                        val domain = entity.toDomain()
                        val isSaved = savedIds.contains(domain.id) || (domain.serverId != null && savedIds.contains(domain.serverId))
                        domain.copy(isSaved = isSaved)
                    }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPlaylist(playlistKey: String): Flow<Playlist?> {
        return userPreferences.observeAccountKey().flatMapLatest { accountKey ->
            flow {
                val entity = playlistDao.getPlaylistByKey(accountKey, playlistKey)
                    ?: playlistDao.getPlaylistByServerId(accountKey, playlistKey)
                    ?: playlistDao.getPlaylistByTitle(accountKey, playlistKey)
                val playlist = entity?.toDomain()
                val isFav = playlist?.kind == PlaylistKind.FAVORITES ||
                    entity?.kind == PlaylistKind.FAVORITES.name ||
                    playlist?.isFavorites == true ||
                    playlistKey.equals("Favorites", ignoreCase = true)

                val baseResolved = if (isFav) {
                    (playlist ?: Playlist(name = "Favorites", isFavorites = true)).copy(kind = PlaylistKind.FAVORITES)
                } else {
                    playlist
                }
                val isSavedInit = baseResolved != null && (
                    userPreferences.isPlaylistSaved(baseResolved.id) ||
                    (baseResolved.serverId != null && userPreferences.isPlaylistSaved(baseResolved.serverId)) ||
                    baseResolved.isSaved
                )
                val resolved = baseResolved?.copy(isSaved = isSavedInit)
                emit(resolved)

                try {
                    combine(
                        playlistDao.observePlaylistByKeyOrTitle(accountKey, playlistKey),
                        userPreferences.observeSavedPlaylistIds()
                    ) { updatedEntity, savedIds ->
                        val updatedDomain = updatedEntity?.toDomain()
                        val updatedIsFav = updatedDomain?.kind == PlaylistKind.FAVORITES ||
                            updatedEntity?.kind == PlaylistKind.FAVORITES.name ||
                            updatedDomain?.isFavorites == true ||
                            playlistKey.equals("Favorites", ignoreCase = true)
                        val updatedResolved = if (updatedIsFav) {
                            (updatedDomain ?: Playlist(name = "Favorites", isFavorites = true)).copy(kind = PlaylistKind.FAVORITES)
                        } else {
                            updatedDomain
                        }
                        val isSavedNow = updatedResolved != null && (
                            savedIds.contains(updatedResolved.id) ||
                            (updatedResolved.serverId != null && savedIds.contains(updatedResolved.serverId)) ||
                            updatedResolved.isSaved
                        )
                        updatedResolved?.copy(isSaved = isSavedNow)
                    }.collect { finalResolved ->
                        emit(finalResolved)
                    }
                } catch (_: Throwable) {
                    // Observe stream may not be configured in mock or unit test environments
                }
            }
        }.distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTracks(playlistKey: String): Flow<List<Track>> {
        return userPreferences.observeAccountKey().flatMapLatest { accountKey ->
            flow {
                val entity = playlistDao.getPlaylistByKey(accountKey, playlistKey)
                    ?: playlistDao.getPlaylistByServerId(accountKey, playlistKey)
                    ?: playlistDao.getPlaylistByTitle(accountKey, playlistKey)
                val playlist = entity?.toDomain()
                val isFav = playlist?.kind == PlaylistKind.FAVORITES ||
                    entity?.kind == PlaylistKind.FAVORITES.name ||
                    playlist?.isFavorites == true ||
                    playlistKey.equals("Favorites", ignoreCase = true)

                if (isFav) {
                    val favoritesFlow = try {
                        combine(favoriteDao.observeFavorites(), trackDao.observeAllTracks()) { favList, allTracks ->
                            val trackMap = allTracks.associateBy { it.id }
                            val mapped = favList.mapNotNull { fav ->
                                trackMap[fav.trackId]?.toDomain(isAvailable = true)?.copy(isFavorite = true)
                                    ?: trackDao.getTrackById(fav.trackId)?.toDomain()?.copy(isFavorite = true)
                            }
                            if (mapped.isEmpty() && favList.isEmpty()) {
                                allTracks.filter { it.isFavorite }.map { it.toDomain(isAvailable = true) }
                            } else {
                                mapped
                            }
                        }
                    } catch (_: Throwable) {
                        trackDao.observeFavoriteTracks().map { entities -> entities.map { it.toDomain() } }
                    }
                    emitAll(favoritesFlow)
                } else {
                    val resolvedKey = entity?.localKey ?: playlistKey
                    emitAll(
                        playlistDao.observeTracksForPlaylistWithAvailability(accountKey, resolvedKey).map { items ->
                            items.map { it.toDomain() }
                        }
                    )
                }
            }
        }
    }

    override fun observeFavorites(): Flow<List<Track>> {
        return observeTracks("Favorites")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDownloadedTracks(): Flow<List<Track>> {
        return userPreferences.observeAccountKey().flatMapLatest { accountKey ->
            trackDao.observeDownloadedTracksForAccount(accountKey).map { entities ->
                entities.map { it.toDomain() }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun searchTracks(query: String): Flow<List<Track>> {
        return userPreferences.observeAccountKey().flatMapLatest { accountKey ->
            trackDao.searchTracksForAccount(accountKey, query).map { entities ->
                entities.map { it.toDomain() }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFavoriteTracks(): Flow<List<Track>> {
        return userPreferences.observeAccountKey().flatMapLatest { accountKey ->
            trackDao.observeFavoriteTracksForAccount(accountKey).map { entities ->
                entities.map { it.toDomain() }
            }
        }
    }

    override suspend fun toggleFavorite(track: Track): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val accountKey = userPreferences.getAccountKey()
            val newFavStatus = database.withTransaction {
                val desired = !(trackDao.getTrackById(track.id)?.isFavorite ?: track.isFavorite)
                trackDao.updateFavoriteStatus(track.id, desired)
                if (desired) favoriteDao.insert(FavoriteEntity(trackId = track.id)) else favoriteDao.delete(track.id)
                val payload = json.encodeToString(SetFavoriteRequest(trackPath = track.id, isFavorite = desired))
                syncQueueDao.enqueueAction(
                    SyncQueueEntity(
                        accountKey = accountKey,
                        actionType = SyncActionType.SET_FAVORITE,
                        payloadJson = payload
                    )
                )
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

    override suspend fun createPlaylist(name: String): Result<Unit> {
        return createPlaylist(title = name, description = null, isPublic = false).map { Unit }
    }

    override suspend fun createPlaylist(
        title: String,
        description: String?,
        isPublic: Boolean
    ): Result<Playlist> = withContext(Dispatchers.IO) {
        if (isPublic && !v2Supported) {
            return@withContext Result.failure(
                UnsupportedOperationException("This feature requires Sangita Server V2 or newer.")
            )
        }
        try {
            val localKey = UUID.randomUUID().toString()
            val visibility = if (isPublic) "public" else "private"
            val accountKey = userPreferences.getAccountKey()
            val entity = PlaylistEntity(
                localKey = localKey,
                serverId = null,
                accountKey = accountKey,
                title = title,
                kind = PlaylistKind.PERSONAL.name,
                owner = userPreferences.getUsername(),
                visibility = visibility,
                description = description,
                trackCount = 0,
                coverTrackPath = null,
                unavailableCount = 0,
                createdAt = System.currentTimeMillis(),
                lastSyncedAt = 0L,
                syncState = PlaylistSyncState.PENDING_CREATE.name
            )

            database.withTransaction {
                playlistDao.insertOrReplace(entity)
                val payload = SyncPayloadSerialization.encodeCreatePlaylist(
                    CreatePlaylistPayload(
                        localKey = localKey,
                        title = title,
                        description = description,
                        visibility = visibility
                    )
                )
                syncQueueDao.enqueueAction(
                    SyncQueueEntity(
                        accountKey = accountKey,
                        actionType = SyncActionType.CREATE_PLAYLIST,
                        payloadJson = payload
                    )
                )
            }
            SangitaSyncWorker.enqueueOneTime(context)

            Result.success(entity.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createPlaylist(
        title: String,
        description: String?,
        visibility: String
    ): Result<Playlist> = createPlaylist(title, description, visibility.equals("public", ignoreCase = true))

    override suspend fun updatePlaylist(
        playlistKey: String,
        title: String?,
        description: String?,
        visibility: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (visibility?.equals("public", ignoreCase = true) == true && !v2Supported) {
            return@withContext Result.failure(
                UnsupportedOperationException("This feature requires Sangita Server V2 or newer.")
            )
        }
        try {
            val accountKey = userPreferences.getAccountKey()
            val entity = playlistDao.getPlaylistByKey(accountKey, playlistKey)
                ?: playlistDao.getPlaylistByTitle(accountKey, playlistKey)
                ?: return@withContext Result.failure(IllegalArgumentException("Playlist not found"))

            val newSyncState = if (entity.syncState == PlaylistSyncState.PENDING_CREATE.name) {
                PlaylistSyncState.PENDING_CREATE.name
            } else {
                PlaylistSyncState.PENDING_UPDATE.name
            }

            val updated = entity.copy(
                title = title ?: entity.title,
                description = description ?: entity.description,
                visibility = visibility ?: entity.visibility,
                syncState = newSyncState,
                lastSyncedAt = System.currentTimeMillis()
            )

            database.withTransaction {
                playlistDao.insertOrReplace(updated)
                val payload = SyncPayloadSerialization.encodeUpdatePlaylist(
                    UpdatePlaylistPayload(
                        localKey = entity.localKey,
                        serverId = entity.serverId,
                        title = title ?: entity.title,
                        description = description ?: entity.description,
                        visibility = visibility ?: entity.visibility
                    )
                )
                syncQueueDao.enqueueAction(
                    SyncQueueEntity(
                        accountKey = accountKey,
                        actionType = SyncActionType.UPDATE_PLAYLIST,
                        payloadJson = payload
                    )
                )
            }
            SangitaSyncWorker.enqueueOneTime(context)

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deletePlaylist(playlistKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accountKey = userPreferences.getAccountKey()
            val entity = playlistDao.getPlaylistByKey(accountKey, playlistKey)
                ?: playlistDao.getPlaylistByTitle(accountKey, playlistKey)
                ?: return@withContext Result.success(Unit)

            if (entity.serverId.isNullOrBlank()) {
                // Local key only -> delete from DB and sync queue directly
                database.withTransaction {
                    syncQueueDao.deleteActionsByLocalKey(entity.localKey)
                    if (playlistKey != entity.localKey) {
                        syncQueueDao.deleteActionsByLocalKey(playlistKey)
                    }
                    playlistDao.clearTracksForPlaylist(entity.localKey)
                    if (playlistKey != entity.localKey) {
                        playlistDao.clearTracksForPlaylist(playlistKey)
                    }
                    playlistDao.deletePlaylistByKey(accountKey, entity.localKey)
                    if (playlistKey != entity.localKey) {
                        playlistDao.deletePlaylistByKey(accountKey, playlistKey)
                    }
                }
            } else {
                // Has serverId -> mark PENDING_DELETE and enqueue sync action
                database.withTransaction {
                    playlistDao.insertOrReplace(
                        entity.copy(syncState = PlaylistSyncState.PENDING_DELETE.name)
                    )
                    val payload = SyncPayloadSerialization.encodeDeletePlaylist(
                        DeletePlaylistPayload(
                            localKey = entity.localKey,
                            serverId = entity.serverId
                        )
                    )
                    syncQueueDao.enqueueAction(
                        SyncQueueEntity(
                            accountKey = accountKey,
                            actionType = SyncActionType.DELETE_PLAYLIST,
                            payloadJson = payload
                        )
                    )
                }
                SangitaSyncWorker.enqueueOneTime(context)
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clonePlaylist(
        playlistKey: String,
        title: String?,
        description: String?,
        isPublic: Boolean
    ): Result<Playlist> = withContext(Dispatchers.IO) {
        if (!v2Supported) {
            return@withContext Result.failure(
                UnsupportedOperationException("This feature requires Sangita Server V2 or newer.")
            )
        }
        try {
            val accountKey = userPreferences.getAccountKey()
            val entity = playlistDao.getPlaylistByKey(accountKey, playlistKey)
                ?: playlistDao.getPlaylistByTitle(accountKey, playlistKey)

            val newLocalKey = UUID.randomUUID().toString()
            val cloneTitle = title?.takeIf { it.isNotBlank() } ?: "${entity?.title ?: "Playlist"} (Copy)"
            val visibility = if (isPublic) "public" else "private"

            val clonedEntity = PlaylistEntity(
                localKey = newLocalKey,
                serverId = null,
                accountKey = accountKey,
                title = cloneTitle,
                kind = PlaylistKind.PERSONAL.name,
                owner = userPreferences.getUsername(),
                visibility = visibility,
                description = description ?: entity?.description,
                trackCount = entity?.trackCount ?: 0,
                coverTrackPath = entity?.coverTrackPath,
                unavailableCount = 0,
                createdAt = System.currentTimeMillis(),
                lastSyncedAt = 0L,
                syncState = PlaylistSyncState.PENDING_CREATE.name
            )

            database.withTransaction {
                playlistDao.insertOrReplace(clonedEntity)
                if (entity != null) {
                    val sourceTracks = playlistDao.observeTracksForPlaylist(entity.localKey).firstOrNull() ?: emptyList()
                    val newRefs = sourceTracks.mapIndexed { idx, track ->
                        PlaylistTrackCrossRef(
                            playlistLocalKey = newLocalKey,
                            trackId = track.id,
                            position = idx,
                            isAvailable = true
                        )
                    }
                    if (newRefs.isNotEmpty()) {
                        playlistDao.insertCrossRefs(newRefs)
                    }
                }

                val sourceServerId = entity?.serverId ?: playlistKey
                val payload = SyncPayloadSerialization.encodeClonePlaylist(
                    ClonePlaylistPayload(
                        sourceServerId = sourceServerId,
                        newLocalKey = newLocalKey,
                        title = cloneTitle,
                        description = description ?: entity?.description,
                        visibility = visibility
                    )
                )
                syncQueueDao.enqueueAction(
                    SyncQueueEntity(
                        accountKey = accountKey,
                        actionType = SyncActionType.CLONE_PLAYLIST,
                        payloadJson = payload
                    )
                )
            }
            SangitaSyncWorker.enqueueOneTime(context)

            Result.success(clonedEntity.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun savePlaylistToLibrary(playlistId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val wasSaved = userPreferences.isPlaylistSaved(playlistId)
        userPreferences.setPlaylistSaved(playlistId, true)
        try {
            val api = networkClient.createApiService()
            val resp = api.savePlaylistToLibrary(playlistId)
            if (resp.isSuccessful) {
                userPreferences.setPlaylistSaved(playlistId, true)
                try {
                    refreshPlaylists()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
                Result.success(Unit)
            } else {
                userPreferences.setPlaylistSaved(playlistId, wasSaved)
                Result.failure(Exception("Failed to save playlist: HTTP ${resp.code()}"))
            }
        } catch (e: CancellationException) {
            userPreferences.setPlaylistSaved(playlistId, wasSaved)
            throw e
        } catch (e: Exception) {
            userPreferences.setPlaylistSaved(playlistId, wasSaved)
            Result.failure(e)
        }
    }

    override suspend fun removePlaylistFromLibrary(playlistId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val wasSaved = userPreferences.isPlaylistSaved(playlistId)
        userPreferences.setPlaylistSaved(playlistId, false)
        try {
            val api = networkClient.createApiService()
            val resp = api.removePlaylistFromLibrary(playlistId)
            if (resp.isSuccessful) {
                userPreferences.setPlaylistSaved(playlistId, false)
                try {
                    refreshPlaylists()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
                Result.success(Unit)
            } else {
                userPreferences.setPlaylistSaved(playlistId, wasSaved)
                Result.failure(Exception("Failed to remove playlist: HTTP ${resp.code()}"))
            }
        } catch (e: CancellationException) {
            userPreferences.setPlaylistSaved(playlistId, wasSaved)
            throw e
        } catch (e: Exception) {
            userPreferences.setPlaylistSaved(playlistId, wasSaved)
            Result.failure(e)
        }
    }

    override fun isPlaylistSaved(playlistId: String): Flow<Boolean> {
        return userPreferences.observeSavedPlaylistIds().map { it.contains(playlistId) }
    }

    override fun observeSavedPlaylistIds(): Flow<Set<String>> {
        return userPreferences.observeSavedPlaylistIds()
    }

    override fun observeSavedArtists(): Flow<List<ArtistDto>> = _savedArtists.asStateFlow()

    override suspend fun getSavedArtists(): Result<List<ArtistDto>> = withContext(Dispatchers.IO) {
        try {
            val requestAccount = userPreferences.getAccountKey()
            val api = networkClient.createApiService()
            val resp = api.getSavedArtists()
            if (resp.isSuccessful && resp.body() != null) {
                if (requestAccount != userPreferences.getAccountKey()) return@withContext Result.failure(IllegalStateException("Account changed"))
                _savedArtists.value = resp.body()!!.artists
                Result.success(resp.body()!!.artists)
            } else {
                Result.failure(Exception("Failed to get saved artists: HTTP ${resp.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveArtist(artistId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val api = networkClient.createApiService()
            val resp = api.saveArtist(artistId)
            if (resp.isSuccessful) {
                getSavedArtists()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to save artist: HTTP ${resp.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeArtist(artistId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val api = networkClient.createApiService()
            val resp = api.removeArtist(artistId)
            if (resp.isSuccessful) {
                _savedArtists.value = _savedArtists.value.filter { it.id != artistId }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove artist: HTTP ${resp.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeSavedTracks(): Flow<List<Track>> = _savedTracks.asStateFlow()

    override suspend fun getSavedTracks(): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val requestAccount = userPreferences.getAccountKey()
            val api = networkClient.createApiService()
            val resp = api.getSavedTracks()
            if (resp.isSuccessful && resp.body() != null) {
                val list = resp.body()!!.tracks.map { pt ->
                    val (fallbackTitle, fallbackArtist) = parseTrackMetadata(pt.trackPath)
                    Track(
                        id = pt.trackPath,
                        playlist = "Library",
                        title = pt.title.ifBlank { fallbackTitle },
                        artist = pt.artist.ifBlank { fallbackArtist },
                        album = "",
                        durationSeconds = pt.duration,
                        remoteUrl = "${userPreferences.getServerUrl()}/api/stream/${pt.trackPath}",
                        isAvailable = pt.isAvailable
                    )
                }
                if (requestAccount != userPreferences.getAccountKey()) return@withContext Result.failure(IllegalStateException("Account changed"))
                _savedTracks.value = list
                Result.success(list)
            } else {
                Result.failure(Exception("Failed to get saved tracks: HTTP ${resp.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveTrack(trackPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val api = networkClient.createApiService()
            val resp = api.saveTrack(SaveTrackRequest(trackPath))
            if (resp.isSuccessful) {
                getSavedTracks()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to save track: HTTP ${resp.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeTrack(trackPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val api = networkClient.createApiService()
            val resp = api.removeTrack(trackPath)
            if (resp.isSuccessful) {
                _savedTracks.value = _savedTracks.value.filter { it.id != trackPath }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to remove track: HTTP ${resp.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addArtistCollectionToPlaylist(
        artistId: String,
        targetPlaylistId: String?,
        newPlaylistTitle: String?
    ): Result<ArtistAddCollectionResult> = withContext(Dispatchers.IO) {
        try {
            val accountKey = userPreferences.getAccountKey()
            val localEntity = if (!targetPlaylistId.isNullOrBlank()) {
                playlistDao.getPlaylistByKey(accountKey, targetPlaylistId)
                    ?: playlistDao.getPlaylistByServerId(accountKey, targetPlaylistId)
                    ?: playlistDao.getPlaylistByTitle(accountKey, targetPlaylistId)
            } else null

            // Use serverId if available, or targetPlaylistId if already representing a server ID
            val effectiveServerId = localEntity?.serverId ?: targetPlaylistId

            val api = networkClient.createApiService()
            val request = AddArtistCollectionRequest(
                playlistId = if (!effectiveServerId.isNullOrBlank() && (localEntity == null || localEntity.serverId != null)) effectiveServerId else null,
                newPlaylistTitle = newPlaylistTitle,
                title = newPlaylistTitle,
                visibility = "private"
            )

            var apiSuccess = false
            var addedCount = 0
            var skippedCount = 0
            var failedCount = 0
            var plId = localEntity?.localKey ?: targetPlaylistId
            var plTitle = localEntity?.title ?: newPlaylistTitle
            var msg: String? = null

            // Only attempt remote API if we have a resolved server ID or are creating a new playlist
            if (!request.playlistId.isNullOrBlank() || !request.newPlaylistTitle.isNullOrBlank()) {
                try {
                    val resp = api.addArtistCollectionToPlaylist(artistId, request)
                    if (resp.isSuccessful && resp.body() != null) {
                        val body = resp.body()!!
                        apiSuccess = true
                        addedCount = body.tracksAdded
                        skippedCount = body.duplicatesSkipped
                        val returnedPlId = body.playlistId
                        if (!returnedPlId.isNullOrBlank()) {
                            plId = returnedPlId
                        }
                        plTitle = body.playlistTitle ?: plTitle
                        msg = body.message
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Remote endpoint not reachable, fallback to client resolution
                }
            }

            if (!apiSuccess) {
                val artistDetail = fetchArtistDetail(artistId).getOrNull()
                    ?: getArtistDetail(artistId).getOrNull()
                val artistTracks = artistDetail?.tracks ?: emptyList()
                val artistName = artistDetail?.name.orEmpty()

                val resolvedPlaylist = if (!targetPlaylistId.isNullOrBlank()) {
                    localEntity?.toDomain()
                        ?: playlistDao.getPlaylistByKey(accountKey, targetPlaylistId)?.toDomain()
                        ?: playlistDao.getPlaylistByServerId(accountKey, targetPlaylistId)?.toDomain()
                } else if (!newPlaylistTitle.isNullOrBlank()) {
                    val createRes = createPlaylist(newPlaylistTitle, null, false)
                    createRes.getOrNull()
                } else null

                if (resolvedPlaylist != null) {
                    plId = resolvedPlaylist.id
                    plTitle = resolvedPlaylist.title
                    val existingTracks = observeTracks(resolvedPlaylist.id).firstOrNull() ?: emptyList()
                    val existingPaths = existingTracks.map { it.id }.toSet()

                    for (tr in artistTracks) {
                        if (tr.path in existingPaths) {
                            skippedCount++
                        } else {
                            val cleanArtist = tr.artist.ifBlank { artistName.ifBlank { "Unknown Artist" } }
                            val cleanTitle = tr.title.ifBlank { tr.path.substringAfterLast('/').substringBeforeLast('.') }
                            val domainTrack = Track(
                                id = tr.path,
                                title = cleanTitle,
                                artist = cleanArtist,
                                playlist = resolvedPlaylist.title,
                                durationSeconds = tr.duration
                            )
                            val addResult = addTrackToPlaylist(resolvedPlaylist.id, domainTrack)
                            if (addResult.isSuccess) {
                                addedCount++
                            } else {
                                failedCount++
                            }
                        }
                    }
                } else {
                    return@withContext Result.failure(Exception("Could not find or create target playlist"))
                }
            }

            if (apiSuccess && !plId.isNullOrBlank()) {
                try {
                    val detailRes = getPlaylistDetail(plId)
                    if (detailRes.isSuccess) {
                        val detail = detailRes.getOrNull()
                        if (detail != null) {
                            val currentUsername = userPreferences.getUsername()
                            val currentRole = userPreferences.getRole()
                            val isOwner = (detail.owner != null && detail.owner == currentUsername) || currentRole == "admin"

                            val targetLocal = localEntity
                                ?: playlistDao.getPlaylistByServerId(accountKey, detail.id)
                                ?: playlistDao.getPlaylistByKey(accountKey, detail.id)
                                ?: PlaylistEntity(
                                    localKey = "pl_${java.util.UUID.randomUUID()}",
                                    serverId = detail.id,
                                    accountKey = accountKey,
                                    title = detail.title,
                                    description = detail.description,
                                    owner = detail.owner ?: currentUsername,
                                    kind = PlaylistKind.PERSONAL.name,
                                    trackCount = detail.trackCount,
                                    unavailableCount = detail.unavailableCount,
                                    syncState = "SYNCED",
                                    lastSyncedAt = System.currentTimeMillis()
                                )

                            // Check pending sync queue actions for this playlist
                            val pendingActions = (syncQueueDao.getPendingActions(accountKey) +
                                if (accountKey != "default_account") syncQueueDao.getPendingActions("default_account") else emptyList())
                                .distinctBy { it.id }

                            val targetKeys = setOfNotNull(targetLocal.localKey, targetLocal.serverId, targetLocal.title).filter { it.isNotBlank() }

                            var hasPendingEdits = targetLocal.syncState != PlaylistSyncState.SYNCED.name
                            var pendingTitle: String? = null
                            var pendingDescription: String? = null
                            var pendingVisibility: String? = null
                            val reconciledRefs = reconcilePlaylistTracks(targetLocal.localKey, detail.tracks, isOwner).toMutableList()

                            // Apply the outbox in the order it will be sent, so the latest
                            // local intent wins when a track is added and removed repeatedly.
                            for (act in pendingActions.sortedWith(compareBy({ it.createdAt }, { it.id }))) {
                                when (act.actionType) {
                                    SyncActionType.ADD_TRACK, SyncActionType.ADD_TRACK_TO_PLAYLIST -> {
                                        val p = try { SyncPayloadSerialization.decodeAddTrack(act.payloadJson) } catch (_: Exception) { null }
                                        if (p != null && (p.localKey in targetKeys || (p.serverId != null && p.serverId in targetKeys) || (p.playlistName != null && p.playlistName in targetKeys))) {
                                            if (reconciledRefs.none { it.trackId == p.trackPath }) {
                                                reconciledRefs.add(PlaylistTrackCrossRef(playlistLocalKey = targetLocal.localKey, trackId = p.trackPath, position = reconciledRefs.size, isAvailable = true))
                                            }
                                            hasPendingEdits = true
                                        }
                                    }
                                    SyncActionType.REMOVE_TRACK, SyncActionType.REMOVE_TRACK_FROM_PLAYLIST -> {
                                        val p = try { SyncPayloadSerialization.decodeRemoveTrack(act.payloadJson) } catch (_: Exception) { null }
                                        if (p != null && (p.localKey in targetKeys || (p.serverId != null && p.serverId in targetKeys) || (p.playlistName != null && p.playlistName in targetKeys))) {
                                            reconciledRefs.removeAll { it.trackId == p.trackPath }
                                            hasPendingEdits = true
                                        }
                                    }
                                    SyncActionType.UPDATE_PLAYLIST -> {
                                        val p = try { SyncPayloadSerialization.decodeUpdatePlaylist(act.payloadJson) } catch (_: Exception) { null }
                                        if (p != null && (p.localKey in targetKeys || (p.serverId != null && p.serverId in targetKeys) || (p.playlistName != null && p.playlistName in targetKeys))) {
                                            p.title?.let { pendingTitle = it }
                                            p.description?.let { pendingDescription = it }
                                            p.visibility?.let { pendingVisibility = it }
                                            p.trackPaths?.let { order ->
                                                val byPath = reconciledRefs.associateBy { it.trackId }
                                                val ordered = order.distinct().mapNotNull { byPath[it] }
                                                val orderedIds = ordered.map { it.trackId }.toSet()
                                                val remaining = reconciledRefs.filter { it.trackId !in orderedIds }
                                                reconciledRefs.clear()
                                                reconciledRefs.addAll(ordered + remaining)
                                            }
                                            hasPendingEdits = true
                                        }
                                    }
                                    SyncActionType.CREATE_PLAYLIST -> {
                                        val p = try { SyncPayloadSerialization.decodeCreatePlaylist(act.payloadJson) } catch (_: Exception) { null }
                                        if (p != null && (p.localKey in targetKeys || p.title in targetKeys)) hasPendingEdits = true
                                    }
                                    else -> Unit
                                }
                            }

                            val finalRefs = reconciledRefs.mapIndexed { idx, ref -> ref.copy(position = idx) }
                            val coverTrack = finalRefs.firstOrNull { it.isAvailable }?.trackId ?: finalRefs.firstOrNull()?.trackId
                            val updatedEntity = targetLocal.copy(
                                serverId = detail.id,
                                title = pendingTitle ?: detail.title,
                                description = pendingDescription ?: detail.description,
                                visibility = pendingVisibility ?: detail.visibility,
                                trackCount = if (hasPendingEdits) (if (isOwner) finalRefs.size else finalRefs.count { it.isAvailable }) else (if (isOwner) detail.trackCount else finalRefs.size),
                                unavailableCount = detail.unavailableCount,
                                coverTrackPath = coverTrack,
                                lastSyncedAt = System.currentTimeMillis(),
                                syncState = if (hasPendingEdits) targetLocal.syncState else "SYNCED"
                            )

                            val trackEntities = mutableListOf<TrackEntity>()
                            for (t in detail.tracks) {
                                val existing = trackDao.getTrackById(t.trackPath)
                                val (fallbackTitle, fallbackArtist) = parseTrackMetadata(t.trackPath)
                                val trackTitle = t.title.takeIf { it.isNotBlank() } ?: existing?.title ?: fallbackTitle
                                val trackArtist = t.artist.takeIf { it.isNotBlank() } ?: existing?.artist ?: fallbackArtist
                                trackEntities.add(
                                    TrackEntity(
                                        id = t.trackPath,
                                        playlistName = detail.title,
                                        title = trackTitle,
                                        artist = trackArtist,
                                        durationSeconds = t.duration ?: existing?.durationSeconds,
                                        remoteUrl = "${userPreferences.getServerUrl()}/api/stream/${t.trackPath}",
                                        localFilePath = existing?.localFilePath,
                                        isDownloaded = existing?.isDownloaded ?: false,
                                        downloadStatus = existing?.downloadStatus ?: DownloadStatus.IDLE,
                                        downloadProgress = existing?.downloadProgress ?: 0,
                                        fileSizeBytes = existing?.fileSizeBytes ?: 0L,
                                        isFavorite = existing?.isFavorite ?: false,
                                        downloadedAt = existing?.downloadedAt
                                    )
                                )
                            }
                            for (addedId in finalRefs.map { it.trackId }) {
                                val existing = trackDao.getTrackById(addedId)
                                if (existing == null) {
                                    val (fallbackTitle, fallbackArtist) = parseTrackMetadata(addedId)
                                    trackEntities.add(
                                        TrackEntity(
                                            id = addedId,
                                            playlistName = detail.title,
                                            title = fallbackTitle,
                                            artist = fallbackArtist,
                                            durationSeconds = null,
                                            remoteUrl = "${userPreferences.getServerUrl()}/api/stream/$addedId",
                                            localFilePath = null,
                                            isDownloaded = false,
                                            downloadStatus = DownloadStatus.IDLE,
                                            downloadProgress = 0,
                                            fileSizeBytes = 0L,
                                            isFavorite = false,
                                            downloadedAt = null
                                        )
                                    )
                                }
                            }

                            database.withTransaction {
                                playlistDao.insertOrReplace(updatedEntity)
                                if (trackEntities.isNotEmpty()) {
                                    trackDao.insertOrReplaceAll(trackEntities)
                                }
                                playlistDao.clearTracksForPlaylist(targetLocal.localKey)
                                if (finalRefs.isNotEmpty()) {
                                    playlistDao.insertCrossRefs(finalRefs)
                                }
                            }
                            plId = detail.id.ifBlank { targetLocal.serverId ?: targetLocal.localKey }
                            plTitle = updatedEntity.title
                        }
                    }
                } catch (_: Exception) {}
            }
            Result.success(
                ArtistAddCollectionResult(
                    addedCount = addedCount,
                    skippedDuplicatesCount = skippedCount,
                    failedCount = failedCount,
                    playlistId = plId,
                    playlistTitle = plTitle,
                    message = msg
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reorderPlaylistTracks(
        playlistKey: String,
        newTrackIds: List<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val accountKey = userPreferences.getAccountKey()
            val entity = playlistDao.getPlaylistByKey(accountKey, playlistKey)
                ?: playlistDao.getPlaylistByTitle(accountKey, playlistKey)
            val targetKey = entity?.localKey ?: playlistKey
            val serverId = entity?.serverId

            var queueActionId: Long = 0
            database.withTransaction {
                newTrackIds.forEachIndexed { index, trackId ->
                    playlistDao.updateTrackPosition(targetKey, trackId, index)
                    if (entity != null && entity.title != targetKey) {
                        playlistDao.updateTrackPosition(entity.title, trackId, index)
                    }
                }

                val payload = SyncPayloadSerialization.encodeUpdatePlaylist(
                    UpdatePlaylistPayload(
                        localKey = targetKey,
                        serverId = serverId,
                        title = entity?.title,
                        description = entity?.description,
                        visibility = entity?.visibility,
                        trackPaths = newTrackIds
                    )
                )
                queueActionId = syncQueueDao.enqueueAction(
                    SyncQueueEntity(
                        accountKey = accountKey,
                        actionType = SyncActionType.UPDATE_PLAYLIST,
                        payloadJson = payload
                    )
                )
            }

            // If online and V2: Call apiService.updatePlaylist(serverId, UpdatePlaylistRequest(tracks = newTrackIds))
            if (!serverId.isNullOrBlank()) {
                try {
                    val api = networkClient.createApiService()
                    val response = api.updatePlaylist(
                        serverId,
                        com.sangita.app.data.remote.dto.UpdatePlaylistRequest(tracks = newTrackIds)
                    )
                    if (response.isSuccessful) {
                        syncQueueDao.deleteAction(queueActionId)
                    } else if (response.code() == 409) {
                        playlistDao.insertOrReplace(
                            entity.copy(
                                syncState = PlaylistSyncState.CONFLICT.name,
                                lastSyncedAt = System.currentTimeMillis()
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    SangitaSyncWorker.enqueueOneTime(context)
                }
            } else {
                SangitaSyncWorker.enqueueOneTime(context)
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addTrackToPlaylist(playlistKey: String, track: Track): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                val accountKey = userPreferences.getAccountKey()
                val entity = playlistDao.getPlaylistByKey(accountKey, playlistKey)
                    ?: playlistDao.getPlaylistByServerId(accountKey, playlistKey)
                    ?: playlistDao.getPlaylistByTitle(accountKey, playlistKey)
                val targetKey = entity?.localKey ?: playlistKey
                val nextPos = playlistDao.nextTrackPosition(targetKey)

                playlistDao.insertCrossRef(
                    PlaylistTrackCrossRef(
                        playlistLocalKey = targetKey,
                        trackId = track.id,
                        position = nextPos,
                        isAvailable = true
                    )
                )

                if (entity != null) {
                    val newCount = entity.trackCount + 1
                    val newCover = entity.coverTrackPath ?: track.id
                    playlistDao.insertOrReplace(
                        entity.copy(trackCount = newCount, coverTrackPath = newCover)
                    )
                }

                val payload = SyncPayloadSerialization.encodeAddTrack(
                    AddTrackPayload(
                        localKey = targetKey,
                        serverId = entity?.serverId,
                        trackPath = track.id
                    )
                )
                syncQueueDao.enqueueAction(
                    SyncQueueEntity(
                        accountKey = accountKey,
                        actionType = SyncActionType.ADD_TRACK,
                        payloadJson = payload
                    )
                )
            }
            SangitaSyncWorker.enqueueOneTime(context)

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeTrackFromPlaylist(playlistKey: String, track: Track): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                val accountKey = userPreferences.getAccountKey()
                val entity = playlistDao.getPlaylistByKey(accountKey, playlistKey)
                    ?: playlistDao.getPlaylistByServerId(accountKey, playlistKey)
                    ?: playlistDao.getPlaylistByTitle(accountKey, playlistKey)
                val targetKey = entity?.localKey ?: playlistKey

                playlistDao.deleteCrossRef(targetKey, track.id)

                if (entity != null) {
                    val newCount = maxOf(0, entity.trackCount - 1)
                    playlistDao.insertOrReplace(entity.copy(trackCount = newCount))
                }

                val payload = SyncPayloadSerialization.encodeRemoveTrack(
                    RemoveTrackPayload(
                        localKey = targetKey,
                        serverId = entity?.serverId,
                        trackPath = track.id
                    )
                )
                syncQueueDao.enqueueAction(
                    SyncQueueEntity(
                        accountKey = accountKey,
                        actionType = SyncActionType.REMOVE_TRACK,
                        payloadJson = payload
                    )
                )
            }
            SangitaSyncWorker.enqueueOneTime(context)

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncLibrary(): Result<Unit> = SyncSerialization.mutex.withLock { withContext(Dispatchers.IO) {
        try {
            val api = networkClient.createApiService()

            // 1. Attempt V2 getPlaylists()
            val v2PlaylistsResp = try {
                api.getPlaylists()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return@withContext Result.failure(e)
            }

            // Fall back to legacy if V2 returns 404
            if (v2PlaylistsResp.code() == 404) {
                v2Supported = false
                return@withContext syncLegacyLibrary(api)
            }

            if (!v2PlaylistsResp.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch V2 playlists: ${v2PlaylistsResp.code()}"))
            }

            v2Supported = true

            val remotePlaylistsDto = v2PlaylistsResp.body()?.playlists ?: emptyList()

            // Best-effort update catalog sources and artists StateFlows
            try { refreshCatalogSources() } catch (_: Exception) {}
            try { refreshArtists() } catch (_: Exception) {}

            // Durations, favorites, metadata
            val durationsResp = api.getDurations()
            val favoritesResp = api.getFavorites()
            val durationsMap = durationsResp.body() ?: emptyMap()
            val favoritesAvailable = favoritesResp.isSuccessful
            val remoteFavorites = (favoritesResp.body() ?: emptyList()).toSet()
            val metadata = try {
                val response = api.getTrackMetadata()
                if (response.isSuccessful) response.body() ?: emptyMap() else emptyMap()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }

            val accountKey = userPreferences.getAccountKey()

            // Check pending sync actions to identify playlists with pending local modifications
            val pending = syncQueueDao.getPendingActions(accountKey)
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
                            val path = (payload["trackId"] ?: payload["track_path"])?.jsonPrimitive?.content
                            val isFav = (payload["isFavorite"] ?: payload["is_favorite"])?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                            if (path != null) {
                                pendingFavorites[path] = isFav ?: (trackDao.getTrackById(path)?.isFavorite ?: false)
                            }
                        }
                        SyncActionType.ADD_TRACK_TO_PLAYLIST, SyncActionType.REMOVE_TRACK_FROM_PLAYLIST,
                        SyncActionType.ADD_TRACK, SyncActionType.REMOVE_TRACK -> {
                            payload["local_key"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["localKey"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["playlist_id"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["serverId"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["playlist"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                        }
                        SyncActionType.CREATE_PLAYLIST, SyncActionType.UPDATE_PLAYLIST,
                        SyncActionType.DELETE_PLAYLIST, SyncActionType.CLONE_PLAYLIST -> {
                            payload["local_key"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["localKey"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["playlist_id"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["serverId"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["new_local_key"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["title"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["name"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                        }
                        else -> Unit
                    }
                } catch (_: Exception) {}
            }

            val currentUsername = userPreferences.getUsername()
            val currentRole = userPreferences.getRole()
            val savedPlaylistIds = userPreferences.getSavedPlaylistIds()

            if (accountKey != "default_account" && !userPreferences.isInitialDefaultAccountMigrated()) {
                migrateDefaultAccountPlaylistsInternal(accountKey, remotePlaylistsDto)
            }
            val localPlaylists = playlistDao.getAllPlaylists(accountKey)
            val plan = reconcilePlaylists(
                localPlaylists = localPlaylists,
                remotePlaylists = remotePlaylistsDto,
                accountKey = accountKey,
                currentUsername = currentUsername,
                savedPlaylistIds = savedPlaylistIds
            )

            // Fetch playlist detail for each remote playlist
            // Never delete local playlists or downloaded tracks during network failures, 500s, or partial fetches!
            val playlistTrackCrossRefs = mutableMapOf<String, List<PlaylistTrackCrossRef>>()
            val trackEntitiesMap = linkedMapOf<String, TrackEntity>()
            val updatedUpserts = plan.toUpsert.toMutableList()

            for (remote in remotePlaylistsDto) {
                val isSaved = remote.isSaved || savedPlaylistIds.contains(remote.id)
                val isUserOwned = !currentUsername.isNullOrBlank() && remote.owner != null && remote.owner.equals(currentUsername, ignoreCase = true)
                // For un-saved folder playlists, do NOT fetch tracks or insert cross-references into the personal library.
                if (remote.isFolderPlaylist && !isSaved && !isUserOwned) {
                    continue
                }

                val localKey = plan.remoteIdToLocalKey[remote.id] ?: continue
                if (localKey in pendingPlaylists) {
                    continue
                }

                try {
                    val detailResp = api.getPlaylistDetail(remote.id)
                    if (detailResp.isSuccessful && detailResp.body() != null) {
                        val detail = detailResp.body()!!
                        val isOwner = (detail.owner != null && detail.owner == currentUsername) || currentRole == "admin"

                        val refs = reconcilePlaylistTracks(localKey, detail.tracks, isOwner)
                        playlistTrackCrossRefs[localKey] = refs

                        val idx = updatedUpserts.indexOfFirst { it.localKey == localKey }
                        if (idx >= 0) {
                            val current = updatedUpserts[idx]
                            val coverTrack = refs.firstOrNull { it.isAvailable }?.trackId ?: refs.firstOrNull()?.trackId
                            updatedUpserts[idx] = current.copy(
                                unavailableCount = detail.unavailableCount,
                                trackCount = if (isOwner) detail.trackCount else refs.size,
                                coverTrackPath = coverTrack,
                                lastSyncedAt = System.currentTimeMillis()
                            )
                        }

                        for (t in detail.tracks) {
                            if (t.trackPath !in trackEntitiesMap) {
                                val existing = trackDao.getTrackById(t.trackPath)
                                val (fallbackTitle, fallbackArtist) = parseTrackMetadata(t.trackPath)
                                val tags = metadata[t.trackPath]
                                val audioFile = localStorageManager.getExistingAudioFile(t.trackPath, existing?.localFilePath)
                                val isDownloadedLocally = audioFile != null

                                val trackTitle = t.title.takeIf { it.isNotBlank() }
                                    ?: tags?.title?.takeIf { it.isNotBlank() }
                                    ?: existing?.title
                                    ?: fallbackTitle
                                val trackArtist = t.artist.takeIf { it.isNotBlank() }
                                    ?: tags?.artist?.takeIf { it.isNotBlank() }
                                    ?: existing?.artist
                                    ?: fallbackArtist

                                trackEntitiesMap[t.trackPath] = TrackEntity(
                                    id = t.trackPath,
                                    playlistName = detail.title,
                                    title = trackTitle,
                                    artist = trackArtist,
                                    album = tags?.album?.takeIf { it.isNotBlank() } ?: existing?.album.orEmpty(),
                                    durationSeconds = t.duration ?: durationsMap[t.trackPath] ?: existing?.durationSeconds,
                                    remoteUrl = "${userPreferences.getServerUrl()}/api/stream/${t.trackPath}",
                                    localFilePath = audioFile?.absolutePath ?: existing?.localFilePath,
                                    isDownloaded = isDownloadedLocally || (existing?.isDownloaded == true),
                                    downloadStatus = existing?.downloadStatus ?: DownloadStatus.IDLE,
                                    downloadProgress = if (isDownloadedLocally) 100 else (existing?.downloadProgress ?: 0),
                                    fileSizeBytes = audioFile?.length() ?: (existing?.fileSizeBytes ?: 0L),
                                    isFavorite = if (favoritesAvailable) t.trackPath in remoteFavorites else existing?.isFavorite ?: false,
                                    downloadedAt = existing?.downloadedAt
                                )
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Partial fetch failure: preserve existing local tracks for this playlist!
                }
            }

            // Apply to database in transaction
            database.withTransaction {
                val desiredFavorites = reconcileFavorites(remoteFavorites, pendingFavorites)
                if (favoritesAvailable) {
                    favoriteDao.clearAll()
                    trackDao.clearFavoriteStatuses()
                    desiredFavorites.forEach { favoriteDao.insert(FavoriteEntity(trackId = it)) }
                }

                // Delete playlists marked for deletion (only if not in pendingPlaylists)
                for (key in plan.toDeleteKeys) {
                    if (key !in pendingPlaylists) {
                        playlistDao.clearTracksForPlaylist(key)
                        playlistDao.deletePlaylistByKey(accountKey, key)
                    }
                }

                // Upsert playlists
                playlistDao.insertOrReplaceAll(updatedUpserts)

                // Update cross-refs
                for ((key, refs) in playlistTrackCrossRefs) {
                    if (key !in pendingPlaylists) {
                        playlistDao.clearTracksForPlaylist(key)
                        playlistDao.insertCrossRefs(refs)
                    }
                }

                // Upsert tracks
                if (trackEntitiesMap.isNotEmpty()) {
                    val finalTracks = trackEntitiesMap.values.map { track ->
                        if (favoritesAvailable) {
                            track.copy(isFavorite = track.id in desiredFavorites)
                        } else track
                    }
                    trackDao.insertOrReplaceAll(finalTracks)
                }
            }

            // Sync saved artists and saved tracks
            try {
                val savedArtistsResp = api.getSavedArtists()
                if (savedArtistsResp.isSuccessful && savedArtistsResp.body() != null) {
                    _savedArtists.value = savedArtistsResp.body()!!.artists
                }
            } catch (_: Exception) {}

            try {
                val savedTracksResp = api.getSavedTracks()
                if (savedTracksResp.isSuccessful && savedTracksResp.body() != null) {
                    _savedTracks.value = savedTracksResp.body()!!.tracks.map { pt ->
                        val (fallbackTitle, fallbackArtist) = parseTrackMetadata(pt.trackPath)
                        Track(
                            id = pt.trackPath,
                            playlist = "Library",
                            title = pt.title.ifBlank { fallbackTitle },
                            artist = pt.artist.ifBlank { fallbackArtist },
                            album = "",
                            durationSeconds = pt.duration,
                            remoteUrl = "${userPreferences.getServerUrl()}/api/stream/${pt.trackPath}",
                            isAvailable = pt.isAvailable
                        )
                    }
                }
            } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    } }

    override suspend fun refreshPlaylists(): Result<Unit> = syncLibrary()

    private suspend fun syncLegacyLibrary(api: SangitaApiService): Result<Unit> {
        val playlistsResp = api.getLegacyPlaylists()
        val durationsResp = api.getDurations()
        val favoritesResp = api.getFavorites()

        if (!playlistsResp.isSuccessful) {
            return Result.failure(Exception("Failed to fetch legacy playlists: ${playlistsResp.code()}"))
        }

        val playlistsMap = playlistsResp.body() ?: emptyMap()
        val durationsMap = durationsResp.body() ?: emptyMap()
        val favoritesAvailable = favoritesResp.isSuccessful
        val remoteFavorites = (favoritesResp.body() ?: emptyList()).toSet()
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
                            val path = (payload["trackId"] ?: payload["track_path"])?.jsonPrimitive?.content
                            val isFav = (payload["isFavorite"] ?: payload["is_favorite"])?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                            if (path != null) {
                                pendingFavorites[path] = isFav ?: (trackDao.getTrackById(path)?.isFavorite ?: false)
                            }
                        }
                        SyncActionType.ADD_TRACK_TO_PLAYLIST, SyncActionType.REMOVE_TRACK_FROM_PLAYLIST,
                        SyncActionType.ADD_TRACK, SyncActionType.REMOVE_TRACK -> {
                            payload["local_key"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["localKey"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["playlist_id"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["serverId"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["playlist"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                        }
                        SyncActionType.CREATE_PLAYLIST, SyncActionType.UPDATE_PLAYLIST,
                        SyncActionType.DELETE_PLAYLIST, SyncActionType.CLONE_PLAYLIST -> {
                            payload["local_key"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["localKey"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["playlist_id"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["serverId"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["new_local_key"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["title"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                            payload["name"]?.jsonPrimitive?.content?.let(pendingPlaylists::add)
                        }
                        else -> Unit
                    }
                } catch (_: Exception) {}
            }

            val desiredFavorites = reconcileFavorites(remoteFavorites, pendingFavorites)
            if (favoritesAvailable) {
                favoriteDao.clearAll()
                trackDao.clearFavoriteStatuses()
                desiredFavorites.forEach { favoriteDao.insert(FavoriteEntity(trackId = it)) }
            }

            val playlistEntities = mutableListOf<PlaylistEntity>()
            val trackEntities = linkedMapOf<String, TrackEntity>()
            val accountKey = userPreferences.getAccountKey()

            playlistsMap.forEach { (plName, trackPaths) ->
                val isFav = plName == "Favorites"
                val existing = playlistDao.getPlaylistByTitle(accountKey, plName)
                val localKey = existing?.localKey ?: ("legacy_" + UUID.randomUUID().toString())
                val kind = if (isFav) PlaylistKind.FAVORITES.name else PlaylistKind.FOLDER.name

                playlistEntities.add(
                    PlaylistEntity(
                        localKey = localKey,
                        serverId = existing?.serverId ?: plName,
                        accountKey = accountKey,
                        title = plName,
                        kind = kind,
                        owner = existing?.owner,
                        visibility = existing?.visibility ?: "private",
                        description = existing?.description,
                        trackCount = trackPaths.size,
                        coverTrackPath = trackPaths.take(4).joinToString(",").ifBlank { null },
                        unavailableCount = 0,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        lastSyncedAt = System.currentTimeMillis(),
                        syncState = existing?.syncState ?: PlaylistSyncState.SYNCED.name
                    )
                )

                if (!isFav && plName !in pendingPlaylists && localKey !in pendingPlaylists) {
                    playlistDao.clearTracksForPlaylist(localKey)
                    playlistDao.insertCrossRefs(playlistMemberships(localKey, trackPaths))
                }

                trackPaths.forEach { path ->
                    if (path !in trackEntities) {
                        val existingTrack = trackDao.getTrackById(path)
                        val (fallbackTitle, fallbackArtist) = parseTrackMetadata(path)
                        val tags = metadata[path]
                        val audioFile = localStorageManager.getExistingAudioFile(path, existingTrack?.localFilePath)
                        val isDownloadedLocally = audioFile != null
                        val sourcePlaylist = path.substringBeforeLast('/', "Library")

                        trackEntities[path] = TrackEntity(
                            id = path,
                            playlistName = sourcePlaylist,
                            title = tags?.title?.takeIf { it.isNotBlank() } ?: existingTrack?.title ?: fallbackTitle,
                            artist = tags?.artist?.takeIf { it.isNotBlank() } ?: existingTrack?.artist ?: fallbackArtist,
                            album = tags?.album?.takeIf { it.isNotBlank() } ?: existingTrack?.album.orEmpty(),
                            durationSeconds = durationsMap[path] ?: existingTrack?.durationSeconds,
                            remoteUrl = "${userPreferences.getServerUrl()}/api/stream/$path",
                            localFilePath = audioFile?.absolutePath ?: existingTrack?.localFilePath,
                            isDownloaded = isDownloadedLocally || (existingTrack?.isDownloaded == true),
                            downloadStatus = existingTrack?.downloadStatus ?: DownloadStatus.IDLE,
                            downloadProgress = if (isDownloadedLocally) 100 else (existingTrack?.downloadProgress ?: 0),
                            fileSizeBytes = audioFile?.length() ?: (existingTrack?.fileSizeBytes ?: 0L),
                            isFavorite = if (favoritesAvailable) path in desiredFavorites else existingTrack?.isFavorite ?: false,
                            downloadedAt = existingTrack?.downloadedAt
                        )
                    }
                }
            }

            playlistDao.insertOrReplaceAll(playlistEntities)
            trackDao.insertOrReplaceAll(trackEntities.values.toList())
        }

        return Result.success(Unit)
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
