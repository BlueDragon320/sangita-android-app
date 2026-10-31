package com.sangita.app.sync

internal fun RemotePlaybackState.withPositionReport(
    positionMs: Long,
    durationMs: Long,
    updatedAt: Long,
    originDeviceId: String?,
    trackId: String? = null,
    activeDeviceId: String? = null,
    reportedPlaying: Boolean? = null,
    reportedBuffering: Boolean? = null
): RemotePlaybackState {
    val trackChanged = !trackId.isNullOrBlank() && trackId != this.trackId
    val progressing = !trackChanged && positionMs > this.positionMs
    return copy(
        trackId = trackId?.takeIf { it.isNotBlank() } ?: this.trackId,
        activeDeviceId = activeDeviceId ?: this.activeDeviceId,
        positionMs = positionMs,
        durationMs = if (durationMs > 0L || trackChanged) durationMs else this.durationMs,
        updatedAt = updatedAt,
        originDeviceId = originDeviceId ?: this.originDeviceId,
        isPlaying = reportedPlaying ?: if (progressing) true else isPlaying,
        isBuffering = reportedBuffering ?: if (progressing) false else isBuffering,
        isTransferHandoff = false
    )
}
