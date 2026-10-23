package com.sangita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sangita.app.domain.model.Playlist

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "is_virtual")
    val isVirtual: Boolean = false,

    @ColumnInfo(name = "is_favorites")
    val isFavorites: Boolean = false,

    @ColumnInfo(name = "track_count")
    val trackCount: Int = 0,

    @ColumnInfo(name = "cover_track_path")
    val coverTrackPath: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = 0L
) {
    fun toDomain(): Playlist = Playlist(
        name = name,
        isVirtual = isVirtual,
        isFavorites = isFavorites,
        trackCount = trackCount,
        coverTrackPath = coverTrackPath
    )

    companion object {
        fun fromDomain(playlist: Playlist): PlaylistEntity = PlaylistEntity(
            name = playlist.name,
            isVirtual = playlist.isVirtual,
            isFavorites = playlist.isFavorites,
            trackCount = playlist.trackCount,
            coverTrackPath = playlist.coverTrackPath
        )
    }
}
