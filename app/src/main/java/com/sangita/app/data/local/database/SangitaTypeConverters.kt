package com.sangita.app.data.local.database

import androidx.room.TypeConverter
import com.sangita.app.domain.model.DownloadStatus
import com.sangita.app.domain.model.SyncActionType

class SangitaTypeConverters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus = try {
        DownloadStatus.valueOf(value)
    } catch (_: Exception) {
        DownloadStatus.IDLE
    }

    @TypeConverter
    fun fromSyncActionType(type: SyncActionType): String = type.name

    @TypeConverter
    fun toSyncActionType(value: String): SyncActionType = try {
        SyncActionType.valueOf(value)
    } catch (_: Exception) {
        SyncActionType.TOGGLE_FAVORITE
    }
}
