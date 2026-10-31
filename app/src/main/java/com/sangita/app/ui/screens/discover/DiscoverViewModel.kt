package com.sangita.app.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.remote.dto.ArtistDto
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.Track
import com.sangita.app.domain.model.path
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
    val tracks: List<Track> = emptyList(),
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
    private var rawTracks: List<Track> = emptyList()

    init {
        observeTracks()
        refresh()
    }

    private fun observeTracks() {
        viewModelScope.launch {
            try {
                musicRepository.observeAllTracks().collect { allTracks ->
                    rawTracks = allTracks
                    if (_uiState.value.searchQuery.isNotBlank()) {
                        _uiState.update { current ->
                            current.copy(tracks = filterTracks(rawTracks, current.searchQuery))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
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
                communityPlaylists = filterCommunityPlaylists(rawCommunityPlaylists, query),
                tracks = if (query.isNotBlank()) filterTracks(rawTracks, query) else emptyList()
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

                val communityWithCovers = loadedCommunity.map { pl ->
                    if (pl.coverTrackPath.isNullOrBlank() && pl.trackCount > 0) {
                        try {
                            val detail = musicRepository.getPlaylistDetail(pl.id).getOrNull()
                            val covers = detail?.tracks?.take(4)?.mapNotNull { it.trackPath }?.joinToString(",")
                            if (!covers.isNullOrBlank()) pl.copy(coverTrackPath = covers) else pl
                        } catch (_: Exception) {
                            pl
                        }
                    } else {
                        pl
                    }
                }

                rawArtists = loadedArtists
                rawFolderPlaylists = loadedFolders
                rawCommunityPlaylists = communityWithCovers

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
                        tracks = if (currentQuery.isNotBlank()) filterTracks(rawTracks, currentQuery) else emptyList(),
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

    fun playTrack(track: Track) {
        val currentTracks = _uiState.value.tracks
        val queue = if (currentTracks.isNotEmpty() && currentTracks.contains(track)) {
            currentTracks
        } else {
            listOf(track)
        }
        playbackController.playTrack(track, queue)
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
        if (query.isBlank()) return list
        return list.filter { artist ->
            matchIndividualLetters(query, artist.name)
        }
    }

    private fun filterPlaylists(list: List<Playlist>, query: String): List<Playlist> {
        if (query.isBlank()) return list
        return list.filter { playlist ->
            matchIndividualLetters(query, playlist.title, playlist.description)
        }
    }

    private fun filterCommunityPlaylists(list: List<Playlist>, query: String): List<Playlist> {
        if (query.isBlank()) return list
        return list.filter { playlist ->
            matchIndividualLetters(query, playlist.title, playlist.owner, playlist.description)
        }
    }

    private fun filterTracks(list: List<Track>, query: String): List<Track> {
        if (query.isBlank()) return emptyList()
        return list.filter { track ->
            matchIndividualLetters(query, track.title, track.artist, track.album, track.path)
        }
    }
}

fun matchIndividualLetters(query: String, vararg fields: String?): Boolean {
    if (query.isBlank()) return true
    val validFields = fields.filterNotNull()
    if (validFields.isEmpty()) return false

    val q = query.trim().lowercase()
    if (q.isEmpty()) return true

    val targets = validFields.map { it.lowercase() }

    // a) Exact substring match: any field contains query
    if (targets.any { it.contains(q) }) {
        return true
    }

    // b) Sequential subsequence match: characters in query appear in field in sequential order
    if (targets.any { isSubsequence(q, it) }) {
        return true
    }

    // c) Individual letter multiset match: every non-whitespace character in the query exists in the target string(s)
    if (targets.any { isMultisetMatch(q, it) }) {
        return true
    }

    // d) Token matching: if query has multiple words, all words match
    val tokens = q.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (tokens.size > 1) {
        return tokens.all { token ->
            targets.any { field ->
                field.contains(token) || isSubsequence(token, field) || isMultisetMatch(token, field)
            }
        }
    }

    return false
}

private fun isSubsequence(pattern: String, text: String): Boolean {
    val cleanPattern = pattern.filter { !it.isWhitespace() }
    if (cleanPattern.isEmpty()) return true
    var patternIdx = 0
    for (ch in text) {
        if (ch == cleanPattern[patternIdx]) {
            patternIdx++
            if (patternIdx == cleanPattern.length) return true
        }
    }
    return false
}

private fun isMultisetMatch(pattern: String, target: String): Boolean {
    val patternChars = pattern.filter { !it.isWhitespace() }
    if (patternChars.isEmpty()) return true

    val patternCounts = HashMap<Char, Int>()
    for (ch in patternChars) {
        patternCounts[ch] = (patternCounts[ch] ?: 0) + 1
    }

    val targetCounts = HashMap<Char, Int>()
    for (ch in target) {
        if (!ch.isWhitespace()) {
            targetCounts[ch] = (targetCounts[ch] ?: 0) + 1
        }
    }

    for ((ch, count) in patternCounts) {
        if ((targetCounts[ch] ?: 0) < count) {
            return false
        }
    }
    return true
}
