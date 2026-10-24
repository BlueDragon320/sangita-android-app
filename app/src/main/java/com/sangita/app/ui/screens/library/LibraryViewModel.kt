package com.sangita.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.MusicRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedFilter: String? = null,
    val showCreateDialog: Boolean = false,
    val newPlaylistName: String = "",
    val isRefreshing: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showDownloadSheet: Boolean = false,
    val connectedDomain: String = "",
    val currentTheme: String = UserPreferences.THEME_AMOLED,
    val isSyncing: Boolean = false,
    val username: String = ""
)

class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val networkClient: NetworkClient,
    val userPreferences: UserPreferences,
    val playbackController: PlaybackController,
    val downloadManager: SangitaDownloadManager,
    val deviceSyncManager: DeviceSyncManager? = null,
    private val connectivityObserver: com.sangita.app.sync.NetworkConnectivityObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LibraryUiState(
            username = userPreferences.getUsername() ?: "Blue Dragon",
            connectedDomain = userPreferences.getServerUrl(),
            currentTheme = userPreferences.getTheme()
        )
    )
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

    val filteredPlaylists: StateFlow<List<Playlist>> = combine(
        musicRepository.observePlaylists(),
        musicRepository.observeDownloadedTracks(),
        isOnline,
        _uiState
    ) { allPlaylists, downloadedTracks, online, state ->
        val query = state.searchQuery.trim()
        val basePlaylists = if (online) {
            when (state.selectedFilter) {
                "Playlists" -> allPlaylists.filter { !it.isFavorites }
                else -> allPlaylists
            }
        } else {
            val downloadedByPlaylist = downloadedTracks.groupBy { it.playlist }
            val offlineList = mutableListOf<Playlist>()
            for (pl in allPlaylists) {
                val dTracks = downloadedByPlaylist[pl.name] ?: emptyList()
                if (dTracks.isNotEmpty()) {
                    offlineList.add(pl.copy(trackCount = dTracks.size, tracks = dTracks))
                }
            }
            val handledNames = offlineList.map { it.name }.toSet()
            for ((name, dTracks) in downloadedByPlaylist) {
                if (name !in handledNames && dTracks.isNotEmpty()) {
                    offlineList.add(
                        Playlist(
                            name = name,
                            isVirtual = false,
                            isFavorites = name.equals("Favorites", ignoreCase = true),
                            trackCount = dTracks.size,
                            tracks = dTracks
                        )
                    )
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

    val playlists: StateFlow<List<Playlist>> = filteredPlaylists

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
            it.copy(selectedFilter = if (it.selectedFilter == filter) null else filter)
        }
    }

    fun openCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true, newPlaylistName = "") }
    }

    fun closeCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun setNewPlaylistName(name: String) {
        _uiState.update { it.copy(newPlaylistName = name) }
    }

    fun onNewPlaylistNameChange(name: String) = setNewPlaylistName(name)

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
                durationMs = if (state.durationMs > 0) state.durationMs else null
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
            _uiState.update { it.copy(isSyncing = true) }
            try {
                musicRepository.refreshPlaylists()
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun logout(onSuccess: () -> Unit) {
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
}
