package com.sangita.app.ui.components

import android.os.SystemClock
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sangita.app.data.local.storage.LocalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

private data class CoverKey(val trackId: String, val audioPath: String?, val revision: Any?)
private data class CachedCover(val file: File?, val checkedAt: Long)
private val coverCache = LruCache<CoverKey, CachedCover>(256)
private val coverLookupSlots = Semaphore(4)
private const val COVER_CACHE_MS = 10_000L

/** Resolve local artwork off the UI thread; Coil handles loading and caching the result. */
@Composable
fun rememberCoverModel(trackId: String?, localAudioPath: String?, serverUrl: String, token: String?, revision: Any? = null): Any? {
    val context = LocalContext.current
    var model by remember(trackId, localAudioPath, serverUrl, token, revision) { mutableStateOf<Any?>(null) }
    LaunchedEffect(trackId, localAudioPath, serverUrl, token, revision) {
        if (trackId == null) return@LaunchedEffect
        model = withContext(Dispatchers.IO) {
            val key = CoverKey(trackId, localAudioPath, revision)
            val cached = coverCache.get(key)
            val local = if (cached != null && SystemClock.elapsedRealtime() - cached.checkedAt < COVER_CACHE_MS && (cached.file == null || cached.file.exists())) {
                cached.file
            } else {
                coverLookupSlots.withPermit {
                    LocalStorageManager.getOrExtractCoverFile(context, trackId, localAudioPath).also {
                        coverCache.put(key, CachedCover(it, SystemClock.elapsedRealtime()))
                    }
                }
            }
            local ?: if (serverUrl.isNotEmpty()) "$serverUrl/api/cover/${android.net.Uri.encode(trackId, "/")}" else null
        }
    }
    return model
}
