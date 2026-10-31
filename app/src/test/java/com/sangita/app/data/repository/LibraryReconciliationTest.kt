package com.sangita.app.data.repository

import com.sangita.app.data.local.entity.PlaylistEntity
import com.sangita.app.data.local.entity.TrackEntity
import com.sangita.app.data.remote.dto.PlaylistDto
import com.sangita.app.data.remote.dto.PlaylistTrackDto
import com.sangita.app.domain.model.PlaylistKind
import com.sangita.app.domain.model.PlaylistSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryReconciliationTest {

    @Test
    fun `pending offline favorite changes win over older server snapshot`() {
        val result = reconcileFavorites(setOf("old.mp3", "keep.mp3"), mapOf("old.mp3" to false, "new.mp3" to true))
        assertEquals(setOf("keep.mp3", "new.mp3"), result)
    }

    @Test
    fun `a track can belong to multiple playlists while retaining playlist order`() {
        val first = playlistMemberships("First", listOf("b.mp3", "a.mp3", "b.mp3"))
        val second = playlistMemberships("Second", listOf("a.mp3"))
        assertEquals(listOf("b.mp3", "a.mp3"), first.map { it.trackId })
        assertEquals(listOf(0, 1), first.map { it.position })
        assertEquals("a.mp3", second.single().trackId)
        assertEquals("Second", second.single().playlistName)
    }

    @Test
    fun `reconcilePlaylists matches local playlist by serverId`() {
        val local = PlaylistEntity(
            localKey = "loc-1",
            serverId = "srv-100",
            title = "Old Title",
            description = "Old Desc",
            syncState = PlaylistSyncState.SYNCED.name
        )
        val remote = PlaylistDto(
            id = "srv-100",
            title = "New Title",
            description = "New Desc",
            visibility = "public",
            trackCount = 5
        )

        val plan = reconcilePlaylists(listOf(local), listOf(remote))

        assertEquals(1, plan.toUpsert.size)
        val updated = plan.toUpsert.first()
        assertEquals("loc-1", updated.localKey)
        assertEquals("srv-100", updated.serverId)
        assertEquals("New Title", updated.title)
        assertEquals("New Desc", updated.description)
        assertEquals("public", updated.visibility)
        assertEquals(5, updated.trackCount)
        assertTrue(plan.toDeleteKeys.isEmpty())
        assertEquals("loc-1", plan.remoteIdToLocalKey["srv-100"])
    }

    @Test
    fun `reconcilePlaylists preserves local playlists with pending local edits`() {
        val local = PlaylistEntity(
            localKey = "loc-2",
            serverId = "srv-200",
            title = "Local Edit Title",
            syncState = PlaylistSyncState.PENDING_UPDATE.name
        )
        val remote = PlaylistDto(
            id = "srv-200",
            title = "Server Title"
        )

        val plan = reconcilePlaylists(listOf(local), listOf(remote))

        assertEquals(1, plan.toUpsert.size)
        val preserved = plan.toUpsert.first()
        assertEquals("Local Edit Title", preserved.title)
        assertEquals(PlaylistSyncState.PENDING_UPDATE.name, preserved.syncState)
        assertTrue(plan.toDeleteKeys.isEmpty())
    }

    @Test
    fun `reconcilePlaylists matches by title when serverId is null and title is unambiguous and kind is not FAVORITES`() {
        val local = PlaylistEntity(
            localKey = "loc-3",
            serverId = null,
            title = "Chill Beats",
            kind = PlaylistKind.PERSONAL.name,
            syncState = PlaylistSyncState.SYNCED.name
        )
        val remote = PlaylistDto(
            id = "srv-300",
            title = "Chill Beats",
            trackCount = 10
        )

        val plan = reconcilePlaylists(listOf(local), listOf(remote))

        assertEquals(1, plan.toUpsert.size)
        val matched = plan.toUpsert.first()
        assertEquals("loc-3", matched.localKey)
        assertEquals("srv-300", matched.serverId)
        assertEquals("Chill Beats", matched.title)
        assertEquals("loc-3", plan.remoteIdToLocalKey["srv-300"])
        assertTrue(plan.toDeleteKeys.isEmpty())
    }

    @Test
    fun `reconcilePlaylists does NOT match by title when local title is ambiguous`() {
        val local1 = PlaylistEntity(
            localKey = "loc-4a",
            serverId = null,
            title = "Rock",
            kind = PlaylistKind.PERSONAL.name
        )
        val local2 = PlaylistEntity(
            localKey = "loc-4b",
            serverId = null,
            title = "Rock",
            kind = PlaylistKind.PERSONAL.name
        )
        val remote = PlaylistDto(
            id = "srv-400",
            title = "Rock"
        )

        val plan = reconcilePlaylists(listOf(local1, local2), listOf(remote))

        // Both locals preserved without serverId, remote created as new local key
        val remoteLocalKey = plan.remoteIdToLocalKey["srv-400"]
        assertTrue(remoteLocalKey != "loc-4a" && remoteLocalKey != "loc-4b")
        assertEquals(3, plan.toUpsert.size)
        assertTrue(plan.toDeleteKeys.isEmpty())
    }

    @Test
    fun `reconcilePlaylists does NOT match by title when remote title is ambiguous`() {
        val local = PlaylistEntity(
            localKey = "loc-5",
            serverId = null,
            title = "Jazz",
            kind = PlaylistKind.PERSONAL.name
        )
        val remote1 = PlaylistDto(id = "srv-501", title = "Jazz")
        val remote2 = PlaylistDto(id = "srv-502", title = "Jazz")

        val plan = reconcilePlaylists(listOf(local), listOf(remote1, remote2))

        // Ambiguous remote titles -> local jazz is not matched, both remotes become new
        assertEquals(3, plan.toUpsert.size)
        val localJazz = plan.toUpsert.first { it.localKey == "loc-5" }
        assertEquals(null, localJazz.serverId)
        assertTrue(plan.toDeleteKeys.isEmpty())
    }

    @Test
    fun `reconcilePlaylists does NOT match FAVORITES by title`() {
        val localFav = PlaylistEntity(
            localKey = "fav-local",
            serverId = null,
            title = "Favorites",
            kind = PlaylistKind.FAVORITES.name
        )
        val remotePl = PlaylistDto(
            id = "srv-fav-playlist",
            title = "Favorites"
        )

        val plan = reconcilePlaylists(listOf(localFav), listOf(remotePl))

        // Local favorites preserved untouched, remote playlist created as separate entity
        val favEntity = plan.toUpsert.first { it.localKey == "fav-local" }
        assertEquals(null, favEntity.serverId)
        assertEquals(PlaylistKind.FAVORITES.name, favEntity.kind)

        val remoteLocalKey = plan.remoteIdToLocalKey["srv-fav-playlist"]
        assertTrue(remoteLocalKey != "fav-local")
        assertTrue(plan.toDeleteKeys.isEmpty())
    }

    @Test
    fun `reconcilePlaylists preserves local offline playlists without serverId`() {
        val localOffline = PlaylistEntity(
            localKey = "offline-key",
            serverId = null,
            title = "Airplane Mode Mix",
            syncState = PlaylistSyncState.PENDING_CREATE.name
        )

        val plan = reconcilePlaylists(listOf(localOffline), emptyList())

        assertEquals(1, plan.toUpsert.size)
        assertEquals("offline-key", plan.toUpsert.first().localKey)
        assertTrue(plan.toDeleteKeys.isEmpty())
    }

    @Test
    fun `reconcilePlaylists marks previously synced server playlists for deletion when removed from server`() {
        val localSynced = PlaylistEntity(
            localKey = "loc-old",
            serverId = "srv-deleted",
            title = "Deleted on Server",
            syncState = PlaylistSyncState.SYNCED.name
        )

        val plan = reconcilePlaylists(listOf(localSynced), emptyList())

        assertTrue(plan.toUpsert.isEmpty())
        assertEquals(listOf("loc-old"), plan.toDeleteKeys)
    }

    @Test
    fun `reconcilePlaylistTracks preserves unavailable tracks for owner with isAvailable false`() {
        val tracks = listOf(
            PlaylistTrackDto(trackPath = "song1.mp3", title = "Song 1", isAvailable = true, position = 0),
            PlaylistTrackDto(trackPath = "song2.mp3", title = "Song 2", isAvailable = false, position = 1),
            PlaylistTrackDto(trackPath = "song3.mp3", title = "Song 3", isAvailable = true, position = 2)
        )

        val refs = reconcilePlaylistTracks("pl-key", tracks, isOwner = true)

        assertEquals(3, refs.size)
        assertEquals(listOf("song1.mp3", "song2.mp3", "song3.mp3"), refs.map { it.trackId })
        assertTrue(refs[0].isAvailable)
        assertFalse(refs[1].isAvailable)
        assertTrue(refs[2].isAvailable)
        assertEquals(listOf(0, 1, 2), refs.map { it.position })
    }

    @Test
    fun `reconcilePlaylistTracks filters unavailable tracks for non-owner`() {
        val tracks = listOf(
            PlaylistTrackDto(trackPath = "song1.mp3", title = "Song 1", isAvailable = true, position = 0),
            PlaylistTrackDto(trackPath = "song2.mp3", title = "Song 2", isAvailable = false, position = 1),
            PlaylistTrackDto(trackPath = "song3.mp3", title = "Song 3", isAvailable = true, position = 2)
        )

        val refs = reconcilePlaylistTracks("pl-key", tracks, isOwner = false)

        assertEquals(2, refs.size)
        assertEquals(listOf("song1.mp3", "song3.mp3"), refs.map { it.trackId })
        assertTrue(refs.all { it.isAvailable })
    }

    @Test
    fun `filterSafeToDeleteTracks never deletes tracks marked as isDownloaded true`() {
        val downloadedTrack = TrackEntity(
            id = "downloaded.mp3",
            playlistName = "Rock",
            title = "Downloaded Song",
            isDownloaded = true
        )
        val undownloadedTrack = TrackEntity(
            id = "stream_only.mp3",
            playlistName = "Rock",
            title = "Stream Only Song",
            isDownloaded = false
        )

        val safe = filterSafeToDeleteTracks(
            candidates = listOf(downloadedTrack, undownloadedTrack),
            activeTrackIds = emptySet()
        )

        assertEquals(1, safe.size)
        assertEquals("stream_only.mp3", safe.first().id)
        assertFalse(safe.any { it.isDownloaded })
    }
}
