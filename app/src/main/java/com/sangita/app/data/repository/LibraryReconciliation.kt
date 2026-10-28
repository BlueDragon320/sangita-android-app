package com.sangita.app.data.repository

import com.sangita.app.data.local.entity.PlaylistTrackCrossRef

/** Pending desired states override the server snapshot until the outbox is flushed. */
internal fun reconcileFavorites(remote: Set<String>, pending: Map<String, Boolean>): Set<String> {
    val result = remote.toMutableSet()
    pending.forEach { (path, desired) -> if (desired) result.add(path) else result.remove(path) }
    return result
}

/** Preserve one ordered membership per playlist even when a song occurs in multiple playlists. */
internal fun playlistMemberships(name: String, paths: List<String>): List<PlaylistTrackCrossRef> =
    paths.distinct().mapIndexed { index, path ->
        PlaylistTrackCrossRef(playlistName = name, trackId = path, position = index)
    }
