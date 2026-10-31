package com.sangita.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.sangita.app.di.AppContainer
import com.sangita.app.player.SangitaPlaybackService
import com.sangita.app.sync.SangitaSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SangitaApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Eagerly reference deviceSyncManager to ensure initialization
        container.deviceSyncManager

        createNotificationChannels()
        SangitaSyncWorker.schedulePeriodic(this)

        // React to auth token changes (login/logout)
        container.applicationScope.launch(Dispatchers.Default) {
            container.userPreferences.tokenFlow.collectLatest { token ->
                if (!token.isNullOrBlank()) {
                    SangitaSyncWorker.enqueueOneTime(this@SangitaApplication)
                    if (container.connectivityObserver.isOnline) container.downloadManager.resumeInterruptedDownloads()
                }
            }
        }

        // Automatically trigger sync when connectivity is restored
        container.applicationScope.launch(Dispatchers.Default) {
            container.connectivityObserver.observe().collectLatest { isOnline ->
                if (isOnline && !container.userPreferences.getToken().isNullOrBlank()) {
                    SangitaSyncWorker.enqueueOneTime(this@SangitaApplication)
                    container.downloadManager.resumeInterruptedDownloads()
                    container.deviceSyncManager.connect()
                }
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150 * 1024 * 1024)
                    .build()
            }
            .okHttpClient(container.networkClient.okHttpClient)
            .crossfade(true)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SangitaPlaybackService.CHANNEL_ID,
                "Sangita Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback notifications"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
