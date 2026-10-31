package com.sangita.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPositionReportTest {
    @Test fun `advancing playback clears a stale buffering flag`() {
        val waiting = RemotePlaybackState(trackId = "song", positionMs = 1000L,
            isPlaying = false, isBuffering = true, isTransferHandoff = true)
        val playing = waiting.withPositionReport(2000L, 10000L, 10L, "web")
        assertTrue(playing.isPlaying)
        assertFalse(playing.isBuffering)
        assertFalse(playing.isTransferHandoff)
        assertEquals("web", playing.originDeviceId)
        assertEquals("song", playing.trackId)
    }

    @Test fun `stalled playback keeps the real buffering indicator`() {
        val waiting = RemotePlaybackState(positionMs = 1000L, isBuffering = true)
        val stalled = waiting.withPositionReport(1000L, 0L, 10L, "web")
        assertTrue(stalled.isBuffering)
        assertFalse(stalled.isPlaying)
    }

    @Test fun `a backward seek does not infer playback and missing duration preserves known duration`() {
        val waiting = RemotePlaybackState(positionMs = 5000L, durationMs = 10000L, isBuffering = true)
        val seeked = waiting.withPositionReport(1000L, 0L, 10L, "web")
        assertFalse(seeked.isPlaying)
        assertTrue(seeked.isBuffering)
        assertEquals(10000L, seeked.durationMs)
        assertEquals(1000L, seeked.positionMs)
    }
    @Test fun `a full progress snapshot replaces old track metadata identity and duration atomically`() {
        val old = RemotePlaybackState(trackId = "First/old.mp3", durationMs = 300000L,
            activeDeviceId = "phone", positionMs = 250000L)
        val next = old.withPositionReport(5000L, 180000L, 20L, "web",
            trackId = "Second/new.mp3", activeDeviceId = "web",
            reportedPlaying = true, reportedBuffering = false)
        assertEquals("Second/new.mp3", next.trackId)
        assertEquals("web", next.activeDeviceId)
        assertEquals(5000L, next.positionMs)
        assertEquals(180000L, next.durationMs)
        assertTrue(next.isPlaying)
        assertFalse(next.isBuffering)
    }

    @Test fun `the new song never inherits the old song duration`() {
        val old = RemotePlaybackState(trackId = "old", durationMs = 300000L)
        val next = old.withPositionReport(0L, 0L, 20L, "web", trackId = "new")
        assertEquals("new", next.trackId)
        assertEquals(0L, next.durationMs)
    }

    @Test fun `authoritative paused state wins over a position advance`() {
        val playing = RemotePlaybackState(trackId = "song", positionMs = 1000L, isPlaying = true)
        val paused = playing.withPositionReport(2000L, 10000L, 20L, "web",
            reportedPlaying = false, reportedBuffering = false)
        assertFalse(paused.isPlaying)
        assertFalse(paused.isBuffering)
    }

    @Test fun `a fresh position snapshot initializes remote playback state directly when null`() {
        val state = RemotePlaybackState(
            activeDeviceId = "web",
            trackId = "Song.mp3",
            positionMs = 15000L,
            durationMs = 210000L,
            isPlaying = true,
            isBuffering = false,
            originDeviceId = "web",
            updatedAt = 1000L
        )
        assertEquals("web", state.activeDeviceId)
        assertEquals("Song.mp3", state.trackId)
        assertEquals(15000L, state.positionMs)
        assertEquals(210000L, state.durationMs)
        assertTrue(state.isPlaying)
        assertFalse(state.isBuffering)
        assertEquals("web", state.originDeviceId)
        assertEquals(1000L, state.updatedAt)
    }
}
