package com.sangita.app.data.repository

import com.sangita.app.data.local.dao.LyricsDao
import com.sangita.app.data.local.entity.LyricsCacheEntity
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.domain.model.LyricsData
import com.sangita.app.domain.repository.LyricsRepository
import com.sangita.app.lyrics.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class LyricsRepositoryImpl(
    private val lyricsDao: LyricsDao,
    private val localStorageManager: LocalStorageManager,
    private val networkClient: NetworkClient
) : LyricsRepository {

    override fun getLyrics(trackId: String, forceRefresh: Boolean): Flow<LyricsData> = flow {
        // 1. Check local file on disk
        val localFile = localStorageManager.getExistingLyricsFile(trackId)
        if (localFile.exists() && !forceRefresh) {
            val text = withContext(Dispatchers.IO) { localFile.readText() }
            val parsed = LrcParser.parse(trackId, text)
            emit(parsed)
            return@flow
        }

        // 2. Check Room database cache
        val dbCached = lyricsDao.getLyrics(trackId)
        if (dbCached != null && !forceRefresh) {
            val parsed = LrcParser.parse(trackId, dbCached.rawLrc)
            emit(parsed)
            return@flow
        }

        // 3. Emit empty/loading state if needed, then fetch from API
        try {
            val api = networkClient.createApiService()
            val response = api.getLyrics(trackId)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.hasLyrics && body.raw.isNotBlank()) {
                    // Save to local file & DB
                    localStorageManager.saveLyricsText(trackId, body.raw)
                    lyricsDao.insertLyrics(
                        LyricsCacheEntity(
                            trackId = trackId,
                            rawLrc = body.raw,
                            hasLyrics = true
                        )
                    )
                    val parsed = LrcParser.parse(trackId, body.raw)
                    emit(parsed)
                    return@flow
                }
            }
        } catch (_: Exception) {
            // Offline or network error
        }

        // Fallback: If DB had something, emit that, otherwise empty
        if (dbCached != null) {
            emit(LrcParser.parse(trackId, dbCached.rawLrc))
        } else {
            emit(LyricsData(trackId = trackId, hasLyrics = false))
        }
    }.flowOn(Dispatchers.IO)
}
