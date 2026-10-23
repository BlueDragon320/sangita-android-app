package com.sangita.app.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun testStandardLrcParsing() {
        val lrc = """
            [ti:Test Song]
            [ar:Test Artist]
            [00:05.00]First line of song
            [00:10.50]Second line of song
        """.trimIndent()

        val result = LrcParser.parse("test.mp3", lrc)
        assertTrue(result.isSynced)
        assertTrue(result.hasLyrics)

        // Since first line starts at 5.0s (> 4.0s), an intro note '♪' at 0.0s is prepended
        assertEquals(3, result.lines.size)
        assertEquals("♪", result.lines[0].text)
        assertEquals(0.0, result.lines[0].time, 0.001)
        assertTrue(result.lines[0].isInstrumental)

        assertEquals("First line of song", result.lines[1].text)
        assertEquals(5.0, result.lines[1].time, 0.001)

        assertEquals("Second line of song", result.lines[2].text)
        assertEquals(10.5, result.lines[2].time, 0.001)
    }

    @Test
    fun testInstrumentalGapInsertion() {
        val lrc = """
            [00:02.00]Line one
            [00:20.00]Line two after 18 seconds
        """.trimIndent()

        val result = LrcParser.parse("gap.mp3", lrc)
        // 18s gap >= 12s -> inserts instrumental note at 2.0 + 6.0 = 8.0s
        assertEquals(3, result.lines.size)
        assertEquals("Line one", result.lines[0].text)
        assertEquals("♪", result.lines[1].text)
        assertEquals(8.0, result.lines[1].time, 0.001)
        assertEquals("Line two after 18 seconds", result.lines[2].text)
    }

    @Test
    fun testOffsetHandling() {
        val lrc = """
            [offset:+500]
            [00:01.00]Shifted line
        """.trimIndent()

        val result = LrcParser.parse("offset.mp3", lrc)
        assertEquals(0.5, result.offsetSec, 0.001)
        assertEquals(1.5, result.lines[0].time, 0.001)
    }

    @Test
    fun testBinarySearchWithLookahead() {
        val lrc = """
            [00:01.00]Line A
            [00:05.00]Line B
            [00:10.00]Line C
        """.trimIndent()

        val result = LrcParser.parse("binary.mp3", lrc)
        val lines = result.lines

        // Target time has +150ms lookahead (currentTime + 0.150)
        // At 0.84s + 0.150 = 0.99s < 1.0s -> index -1
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 0.84))

        // At 0.86s + 0.150 = 1.01s >= 1.0s -> Line A index
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 0.86))

        // At 4.86s + 0.150 = 5.01s >= 5.0s -> Line B index
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 4.86))
    }
}
