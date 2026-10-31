package com.sangita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlist_local_key", "track_id"],
    indices = [
        Index(value = ["playlist_local_key"]),
        Index(value = ["track_id"])
    ]
)
data class PlaylistTrackCrossRef(
    @ColumnInfo(name = "playlist_local_key")
    val playlistLocalKey: String,
    @ColumnInfo(name = "track_id")
    val trackId: String,
    @ColumnInfo(name = "position")
    val position: Int = 0,
    @ColumnInfo(name = "is_available")
    val isAvailable: Boolean = true,
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
) {
    @get:Ignore
    val playlistName: String get() = playlistLocalKey
}
