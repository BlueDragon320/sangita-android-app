package com.sangita.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.UUID

@Immutable
@Serializable
data class Playlist(
    val id: String, // Local opaque key (UUID)
    val serverId: String? = null,
    val title: String,
    val kind: PlaylistKind = PlaylistKind.PERSONAL,
    val owner: String? = null,
    val visibility: String = "private",
    val description: String? = null,
    val trackCount: Int = 0,
    val coverTrackPath: String? = null,
    val unavailableCount: Int = 0,
    val isAvailable: Boolean = true,
    val syncState: PlaylistSyncState = PlaylistSyncState.SYNCED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val tracks: List<Track> = emptyList(),
    val isSaved: Boolean = false,
    val isDownloaded: Boolean = false
) {
    val isFavorites: Boolean get() = kind == PlaylistKind.FAVORITES
    val isFolder: Boolean get() = kind == PlaylistKind.FOLDER
    val isPersonal: Boolean get() = kind == PlaylistKind.PERSONAL
    val isPublic: Boolean get() = visibility.equals("public", ignoreCase = true)

    val name: String get() = title
    val isVirtual: Boolean get() = isFolder || kind == PlaylistKind.LEGACY_PENDING

    constructor(
        name: String,
        isVirtual: Boolean = false,
        isFavorites: Boolean = false,
        trackCount: Int = 0,
        coverTrackPath: String? = null,
        tracks: List<Track> = emptyList(),
        id: String = "local_" + UUID.randomUUID().toString(),
        isDownloaded: Boolean = false
    ) : this(
        id = id,
        serverId = null,
        title = name,
        kind = if (isFavorites) PlaylistKind.FAVORITES else if (isVirtual) PlaylistKind.FOLDER else PlaylistKind.PERSONAL,
        owner = null,
        visibility = "private",
        description = null,
        trackCount = trackCount,
        coverTrackPath = coverTrackPath,
        unavailableCount = 0,
        isAvailable = true,
        syncState = PlaylistSyncState.SYNCED,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        tracks = tracks,
        isSaved = false,
        isDownloaded = isDownloaded
    )
}
