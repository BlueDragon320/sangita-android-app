package com.sangita.app.ui.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.PlaylistKind
import com.sangita.app.domain.model.PlaylistSyncState
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.MusicRepository
import com.sangita.app.download.SangitaDownloadManager
import com.sangita.app.player.PlaybackController
import com.sangita.app.sync.DeviceSyncManager
import com.sangita.app.sync.NetworkConnectivityObserver
import com.sangita.app.sync.SyncedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistUiState(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isOwner: Boolean = false,
    val isSaved: Boolean = false,
    val unavailableCount: Int = 0,
    val userMessage: String? = null
)

class PlaylistViewModel(
    val playlistKey: String,
    val musicRepository: MusicRepository,
    val userPreferences: UserPreferences,
    val playbackController: PlaybackController? = null,
    val downloadManager: SangitaDownloadManager? = null,
    val deviceSyncManager: DeviceSyncManager? = null,
    private val connectivityObserver: NetworkConnectivityObserver? = null,
    var onPlayTracksListener: ((Track, List<Track>) -> Unit)? = null,
    var onDownloadTracksListener: ((List<Track>) -> Unit)? = null
) : ViewModel() {

    val serverUrl: String get() = userPreferences.getServerUrl()
    val token: String? get() = userPreferences.getToken()
    val playbackState: StateFlow<com.sangita.app.domain.model.PlaybackState> = playbackController?.playbackState
        ?: MutableStateFlow(com.sangita.app.domain.model.PlaybackState())

    val isOnline: StateFlow<Boolean> = connectivityObserver?.observe()
        ?.stateIn(viewModelScope, SharingStarted.Eagerly, connectivityObserver.isOnline)
        ?: MutableStateFlow(true)

    val devices: StateFlow<List<SyncedDevice>> = deviceSyncManager?.devices
        ?: MutableStateFlow(emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _remotePlaylist = MutableStateFlow<Playlist?>(null)
    private val _remoteTracks = MutableStateFlow<List<Track>>(emptyList())

    val playlist: StateFlow<Playlist?> = combine(
        musicRepository.getPlaylist(playlistKey),
        _remotePlaylist
    ) { local, remote ->
        local ?: remote
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tracks: StateFlow<List<Track>> = combine(
        musicRepository.observeTracks(playlistKey),
        _remoteTracks,
        isOnline
    ) { localTracks, remoteTracks, online ->
        val effective = if (localTracks.isNotEmpty()) localTracks else remoteTracks
        if (online) effective else effective.filter { it.isDownloaded }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylists: StateFlow<List<Playlist>> = musicRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadRemoteDetailsIfNeeded()
    }

    private fun loadRemoteDetailsIfNeeded() {
        viewModelScope.launch {
            try {
                val localPl = musicRepository.getPlaylist(playlistKey).firstOrNull()
                val localTracks = musicRepository.observeTracks(playlistKey).firstOrNull() ?: emptyList()
                if (localPl == null || localTracks.isEmpty()) {
                    _isLoading.value = true
                    val result = musicRepository.getPlaylistDetail(playlistKey)
                    if (result.isSuccess) {
                        val detail = result.getOrNull()
                        if (detail != null) {
                            val isSaved = userPreferences.isPlaylistSaved(detail.id) || detail.isSaved
                            val domainPl = Playlist(
                                id = detail.id,
                                serverId = detail.id,
                                title = detail.title,
                                kind = if (detail.isFolderPlaylist) PlaylistKind.FOLDER else PlaylistKind.PUBLIC,
                                owner = detail.owner,
                                visibility = detail.visibility,
                                description = detail.description,
                                trackCount = detail.trackCount,
                                unavailableCount = detail.unavailableCount,
                                isAvailable = true,
                                syncState = PlaylistSyncState.SYNCED,
                                isSaved = isSaved
                            )
                            _remotePlaylist.value = domainPl
                            val baseServer = userPreferences.getServerUrl().trimEnd('/')
                            val mapped = detail.tracks.map { td ->
                                val streamUrl = if (baseServer.isNotBlank()) {
                                    "$baseServer/api/v1/stream/${td.trackPath.trimStart('/')}"
                                } else ""
                                Track(
                                    id = td.trackPath,
                                    title = td.title,
                                    artist = td.artist,
                                    playlist = detail.title,
                                    durationSeconds = td.duration,
                                    remoteUrl = streamUrl,
                                    isAvailable = td.isAvailable
                                )
                            }
                            _remoteTracks.value = mapped
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore network failure when loading remote details
            } finally {
                _isLoading.value = false
            }
        }
    }

    val playlistName: String get() = playlist.value?.title ?: playlistKey

    private val currentUsername: String? get() = userPreferences.getUsername()
    private val isAdmin: Boolean get() = userPreferences.getRole().equals("admin", ignoreCase = true)

    val uiState: StateFlow<PlaylistUiState> = combine(
        playlist,
        tracks,
        musicRepository.observeSavedPlaylistIds(),
        _isLoading,
        _userMessage
    ) { pl, tr, savedIds, loading, message ->
        val owner = computeIsOwner(pl)
        val unavail = computeUnavailableCount(pl, tr)
        val saved = pl != null && (savedIds.contains(pl.id) || (pl.serverId != null && savedIds.contains(pl.serverId)) || pl.isSaved)
        PlaylistUiState(
            playlist = pl,
            tracks = tr,
            isLoading = loading,
            isOwner = owner,
            isSaved = saved,
            unavailableCount = unavail,
            userMessage = message
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PlaylistUiState()
    )

    val isOwner: Boolean get() = uiState.value.isOwner
    val unavailableCount: Int get() = uiState.value.unavailableCount

    private fun computeIsOwner(pl: Playlist?): Boolean {
        if (isAdmin) return true
        if (pl == null) {
            return playlistKey.equals("Favorites", ignoreCase = true)
        }
        if (pl.isFolder) return false
        val user = currentUsername
        if (pl.owner != null && user != null && pl.owner.equals(user, ignoreCase = true)) {
            return true
        }
        return pl.isPersonal && (pl.owner.isNullOrBlank() || pl.owner == user)
    }

    private fun computeUnavailableCount(pl: Playlist?, trackList: List<Track>): Int {
        val countFromTracks = trackList.count { !it.isAvailable }
        val countFromPlaylist = pl?.unavailableCount ?: 0
        return maxOf(countFromTracks, countFromPlaylist)
    }

    fun playTrack(track: Track) {
        if (!track.isAvailable) return
        val availableTracks = tracks.value.filter { it.isAvailable }
        playbackController?.playTrack(track, availableTracks)
        onPlayTracksListener?.invoke(track, availableTracks)
    }

    fun playAll(shuffle: Boolean = false) {
        if (shuffle) shufflePlay() else playAll()
    }

    fun playAll() {
        val availableTracks = tracks.value.filter { it.isAvailable }
        if (availableTracks.isNotEmpty()) {
            playbackController?.playTrack(availableTracks.first(), availableTracks)
            onPlayTracksListener?.invoke(availableTracks.first(), availableTracks)
        }
    }

    fun shufflePlay() {
        val availableTracks = tracks.value.filter { it.isAvailable }.shuffled()
        if (availableTracks.isNotEmpty()) {
            playbackController?.playTrack(availableTracks.first(), availableTracks)
            onPlayTracksListener?.invoke(availableTracks.first(), availableTracks)
        }
    }

    fun updatePlaylist(
        title: String,
        description: String?,
        visibility: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = musicRepository.updatePlaylist(
                playlistKey = playlistKey,
                title = title,
                description = description,
                visibility = visibility
            )
            _isLoading.value = false
            if (result.isFailure) {
                _userMessage.value = result.exceptionOrNull()?.message ?: "Failed to update playlist"
            }
            onComplete?.invoke(result.isSuccess)
        }
    }

    fun deletePlaylist(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = musicRepository.deletePlaylist(playlistKey)
            _isLoading.value = false
            if (result.isFailure) {
                _userMessage.value = result.exceptionOrNull()?.message ?: "Failed to delete playlist"
            }
            onComplete?.invoke(result.isSuccess)
        }
    }

    fun clonePlaylist(
        title: String? = null,
        description: String? = null,
        isPublic: Boolean = false,
        onComplete: ((Playlist?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = musicRepository.clonePlaylist(
                playlistKey = playlistKey,
                title = title,
                description = description,
                isPublic = isPublic
            )
            _isLoading.value = false
            if (result.isSuccess) {
                onComplete?.invoke(result.getOrNull())
            } else {
                _userMessage.value = result.exceptionOrNull()?.message ?: "Failed to clone playlist"
                onComplete?.invoke(null)
            }
        }
    }

    fun saveToLibrary(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val targetId = playlist.value?.serverId ?: playlistKey
            val result = musicRepository.savePlaylistToLibrary(targetId)
            _isLoading.value = false
            if (result.isSuccess) {
                _userMessage.value = "Saved to library"
                _remotePlaylist.value = _remotePlaylist.value?.copy(isSaved = true)
            } else {
                _userMessage.value = result.exceptionOrNull()?.message ?: "Failed to save to library"
            }
            onComplete?.invoke(result.isSuccess)
        }
    }

    fun removeFromLibrary(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val targetId = playlist.value?.serverId ?: playlistKey
            val result = musicRepository.removePlaylistFromLibrary(targetId)
            _isLoading.value = false
            if (result.isSuccess) {
                _userMessage.value = "Removed from library"
                _remotePlaylist.value = _remotePlaylist.value?.copy(isSaved = false)
            } else {
                _userMessage.value = result.exceptionOrNull()?.message ?: "Failed to remove from library"
            }
            onComplete?.invoke(result.isSuccess)
        }
    }

    fun addTrack(track: Track) {
        viewModelScope.launch {
            musicRepository.addTrackToPlaylist(playlistKey, track)
        }
    }

    fun reorderTracks(newTrackOrder: List<Track>) {
        viewModelScope.launch {
            _isLoading.value = true
            val trackIds = newTrackOrder.map { it.id }
            val result = musicRepository.reorderPlaylistTracks(playlistKey, trackIds)
            _isLoading.value = false
            if (result.isFailure) {
                _userMessage.value = result.exceptionOrNull()?.message ?: "Failed to reorder tracks"
            }
        }
    }

    fun removeTrack(track: Track) {
        viewModelScope.launch {
            musicRepository.removeTrackFromPlaylist(playlistKey, track)
        }
    }

    fun createNewPlaylist(
        title: String,
        description: String?,
        isPublic: Boolean,
        onCreated: ((Playlist) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = musicRepository.createPlaylist(title = title, description = description, isPublic = isPublic)
            if (result.isSuccess) {
                result.getOrNull()?.let { onCreated?.invoke(it) }
            } else {
                _userMessage.value = result.exceptionOrNull()?.message ?: "Failed to create playlist"
            }
        }
    }

    fun downloadAll() {
        val available = tracks.value.filter { it.isAvailable }
        downloadManager?.downloadPlaylist(available)
        onDownloadTracksListener?.invoke(available)
    }

    fun downloadTrack(track: Track) {
        if (track.isAvailable) {
            downloadManager?.downloadTrack(track)
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(track)
        }
    }

    fun transferPlayback(targetDeviceId: String) {
        if (deviceSyncManager != null && targetDeviceId == deviceSyncManager.myDeviceId) {
            playbackController?.playHere()
        } else {
            val state = playbackState.value
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

    fun togglePlayPause() {
        playbackController?.togglePlayPause()
    }

    fun skipPrevious() {
        playbackController?.skipPrevious()
    }

    fun skipNext() {
        playbackController?.skipNext()
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
