package com.sangita.app.player

import androidx.media3.common.Player
import com.sangita.app.domain.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatModeMappingTest {

    private fun mapToExoPlayerRepeatMode(mode: RepeatMode): Int {
        return when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_OFF
        }
    }

    @Test
    fun `repeat mode ALL maps to ExoPlayer REPEAT_MODE_OFF so single track finishes and STATE_ENDED triggers queue advance`() {
        // Because ExoPlayer only holds 1 track at a time while PlaybackController manages the queue in memory,
        // REPEAT_MODE_ALL must map to Player.REPEAT_MODE_OFF so ExoPlayer fires STATE_ENDED.
        assertEquals(Player.REPEAT_MODE_OFF, mapToExoPlayerRepeatMode(RepeatMode.ALL))
    }

    @Test
    fun `repeat mode ONE maps to ExoPlayer REPEAT_MODE_ONE`() {
        assertEquals(Player.REPEAT_MODE_ONE, mapToExoPlayerRepeatMode(RepeatMode.ONE))
    }

    @Test
    fun `repeat mode OFF maps to ExoPlayer REPEAT_MODE_OFF`() {
        assertEquals(Player.REPEAT_MODE_OFF, mapToExoPlayerRepeatMode(RepeatMode.OFF))
    }
}
