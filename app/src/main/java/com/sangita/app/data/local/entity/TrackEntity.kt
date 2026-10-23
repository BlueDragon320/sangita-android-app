package com.sangita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sangita.app.domain.model.DownloadStatus
import com.sangita.app.domain.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // e.g. "Rock/Song.mp3" or "Library/Track.flac"

    @ColumnInfo(name = "playlist_name")
    val playlistName: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "artist")
    val artist: String = "Unknown Artist",

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Double? = null,

    @ColumnInfo(name = "remote_url")
    val remoteUrl: String = "",

    @ColumnInfo(name = "local_file_path")
    val localFilePath: String? = null,

    @ColumnInfo(name = "is_downloaded")
    val isDownloaded: Boolean = false,

    @ColumnInfo(name = "download_status")
    val downloadStatus: DownloadStatus = DownloadStatus.IDLE,

    @ColumnInfo(name = "download_progress")
    val downloadProgress: Int = 0,

    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long = 0,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "downloaded_at")
    val downloadedAt: Long? = null,

    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: Long? = null
) {
    fun toDomain(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        playlist = playlistName,
        durationSeconds = durationSeconds,
        remoteUrl = remoteUrl,
        localFilePath = localFilePath,
        isDownloaded = isDownloaded,
        downloadStatus = downloadStatus,
        downloadProgress = downloadProgress,
        fileSizeBytes = fileSizeBytes,
        isFavorite = isFavorite
    )

    companion object {
        fun fromDomain(track: Track): TrackEntity = TrackEntity(
            id = track.id,
            playlistName = track.playlist,
            title = track.title,
            artist = track.artist,
            durationSeconds = track.durationSeconds,
            remoteUrl = track.remoteUrl,
            localFilePath = track.localFilePath,
            isDownloaded = track.isDownloaded,
            downloadStatus = track.downloadStatus,
            downloadProgress = track.downloadProgress,
            fileSizeBytes = track.fileSizeBytes,
            isFavorite = track.isFavorite
        )
    }
}
