package com.sangita.app.data.local.storage

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class LocalStorageManager(private val context: Context) {

    val audioDir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "audio").apply {
            if (!exists()) mkdirs()
        }

    val coversDir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "covers").apply {
            if (!exists()) mkdirs()
        }

    val lyricsDir: File
        get() = File(context.getExternalFilesDir(null), "lyrics").apply {
            if (!exists()) mkdirs()
        }

    fun sanitizeFilename(trackId: String): String {
        return trackId.replace('/', '_').replace('\\', '_')
    }

    fun getAudioFile(trackId: String): File {
        val sanitized = sanitizeFilename(trackId)
        return File(audioDir, sanitized)
    }

    fun getCoverFile(trackId: String): File {
        val sanitized = sanitizeFilename(trackId).substringBeforeLast('.') + ".jpg"
        return File(coversDir, sanitized)
    }

    fun getLyricsFile(trackId: String): File {
        val sanitized = sanitizeFilename(trackId).substringBeforeLast('.') + ".lrc"
        return File(lyricsDir, sanitized)
    }

    suspend fun saveAudioStream(trackId: String, inputStream: InputStream): File = withContext(Dispatchers.IO) {
        val file = getAudioFile(trackId)
        val tempFile = File(audioDir, "${file.name}.tmp")
        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }
        if (tempFile.renameTo(file)) {
            file
        } else {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
            file
        }
    }

    suspend fun saveCoverBytes(trackId: String, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val file = getCoverFile(trackId)
        file.writeBytes(bytes)
        file
    }

    suspend fun saveLyricsText(trackId: String, lrcText: String): File = withContext(Dispatchers.IO) {
        val file = getLyricsFile(trackId)
        file.writeText(lrcText)
        file
    }

    suspend fun deleteTrackFiles(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val audio = getAudioFile(trackId)
        val cover = getCoverFile(trackId)
        val lyrics = getLyricsFile(trackId)
        val aDeleted = if (audio.exists()) audio.delete() else true
        if (cover.exists()) cover.delete()
        if (lyrics.exists()) lyrics.delete()
        aDeleted
    }

    suspend fun getStorageUsage(): StorageUsageInfo = withContext(Dispatchers.IO) {
        val audioBytes = calculateDirectorySize(audioDir)
        val coversBytes = calculateDirectorySize(coversDir)
        val lyricsBytes = calculateDirectorySize(lyricsDir)
        val cacheBytes = calculateDirectorySize(context.cacheDir)
        val freeBytes = context.getExternalFilesDir(null)?.freeSpace ?: 0L

        StorageUsageInfo(
            audioBytes = audioBytes,
            coversBytes = coversBytes,
            lyricsBytes = lyricsBytes,
            cacheBytes = cacheBytes,
            totalAppBytes = audioBytes + coversBytes + lyricsBytes + cacheBytes,
            freeDeviceBytes = freeBytes
        )
    }

    private fun calculateDirectorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirectorySize(file) else file.length()
        }
        return size
    }
}

data class StorageUsageInfo(
    val audioBytes: Long,
    val coversBytes: Long,
    val lyricsBytes: Long,
    val cacheBytes: Long,
    val totalAppBytes: Long,
    val freeDeviceBytes: Long
) {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var b = bytes.toDouble()
        var idx = 0
        while (b >= 1024.0 && idx < units.size - 1) {
            b /= 1024.0
            idx++
        }
        return if (idx == 0) "$bytes B" else String.format("%.1f %s", b, units[idx])
    }
}
