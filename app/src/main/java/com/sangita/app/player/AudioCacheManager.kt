package com.sangita.app.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.io.File

@OptIn(UnstableApi::class)
class AudioCacheManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val cacheDir = File(context.cacheDir, "sangita_media_cache")
    private val evictor = LeastRecentlyUsedCacheEvictor(1024 * 1024 * 1024L) // 1 GB LRU Cache
    private val databaseProvider = StandaloneDatabaseProvider(context)

    val simpleCache: SimpleCache by lazy {
        SimpleCache(cacheDir, evictor, databaseProvider)
    }

    fun createDataSourceFactory(): DataSource.Factory {
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Sangita-Android-Native/1.0.0")

        val cacheFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // DefaultDataSource.Factory inspects URI scheme:
        // - file:// URIs (downloaded tracks) -> FileDataSource
        // - content:// URIs -> ContentDataSource
        // - http:// & https:// -> cacheFactory (SimpleCache + OkHttp)
        return DefaultDataSource.Factory(context, cacheFactory)
    }
}
