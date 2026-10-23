package com.sangita.app.domain.repository

import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun observeAllTracks(): Flow<List<Track>>
    fun observePlaylists(): Flow<List<Playlist>>
    fun observeTracks(playlistName: String): Flow<List<Track>>
    fun observeFavorites(): Flow<List<Track>>
    fun observeDownloadedTracks(): Flow<List<Track>>
    suspend fun toggleFavorite(track: Track): Result<Boolean>
    suspend fun createPlaylist(name: String): Result<Unit>
    suspend fun addTrackToPlaylist(playlistName: String, track: Track): Result<Unit>
    suspend fun removeTrackFromPlaylist(playlistName: String, track: Track): Result<Unit>
    suspend fun refreshPlaylists(): Result<Unit>
}
