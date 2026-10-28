package com.sangita.app.di

import android.content.Context
import com.sangita.app.data.local.database.SangitaDatabase
import com.sangita.app.data.local.preferences.UserPreferences
import com.sangita.app.data.local.storage.LocalStorageManager
import com.sangita.app.data.remote.api.NetworkClient
import com.sangita.app.data.repository.MusicRepositoryImpl
import com.sangita.app.data.repository.LyricsRepositoryImpl
import com.sangita.app.data.repository.SyncRepositoryImpl
import com.sangita.app.download.SangitaDownloadManager
import com.sangita.app.domain.repository.LyricsRepository
import com.sangita.app.domain.repository.MusicRepository
import com.sangita.app.domain.repository.SyncRepository
import com.sangita.app.player.AudioCacheManager
import com.sangita.app.player.PlaybackController
import com.sangita.app.sync.DeviceSyncManager
import com.sangita.app.sync.NetworkConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(val context: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: SangitaDatabase by lazy {
        SangitaDatabase.getInstance(context)
    }

    val localStorageManager: LocalStorageManager by lazy {
        LocalStorageManager(context)
    }

    val userPreferences: UserPreferences by lazy {
        UserPreferences(context)
    }

    val networkClient: NetworkClient by lazy {
        NetworkClient(userPreferences)
    }

    val musicRepository: MusicRepository by lazy {
        MusicRepositoryImpl(
            context = context,
            database = database,
            trackDao = database.trackDao(),
            playlistDao = database.playlistDao(),
            favoriteDao = database.favoriteDao(),
            syncQueueDao = database.syncQueueDao(),
            localStorageManager = localStorageManager,
            userPreferences = userPreferences,
            networkClient = networkClient
        )
    }

    val lyricsRepository: LyricsRepository by lazy {
        LyricsRepositoryImpl(
            lyricsDao = database.lyricsDao(),
            localStorageManager = localStorageManager,
            networkClient = networkClient
        )
    }

    val syncRepository: SyncRepository by lazy {
        SyncRepositoryImpl(
            syncQueueDao = database.syncQueueDao(),
            playbackEventDao = database.playbackEventDao(),
            networkClient = networkClient
        )
    }

    val audioCacheManager: AudioCacheManager by lazy {
        AudioCacheManager(context, networkClient.okHttpClient)
    }

    val deviceSyncManager: DeviceSyncManager by lazy {
        DeviceSyncManager(
            userPreferences = userPreferences,
            scope = applicationScope
        )
    }

    val playbackController: PlaybackController by lazy {
        PlaybackController(
            context = context,
            localStorageManager = localStorageManager,
            userPreferences = userPreferences,
            playbackEventDao = database.playbackEventDao(),
            trackDao = database.trackDao(),
            deviceSyncManager = deviceSyncManager,
            scope = applicationScope
        )
    }

    val downloadManager: SangitaDownloadManager by lazy {
        SangitaDownloadManager(
            trackDao = database.trackDao(),
            localStorageManager = localStorageManager,
            networkClient = networkClient,
            scope = applicationScope
        )
    }

    val connectivityObserver: NetworkConnectivityObserver by lazy {
        NetworkConnectivityObserver(context)
    }
}
