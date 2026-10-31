package com.sangita.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sangita.app.data.local.dao.PlaylistDao
import com.sangita.app.data.local.dao.TrackDao
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.data.remote.dto.ArtistDto
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.MusicRepository
import com.sangita.app.domain.repository.SyncRepository
import kotlinx.coroutines.CancellationException
import com.sangita.app.download.SangitaDownloadManager
import com.sangita.app.player.PlaybackController
import com.sangita.app.sync.DeviceSyncManager
import com.sangita.app.sync.SyncedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedFilter: String? = "Playlists",
    val showCreateDialog: Boolean = false,
    val showRecoveryDialog: Boolean = false,
    val newPlaylistName: String = "",
    val isRefreshing: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showDownloadSheet: Boolean = false,
    val connectedDomain: String = "",
    val currentTheme: String = UserPreferences.THEME_DARK,
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val username: String = ""
)

class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val syncRepository: SyncRepository,
    private val networkClient: NetworkClient,
    val userPreferences: UserPreferences,
    val playbackController: PlaybackController,
    val downloadManager: SangitaDownloadManager,
    val deviceSyncManager: DeviceSyncManager? = null,
    private val connectivityObserver: com.sangita.app.sync.NetworkConnectivityObserver,
    private val playlistDao: PlaylistDao? = null,
    private val trackDao: TrackDao? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LibraryUiState(
            username = userPreferences.getUsername() ?: "Blue Dragon",
            connectedDomain = userPreferences.getServerUrl(),
            currentTheme = userPreferences.getTheme()
        )
    )
    val pendingChanges = syncRepository.getSyncQueueSize().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, connectivityObserver.isOnline)

    val serverUrl: String
        get() = userPreferences.getServerUrl()
    val token: String?
        get() = userPreferences.getToken()
    val playbackState = playbackController.playbackState

    val devices: StateFlow<List<SyncedDevice>> = deviceSyncManager?.devices
        ?: MutableStateFlow(emptyList())

    val username: String
        get() = userPreferences.getUsername()?.takeIf { it.isNotBlank() } ?: "Blue Dragon"
    val userInitial: Char
        get() = username.firstOrNull()?.uppercaseChar() ?: 'B'

    val libraryPlaylists: StateFlow<List<Playlist>> = combine(
        musicRepository.observePlaylists(),
        musicRepository.observeDownloadedTracks(),
        userPreferences.observeSavedPlaylistIds(),
        isOnline,
        _uiState
    ) { allPlaylists, downloadedTracks, savedIds, online, state ->
        val query = state.searchQuery.trim()
        val currentUsername = userPreferences.getUsername()

        // Filter: user's own playlists, Favorites, and explicitly saved/added playlists
        val libraryPlaylists = allPlaylists.filter { pl ->
            pl.isFavorites ||
                pl.isSaved ||
                savedIds.contains(pl.id) ||
                (pl.serverId != null && savedIds.contains(pl.serverId)) ||
                (pl.owner != null && pl.owner.equals(currentUsername, ignoreCase = true)) ||
                (pl.isPersonal && (pl.owner.isNullOrBlank() || pl.owner.equals(currentUsername, ignoreCase = true)))
        }

        val basePlaylists = if (online) {
            libraryPlaylists
        } else {
            val downloadedIds = downloadedTracks.filter { it.isDownloaded }.map { it.id }.toSet()
            val offlineList = mutableListOf<Playlist>()
            for (pl in libraryPlaylists) {
                val plTracks = if (pl.isFavorites) {
                    musicRepository.observeFavorites().firstOrNull() ?: emptyList()
                } else if (playlistDao != null) {
                    playlistDao.observeTracksForPlaylist(pl.id).firstOrNull()?.map { it.toDomain() } ?: emptyList()
                } else {
                    musicRepository.observeTracks(pl.id).firstOrNull() ?: emptyList()
                }
                val dTracks = plTracks.filter { it.id in downloadedIds || it.isDownloaded }
                if (dTracks.isNotEmpty()) {
                    offlineList.add(pl.copy(trackCount = dTracks.size, tracks = dTracks))
                }
            }
            if (offlineList.none { it.isFavorites }) {
                val favTracks = musicRepository.observeFavorites().firstOrNull()
                    ?.filter { it.id in downloadedIds || it.isDownloaded } ?: emptyList()
                if (favTracks.isNotEmpty()) {
                    offlineList.add(0, Playlist(name = "Favorites", isFavorites = true, trackCount = favTracks.size, tracks = favTracks))
                }
            }
            offlineList
        }

        if (query.isBlank()) {
            basePlaylists
        } else {
            basePlaylists.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = libraryPlaylists
    val filteredPlaylists: StateFlow<List<Playlist>> = libraryPlaylists

    val librarySongs: StateFlow<List<Track>> = combine(
        musicRepository.observeSavedTracks(), musicRepository.observeDownloadedTracks(), isOnline, _uiState
    ) { saved, downloads, online, state ->
        val downloadedById = downloads.associateBy { it.id }
        val visible = if (online) saved else saved.mapNotNull { downloadedById[it.id] }
        val query = state.searchQuery.trim()
        visible.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryArtists: StateFlow<List<ArtistDto>> = combine(
        musicRepository.observeSavedArtists(), _uiState
    ) { artists, state ->
        val query = state.searchQuery.trim()
        artists.filter { query.isBlank() || it.name.contains(query, true) }.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { musicRepository.getSavedArtists(); musicRepository.getSavedTracks() }
    }

    fun removeSavedTrack(path: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(musicRepository.removeTrack(path).isSuccess) }
    }

    fun removeSavedArtist(id: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(musicRepository.removeArtist(id).isSuccess) }
    }

    val downloadedTracks: StateFlow<List<Track>> = combine(
        musicRepository.observeDownloadedTracks(),
        _uiState
    ) { tracks, state ->
        val query = state.searchQuery.trim()
        if (query.isBlank()) {
            tracks
        } else {
            tracks.filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                track.artist.contains(query, ignoreCase = true) ||
                track.album.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTracks: StateFlow<List<Track>> = combine(
        musicRepository.observeAllTracks(),
        musicRepository.observeDownloadedTracks(),
        isOnline,
        _uiState
    ) { allTracks, downloadedTracks, online, state ->
        val sourceTracks = if (online) allTracks else downloadedTracks
        val query = state.searchQuery.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            sourceTracks.filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                track.artist.contains(query, ignoreCase = true) ||
                track.album.contains(query, ignoreCase = true) ||
                track.playlist.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchQueryChange(query: String) = updateSearchQuery(query)

    fun toggleSearch() {
        _uiState.update {
            val nextActive = !it.isSearchActive
            it.copy(
                isSearchActive = nextActive,
                searchQuery = if (!nextActive) "" else it.searchQuery
            )
        }
    }

    fun setFilter(filter: String?) {
        _uiState.update {
            it.copy(selectedFilter = filter ?: "Playlists")
        }
    }

    fun selectFilter(filter: String) {
        _uiState.update {
            it.copy(selectedFilter = filter)
        }
    }

    fun openCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true, newPlaylistName = "") }
    }

    fun openCreatePlaylistDialog() = openCreateDialog()

    fun addTrackToPlaylist(playlist: Playlist, track: Track, onResult: ((Boolean) -> Unit)? = null) {
        addTrackToPlaylist(playlist.id, track, onResult)
    }

    fun addTrackToPlaylist(playlistKey: String, track: Track, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val result = musicRepository.addTrackToPlaylist(playlistKey, track)
            onResult?.invoke(result.isSuccess)
        }
    }

    fun closeCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun setNewPlaylistName(name: String) {
        _uiState.update { it.copy(newPlaylistName = name) }
    }

    fun onNewPlaylistNameChange(name: String) = setNewPlaylistName(name)

    fun createPlaylist(
        title: String,
        description: String? = null,
        isPublic: Boolean = false,
        onSuccess: ((Playlist) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = musicRepository.createPlaylist(title = title, description = description, isPublic = isPublic)
            if (result.isSuccess) {
                closeCreateDialog()
                result.getOrNull()?.let { onSuccess?.invoke(it) }
            }
        }
    }

    fun createPlaylist(onSuccess: (String) -> Unit = {}) {
        val name = _uiState.value.newPlaylistName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            val result = musicRepository.createPlaylist(name)
            if (result.isSuccess) {
                closeCreateDialog()
                onSuccess(name)
            }
        }
    }

    fun refreshPlaylists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                musicRepository.refreshPlaylists()
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun downloadPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val tracks = musicRepository.observeTracks(playlist.name).firstOrNull() ?: emptyList()
            downloadManager.downloadPlaylist(tracks)
        }
    }

    fun transferPlayback(targetDeviceId: String) {
        if (deviceSyncManager != null && targetDeviceId == deviceSyncManager.myDeviceId) {
            playbackController.playHere()
        } else {
            val state = playbackController.playbackState.value
            deviceSyncManager?.transferPlayback(
                targetDeviceId = targetDeviceId,
                positionMs = state.currentPositionMs,
                durationMs = if (state.durationMs > 0) state.durationMs else null,
                trackId = state.currentTrack?.id
            )
        }
    }

    fun refreshDevices() {
        deviceSyncManager?.refreshDevices()
    }

    fun openSettingsSheet() {
        _uiState.update {
            it.copy(
                showSettingsSheet = true,
                connectedDomain = userPreferences.getServerUrl(),
                currentTheme = userPreferences.getTheme(),
                username = userPreferences.getUsername() ?: "Blue Dragon"
            )
        }
    }

    fun closeSettingsSheet() {
        _uiState.update { it.copy(showSettingsSheet = false) }
    }

    fun updateDomain(newDomain: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val normalized = UserPreferences.normalizeUrl(newDomain)
            if (normalized.isBlank()) {
                onResult(false, "Invalid URL entered")
                return@launch
            }
            userPreferences.setServerUrl(normalized)
            _uiState.update { it.copy(connectedDomain = normalized) }
            try {
                val api = networkClient.createApiService()
                val resp = api.getPlaylists()
                if (resp.isSuccessful) {
                    musicRepository.refreshPlaylists()
                    onResult(true, "Successfully connected to $normalized")
                } else {
                    onResult(true, "Saved domain: $normalized (HTTP ${resp.code()})")
                }
            } catch (e: Exception) {
                onResult(false, "Could not reach server: ${e.localizedMessage ?: "Connection error"}")
            }
        }
    }

    fun setTheme(theme: String) {
        userPreferences.setTheme(theme)
        _uiState.update { it.copy(currentTheme = theme) }
    }

    fun syncLibraryNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncError = null) }
            try {
                syncRepository.syncPendingActions().getOrThrow()
                musicRepository.refreshPlaylists().getOrThrow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(syncError = "Could not sync. Check your connection and try again.") }
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun logout(onSuccess: () -> Unit) {
        musicRepository.clearMemoryCache()
        deviceSyncManager?.disconnect()
        deviceSyncManager?.resetSyncState()
        playbackController.resetPlayback()
        userPreferences.clearAuth()
        onSuccess()
    }

    fun playTrack(track: Track) {
        playbackController.playTrack(track, filteredTracks.value.ifEmpty { listOf(track) })
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(track)
        }
    }

    val activeDownloads = downloadManager.activeDownloads
    val activeDownloadsCount = downloadManager.activeDownloadsCount
    val totalDownloadSpeed = downloadManager.totalSpeedBytesPerSec

    val recoverableLegacyPlaylists: StateFlow<List<Playlist>> = musicRepository.observeRecoverableLegacyPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openRecoveryDialog() {
        _uiState.update { it.copy(showRecoveryDialog = true) }
    }

    fun closeRecoveryDialog() {
        _uiState.update { it.copy(showRecoveryDialog = false) }
    }

    fun recoverLegacyPlaylist(localKey: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val res = musicRepository.recoverLegacyPlaylist(localKey)
            _uiState.update { it.copy(isRefreshing = false) }
            onComplete?.invoke(res.isSuccess)
        }
    }

    fun recoverAllLegacyPlaylists(onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val res = musicRepository.recoverAllLegacyPlaylists()
            _uiState.update { it.copy(isRefreshing = false, showRecoveryDialog = false) }
            onComplete?.invoke(res.getOrDefault(0))
        }
    }

    fun openDownloadSheet() {
        _uiState.update { it.copy(showDownloadSheet = true) }
    }

    fun closeDownloadSheet() {
        _uiState.update { it.copy(showDownloadSheet = false) }
    }

    fun cancelDownload(trackId: String) {
        downloadManager.cancelDownload(trackId)
    }

    fun cancelAllDownloads() {
        downloadManager.cancelAllDownloads()
    }

    fun downloadTrack(track: Track) {
        downloadManager.downloadTrack(track)
    }

    fun deleteDownloadedTrack(track: Track) {
        viewModelScope.launch {
            downloadManager.removeDownloadedTrack(track.id)
        }
    }
}
