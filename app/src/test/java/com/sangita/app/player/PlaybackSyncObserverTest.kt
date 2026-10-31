package com.sangita.app.player

import com.sangita.app.sync.RemotePlaybackState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSyncObserverTest {
    @Test fun `opening phone after browser registered replays the browser song`() = runBlocking {
        val browser = RemotePlaybackState(activeDeviceId = "web", trackId = "browser-song", originDeviceId = "web")
        val states = MutableStateFlow<RemotePlaybackState?>(browser)
        val applied = CompletableDeferred<RemotePlaybackState>()
        val job = launch { observePlaybackSync(states, "phone") { applied.complete(it) } }
        try {
            assertEquals("browser-song", withTimeout(2000) { applied.await() }.trackId)
        } finally { job.cancelAndJoin() }
    }

    @Test fun `a newer song cancels an older database lookup`() = runBlocking {
        val states = MutableStateFlow<RemotePlaybackState?>(
            RemotePlaybackState(trackId = "old", originDeviceId = "web"))
        val started = CompletableDeferred<Unit>()
        val applied = CompletableDeferred<String?>()
        var cancelled = false
        val job = launch {
            observePlaybackSync(states, "phone") { state ->
                if (state.trackId == "old") {
                    started.complete(Unit)
                    try { awaitCancellation() } finally { cancelled = true }
                } else applied.complete(state.trackId)
            }
        }
        try {
            withTimeout(2000) { started.await() }
            states.value = RemotePlaybackState(trackId = "new", originDeviceId = "web")
            assertEquals("new", withTimeout(2000) { applied.await() })
            assertTrue(cancelled)
        } finally { job.cancelAndJoin() }
    }

    @Test fun `claiming local playback cancels a stale browser lookup`() = runBlocking {
        val states = MutableStateFlow<RemotePlaybackState?>(RemotePlaybackState(trackId = "old", originDeviceId = "web"))
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val job = launch {
            observePlaybackSync(states, "phone") {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
        }
        try {
            withTimeout(2000) { started.await() }
            states.value = RemotePlaybackState(trackId = "local", originDeviceId = "phone")
            withTimeout(2000) { cancelled.await() }
        } finally { job.cancelAndJoin() }
    }

    @Test fun `phone initiated transfer applies the server song despite own origin`() = runBlocking {
        val states = MutableStateFlow<RemotePlaybackState?>(null)
        val applied = CompletableDeferred<RemotePlaybackState>()
        val job = launch { observePlaybackSync(states, "phone") { applied.complete(it) } }
        try {
            states.value = RemotePlaybackState(activeDeviceId = "phone", trackId = "browser-song",
                positionMs = 42000L, originDeviceId = "phone", isTransferHandoff = true)
            val state = withTimeout(2000) { applied.await() }
            assertEquals("browser-song", state.trackId)
            assertEquals(42000L, state.positionMs)
        } finally { job.cancelAndJoin() }
    }
}
