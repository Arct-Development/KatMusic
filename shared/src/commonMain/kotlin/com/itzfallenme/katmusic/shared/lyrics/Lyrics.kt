package com.itzfallenme.katmusic.shared.lyrics

import com.itzfallenme.katmusic.shared.models.Track

/** One lyric line. [atMs] is -1 for unsynced lyrics. */
data class LyricsLine(val atMs: Long, val text: String)

/** A track's lyrics, synced or plain. */
data class Lyrics(
    val lines: List<LyricsLine>,
    val plain: String? = null,
    val isSynced: Boolean = lines.any { it.atMs >= 0 },
    val isInstrumental: Boolean = false,
    val source: String? = null,
) {
    val isEmpty: Boolean get() = lines.none { it.text.isNotBlank() }
}

sealed interface LyricsResult {
    data class Found(val lyrics: Lyrics) : LyricsResult
    data object NotFound : LyricsResult
    data class Failed(val message: String) : LyricsResult
}

/**
 * Lyrics for a track, however the platform obtains them: Android's provider
 * chain (Apple Music, LRCLIB, YouTube captions, local .lrc files...) or the
 * desktop LRCLIB client. The shared lyrics screen only sees this contract.
 */
interface LyricsProvider {
    suspend fun lyricsFor(track: Track): LyricsResult
}

/**
 * LRC parsing shared by every platform. Handles the shapes real files use:
 * several timestamps on one line, `[mm:ss]` and `[mm:ss.xxx]`, metadata tags
 * (`[ar:]`, `[offset:]`), and plain text fallback.
 */
object LrcParser {

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val META_TAG = Regex("""^\[[a-zA-Z#]+:.*]$""")

    fun parse(lrc: String): List<LyricsLine> {
        val offset = offsetMillis(lrc)
        val out = ArrayList<LyricsLine>()
        for (rawLine in lrc.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || META_TAG.matches(line)) continue
            val matches = TIME_TAG.findAll(line).toList()
            if (matches.isEmpty()) {
                val text = line.trim()
                if (text.isNotEmpty()) out += LyricsLine(-1L, text)
                continue
            }
            val text = line.substring(matches.last().range.last + 1).trim()
            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                val fraction = match.groupValues[3]
                val fractionMs = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100L
                    2 -> fraction.toLong() * 10L
                    else -> fraction.take(3).toLong()
                }
                out += LyricsLine(minutes * 60_000L + seconds * 1000L + fractionMs - offset, text)
            }
        }
        return out.sortedBy { if (it.atMs < 0) Long.MAX_VALUE else it.atMs }
    }

    /** Plain (unsynced) lyrics as lines that never highlight. */
    fun fromPlainText(plain: String): List<LyricsLine> =
        plain.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { LyricsLine(-1L, it) }
            .toList()

    /**
     * Index of the line that is playing at [positionMs], or -1 before the first
     * timestamp. Unsynced lyrics always answer -1 so nothing highlights.
     */
    fun currentLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
        if (lines.isEmpty() || lines.first().atMs < 0) return -1
        var low = 0
        var high = lines.lastIndex
        var answer = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].atMs <= positionMs) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }

    private fun offsetMillis(lrc: String): Long =
        Regex("""\[offset:\s*([+-]?\d+)]""").find(lrc)
            ?.groupValues?.get(1)?.toLongOrNull()
            ?: 0L
}
