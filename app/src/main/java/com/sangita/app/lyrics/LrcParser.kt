package com.sangita.app.lyrics

import com.sangita.app.domain.model.LyricLine
import com.sangita.app.domain.model.LyricsData

object LrcParser {

    private val timeRegex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{1,3}))?]""")
    private val offsetRegex = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)
    private val metaHeaderRegex = Regex("""^\[(ti|ar|al|by|offset|length|re|ve):""", RegexOption.IGNORE_CASE)

    fun parse(trackId: String, rawText: String?): LyricsData {
        if (rawText.isNullOrBlank()) {
            return LyricsData(trackId = trackId, hasLyrics = false)
        }

        val offsetMatch = offsetRegex.find(rawText)
        val fileOffsetSec = if (offsetMatch != null) {
            (offsetMatch.groupValues[1].toIntOrNull() ?: 0) / 1000.0
        } else {
            0.0
        }

        val parsedLines = mutableListOf<LyricLine>()
        var hasTimestamp = false

        rawText.lines().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || metaHeaderRegex.containsMatchIn(trimmed)) {
                return@forEach
            }

            val matches = timeRegex.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                hasTimestamp = true
                val textOnly = trimmed.replace(timeRegex, "").trim()
                matches.forEach { match ->
                    val min = match.groupValues[1].toIntOrNull() ?: 0
                    val sec = match.groupValues[2].toIntOrNull() ?: 0
                    val msStr = match.groupValues.getOrNull(3)?.ifEmpty { "0" } ?: "0"
                    val ms = "0.$msStr".toDoubleOrNull() ?: 0.0

                    val timeInSec = maxOf(0.0, (min * 60 + sec + ms) + fileOffsetSec)
                    parsedLines.add(LyricLine(time = timeInSec, text = textOnly, isInstrumental = false))
                }
            } else if (!hasTimestamp && !trimmed.startsWith("[")) {
                parsedLines.add(LyricLine(time = -1.0, text = trimmed, isInstrumental = false))
            }
        }

        if (hasTimestamp) {
            parsedLines.sortBy { it.time }

            // 1. Prepend intro note if first line starts after 4 seconds
            if (parsedLines.isNotEmpty() && parsedLines[0].time > 4.0) {
                parsedLines.add(0, LyricLine(time = 0.0, text = "♪", isInstrumental = true))
            }

            // 2. Insert instrumental note for long gaps (>= 12s)
            val withInstrumentals = mutableListOf<LyricLine>()
            for (i in 0 until parsedLines.size) {
                withInstrumentals.add(parsedLines[i])
                if (i < parsedLines.size - 1) {
                    val gap = parsedLines[i + 1].time - parsedLines[i].time
                    if (gap >= 12.0) {
                        withInstrumentals.add(
                            LyricLine(
                                time = parsedLines[i].time + 6.0,
                                text = "♪",
                                isInstrumental = true
                            )
                        )
                    }
                }
            }

            return LyricsData(
                trackId = trackId,
                lines = withInstrumentals,
                isSynced = true,
                offsetSec = fileOffsetSec,
                hasLyrics = true,
                rawLrc = rawText
            )
        }

        return LyricsData(
            trackId = trackId,
            lines = parsedLines,
            isSynced = false,
            offsetSec = 0.0,
            hasLyrics = parsedLines.isNotEmpty(),
            rawLrc = rawText
        )
    }

    /**
     * Binary search with 150ms lookahead (currentTimeSec + 0.150)
     * Active Line Lookup: findActiveLineIndex(lines, currentTime + 0.15)
     */
    fun findActiveLineIndex(lines: List<LyricLine>, currentTimeSec: Double, lookaheadSec: Double = 0.150): Int {
        if (lines.isEmpty()) return -1
        val targetTime = currentTimeSec + lookaheadSec
        if (targetTime < lines[0].time) return -1

        var low = 0
        var high = lines.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].time <= targetTime) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
