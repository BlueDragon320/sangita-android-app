package com.sangita.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryReconciliationTest {
    @Test fun `pending offline favorite changes win over older server snapshot`() {
        val result = reconcileFavorites(setOf("old.mp3", "keep.mp3"), mapOf("old.mp3" to false, "new.mp3" to true))
        assertEquals(setOf("keep.mp3", "new.mp3"), result)
    }

    @Test fun `a track can belong to multiple playlists while retaining playlist order`() {
        val first = playlistMemberships("First", listOf("b.mp3", "a.mp3", "b.mp3"))
        val second = playlistMemberships("Second", listOf("a.mp3"))
        assertEquals(listOf("b.mp3", "a.mp3"), first.map { it.trackId })
        assertEquals(listOf(0, 1), first.map { it.position })
        assertEquals("a.mp3", second.single().trackId)
        assertEquals("Second", second.single().playlistName)
    }
}
