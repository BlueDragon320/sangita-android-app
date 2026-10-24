package com.sangita.app.download

import com.sangita.app.data.local.dao.TrackDao
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.domain.model.DownloadStatus
import com.sangita.app.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

data class DownloadTask(
    val trackId: String,
    val trackTitle: String,
    val artist: String,
    val coverUrl: String? = null,
    val progress: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: DownloadStatus = DownloadStatus.DOWNLOADING
)

object DownloadFormatting {
    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec <= 0L -> "0 KB/s"
            bytesPerSec < 1024L * 1024L -> "%.1f KB/s".format(bytesPerSec / 1024.0)
            else -> "%.1f MB/s".format(bytesPerSec / (1024.0 * 1024.0))
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes <= 0L -> "0 MB"
            bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}

class SangitaDownloadManager(
    private val trackDao: TrackDao,
    private val localStorageManager: LocalStorageManager,
    private val networkClient: NetworkClient,
    private val scope: CoroutineScope
) {
    companion object {
        private const val MAX_CONCURRENT_DOWNLOADS = 2
    }

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val queuedTracks = ConcurrentHashMap<String, Track>()

    private val _downloadProgressFlow = MutableSharedFlow<Pair<String, Int>>(replay = 1)
    val downloadProgressFlow: SharedFlow<Pair<String, Int>> = _downloadProgressFlow.asSharedFlow()

    private val _activeDownloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val activeDownloads: StateFlow<List<DownloadTask>> = _activeDownloads.asStateFlow()

    val activeDownloadsCount: StateFlow<Int> = _activeDownloads.map { list ->
        list.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    val totalSpeedBytesPerSec: StateFlow<Long> = _activeDownloads.map { list ->
        list.filter { it.status == DownloadStatus.DOWNLOADING }.sumOf { it.speedBytesPerSec }
    }.stateIn(scope, SharingStarted.Eagerly, 0L)

    fun downloadTrack(track: Track) {
        synchronized(this) {
            if (_activeDownloads.value.any { it.trackId == track.id }) {
                return
            }

            queuedTracks[track.id] = track

            val currentDownloadingCount = _activeDownloads.value.count { it.status == DownloadStatus.DOWNLOADING }
            if (currentDownloadingCount < MAX_CONCURRENT_DOWNLOADS) {
                val task = DownloadTask(
                    trackId = track.id,
                    trackTitle = track.title,
                    artist = track.artist,
                    coverUrl = track.remoteUrl,
                    progress = 0,
                    status = DownloadStatus.DOWNLOADING
                )
                _activeDownloads.value = _activeDownloads.value + task
                startDownloadJob(track)
            } else {
                val task = DownloadTask(
                    trackId = track.id,
                    trackTitle = track.title,
                    artist = track.artist,
                    coverUrl = track.remoteUrl,
                    progress = 0,
                    status = DownloadStatus.QUEUED
                )
                _activeDownloads.value = _activeDownloads.value + task
                scope.launch(Dispatchers.IO) {
                    trackDao.updateDownloadProgress(track.id, 0, DownloadStatus.QUEUED)
                }
            }
        }
    }

    private fun startDownloadJob(track: Track) {
        val job = scope.launch(Dispatchers.IO) {
            try {
                trackDao.updateDownloadProgress(track.id, 0, DownloadStatus.DOWNLOADING)
                _downloadProgressFlow.emit(track.id to 0)

                val api = networkClient.createApiService()

                // 1. Download Audio Stream
                val streamResp = api.streamAudio(track.id)
                if (!streamResp.isSuccessful || streamResp.body() == null) {
                    trackDao.updateDownloadProgress(track.id, 0, DownloadStatus.FAILED)
                    updateTaskStatus(track.id, DownloadStatus.FAILED)
                    return@launch
                }

                val body = streamResp.body()!!
                val contentLength = body.contentLength()
                val targetFile = localStorageManager.getAudioFile(track.id)
                val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

                var lastSampleTime = System.currentTimeMillis()
                var lastSampleBytes = 0L
                var totalBytes = 0L
                var lastReportedPercent = 0

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead

                            val now = System.currentTimeMillis()
                            val dt = now - lastSampleTime
                            val percent = if (contentLength > 0) ((totalBytes * 100) / contentLength).toInt().coerceIn(0, 100) else 0

                            if (dt >= 400 || (percent >= lastReportedPercent + 2 && contentLength > 0)) {
                                val dBytes = totalBytes - lastSampleBytes
                                val speed = if (dt > 0) (dBytes * 1000) / dt else 0L

                                lastSampleTime = now
                                lastSampleBytes = totalBytes

                                updateTaskProgress(
                                    trackId = track.id,
                                    bytesDownloaded = totalBytes,
                                    totalBytes = contentLength,
                                    progress = percent,
                                    speed = speed
                                )

                                if (percent >= lastReportedPercent + 2) {
                                    lastReportedPercent = percent
                                    trackDao.updateDownloadProgress(track.id, percent, DownloadStatus.DOWNLOADING)
                                    _downloadProgressFlow.emit(track.id to percent)
                                }
                            }
                        }
                    }
                }

                if (tempFile.renameTo(targetFile) || tempFile.copyTo(targetFile, overwrite = true).also { tempFile.delete() }.exists()) {
                    // 2. Download Cover Art
                    try {
                        val coverResp = api.getCoverArt(track.id)
                        if (coverResp.isSuccessful && coverResp.body() != null) {
                            localStorageManager.saveCoverBytes(track.id, coverResp.body()!!.bytes())
                        }
                    } catch (_: Exception) {}

                    // 3. Download Lyrics Sidecar
                    try {
                        val lyricsResp = api.getLyrics(track.id)
                        if (lyricsResp.isSuccessful && lyricsResp.body()?.raw?.isNotBlank() == true) {
                            val raw = lyricsResp.body()!!.raw
                            localStorageManager.saveLyricsText(track.id, raw)
                        }
                    } catch (_: Exception) {}

                    // 4. Mark Complete in Room DB
                    trackDao.updateDownloadStatus(
                        trackId = track.id,
                        isDownloaded = true,
                        localPath = targetFile.absolutePath,
                        status = DownloadStatus.COMPLETED,
                        progress = 100,
                        fileSize = targetFile.length(),
                        downloadedAt = System.currentTimeMillis()
                    )
                    _downloadProgressFlow.emit(track.id to 100)
                } else {
                    trackDao.updateDownloadProgress(track.id, 0, DownloadStatus.FAILED)
                }

            } catch (e: Exception) {
                trackDao.updateDownloadProgress(track.id, 0, DownloadStatus.FAILED)
            } finally {
                activeJobs.remove(track.id)
                queuedTracks.remove(track.id)
                removeTask(track.id)
                processQueue()
            }
        }
        activeJobs[track.id] = job
    }

    private fun updateTaskProgress(
        trackId: String,
        bytesDownloaded: Long,
        totalBytes: Long,
        progress: Int,
        speed: Long
    ) {
        _activeDownloads.value = _activeDownloads.value.map { task ->
            if (task.trackId == trackId) {
                task.copy(
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes,
                    progress = progress,
                    speedBytesPerSec = speed,
                    status = DownloadStatus.DOWNLOADING
                )
            } else {
                task
            }
        }
    }

    private fun updateTaskStatus(trackId: String, status: DownloadStatus) {
        _activeDownloads.value = _activeDownloads.value.map { task ->
            if (task.trackId == trackId) {
                task.copy(status = status)
            } else {
                task
            }
        }
    }

    private fun removeTask(trackId: String) {
        _activeDownloads.value = _activeDownloads.value.filter { it.trackId != trackId }
    }

    private fun processQueue() {
        synchronized(this) {
            val currentDownloadingCount = _activeDownloads.value.count { it.status == DownloadStatus.DOWNLOADING }
            if (currentDownloadingCount < MAX_CONCURRENT_DOWNLOADS) {
                val nextTask = _activeDownloads.value.firstOrNull { it.status == DownloadStatus.QUEUED }
                if (nextTask != null) {
                    val track = queuedTracks[nextTask.trackId]
                    if (track != null) {
                        _activeDownloads.value = _activeDownloads.value.map { task ->
                            if (task.trackId == nextTask.trackId) {
                                task.copy(status = DownloadStatus.DOWNLOADING)
                            } else {
                                task
                            }
                        }
                        startDownloadJob(track)
                    }
                }
            }
        }
    }

    fun downloadPlaylist(tracks: List<Track>) {
        tracks.forEach { track ->
            if (!track.isDownloaded) {
                downloadTrack(track)
            }
        }
    }

    fun cancelDownload(trackId: String) {
        synchronized(this) {
            val job = activeJobs[trackId]
            job?.cancel()
            activeJobs.remove(trackId)
            queuedTracks.remove(trackId)
            removeTask(trackId)

            scope.launch(Dispatchers.IO) {
                try {
                    val targetFile = localStorageManager.getAudioFile(trackId)
                    val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                    if (tempFile.exists()) tempFile.delete()
                    trackDao.updateDownloadProgress(trackId, 0, DownloadStatus.IDLE)
                } catch (_: Exception) {}
            }

            processQueue()
        }
    }

    fun cancelAllDownloads() {
        synchronized(this) {
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()

            val tasksToCancel = _activeDownloads.value
            _activeDownloads.value = emptyList()
            queuedTracks.clear()

            scope.launch(Dispatchers.IO) {
                tasksToCancel.forEach { task ->
                    try {
                        val targetFile = localStorageManager.getAudioFile(task.trackId)
                        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                        if (tempFile.exists()) tempFile.delete()
                        trackDao.updateDownloadProgress(task.trackId, 0, DownloadStatus.IDLE)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    suspend fun removeDownloadedTrack(trackId: String): Boolean = withContext(Dispatchers.IO) {
        cancelDownload(trackId)
        val deleted = localStorageManager.deleteTrackFiles(trackId)
        trackDao.updateDownloadStatus(
            trackId = trackId,
            isDownloaded = false,
            localPath = null,
            status = DownloadStatus.IDLE,
            progress = 0,
            fileSize = 0L,
            downloadedAt = null
        )
        deleted
    }
}
