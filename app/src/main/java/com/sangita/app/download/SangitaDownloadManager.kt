package com.sangita.app.download

import com.sangita.app.data.local.dao.TrackDao
import com.sangita.app.data.local.entity.LyricsCacheEntity
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.domain.model.DownloadStatus
import com.sangita.app.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class SangitaDownloadManager(
    private val trackDao: TrackDao,
    private val localStorageManager: LocalStorageManager,
    private val networkClient: NetworkClient,
    private val scope: CoroutineScope
) {
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _downloadProgressFlow = MutableSharedFlow<Pair<String, Int>>(replay = 1)
    val downloadProgressFlow: SharedFlow<Pair<String, Int>> = _downloadProgressFlow.asSharedFlow()

    fun downloadTrack(track: Track) {
        if (activeJobs.containsKey(track.id)) return

        val job = scope.launch(Dispatchers.IO) {
            try {
                trackDao.updateDownloadProgress(track.id, 0, DownloadStatus.DOWNLOADING)
                _downloadProgressFlow.emit(track.id to 0)

                val api = networkClient.createApiService()

                // 1. Download Audio Stream
                val streamResp = api.streamAudio(track.id)
                if (!streamResp.isSuccessful || streamResp.body() == null) {
                    trackDao.updateDownloadProgress(track.id, 0, DownloadStatus.FAILED)
                    return@launch
                }

                val body = streamResp.body()!!
                val contentLength = body.contentLength()
                val targetFile = localStorageManager.getAudioFile(track.id)
                val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalBytes = 0L
                        var lastReportedPercent = 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                            if (contentLength > 0) {
                                val percent = ((totalBytes * 100) / contentLength).toInt()
                                if (percent >= lastReportedPercent + 5) {
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
            }
        }
        activeJobs[track.id] = job
    }

    fun downloadPlaylist(tracks: List<Track>) {
        tracks.forEach { track ->
            if (!track.isDownloaded) {
                downloadTrack(track)
            }
        }
    }

    fun cancelDownload(trackId: String) {
        activeJobs[trackId]?.cancel()
        activeJobs.remove(trackId)
        scope.launch(Dispatchers.IO) {
            trackDao.updateDownloadProgress(trackId, 0, DownloadStatus.IDLE)
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
