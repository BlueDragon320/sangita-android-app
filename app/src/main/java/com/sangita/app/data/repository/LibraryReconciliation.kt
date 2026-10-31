package com.sangita.app.data.repository

import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.data.local.entity.PlaylistTrackCrossRef
import com.sangita.app.data.local.entity.TrackEntity
import com.sangita.app.data.remote.dto.PlaylistDto
import com.sangita.app.data.remote.dto.PlaylistTrackDto
import com.sangita.app.domain.model.PlaylistKind
import com.sangita.app.domain.model.PlaylistSyncState
import java.util.UUID

/** Pending desired states override the server snapshot until the outbox is flushed. */
internal fun reconcileFavorites(remote: Set<String>, pending: Map<String, Boolean>): Set<String> {
    val result = remote.toMutableSet()
    pending.forEach { (path, desired) -> if (desired) result.add(path) else result.remove(path) }
    return result
}

/** Preserve one ordered membership per playlist even when a song occurs in multiple playlists. */
internal fun playlistMemberships(name: String, paths: List<String>): List<PlaylistTrackCrossRef> =
    paths.distinct().mapIndexed { index, path ->
        PlaylistTrackCrossRef(playlistLocalKey = name, trackId = path, position = index)
    }

data class PlaylistReconciliationPlan(
    val toUpsert: List<PlaylistEntity>,
    val toDeleteKeys: List<String>,
    val remoteIdToLocalKey: Map<String, String>
)

/**
 * Reconciles remote playlists with local Room database playlists:
 * 1. Match by serverId == remote.id.
 * 2. If serverId is null, match by title ONLY when title is unambiguous and kind is not FAVORITES.
 * 3. Only upsert playlists into the local Room database under accountKey if they are:
 *    - User-owned (owner == currentUsername or kind == "PERSONAL"), OR
 *    - User favorites (kind == "FAVORITES"), OR
 *    - Explicitly saved (isSaved == true or in savedPlaylistIds).
 *    Do NOT insert un-saved folder playlists (isFolderPlaylist == true && !isSaved) into Room.
 * 4. Preserve local playlists that have pending local edits (syncState != SYNCED).
 * 5. Never delete local-only playlists or favorites.
 * 6. Safely mark server-synced playlists deleted remotely (or unsaved folder playlists) for removal.
 */
