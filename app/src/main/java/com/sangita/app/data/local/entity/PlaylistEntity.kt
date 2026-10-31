package com.sangita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sangita.app.domain.model.Playlist
import com.sangita.app.domain.model.PlaylistKind
import com.sangita.app.domain.model.PlaylistSyncState
import java.util.UUID

@Entity(
    tableName = "playlists",
    indices = [
        Index(value = ["account_key"]),
        Index(value = ["server_id"]),
        Index(value = ["account_key", "server_id"])
    ]
)
data class PlaylistEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_key")
    val localKey: String,
    @ColumnInfo(name = "server_id")
    val serverId: String? = null,
    @ColumnInfo(name = "account_key")
    val accountKey: String = "default_account",
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "kind")
    val kind: String = "PERSONAL", // Stored as String for Room simplicity
    @ColumnInfo(name = "owner")
    val owner: String? = null,
    @ColumnInfo(name = "visibility")
    val visibility: String = "private",
    @ColumnInfo(name = "description")
    val description: String? = null,
    @ColumnInfo(name = "track_count")
    val trackCount: Int = 0,
    @ColumnInfo(name = "cover_track_path")
    val coverTrackPath: String? = null,
    @ColumnInfo(name = "unavailable_count")
    val unavailableCount: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = 0L,
    @ColumnInfo(name = "sync_state")
    val syncState: String = "SYNCED"
) {
    @get:Ignore
    val name: String get() = title

    @get:Ignore
    val isFavorites: Boolean get() = kind == "FAVORITES"

    @get:Ignore
    val isVirtual: Boolean get() = kind == "FOLDER" || kind == "LEGACY_PENDING"

    @Ignore
    constructor(
        name: String,
        isVirtual: Boolean = false,
        isFavorites: Boolean = false,
        trackCount: Int = 0,
        coverTrackPath: String? = null,
        createdAt: Long = System.currentTimeMillis(),
        lastSyncedAt: Long = 0L,
        localKey: String = "local_" + UUID.randomUUID().toString(),
        accountKey: String = "default_account"
    ) : this(
        localKey = localKey,
        serverId = null,
        accountKey = accountKey,
        title = name,
        kind = if (isFavorites) "FAVORITES" else if (isVirtual) "FOLDER" else "PERSONAL",
        owner = null,
        visibility = "private",
        description = null,
        trackCount = trackCount,
        coverTrackPath = coverTrackPath,
        unavailableCount = 0,
        createdAt = createdAt,
        lastSyncedAt = lastSyncedAt,
        syncState = "SYNCED"
    )

    fun toDomain(): Playlist {
        val parsedKind = try {
            PlaylistKind.valueOf(kind)
        } catch (_: Exception) {
            PlaylistKind.PERSONAL
        }
        val parsedSyncState = try {
            PlaylistSyncState.valueOf(syncState)
        } catch (_: Exception) {
            PlaylistSyncState.SYNCED
        }
        return Playlist(
            id = localKey,
            serverId = serverId,
            title = title,
            kind = parsedKind,
            owner = owner,
            visibility = visibility,
            description = description,
            trackCount = trackCount,
            coverTrackPath = coverTrackPath,
            unavailableCount = unavailableCount,
            isAvailable = true,
            syncState = parsedSyncState,
            createdAt = createdAt,
            updatedAt = if (lastSyncedAt > 0) lastSyncedAt else createdAt
        )
    }

    companion object {
        fun fromDomain(playlist: Playlist, accountKey: String = "default_account"): PlaylistEntity = PlaylistEntity(
            localKey = playlist.id,
            serverId = playlist.serverId,
            accountKey = accountKey,
            title = playlist.title,
            kind = playlist.kind.name,
            owner = playlist.owner,
            visibility = playlist.visibility,
            description = playlist.description,
            trackCount = playlist.trackCount,
            coverTrackPath = playlist.coverTrackPath,
            unavailableCount = playlist.unavailableCount,
            createdAt = playlist.createdAt,
            lastSyncedAt = playlist.updatedAt,
            syncState = playlist.syncState.name
        )
    }
}
