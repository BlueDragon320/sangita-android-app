package com.sangita.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Track(
    val id: String,                    // e.g. "Rock/Song.mp3" or "Library/Track.flac"
    val title: String,
    val artist: String = "Unknown Artist",
    val playlist: String = "Library",
    val durationSeconds: Double? = null,
    val remoteUrl: String = "",
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false,
    val downloadStatus: DownloadStatus = DownloadStatus.IDLE,
    val downloadProgress: Int = 0,
    val fileSizeBytes: Long = 0,
    val isFavorite: Boolean = false
)

enum class DownloadStatus {
    IDLE, QUEUED, DOWNLOADING, COMPLETED, FAILED
}
