package com.sangita.app.ui.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.MusicRepository
import com.sangita.app.download.SangitaDownloadManager
import com.sangita.app.player.PlaybackController
import com.sangita.app.sync.DeviceSyncManager
import com.sangita.app.sync.SyncedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(
    val playlistName: String,
    private val musicRepository: MusicRepository,
    val userPreferences: UserPreferences,
    val playbackController: PlaybackController,
    val downloadManager: SangitaDownloadManager,
    val deviceSyncManager: DeviceSyncManager? = null,
    private val connectivityObserver: com.sangita.app.sync.NetworkConnectivityObserver
) : ViewModel() {

    val serverUrl = userPreferences.getServerUrl()
    val token = userPreferences.getToken()
    val playbackState = playbackController.playbackState

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, connectivityObserver.isOnline)

    val devices: StateFlow<List<SyncedDevice>> = deviceSyncManager?.devices
        ?: MutableStateFlow(emptyList())

    val tracks: StateFlow<List<Track>> = combine(
        musicRepository.observeTracks(playlistName),
        isOnline
    ) { trackList, online ->
        if (online) trackList else trackList.filter { it.isDownloaded }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playTrack(track: Track) {
        val currentTracks = tracks.value
        playbackController.playTrack(track, currentTracks)
    }

    fun playAll(shuffle: Boolean = false) {
        val currentTracks = tracks.value
        if (currentTracks.isNotEmpty()) {
            val listToPlay = if (shuffle) currentTracks.shuffled() else currentTracks
            playbackController.playTrack(listToPlay.first(), listToPlay)
        }
    }

    fun downloadAll() {
        downloadManager.downloadPlaylist(tracks.value)
    }

    fun downloadTrack(track: Track) {
        downloadManager.downloadTrack(track)
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(track)
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
}
