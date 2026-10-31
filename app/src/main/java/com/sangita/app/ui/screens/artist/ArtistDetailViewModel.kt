package com.sangita.app.ui.screens.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.remote.dto.ArtistDetailDto
import com.sangita.app.data.remote.dto.ArtistTrackDto
import com.sangita.app.download.SangitaDownloadManager
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.ArtistAddCollectionResult
import com.sangita.app.domain.repository.MusicRepository
import com.sangita.app.player.PlaybackController
import com.sangita.app.sync.NetworkConnectivityObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtistDetailUiState(
    val isLoading: Boolean = false,
    val artist: ArtistDetailDto? = null,
    val errorMessage: String? = null,
    val collectionAddResult: ArtistAddCollectionResult? = null,
    val feedbackMessage: String? = null
)

class ArtistDetailViewModel(
    val artistId: String,
    val musicRepository: MusicRepository,
    val playbackController: PlaybackController,
    val userPreferences: UserPreferences? = null,
    val connectivityObserver: NetworkConnectivityObserver? = null,
    val downloadManager: SangitaDownloadManager? = null
) : ViewModel() {

    // Overloaded constructor supporting (musicRepository, playbackController, artistId)
    constructor(
        musicRepository: MusicRepository,
        playbackController: PlaybackController,
        artistId: String
    ) : this(artistId, musicRepository, playbackController, null, null, null)

    val serverUrl: String = userPreferences?.getServerUrl().orEmpty()
    val token: String? = userPreferences?.getToken()
    val playbackState = playbackController.playbackState

    val allPlaylists: StateFlow<List<Playlist>> = musicRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(ArtistDetailUiState(isLoading = true))
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    init {
        loadArtist()
        viewModelScope.launch { musicRepository.getSavedArtists(); musicRepository.getSavedTracks() }
    }

    val savedArtists = musicRepository.observeSavedArtists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val savedTracks = musicRepository.observeSavedTracks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites: StateFlow<List<Track>> = musicRepository.observeFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val downloadedTracks: StateFlow<List<Track>> = musicRepository.observeDownloadedTracks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleSavedArtist(isSaved: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult((if (isSaved) musicRepository.removeArtist(artistId) else musicRepository.saveArtist(artistId)).isSuccess) }
    }
    fun toggleSavedTrack(path: String, isSaved: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult((if (isSaved) musicRepository.removeTrack(path) else musicRepository.saveTrack(path)).isSuccess) }
    }

    fun toggleFavorite(track: ArtistTrackDto, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val artistName = _uiState.value.artist?.name.orEmpty()
            val domainTrack = track.toTrack(artistName)
            val result = musicRepository.toggleFavorite(domainTrack)
            onComplete?.invoke(result.getOrDefault(false))
        }
    }

    fun downloadTrack(track: ArtistTrackDto) {
        val artistName = _uiState.value.artist?.name.orEmpty()
        val domainTrack = track.toTrack(artistName)
        downloadManager?.downloadTrack(domainTrack)
    }

    fun deleteDownloadedTrack(track: ArtistTrackDto) {
        viewModelScope.launch {
            downloadManager?.removeDownloadedTrack(track.path)
        }
    }

    fun deleteDownloadedTrack(track: Track) {
        viewModelScope.launch {
            downloadManager?.removeDownloadedTrack(track.id)
        }
    }

    fun refresh() {
        loadArtist()
    }

    fun loadArtist() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val result = musicRepository.fetchArtistDetail(artistId)
                if (result.isSuccess && result.getOrNull() != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            artist = result.getOrNull(),
                            errorMessage = null
                        )
                    }
                } else {
                    val fallback = musicRepository.getArtistDetail(artistId)
                    if (fallback.isSuccess && fallback.getOrNull() != null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                artist = fallback.getOrNull(),
                                errorMessage = null
                            )
                        }
                    } else {
                        val error = result.exceptionOrNull()?.message
                            ?: fallback.exceptionOrNull()?.message
                            ?: "Failed to load artist details"
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load artist details"
                    )
                }
            }
        }
    }

    fun playTrack(track: ArtistTrackDto) {
        val artistName = _uiState.value.artist?.name.orEmpty()
        val allTracks = _uiState.value.artist?.tracks?.map { it.toTrack(artistName) }
            ?: listOf(track.toTrack(artistName))
        val targetTrack = track.toTrack(artistName)
        playbackController.playTrack(targetTrack, allTracks)
    }

    fun playAll() {
        val artistName = _uiState.value.artist?.name.orEmpty()
        val tracks = _uiState.value.artist?.tracks?.map { it.toTrack(artistName) } ?: emptyList()
        if (tracks.isNotEmpty()) {
            playbackController.playTrack(tracks.first(), tracks)
        }
    }

    fun shuffle() {
        val artistName = _uiState.value.artist?.name.orEmpty()
        val tracks = _uiState.value.artist?.tracks?.map { it.toTrack(artistName) } ?: emptyList()
        if (tracks.isNotEmpty()) {
            val shuffled = tracks.shuffled()
            playbackController.playTrack(shuffled.first(), shuffled)
        }
    }

    fun addTrackToPlaylist(playlistKey: String, track: ArtistTrackDto, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val artistName = _uiState.value.artist?.name.orEmpty()
            val domainTrack = track.toTrack(artistName)
            val res = musicRepository.addTrackToPlaylist(playlistKey, domainTrack)
            onComplete(res.isSuccess)
        }
    }

    fun addArtistCollectionToPlaylist(
        targetPlaylistId: String? = null,
        newPlaylistTitle: String? = null,
        onComplete: ((ArtistAddCollectionResult?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = musicRepository.addArtistCollectionToPlaylist(
                artistId = artistId,
                targetPlaylistId = targetPlaylistId,
                newPlaylistTitle = newPlaylistTitle
            )
            _uiState.update { it.copy(isLoading = false) }
            if (result.isSuccess) {
                val res = result.getOrNull()
                _uiState.update { it.copy(collectionAddResult = res, feedbackMessage = res?.feedbackMessage) }
                onComplete?.invoke(res)
            } else {
                val error = result.exceptionOrNull()?.message ?: "Failed to add artist collection"
                _uiState.update { it.copy(errorMessage = error) }
                onComplete?.invoke(null)
            }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}

fun ArtistTrackDto.toTrack(artistFallback: String = ""): Track {
    val cleanArtist = artist.ifBlank { artistFallback.ifBlank { "Unknown Artist" } }
    val cleanTitle = title.ifBlank { path.substringAfterLast('/').substringBeforeLast('.') }
    return Track(
        id = path,
        title = cleanTitle,
        artist = cleanArtist,
        playlist = artistFallback,
        durationSeconds = duration
    )
}
