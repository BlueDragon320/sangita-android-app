package com.sangita.app.player

import com.sangita.app.sync.RemotePlaybackState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

internal suspend fun observePlaybackSync(
    states: StateFlow<RemotePlaybackState?>,
    myDeviceId: String,
    applyState: suspend (RemotePlaybackState) -> Unit
) {
    states.collectLatest { state ->
        // Even a local update cancels an older suspended remote lookup.
        if (state != null && (state.originDeviceId != myDeviceId || state.isTransferHandoff)) {
            applyState(state)
        }
    }
}
