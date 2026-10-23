package com.sangita.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.sangita.app.data.local.dao.PlaybackEventDao
import com.sangita.app.data.local.dao.TrackDao
import com.sangita.app.data.local.entity.PlaybackEventEntity
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.domain.model.PlaybackState
import com.sangita.app.domain.model.RepeatMode
import com.sangita.app.domain.model.Track
import com.sangita.app.sync.DeviceSyncManager
import com.sangita.app.sync.RemotePlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
class PlaybackController(
    private val context: Context,
    private val localStorageManager: LocalStorageManager,
    private val userPreferences: UserPreferences,
    private val playbackEventDao: PlaybackEventDao,
    private val trackDao: TrackDao,
    private val deviceSyncManager: DeviceSyncManager,
    private val scope: CoroutineScope
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Track>>(emptyList())
    val currentQueue: StateFlow<List<Track>> = _currentQueue.asStateFlow()

    private var progressPollJob: Job? = null
    private var currentTrackPlayStartTime: Long = 0L
    private var lastReportTime: Long = 0L

    init {
        initializeController()

        deviceSyncManager.remoteCommandHandler = { action, payload ->
            handleRemoteCommand(action, payload)
        }

        deviceSyncManager.onOtherDeviceActiveCallback = { otherId, state ->
            onRemoteDeviceClaimed(otherId, state)
        }

        deviceSyncManager.onPlaybackStateReceived = { state ->
            handleRemotePlaybackState(state)
        }

        scope.launch {
            deviceSyncManager.devices.collect { devices ->
                val activeId = deviceSyncManager.activeDeviceId.value
                val myId = deviceSyncManager.myDeviceId
                if (activeId != null && activeId != myId) {
                    val activeDev = devices.find { it.deviceId == activeId }
                    if (activeDev != null) {
                        _playbackState.update { it.copy(activeDeviceName = activeDev.deviceName) }
                    }
                }
            }
        }
    }

    private fun handleRemotePlaybackState(state: RemotePlaybackState) {
        val myId = deviceSyncManager.myDeviceId
        val activeId = state.activeDeviceId
        val isRemoteActive = activeId != null && activeId != myId

        scope.launch(Dispatchers.Main) {
            if (isRemoteActive) {
                // Another device is active: pause local playback if it was active
                if (mediaController?.isPlaying == true) {
                    mediaController?.pause()
                    Log.d("PlaybackController", "Paused local playback because active device is $activeId")
                }

                val activeDeviceName = deviceSyncManager.devices.value
                    .find { it.deviceId == activeId }?.deviceName ?: "Another device"

                val trackId = state.trackId
                if (!trackId.isNullOrBlank()) {
                    val resolvedTrack = resolveTrack(trackId, state.durationMs)
                    val drift = if (state.isPlaying && !state.isBuffering && state.updatedAt > 0L) {
                        (System.currentTimeMillis() - state.updatedAt).coerceAtLeast(0L)
                    } else 0L

                    val currentPos = (state.positionMs + drift).coerceAtMost(
                        if (state.durationMs > 0) state.durationMs else Long.MAX_VALUE
                    )

                    _playbackState.update {
                        it.copy(
                            currentTrack = resolvedTrack,
                            isPlaying = state.isPlaying,
                            isBuffering = state.isBuffering,
                            currentPositionMs = currentPos,
                            durationMs = state.durationMs,
                            repeatMode = if (state.isLoop) RepeatMode.ALL else RepeatMode.OFF,
                            shuffleModeEnabled = state.isShuffle,
                            isActiveDevice = false,
                            activeDeviceName = activeDeviceName
                        )
                    }

                    if (state.isPlaying && !state.isBuffering) {
                        startProgressPolling()
                    } else {
                        stopProgressPolling()
                    }
                } else {
                    _playbackState.update {
                        it.copy(
                            currentTrack = null,
                            isPlaying = false,
                            isBuffering = false,
                            isActiveDevice = false,
                            activeDeviceName = activeDeviceName
                        )
                    }
                    stopProgressPolling()
                }
            } else {
                // This device is active
                _playbackState.update {
                    it.copy(
                        isActiveDevice = true,
                        activeDeviceName = null
                    )
                }
                if (state.isTransferHandoff && !state.trackId.isNullOrBlank()) {
                    Log.d("PlaybackController", "Transfer handoff targeting phone: ${state.trackId} at ${state.positionMs}ms")
                    val resolvedTrack = resolveTrack(state.trackId, state.durationMs)
                    playTrack(resolvedTrack, listOf(resolvedTrack), forceLocal = true)
                    if (state.positionMs > 0) {
                        seekTo(state.positionMs)
                    }
                } else if (mediaController?.isPlaying == true) {
                    startProgressPolling()
                }
            }
        }
    }

    private suspend fun resolveTrack(trackId: String, durationMs: Long): Track {
        // 1. Check current local queue
        _currentQueue.value.find { it.id == trackId }?.let { return it }

        // 2. Check local database
        try {
            val dbTrack = trackDao.getTrackById(trackId)
            if (dbTrack != null) {
                return dbTrack.toDomain()
            }
        } catch (e: Exception) {
            Log.w("PlaybackController", "Failed to resolve track from DB: ${e.message}")
        }

        // 3. Fallback: Parse path into clean title and artist
        val clean = trackId.trim()
        val playlist = if (clean.contains('/')) clean.substringBefore('/') else "Library"
        val filename = clean.substringAfterLast('/')
        val withoutExt = filename.substringBeforeLast('.').ifBlank { filename }
        val (artist, title) = if (withoutExt.contains(" - ")) {
            val parts = withoutExt.split(" - ", limit = 2)
            Pair(parts[0].trim(), parts[1].trim())
        } else {
            Pair(playlist, withoutExt.trim())
        }
        val serverUrl = userPreferences.getServerUrl()
        val token = userPreferences.getToken() ?: ""
        return Track(
            id = clean,
            title = title,
            artist = artist,
            playlist = playlist,
            durationSeconds = if (durationMs > 0) durationMs / 1000.0 else null,
            remoteUrl = "$serverUrl/api/stream/$clean?token=$token"
        )
    }

    private fun handleRemoteCommand(action: String, payload: JSONObject?) {
        scope.launch(Dispatchers.Main) {
            Log.d("PlaybackController", "Handling remote command: action=$action, payload=$payload")
            when (action) {
                "play" -> {
                    val player = mediaController
                    if (player != null) {
                        if (player.currentMediaItem == null && _playbackState.value.currentTrack != null) {
                            val track = _playbackState.value.currentTrack!!
                            playTrack(track, _currentQueue.value.ifEmpty { listOf(track) })
                        } else {
                            player.play()
                            broadcastPlaybackState()
                        }
                    }
                }
                "pause" -> {
                    mediaController?.pause()
                    broadcastPlaybackState(overridePlaying = false)
                }
                "next" -> skipNext()
                "prev" -> skipPrevious()
                "seek" -> {
                    val pos = if (payload?.has("positionMs") == true) {
                        payload.getLong("positionMs")
                    } else if (payload?.has("pct") == true) {
                        val duration = _playbackState.value.durationMs
                        ((payload.getDouble("pct") / 100.0) * duration).toLong()
                    } else {
                        null
                    }
                    if (pos != null) {
                        seekTo(pos)
                    }
                }
                "loop" -> {
                    val loop = payload?.optBoolean("loop", false) ?: false
                    setRepeatMode(if (loop) RepeatMode.ALL else RepeatMode.OFF)
                }
                "shuffle" -> {
                    val shuffle = payload?.optBoolean("shuffle", false) ?: false
                    _playbackState.update { it.copy(shuffleModeEnabled = shuffle) }
                    broadcastPlaybackState()
                }
                "play_track" -> {
                    val path = payload?.optString("path", "") ?: ""
                    if (path.isNotBlank()) {
                        val track = resolveTrack(path, 0L)
                        playTrack(track, if (_currentQueue.value.contains(track)) _currentQueue.value else listOf(track), forceLocal = true)
                    }
                }
            }
        }
    }

    private fun onRemoteDeviceClaimed(otherDeviceId: String, state: RemotePlaybackState) {
        scope.launch(Dispatchers.Main) {
            if (mediaController?.isPlaying == true) {
                mediaController?.pause()
                Log.d("PlaybackController", "Paused local playback because active device switched to $otherDeviceId")
            }
        }
    }

    private fun broadcastPlaybackState(
        overridePositionMs: Long? = null,
        overrideDurationMs: Long? = null,
        overridePlaying: Boolean? = null,
        overrideBuffering: Boolean? = null,
        forceClaim: Boolean = false
    ) {
        val state = _playbackState.value
        val track = state.currentTrack ?: return
        val player = mediaController
        val pos = overridePositionMs ?: player?.currentPosition ?: state.currentPositionMs
        val dur = overrideDurationMs ?: (if ((player?.duration ?: 0L) > 0) player?.duration ?: 0L else state.durationMs)
        val isBuffering = overrideBuffering ?: (player?.playbackState == Player.STATE_BUFFERING || state.isBuffering)
        val isActuallyPlaying = overridePlaying ?: ((player?.playbackState == Player.STATE_READY || player?.playbackState == Player.STATE_ENDED) && (player?.isPlaying == true))
        val isLoop = state.repeatMode != RepeatMode.OFF
        val isShuffle = state.shuffleModeEnabled

        deviceSyncManager.broadcastPlaybackState(
            trackId = track.id,
            positionMs = pos,
            durationMs = dur,
            isPlaying = if (isBuffering) false else isActuallyPlaying,
            isBuffering = isBuffering,
            isLoop = isLoop,
            isShuffle = isShuffle,
            volume = 1.0,
            forceClaim = forceClaim
        )
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, SangitaPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupPlayerListener()
            } catch (_: Exception) {}
        }, { runnable -> runnable.run() })
    }

    private fun setupPlayerListener() {
        val player = mediaController ?: return
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (_playbackState.value.isActiveDevice) {
                    val isBuffering = (player.playbackState == Player.STATE_BUFFERING)
                    val isActuallyPlaying = (player.playbackState == Player.STATE_READY && isPlaying)
                    _playbackState.update {
                        it.copy(
                            isPlaying = isActuallyPlaying,
                            isBuffering = isBuffering
                        )
                    }
                    if (isActuallyPlaying) {
                        startProgressPolling()
                    } else if (!isBuffering) {
                        stopProgressPolling()
                    }
                    broadcastPlaybackState(overridePlaying = isActuallyPlaying, overrideBuffering = isBuffering)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (_playbackState.value.isActiveDevice) {
                    val isBuffering = (playbackState == Player.STATE_BUFFERING)
                    val isActuallyPlaying = (playbackState == Player.STATE_READY && player.isPlaying)
                    _playbackState.update {
                        it.copy(
                            isBuffering = isBuffering,
                            isPlaying = isActuallyPlaying
                        )
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        recordPlayEvent(isComplete = true)
                        skipNext()
                    }
                    updateProgressFromPlayer()
                    broadcastPlaybackState(overridePlaying = isActuallyPlaying, overrideBuffering = isBuffering)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (_playbackState.value.isActiveDevice) {
                    updateProgressFromPlayer()
                    broadcastPlaybackState()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (_playbackState.value.isActiveDevice) {
                    updateProgressFromPlayer()
                    broadcastPlaybackState()
                }
            }
        })
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track), forceLocal: Boolean = false) {
        _currentQueue.value = queue
        val trackDurationMs = ((track.durationSeconds ?: 0.0) * 1000).toLong()

        val localAudio = localStorageManager.getAudioFile(track.id)
        val hasLocalAudio = localAudio.exists() && localAudio.length() > 0

        val isSyncConnected = deviceSyncManager.isConnected.value
        val sState = deviceSyncManager.remotePlaybackState.value
        val currentActiveId = sState?.activeDeviceId ?: deviceSyncManager.activeDeviceId.value
        val myId = deviceSyncManager.myDeviceId
        val isRemoteActive = isSyncConnected && currentActiveId != null && currentActiveId != myId

        // If another device (like web browser) is currently active, and forceLocal is not requested,
        // and we are NOT offline and track is NOT downloaded locally:
        // Route play_track command to the active remote speaker!
        if (isRemoteActive && !forceLocal && !hasLocalAudio) {
            Log.d("PlaybackController", "Remote device $currentActiveId is active. Routing play_track for ${track.id}")
            mediaController?.pause()

            val remoteName = deviceSyncManager.devices.value.find { it.deviceId == currentActiveId }?.deviceName ?: "Web Browser"

            _playbackState.update {
                it.copy(
                    currentTrack = track,
                    isPlaying = false,
                    isBuffering = true,
                    currentPositionMs = 0L,
                    durationMs = trackDurationMs,
                    isActiveDevice = false,
                    activeDeviceName = remoteName
                )
            }

            val payload = JSONObject().apply {
                put("path", track.id)
            }
            deviceSyncManager.sendRemoteControl("play_track", payload)
            return
        }

        // Local playback on this phone
        val player = mediaController ?: return

        // Claim active device so server and webapp know this phone is now active
        if (isSyncConnected) {
            deviceSyncManager.claimActiveDevice(0L, trackDurationMs)
        }

        // Determine if local downloaded file is available
        val uri = if (hasLocalAudio) {
            Uri.fromFile(localAudio)
        } else {
            val serverUrl = userPreferences.getServerUrl()
            val token = userPreferences.getToken() ?: ""
            Uri.parse("$serverUrl/api/stream/${track.id}?token=$token")
        }

        val localCover = localStorageManager.getCoverFile(track.id)
        val coverUri = if (localCover.exists() && localCover.length() > 0) {
            Uri.fromFile(localCover)
        } else {
            Uri.parse("${userPreferences.getServerUrl()}/api/cover/${track.id}?token=${userPreferences.getToken() ?: ""}")
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.playlist)
            .setArtworkUri(coverUri)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        currentTrackPlayStartTime = System.currentTimeMillis()
        _playbackState.update {
            it.copy(
                currentTrack = track,
                isPlaying = false,
                isBuffering = true,
                currentPositionMs = 0L,
                durationMs = trackDurationMs,
                isActiveDevice = true,
                activeDeviceName = null
            )
        }

        if (isSyncConnected) {
            deviceSyncManager.broadcastPlaybackState(
                trackId = track.id,
                positionMs = 0L,
                durationMs = trackDurationMs,
                isPlaying = false,
                isLoop = _playbackState.value.repeatMode != RepeatMode.OFF,
                isShuffle = _playbackState.value.shuffleModeEnabled
            )
        }
    }

    fun playHere() {
        val current = _playbackState.value.currentTrack ?: return
        val pos = _playbackState.value.currentPositionMs
        val dur = _playbackState.value.durationMs

        deviceSyncManager.claimActiveDevice(pos, dur)
        _playbackState.update {
            it.copy(
                isActiveDevice = true,
                activeDeviceName = null
            )
        }
        playTrack(current, _currentQueue.value.ifEmpty { listOf(current) }, forceLocal = true)
        seekTo(pos)
    }

    fun togglePlayPause() {
        val isSyncConnected = deviceSyncManager.isConnected.value
        val sState = deviceSyncManager.remotePlaybackState.value
        val currentActiveId = sState?.activeDeviceId ?: deviceSyncManager.activeDeviceId.value
        val myId = deviceSyncManager.myDeviceId
        val isRemoteActive = isSyncConnected && currentActiveId != null && currentActiveId != myId

        if (isRemoteActive) {
            val willPlay = !_playbackState.value.isPlaying
            if (willPlay) {
                _playbackState.update { it.copy(isBuffering = true) }
                deviceSyncManager.sendRemoteControl("play")
            } else {
                _playbackState.update { it.copy(isPlaying = false, isBuffering = false) }
                deviceSyncManager.sendRemoteControl("pause")
                stopProgressPolling()
            }
            return
        }

        val player = mediaController ?: return
        val currentTrack = _playbackState.value.currentTrack

        if (player.currentMediaItem == null && currentTrack != null) {
            playTrack(currentTrack, _currentQueue.value.ifEmpty { listOf(currentTrack) })
            return
        }

        val pos = player.currentPosition
        val dur = if (player.duration > 0) player.duration else _playbackState.value.durationMs

        if (isSyncConnected && deviceSyncManager.activeDeviceId.value != myId) {
            deviceSyncManager.claimActiveDevice(pos, dur)
        }

        if (player.isPlaying) {
            player.pause()
            _playbackState.update { it.copy(isPlaying = false, isBuffering = false) }
            if (isSyncConnected) {
                deviceSyncManager.broadcastPlaybackState(
                    trackId = currentTrack?.id,
                    positionMs = pos,
                    durationMs = dur,
                    isPlaying = false,
                    isLoop = _playbackState.value.repeatMode != RepeatMode.OFF,
                    isShuffle = _playbackState.value.shuffleModeEnabled
                )
            }
        } else {
            // Buffer/loading animation until player actually starts playing
            _playbackState.update { it.copy(isBuffering = true) }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val isSyncConnected = deviceSyncManager.isConnected.value
        val sState = deviceSyncManager.remotePlaybackState.value
        val currentActiveId = sState?.activeDeviceId ?: deviceSyncManager.activeDeviceId.value
        val myId = deviceSyncManager.myDeviceId
        val isRemoteActive = isSyncConnected && currentActiveId != null && currentActiveId != myId

        if (isRemoteActive) {
            _playbackState.update { it.copy(currentPositionMs = positionMs) }
            val dur = _playbackState.value.durationMs
            val payload = JSONObject().apply {
                put("positionMs", positionMs)
                if (dur > 0) {
                    put("pct", (positionMs.toDouble() / dur.toDouble()) * 100.0)
                }
            }
            deviceSyncManager.sendRemoteControl("seek", payload)
            return
        }

        val player = mediaController ?: return
        player.seekTo(positionMs)
        _playbackState.update { it.copy(currentPositionMs = positionMs) }

        val currentTrack = _playbackState.value.currentTrack
        val dur = if (player.duration > 0) player.duration else _playbackState.value.durationMs
        if (isSyncConnected) {
            deviceSyncManager.broadcastPlaybackState(
                trackId = currentTrack?.id,
                positionMs = positionMs,
                durationMs = dur,
                isPlaying = player.isPlaying,
                isLoop = _playbackState.value.repeatMode != RepeatMode.OFF,
                isShuffle = _playbackState.value.shuffleModeEnabled
            )
        }
    }

    fun skipNext() {
        val isSyncConnected = deviceSyncManager.isConnected.value
        val sState = deviceSyncManager.remotePlaybackState.value
        val currentActiveId = sState?.activeDeviceId ?: deviceSyncManager.activeDeviceId.value
        val myId = deviceSyncManager.myDeviceId
        val isRemoteActive = isSyncConnected && currentActiveId != null && currentActiveId != myId

        if (isRemoteActive) {
            _playbackState.update { it.copy(isBuffering = true) }
            deviceSyncManager.sendRemoteControl("next")
            return
        }

        val queue = _currentQueue.value
        val current = _playbackState.value.currentTrack ?: return
        val currentIndex = queue.indexOfFirst { it.id == current.id }
        if (currentIndex != -1 && currentIndex < queue.size - 1) {
            playTrack(queue[currentIndex + 1], queue)
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL && queue.isNotEmpty()) {
            playTrack(queue.first(), queue)
        }
    }

    fun skipPrevious() {
        val isSyncConnected = deviceSyncManager.isConnected.value
        val sState = deviceSyncManager.remotePlaybackState.value
        val currentActiveId = sState?.activeDeviceId ?: deviceSyncManager.activeDeviceId.value
        val myId = deviceSyncManager.myDeviceId
        val isRemoteActive = isSyncConnected && currentActiveId != null && currentActiveId != myId

        if (isRemoteActive) {
            _playbackState.update { it.copy(isBuffering = true) }
            deviceSyncManager.sendRemoteControl("prev")
            return
        }

        val queue = _currentQueue.value
        val current = _playbackState.value.currentTrack ?: return
        if ((mediaController?.currentPosition ?: 0L) > 3000L) {
            seekTo(0L)
            return
        }
        val currentIndex = queue.indexOfFirst { it.id == current.id }
        if (currentIndex > 0) {
            playTrack(queue[currentIndex - 1], queue)
        } else {
            seekTo(0L)
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        _playbackState.update { it.copy(repeatMode = mode) }

        if (!_playbackState.value.isActiveDevice) {
            val payload = JSONObject().apply {
                put("loop", mode != RepeatMode.OFF)
            }
            deviceSyncManager.sendRemoteControl("loop", payload)
            return
        }

        val player = mediaController
        val track = _playbackState.value.currentTrack
        val pos = player?.currentPosition ?: _playbackState.value.currentPositionMs
        val dur = if ((player?.duration ?: 0L) > 0) player?.duration ?: 0L else _playbackState.value.durationMs
        deviceSyncManager.broadcastPlaybackState(
            trackId = track?.id,
            positionMs = pos,
            durationMs = dur,
            isPlaying = player?.isPlaying ?: _playbackState.value.isPlaying,
            isLoop = mode != RepeatMode.OFF,
            isShuffle = _playbackState.value.shuffleModeEnabled
        )
    }

    fun toggleShuffle() {
        val newShuffle = !_playbackState.value.shuffleModeEnabled
        _playbackState.update { it.copy(shuffleModeEnabled = newShuffle) }

        if (!_playbackState.value.isActiveDevice) {
            val payload = JSONObject().apply {
                put("shuffle", newShuffle)
            }
            deviceSyncManager.sendRemoteControl("shuffle", payload)
            return
        }

        val player = mediaController
        val track = _playbackState.value.currentTrack
        val pos = player?.currentPosition ?: _playbackState.value.currentPositionMs
        val dur = if ((player?.duration ?: 0L) > 0) player?.duration ?: 0L else _playbackState.value.durationMs
        deviceSyncManager.broadcastPlaybackState(
            trackId = track?.id,
            positionMs = pos,
            durationMs = dur,
            isPlaying = player?.isPlaying ?: _playbackState.value.isPlaying,
            isLoop = _playbackState.value.repeatMode != RepeatMode.OFF,
            isShuffle = newShuffle
        )
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _playbackState.update { it.copy(playbackSpeed = speed) }
    }

    private fun startProgressPolling() {
        progressPollJob?.cancel()
        progressPollJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                if (_playbackState.value.isActiveDevice) {
                    if (_playbackState.value.isPlaying && !_playbackState.value.isBuffering) {
                        updateProgressFromPlayer()
                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 1000L) {
                            if (deviceSyncManager.isActiveDevice.value) {
                                val player = mediaController
                                val pos = player?.currentPosition ?: _playbackState.value.currentPositionMs
                                val dur = if ((player?.duration ?: 0L) > 0) player?.duration ?: 0L else _playbackState.value.durationMs
                                deviceSyncManager.reportPosition(pos, dur)
                            }
                            lastReportTime = now
                        }
                    }
                } else {
                    // Update remote progress smoothly in real time from server state + elapsed drift
                    val remoteState = deviceSyncManager.remotePlaybackState.value
                    if (remoteState != null) {
                        val interpolatedPos = if (remoteState.isPlaying && !_playbackState.value.isBuffering) {
                            val drift = if (remoteState.updatedAt > 0L) {
                                (System.currentTimeMillis() - remoteState.updatedAt).coerceAtLeast(0L)
                            } else 0L
                            val maxDur = if (remoteState.durationMs > 0) remoteState.durationMs else Long.MAX_VALUE
                            (remoteState.positionMs + drift).coerceAtMost(maxDur)
                        } else {
                            remoteState.positionMs
                        }
                        _playbackState.update { current ->
                            current.copy(
                                currentPositionMs = interpolatedPos,
                                durationMs = if (remoteState.durationMs > 0) remoteState.durationMs else current.durationMs
                            )
                        }
                    }
                }
                delay(PROGRESS_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressPolling() {
        progressPollJob?.cancel()
        progressPollJob = null
    }

    private fun updateProgressFromPlayer() {
        val player = mediaController ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        val buf = player.bufferedPosition
        _playbackState.update {
            it.copy(
                currentPositionMs = pos,
                durationMs = if (dur > 0) dur else it.durationMs,
                bufferedPositionMs = buf
            )
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun recordPlayEvent(isComplete: Boolean) {
        val track = _playbackState.value.currentTrack ?: return
        val elapsedSec = ((System.currentTimeMillis() - currentTrackPlayStartTime) / 1000).toInt()
        if (elapsedSec < 10) return

        val formattedDate = synchronized(dateFormat) { dateFormat.format(Date()) }
        val event = PlaybackEventEntity(
            trackId = track.id,
            playlist = track.playlist,
            durationSec = elapsedSec,
            deviceId = userPreferences.getOrCreateDeviceId(),
            deviceName = android.os.Build.MODEL ?: "Android Device",
            timestamp = formattedDate
        )
        scope.launch(Dispatchers.IO) {
            playbackEventDao.insertEvent(event)
        }
    }

    companion object {
        private const val PROGRESS_POLL_INTERVAL_MS = 60L
        private const val AUDIO_TRACK_LATENCY_OFFSET_MS = 180L
    }
}
