package com.itzfallenme.katmusic

import com.itzfallenme.katmusic.data.LyricLine
import com.itzfallenme.katmusic.data.LyricSyllable
import com.itzfallenme.katmusic.ui.player.activeLineIndex
import com.itzfallenme.katmusic.ui.player.resolveSyllableDisplayTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lyric timing helpers the karaoke surface depends on. */
class KaraokeLyricsTest {

    private val lines = listOf(
        LyricLine(timeMs = 0, text = "intro"),
        LyricLine(timeMs = 4_200, text = "first"),
        LyricLine(timeMs = 9_500, text = "second"),
        LyricLine(timeMs = 15_000, text = "third"),
    )

    @Test
    fun `active line is the last one whose timestamp has passed`() {
        assertEquals(-1, activeLineIndex(lines, -1))
        assertEquals(0, activeLineIndex(lines, 0))
        assertEquals(0, activeLineIndex(lines, 4_199))
        assertEquals(1, activeLineIndex(lines, 4_200))
        assertEquals(2, activeLineIndex(lines, 14_999))
        assertEquals(3, activeLineIndex(lines, 60_000))
    }

    @Test
    fun `empty lyric list has no active line`() {
        assertEquals(-1, activeLineIndex(emptyList(), 10_000))
    }

    @Test
    fun `provider tokens get their separator spaces back from the line text`() {
        val line = LyricLine(
            timeMs = 0,
            text = "hold on tight",
            syllables = listOf(
                LyricSyllable(timeMs = 0, durationMs = 300, text = "hold"),
                LyricSyllable(timeMs = 300, durationMs = 200, text = "on"),
                LyricSyllable(timeMs = 500, durationMs = 600, text = "tight"),
            )
        )
        val display = resolveSyllableDisplayTexts(line)
        assertEquals(listOf("hold ", "on ", "tight"), display)
        assertEquals(line.text, display.joinToString(""))
    }

    @Test
    fun `tokens that already carry spaces are left untouched`() {
        val line = LyricLine(
            timeMs = 0,
            text = "we are",
            syllables = listOf(
                LyricSyllable(timeMs = 0, durationMs = 200, text = "we "),
                LyricSyllable(timeMs = 200, durationMs = 300, text = "are")
            )
        )
        val display = resolveSyllableDisplayTexts(line)
        assertEquals("we are", display.joinToString(""))
    }

    @Test
    fun `lines without syllables produce no display tokens`() {
        assertTrue(resolveSyllableDisplayTexts(LyricLine(timeMs = 0, text = "plain")).isEmpty())
    }
}
