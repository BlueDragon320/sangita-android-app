package com.sangita.app.domain.repository

import com.sangita.app.domain.model.LyricsData
import kotlinx.coroutines.flow.Flow

interface LyricsRepository {
    fun getLyrics(trackId: String, forceRefresh: Boolean = false): Flow<LyricsData>
}
