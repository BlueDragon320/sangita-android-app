package com.sangita.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sangita.app.data.local.entity.TrackEntity
import com.sangita.app.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks")
    fun observeAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE playlist_name = :playlist ORDER BY title ASC")
    fun observeTracksByPlaylist(playlist: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    fun observeTrackById(id: String): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE is_favorite = 1 ORDER BY title ASC")
    fun observeFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE is_downloaded = 1 ORDER BY downloaded_at DESC, title ASC")
    fun observeDownloadedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchTracks(query: String): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(tracks: List<TrackEntity>)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("UPDATE tracks SET is_favorite = :isFavorite WHERE id = :trackId")
    suspend fun updateFavoriteStatus(trackId: String, isFavorite: Boolean)

    @Query("""
        UPDATE tracks 
        SET is_downloaded = :isDownloaded, 
            local_file_path = :localPath, 
            download_status = :status, 
            download_progress = :progress, 
            file_size_bytes = :fileSize,
            downloaded_at = :downloadedAt
        WHERE id = :trackId
    """)
    suspend fun updateDownloadStatus(
        trackId: String,
        isDownloaded: Boolean,
        localPath: String?,
        status: DownloadStatus,
        progress: Int,
        fileSize: Long,
        downloadedAt: Long?
    )

    @Query("UPDATE tracks SET download_progress = :progress, download_status = :status WHERE id = :trackId")
    suspend fun updateDownloadProgress(trackId: String, progress: Int, status: DownloadStatus)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: String)

    @Query("SELECT COUNT(*) FROM tracks WHERE is_downloaded = 1")
    fun observeDownloadedTrackCount(): Flow<Int>
}
