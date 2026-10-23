package com.sangita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_events")
data class PlaybackEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "track_id")
    val trackId: String,

    @ColumnInfo(name = "playlist")
    val playlist: String,

    @ColumnInfo(name = "duration_sec")
    val durationSec: Int,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "device_name")
    val deviceName: String,

    @ColumnInfo(name = "device_type")
    val deviceType: String = "mobile",

    @ColumnInfo(name = "browser")
    val browser: String = "Android Native App",

    @ColumnInfo(name = "os")
    val os: String = "Android",

    @ColumnInfo(name = "timestamp")
    val timestamp: String,

    @ColumnInfo(name = "synced")
    val synced: Boolean = false
)
