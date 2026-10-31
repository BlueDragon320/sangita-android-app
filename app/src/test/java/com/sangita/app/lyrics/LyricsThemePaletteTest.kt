package com.sangita.app.lyrics

import androidx.compose.ui.graphics.Color
import com.sangita.app.domain.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsThemePaletteTest {

    @Test
    fun testWhitePaletteHasDarkTextToPreventWhiteOnWhite() {
        val whitePalette = LyricsThemePalette.WHITE
        assertTrue(whitePalette.isLight)
        assertEquals(Color(0xFFFFFFFF), whitePalette.bgColor)
        assertNotEquals(Color.White, whitePalette.activeTextColor)
        assertNotEquals(Color.White, whitePalette.inactiveTextColor)
        assertEquals(Color(0xFF0F172A), whitePalette.activeTextColor)
        assertEquals(Color(0xFF64748B), whitePalette.inactiveTextColor)
    }

    @Test
    fun testAllPalettesHaveDistinctTextColorsFromBackground() {
        for (palette in LyricsThemePalette.entries) {
            assertNotEquals(
                "Active text in ${palette.name} must differ from background",
                palette.bgColor,
                palette.activeTextColor
            )
            assertNotEquals(
                "Inactive text in ${palette.name} must differ from background",
                palette.bgColor,
                palette.inactiveTextColor
            )
        }
    }

    @Test
    fun testPaletteFallbackDefaultsToBlack() {
        val unknown = LyricsThemePalette.fromId("unknown_theme_123")
        assertEquals(LyricsThemePalette.BLACK, unknown)
    }

    @Test
    fun testRepeatModeDescriptions() {
        fun getRepeatDesc(mode: RepeatMode): String = when (mode) {
            RepeatMode.ONE -> "Repeat one"
            RepeatMode.ALL -> "Repeat all"
            RepeatMode.OFF -> "Repeat off"
        }

        assertEquals("Repeat off", getRepeatDesc(RepeatMode.OFF))
        assertEquals("Repeat all", getRepeatDesc(RepeatMode.ALL))
        assertEquals("Repeat one", getRepeatDesc(RepeatMode.ONE))
    }

    @Test
    fun testOutputDeviceResolution() {
        fun resolveReceiverName(isActiveDevice: Boolean, activeDeviceName: String?): String {
            return if (!isActiveDevice) {
                activeDeviceName ?: "Web Player"
            } else {
                "this phone"
            }
        }

        assertEquals("this phone", resolveReceiverName(true, null))
        assertEquals("this phone", resolveReceiverName(true, "Other Device"))
        assertEquals("Web Player", resolveReceiverName(false, null))
        assertEquals("Living Room TV", resolveReceiverName(false, "Living Room TV"))
    }
}
