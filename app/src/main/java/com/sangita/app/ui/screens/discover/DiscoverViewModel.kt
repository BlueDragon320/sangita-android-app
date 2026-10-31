package com.sangita.app.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.remote.dto.ArtistDto
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.repository.MusicRepository
import com.sangita.app.player.PlaybackController
import com.sangita.app.sync.NetworkConnectivityObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class DiscoverUiState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val artists: List<ArtistDto> = emptyList(),
    val folderPlaylists: List<Playlist> = emptyList(),
    val communityPlaylists: List<Playlist> = emptyList(),
    val errorMessage: String? = null,
    val isOffline: Boolean = false
)

class DiscoverViewModel(
    val musicRepository: MusicRepository,
    val playbackController: PlaybackController,
    val userPreferences: UserPreferences? = null,
    private val connectivityObserver: NetworkConnectivityObserver? = null
) : ViewModel() {

    val serverUrl: String = userPreferences?.getServerUrl().orEmpty()
    val token: String? = userPreferences?.getToken()
    val playbackState = playbackController.playbackState

    val savedPlaylistIds: StateFlow<Set<String>> = musicRepository.observeSavedPlaylistIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var rawArtists: List<ArtistDto> = emptyList()
    private var rawFolderPlaylists: List<Playlist> = emptyList()
    private var rawCommunityPlaylists: List<Playlist> = emptyList()

    init {
        refresh()
    }

    fun toggleSavePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val targetId = playlist.serverId ?: playlist.id
            val isSaved = savedPlaylistIds.value.contains(targetId) || playlist.isSaved
            if (isSaved) {
                musicRepository.removePlaylistFromLibrary(targetId)
            } else {
                musicRepository.savePlaylistToLibrary(targetId)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                artists = filterArtists(rawArtists, query),
                folderPlaylists = filterPlaylists(rawFolderPlaylists, query),
                communityPlaylists = filterCommunityPlaylists(rawCommunityPlaylists, query)
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val offline = connectivityObserver?.isOnline == false
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isOffline = offline) }

            try {
                // 1. Fetch Artists
                val artistsRes = musicRepository.getArtistsList()
                val loadedArtists = if (artistsRes.isSuccess) {
                    artistsRes.getOrDefault(emptyList())
                } else {
                    try {
                        musicRepository.getArtists().firstOrNull() ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                // 2. Fetch Folder Playlists
                val foldersRes = musicRepository.getFolderPlaylists()
                val loadedFolders = if (foldersRes.isSuccess) {
                    foldersRes.getOrDefault(emptyList())
                } else {
                    try {
                        val all = musicRepository.observePlaylists().firstOrNull() ?: emptyList()
                        all.filter { it.isFolder }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                // 3. Fetch Community Playlists
                val communityRes = musicRepository.getCommunityPlaylists()
                val loadedCommunity = if (communityRes.isSuccess) {
                    communityRes.getOrDefault(emptyList())
                } else {
                    try {
                        val all = musicRepository.observePlaylists().firstOrNull() ?: emptyList()
                        all.filter { it.isPublic && !it.isFolder && !it.isFavorites }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                rawArtists = loadedArtists
                rawFolderPlaylists = loadedFolders
                rawCommunityPlaylists = loadedCommunity

                val hasFailure = artistsRes.isFailure && foldersRes.isFailure && communityRes.isFailure
                val isEmptyResult = loadedArtists.isEmpty() && loadedFolders.isEmpty() && loadedCommunity.isEmpty()
                val isNetworkIssue = offline || hasFailure

                val errorMsg = if (hasFailure && isEmptyResult) {
                    artistsRes.exceptionOrNull()?.message
                        ?: foldersRes.exceptionOrNull()?.message
                        ?: "Failed to load discovery content"
                } else null

                val currentQuery = _uiState.value.searchQuery
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        artists = filterArtists(rawArtists, currentQuery),
                        folderPlaylists = filterPlaylists(rawFolderPlaylists, currentQuery),
                        communityPlaylists = filterCommunityPlaylists(rawCommunityPlaylists, currentQuery),
                        errorMessage = errorMsg,
                        isOffline = isNetworkIssue && isEmptyResult
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val isNet = e is IOException || offline
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "An unexpected error occurred",
                        isOffline = isNet
                    )
                }
            }
        }
    }

    fun playPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val tracks = if (playlist.tracks.isNotEmpty()) {
                playlist.tracks
            } else {
                try {
                    val fromRepo = musicRepository.observeTracks(playlist.id).firstOrNull()
                        ?: musicRepository.observeTracks(playlist.title).firstOrNull()
                        ?: emptyList()
                    if (fromRepo.isNotEmpty()) {
                        fromRepo
                    } else {
                        val detailRes = musicRepository.getPlaylistDetail(playlist.id)
                        if (detailRes.isSuccess) {
                            detailRes.getOrNull()?.tracks
                                ?.filter { it.isAvailable }
                                ?.map { dt ->
                                    Track(
                                        id = dt.trackPath,
                                        title = dt.title,
                                        artist = dt.artist,
                                        playlist = playlist.title,
                                        durationSeconds = dt.duration
                                    )
                                } ?: emptyList()
                        } else {
                            emptyList()
                        }
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            if (tracks.isNotEmpty()) {
                playbackController.playTrack(tracks.first(), tracks)
            }
        }
    }

    private fun filterArtists(list: List<ArtistDto>, query: String): List<ArtistDto> {
        val q = query.trim()
        if (q.isEmpty()) return list
        return list.filter { it.name.contains(q, ignoreCase = true) }
    }

    private fun filterPlaylists(list: List<Playlist>, query: String): List<Playlist> {
        val q = query.trim()
        if (q.isEmpty()) return list
        return list.filter { it.title.contains(q, ignoreCase = true) }
    }

    private fun filterCommunityPlaylists(list: List<Playlist>, query: String): List<Playlist> {
        val q = query.trim()
        if (q.isEmpty()) return list
        return list.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.owner?.contains(q, ignoreCase = true) == true ||
                it.description?.contains(q, ignoreCase = true) == true
        }
    }
}
