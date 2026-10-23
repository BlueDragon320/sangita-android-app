package com.sangita.app.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sangita.app.data.local.dao.TrackDao
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.data.local.storage.StorageUsageInfo
import com.sangita.app.domain.model.Track
import com.sangita.app.download.SangitaDownloadManager
import com.sangita.app.player.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val storageUsage: StorageUsageInfo? = null,
    val isLoading: Boolean = false
)

class DownloadsViewModel(
    private val trackDao: TrackDao,
    private val localStorageManager: LocalStorageManager,
    private val downloadManager: SangitaDownloadManager,
    val playbackController: PlaybackController,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    val serverUrl = userPreferences.getServerUrl()
    val token = userPreferences.getToken()
    val playbackState = playbackController.playbackState

    val downloadedTracks: StateFlow<List<Track>> = trackDao.observeDownloadedTracks()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshStorageUsage()
    }

    fun refreshStorageUsage() {
        viewModelScope.launch {
            val usage = localStorageManager.getStorageUsage()
            _uiState.update { it.copy(storageUsage = usage) }
        }
    }

    fun playTrack(track: Track) {
        val tracks = downloadedTracks.value
        playbackController.playTrack(track, tracks, forceLocal = true)
    }

    fun deleteDownloadedTrack(track: Track) {
        viewModelScope.launch {
            downloadManager.removeDownloadedTrack(track.id)
            refreshStorageUsage()
        }
    }
}
