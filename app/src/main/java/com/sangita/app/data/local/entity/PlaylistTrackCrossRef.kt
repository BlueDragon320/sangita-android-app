package com.sangita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlist_name", "track_id"],
    indices = [
        Index(value = ["playlist_name"]),
        Index(value = ["track_id"])
    ]
)
data class PlaylistTrackCrossRef(
    @ColumnInfo(name = "playlist_name")
    val playlistName: String,

    @ColumnInfo(name = "track_id")
    val trackId: String,

    @ColumnInfo(name = "position")
    val position: Int = 0,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)
