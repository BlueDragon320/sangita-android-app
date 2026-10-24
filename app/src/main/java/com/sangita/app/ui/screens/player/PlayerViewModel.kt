package com.sangita.app.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.domain.model.RepeatMode
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.MusicRepository
import com.sangita.app.download.SangitaDownloadManager
import com.sangita.app.player.PlaybackController
import com.sangita.app.sync.DeviceSyncManager
import com.sangita.app.sync.SyncedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    val playbackController: PlaybackController,
    private val musicRepository: MusicRepository,
    private val downloadManager: SangitaDownloadManager,
    private val userPreferences: UserPreferences,
    val deviceSyncManager: DeviceSyncManager? = null
) : ViewModel() {

    val playbackState = playbackController.playbackState
    val serverUrl = userPreferences.getServerUrl()
    val token = userPreferences.getToken()

    val devices: StateFlow<List<SyncedDevice>> = deviceSyncManager?.devices
        ?: MutableStateFlow(emptyList())

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun skipNext() = playbackController.skipNext()
    fun skipPrevious() = playbackController.skipPrevious()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    fun toggleRepeatMode() {
        val current = playbackState.value.repeatMode
        val next = when (current) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        playbackController.setRepeatMode(next)
    }

    fun toggleShuffle() = playbackController.toggleShuffle()

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(track)
        }
    }

    fun downloadCurrentTrack() {
        playbackState.value.currentTrack?.let {
            downloadManager.downloadTrack(it)
        }
    }

    fun transferPlayback(targetDeviceId: String) {
        if (deviceSyncManager != null && targetDeviceId == deviceSyncManager.myDeviceId) {
            playbackController.playHere()
        } else {
            val currentPos = playbackState.value.currentPositionMs
            val currentDur = playbackState.value.durationMs
            val currentTrackId = playbackState.value.currentTrack?.id
            deviceSyncManager?.transferPlayback(
                targetDeviceId = targetDeviceId,
                positionMs = currentPos,
                durationMs = if (currentDur > 0) currentDur else null,
                trackId = currentTrackId
            )
        }
    }

    fun refreshDevices() {
        deviceSyncManager?.refreshDevices()
    }
}
