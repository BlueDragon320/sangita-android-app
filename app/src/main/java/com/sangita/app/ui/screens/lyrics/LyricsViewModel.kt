package com.sangita.app.ui.screens.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.domain.model.LyricsData
import com.sangita.app.domain.repository.LyricsRepository
import com.sangita.app.lyrics.LrcParser
import com.sangita.app.player.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LyricsViewModel(
    val playbackController: PlaybackController,
    private val lyricsRepository: LyricsRepository,
    private val userPreferences: UserPreferences? = null
) : ViewModel() {

    val playbackState = playbackController.playbackState
    val serverUrl: String = userPreferences?.getServerUrl() ?: ""
    val token: String? = userPreferences?.getToken()

    private val _lyricsData = MutableStateFlow<LyricsData?>(null)
    val lyricsData: StateFlow<LyricsData?> = _lyricsData.asStateFlow()

    init {
        viewModelScope.launch {
            playbackState.collectLatest { state ->
                val track = state.currentTrack
                if (track != null && track.id != _lyricsData.value?.trackId) {
                    lyricsRepository.getLyrics(track.id).collectLatest { data ->
                        _lyricsData.value = data
                    }
                }
            }
        }
    }

    val activeLineIndex: StateFlow<Int> = combine(
        playbackState,
        _lyricsData
    ) { state, data ->
        if (data == null || !data.isSynced || data.lines.isEmpty()) {
            -1
        } else {
            val currentSec = state.currentPositionMs / 1000.0
            LrcParser.findActiveLineIndex(data.lines, currentSec)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    fun seekToLine(timeSec: Double) {
        val targetMs = (timeSec * 1000).toLong()
        playbackController.seekTo(targetMs)
    }

    fun togglePlayPause() = playbackController.togglePlayPause()

    fun skipNext() = playbackController.skipNext()

    fun skipPrevious() = playbackController.skipPrevious()

    val currentTheme: StateFlow<String> = userPreferences?.lyricsThemeFlow ?: MutableStateFlow("combo1")

    fun cycleTheme() {
        val curr = currentTheme.value
        val next = com.sangita.app.lyrics.LyricsThemePalette.getNextTheme(curr).id
        userPreferences?.setLyricsTheme(next)
    }

    fun toggleMonochrome() {
        val curr = currentTheme.value
        val next = com.sangita.app.lyrics.LyricsThemePalette.toggleMonochrome(curr).id
        userPreferences?.setLyricsTheme(next)
    }
}
