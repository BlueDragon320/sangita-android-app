package com.sangita.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.data.remote.dto.UserStatsResponse
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.MusicRepository
import com.sangita.app.download.SangitaDownloadManager
import com.sangita.app.player.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val username: String = "",
    val connectedDomain: String = "",
    val stats: UserStatsResponse? = null,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showDevicesSheet: Boolean = false,
    val currentTheme: String = UserPreferences.THEME_AMOLED,
    val errorMessage: String? = null
)

class HomeViewModel(
    val userPreferences: UserPreferences,
    private val networkClient: NetworkClient,
    private val musicRepository: MusicRepository,
    val playbackController: PlaybackController,
    val downloadManager: SangitaDownloadManager,
    val deviceSyncManager: com.sangita.app.sync.DeviceSyncManager? = null,
    private val connectivityObserver: com.sangita.app.sync.NetworkConnectivityObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            username = userPreferences.getUsername() ?: "Listener",
            connectedDomain = userPreferences.getServerUrl(),
            currentTheme = userPreferences.getTheme()
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, connectivityObserver.isOnline)

    private val _dataUsageToday = MutableStateFlow("0 B")
    val dataUsageToday: StateFlow<String> = _dataUsageToday.asStateFlow()

    val playbackState = playbackController.playbackState
    val serverUrl: String get() = userPreferences.getServerUrl()
    val token: String? get() = userPreferences.getToken()
    val themeFlow: StateFlow<String> = userPreferences.themeFlow

    val tracksMap: StateFlow<Map<String, Track>> = musicRepository.observeAllTracks()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch {
            connectivityObserver.observe().collect { online ->
                if (online && _uiState.value.stats == null) {
                    loadHomeData(force = true)
                }
            }
        }
        loadHomeData()
    }

    fun loadHomeData(force: Boolean = false) {
        if (!force && _uiState.value.stats != null) return
        viewModelScope.launch {
            if (!connectivityObserver.isOnline) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true) }
            try {
                val api = networkClient.createApiService()
                val resp = api.getUserStats()
                if (resp.isSuccessful && resp.body() != null) {
                    val stats = resp.body()
                    _uiState.update {
                        it.copy(
                            stats = stats,
                            isLoading = false
                        )
                    }
                    if (stats?.dataConsumedToday?.formatted != null) {
                        _dataUsageToday.value = stats.dataConsumedToday.formatted
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }

                try {
                    val dataUsageResp = api.getDataUsage()
                    if (dataUsageResp.isSuccessful && dataUsageResp.body() != null) {
                        _dataUsageToday.value = dataUsageResp.body()!!.today.formatted
                    }
                } catch (_: Exception) {}
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun syncLibraryNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            musicRepository.refreshPlaylists()
            loadHomeData()
            _uiState.update { it.copy(isSyncing = false) }
        }
    }

    fun openSettingsSheet() {
        _uiState.update {
            it.copy(
                showSettingsSheet = true,
                connectedDomain = userPreferences.getServerUrl(),
                currentTheme = userPreferences.getTheme()
            )
        }
    }

    val devices: StateFlow<List<com.sangita.app.sync.SyncedDevice>> = deviceSyncManager?.devices
        ?: MutableStateFlow(emptyList())

    fun openDevicesSheet() {
        _uiState.update { it.copy(showDevicesSheet = true) }
    }

    fun closeDevicesSheet() {
        _uiState.update { it.copy(showDevicesSheet = false) }
    }

    fun refreshDevices() {
        deviceSyncManager?.refreshDevices()
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
        closeDevicesSheet()
    }

    fun closeSettingsSheet() {
        _uiState.update { it.copy(showSettingsSheet = false) }
    }

    fun setTheme(theme: String) {
        userPreferences.setTheme(theme)
        _uiState.update { it.copy(currentTheme = theme) }
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
                    loadHomeData()
                    onResult(true, "Successfully connected to $normalized")
                } else {
                    onResult(true, "Saved domain: $normalized (HTTP ${resp.code()})")
                }
            } catch (e: Exception) {
                onResult(false, "Could not reach server: ${e.localizedMessage ?: "Connection error"}")
            }
        }
    }

    fun quickShuffle() {
        viewModelScope.launch {
            val playlists = musicRepository.observePlaylists().firstOrNull() ?: emptyList()
            val allTracks = playlists.flatMap { it.tracks }.ifEmpty {
                musicRepository.observeTracks("Library").firstOrNull() ?: emptyList()
            }
            if (allTracks.isNotEmpty()) {
                val shuffled = allTracks.shuffled()
                playbackController.playTrack(shuffled.first(), shuffled)
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        userPreferences.clearAuth()
        onLoggedOut()
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        playbackController.playTrack(track, queue)
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(track)
        }
    }

    fun downloadTrack(track: Track) {
        downloadManager.downloadTrack(track)
    }
}
