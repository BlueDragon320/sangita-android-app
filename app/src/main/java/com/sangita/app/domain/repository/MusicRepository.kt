package com.sangita.app.domain.repository

import com.sangita.app.data.remote.dto.ArtistDetailDto
import com.sangita.app.data.remote.dto.ArtistDto
import com.sangita.app.data.remote.dto.CatalogSourceDto
import com.sangita.app.data.remote.dto.PlaylistDetailDto
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.Track
import kotlinx.coroutines.flow.Flow

data class ArtistAddCollectionResult(
    val addedCount: Int = 0,
    val skippedDuplicatesCount: Int = 0,
    val failedCount: Int = 0,
    val playlistId: String? = null,
    val playlistTitle: String? = null,
    val message: String? = null
) {
    val feedbackMessage: String
        get() = when {
            message != null -> message
            failedCount > 0 && addedCount > 0 -> "Added $addedCount songs ($failedCount failed, $skippedDuplicatesCount duplicates skipped)"
            failedCount > 0 && addedCount == 0 -> "Failed to add songs ($failedCount failed)"
            skippedDuplicatesCount > 0 -> "Added $addedCount songs ($skippedDuplicatesCount duplicates skipped)"
            addedCount == 1 -> "Added 1 song"
            else -> "Added $addedCount songs"
        }
}

interface MusicRepository {
    fun getCatalogSources(): Flow<List<CatalogSourceDto>> = kotlinx.coroutines.flow.emptyFlow()
    suspend fun refreshCatalogSources(): Result<Unit> = Result.success(Unit)
    fun getArtists(): Flow<List<ArtistDto>> = kotlinx.coroutines.flow.emptyFlow()
    suspend fun refreshArtists(): Result<Unit> = Result.success(Unit)
    suspend fun fetchArtistDetail(artistId: String): Result<ArtistDetailDto> = Result.failure(NotImplementedError())

    fun observeAllTracks(): Flow<List<Track>>
    fun observePlaylists(): Flow<List<Playlist>>
    fun getPlaylist(playlistKey: String): Flow<Playlist?>
    fun observeTracks(playlistKey: String): Flow<List<Track>>
    fun observeFavorites(): Flow<List<Track>>
    fun observeFavoriteTracks(): Flow<List<Track>> = kotlinx.coroutines.flow.emptyFlow()
    fun observeDownloadedTracks(): Flow<List<Track>>
    fun searchTracks(query: String): Flow<List<Track>> = kotlinx.coroutines.flow.emptyFlow()

    suspend fun toggleFavorite(track: Track): Result<Boolean>
    suspend fun createPlaylist(title: String, description: String? = null, isPublic: Boolean = false): Result<Playlist>
    suspend fun createPlaylist(title: String, description: String?, visibility: String): Result<Playlist> =
        createPlaylist(title, description, visibility.equals("public", ignoreCase = true))
    suspend fun createPlaylist(name: String): Result<Unit> =
        createPlaylist(name, null, false).map { Unit }
    suspend fun updatePlaylist(
        playlistKey: String,
        title: String? = null,
        description: String? = null,
        visibility: String? = null
    ): Result<Unit> = Result.success(Unit)
    suspend fun deletePlaylist(playlistKey: String): Result<Unit> = Result.success(Unit)
    suspend fun clonePlaylist(
        playlistKey: String,
        title: String? = null,
        description: String? = null,
        isPublic: Boolean = false
    ): Result<Playlist> = Result.failure(NotImplementedError())
    suspend fun reorderPlaylistTracks(playlistKey: String, newTrackIds: List<String>): Result<Unit> = Result.success(Unit)
    suspend fun addTrackToPlaylist(playlistKey: String, track: Track): Result<Unit>
    suspend fun removeTrackFromPlaylist(playlistKey: String, track: Track): Result<Unit>

    // Add-to-Library Flow for Public & Shared Playlists
    suspend fun savePlaylistToLibrary(playlistId: String): Result<Unit> = Result.success(Unit)
    suspend fun removePlaylistFromLibrary(playlistId: String): Result<Unit> = Result.success(Unit)
    fun isPlaylistSaved(playlistId: String): Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)
    fun observeSavedPlaylistIds(): Flow<Set<String>> = kotlinx.coroutines.flow.flowOf(emptySet())

    // Add Artist's Complete Collection
    suspend fun addArtistCollectionToPlaylist(
        artistId: String,
        targetPlaylistId: String? = null,
        newPlaylistTitle: String? = null
    ): Result<ArtistAddCollectionResult> = Result.success(ArtistAddCollectionResult())

    suspend fun syncLibrary(): Result<Unit> = Result.success(Unit)
    suspend fun refreshPlaylists(): Result<Unit>
    suspend fun migrateDefaultAccountPlaylists(accountKey: String = "default_account"): Result<Int> = Result.success(0)
    fun observeRecoverableLegacyPlaylists(): Flow<List<Playlist>> = kotlinx.coroutines.flow.emptyFlow()
    suspend fun getRecoverableLegacyPlaylists(): Result<List<Playlist>> = Result.success(emptyList())
    suspend fun recoverLegacyPlaylist(localKey: String): Result<Unit> = Result.success(Unit)
    suspend fun recoverAllLegacyPlaylists(): Result<Int> = Result.success(0)
    fun clearMemoryCache() {}

    // Discover & Artist Endpoints
    suspend fun checkV2Support(): Boolean = true
    suspend fun getArtistsList(): Result<List<ArtistDto>> = Result.success(emptyList())
    suspend fun getArtistDetail(artistId: String): Result<ArtistDetailDto> = fetchArtistDetail(artistId)
    suspend fun getFolderPlaylists(): Result<List<Playlist>> = Result.success(emptyList())
    suspend fun getCommunityPlaylists(): Result<List<Playlist>> = Result.success(emptyList())
    suspend fun getPlaylistDetail(playlistId: String): Result<PlaylistDetailDto> = Result.failure(NoSuchElementException("Playlist not found"))
}