fun reconcilePlaylists(
    localPlaylists: List<PlaylistEntity>,
    remotePlaylists: List<PlaylistDto>,
    accountKey: String = "default_account",
    currentUsername: String? = null,
    savedPlaylistIds: Set<String> = emptySet()
): PlaylistReconciliationPlan {
    val toUpsert = mutableListOf<PlaylistEntity>()
    val toDeleteKeys = mutableListOf<String>()
    val remoteIdToLocalKey = mutableMapOf<String, String>()

    val matchedLocalKeys = mutableSetOf<String>()
    val matchedRemoteIds = mutableSetOf<String>()

    fun isEligibleForPersonalLibrary(remote: PlaylistDto, local: PlaylistEntity? = null): Boolean {
        val isExplicitlySaved = remote.isSaved ||
            savedPlaylistIds.contains(remote.id) ||
            (local?.serverId != null && savedPlaylistIds.contains(local.serverId))
        if (isExplicitlySaved) return true

        val isUserFavorites = remote.title.equals("Favorites", ignoreCase = true) ||
            local?.kind == PlaylistKind.FAVORITES.name
        if (isUserFavorites) return true

        val isOwner = !currentUsername.isNullOrBlank() && remote.owner != null && remote.owner.equals(currentUsername, ignoreCase = true)
        if (isOwner) return true

        // Non-folder playlists default to personal playlists
        if (!remote.isFolderPlaylist && (remote.owner.isNullOrBlank() || (!currentUsername.isNullOrBlank() && remote.owner.equals(currentUsername, ignoreCase = true)))) {
            return true
        }

        return false
    }

    // 1. Match by serverId == remote.id
    val localByServerId = localPlaylists.filter { !it.serverId.isNullOrBlank() }.associateBy { it.serverId!! }
    for (remote in remotePlaylists) {
        val local = localByServerId[remote.id]
        if (local != null) {
            matchedLocalKeys.add(local.localKey)
            matchedRemoteIds.add(remote.id)

            if (!isEligibleForPersonalLibrary(remote, local)) {
                // Un-saved folder playlist or un-saved remote: do not keep in personal library
                if (local.syncState != PlaylistSyncState.SYNCED.name) {
                    toUpsert.add(local)
                } else {
                    toDeleteKeys.add(local.localKey)
                }
                continue
            }

            remoteIdToLocalKey[remote.id] = local.localKey

            if (local.syncState != PlaylistSyncState.SYNCED.name) {
                // Preserve local playlist that has pending local edits
                toUpsert.add(local)
            } else {
                toUpsert.add(
                    local.copy(
                        title = remote.title,
                        description = remote.description,
                        owner = remote.owner,
                        visibility = remote.visibility,
                        kind = if (remote.isFolderPlaylist) PlaylistKind.FOLDER.name else PlaylistKind.PERSONAL.name,
                        trackCount = remote.trackCount,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // 2. If serverId is null, match by title ONLY when title is unambiguous and kind is not FAVORITES
    val remainingLocals = localPlaylists.filter { it.localKey !in matchedLocalKeys }
    val remainingRemotes = remotePlaylists.filter { it.id !in matchedRemoteIds }

    val candidateLocals = remainingLocals.filter {
        it.serverId.isNullOrBlank() && it.kind != PlaylistKind.FAVORITES.name
    }

    val candidateLocalTitleCounts = candidateLocals.groupingBy { it.title }.eachCount()
    val remainingRemoteTitleCounts = remainingRemotes.groupingBy { it.title }.eachCount()

    for (candidate in candidateLocals) {
        val title = candidate.title
        val isUnambiguous = (candidateLocalTitleCounts[title] == 1) && (remainingRemoteTitleCounts[title] == 1)
        if (isUnambiguous) {
            val remote = remainingRemotes.first { it.title == title }
            matchedLocalKeys.add(candidate.localKey)
            matchedRemoteIds.add(remote.id)

            if (!isEligibleForPersonalLibrary(remote, candidate)) {
                if (candidate.syncState != PlaylistSyncState.SYNCED.name) {
                    toUpsert.add(candidate)
                } else {
                    toDeleteKeys.add(candidate.localKey)
                }
                continue
            }

            remoteIdToLocalKey[remote.id] = candidate.localKey

            if (candidate.syncState != PlaylistSyncState.SYNCED.name) {
                // Preserve pending local edits, but link serverId
                toUpsert.add(candidate.copy(serverId = remote.id))
            } else {
                toUpsert.add(
                    candidate.copy(
                        serverId = remote.id,
                        description = remote.description,
                        owner = remote.owner,
                        visibility = remote.visibility,
                        kind = if (remote.isFolderPlaylist) PlaylistKind.FOLDER.name else PlaylistKind.PERSONAL.name,
                        trackCount = remote.trackCount,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // 3. New remote playlists (remotes not matched by serverId or unambiguous title)
    val newRemotes = remainingRemotes.filter { it.id !in matchedRemoteIds }
    for (remote in newRemotes) {
        if (!isEligibleForPersonalLibrary(remote)) {
            // Un-saved folder playlist: do NOT insert into user's personal Room playlists table
            continue
        }

        val newLocalKey = UUID.randomUUID().toString()
        remoteIdToLocalKey[remote.id] = newLocalKey
        toUpsert.add(
            PlaylistEntity(
                localKey = newLocalKey,
                serverId = remote.id,
                accountKey = accountKey,
                title = remote.title,
                kind = if (remote.isFolderPlaylist) PlaylistKind.FOLDER.name else PlaylistKind.PERSONAL.name,
                owner = remote.owner,
                visibility = remote.visibility,
                description = remote.description,
                trackCount = remote.trackCount,
                coverTrackPath = null,
                unavailableCount = 0,
                createdAt = System.currentTimeMillis(),
                lastSyncedAt = System.currentTimeMillis(),
                syncState = PlaylistSyncState.SYNCED.name
            )
        )
    }

    // 4. Remaining unmatched locals
    val unhandledLocals = remainingLocals.filter { it.localKey !in matchedLocalKeys }
    for (local in unhandledLocals) {
        val isFolder = local.kind == PlaylistKind.FOLDER.name
        val isSaved = savedPlaylistIds.contains(local.localKey) || (local.serverId != null && savedPlaylistIds.contains(local.serverId))
        val isOwner = !currentUsername.isNullOrBlank() && local.owner != null && local.owner.equals(currentUsername, ignoreCase = true)

        when {
            // Unsaved folder playlist previously synced -> remove from personal Room playlists
            isFolder && !isSaved && !isOwner && local.syncState == PlaylistSyncState.SYNCED.name -> {
                toDeleteKeys.add(local.localKey)
            }
            // Preserve local playlists that have pending local edits (syncState != SYNCED)
            local.syncState != PlaylistSyncState.SYNCED.name -> {
                toUpsert.add(local)
            }
            // Preserve Favorites
            local.kind == PlaylistKind.FAVORITES.name -> {
                toUpsert.add(local)
            }
            // Preserve local-only playlists without server ID
            local.serverId.isNullOrBlank() -> {
                toUpsert.add(local)
            }
            // Previously synced playlist that is no longer present on server
            else -> {
                toDeleteKeys.add(local.localKey)
            }
        }
    }

    return PlaylistReconciliationPlan(
        toUpsert = toUpsert,
        toDeleteKeys = toDeleteKeys,
        remoteIdToLocalKey = remoteIdToLocalKey
    )
}

/**
 * Reconciles playlist tracks:
 * - If owner, preserve unavailable tracks in order with isAvailable = false.
 * - If not owner, tracks already filtered by server, but record isAvailable = true.
 */
fun reconcilePlaylistTracks(
    playlistLocalKey: String,
    tracks: List<PlaylistTrackDto>,
    isOwner: Boolean
): List<PlaylistTrackCrossRef> {
    return if (isOwner) {
        tracks.mapIndexed { index, trackDto ->
            PlaylistTrackCrossRef(
                playlistLocalKey = playlistLocalKey,
                trackId = trackDto.trackPath,
                position = if (trackDto.position >= 0) trackDto.position else index,
                isAvailable = trackDto.isAvailable
            )
        }
    } else {
        tracks.filter { it.isAvailable }.mapIndexed { index, trackDto ->
            PlaylistTrackCrossRef(
                playlistLocalKey = playlistLocalKey,
                trackId = trackDto.trackPath,
                position = if (trackDto.position >= 0) trackDto.position else index,
                isAvailable = true
            )
        }
    }
}

/**
 * Ensures tracks marked as isDownloaded == true are NEVER deleted.
 */
fun filterSafeToDeleteTracks(
    candidates: List<TrackEntity>,
    activeTrackIds: Set<String>
): List<TrackEntity> {
    return candidates.filter { track ->
        track.id !in activeTrackIds && !track.isDownloaded
    }
}

/**
 * Identifies unambiguous local/offline playlists from default_account that can be safely
 * migrated to the active accountKey.
 *
 * Rules:
 * - Target account must not be default_account or blank.
 * - Must belong to "default_account".
 * - Must be an offline local playlist (serverId is null or blank).
 * - Kind must not be FAVORITES (favorites are user-specific and must never bleed across accounts).
 * - Owner must be unassigned/blank or match target user (cannot adopt another user's playlists).
 * - Title must be unique within default_account candidates (duplicate titles are ambiguous).
 * - Title must not conflict with any playlist in activeAccountPlaylists (ambiguous ownership).
 * - Title must not conflict with any playlist in remotePlaylists (e.g. if server already has playlists).
 *
 * When migrated, preserves localKey, track memberships, and download links.
 */
/**
 * Resolves legacy default_account playlists that can be safely assigned to targetAccountKey.
 * To avoid assigning ownerless legacy data by title alone:
 * - Only playlists explicitly owned by targetUsername are eligible for migration.
 * - Ownerless or ambiguous legacy playlists (where owner is blank or does not match targetUsername)
 *   are preserved in default_account for explicit recovery rather than silently reassigned on account switches.
 * When migrated, preserves localKey, track memberships, and download links.
 */
fun findUnambiguousDefaultAccountPlaylistsToMigrate(
    defaultPlaylists: List<PlaylistEntity>,
    activeAccountPlaylists: List<PlaylistEntity>,
    remotePlaylists: List<PlaylistDto> = emptyList(),
    targetAccountKey: String,
    targetUsername: String? = null
): List<PlaylistEntity> {
    if (targetAccountKey == "default_account" || targetAccountKey.isBlank() || targetUsername.isNullOrBlank()) {
        return emptyList()
    }

    val defaultCandidates = defaultPlaylists.filter { candidate ->
        candidate.accountKey == "default_account" &&
        candidate.serverId.isNullOrBlank() &&
        candidate.kind != PlaylistKind.FAVORITES.name &&
        !candidate.owner.isNullOrBlank() &&
        candidate.owner.equals(targetUsername, ignoreCase = true)
    }

    val titleCounts = defaultCandidates.groupingBy { it.title.trim().lowercase() }.eachCount()
    val activeTitles = activeAccountPlaylists.map { it.title.trim().lowercase() }.toSet()
    val remoteTitles = remotePlaylists.map { it.title.trim().lowercase() }.toSet()

    return defaultCandidates.filter { candidate ->
        val key = candidate.title.trim().lowercase()
        val isUniqueInDefault = titleCounts[key] == 1
        val conflictsWithActive = key in activeTitles
        val conflictsWithRemote = key in remoteTitles
        isUniqueInDefault && !conflictsWithActive && !conflictsWithRemote
    }.map { candidate ->
        candidate.copy(
            accountKey = targetAccountKey,
            owner = candidate.owner
        )
    }
}

