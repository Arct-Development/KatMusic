package com.itzfallenme.katmusic.shared.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LrcParserTest {

    @Test
    fun `parses timestamps and text`() {
        val lines = LrcParser.parse(
            """
            [00:12.34] First line
            [00:15.00] Second line
            """.trimIndent(),
        )
        assertEquals(2, lines.size)
        assertEquals(12_340L, lines[0].atMs)
        assertEquals("First line", lines[0].text)
        assertEquals(15_000L, lines[1].atMs)
    }

    @Test
    fun `handles several timestamps on one line`() {
        val lines = LrcParser.parse("[00:10.00][00:40.00]Chorus")
        assertEquals(listOf(10_000L, 40_000L), lines.map { it.atMs })
        assertEquals(listOf("Chorus", "Chorus"), lines.map { it.text })
    }

    @Test
    fun `skips metadata tags`() {
        val lines = LrcParser.parse("[ar:Björk]\n[ti:Jóga]\n[00:01.00]Text")
        assertEquals(1, lines.size)
        assertEquals("Text", lines.single().text)
    }

    @Test
    fun `applies the offset tag`() {
        val lines = LrcParser.parse("[offset:500]\n[00:10.00]Shifted")
        assertEquals(9_500L, lines.single().atMs)
    }

    @Test
    fun `parses two and three digit fractions`() {
        val lines = LrcParser.parse("[00:01.5]a\n[00:02.25]b\n[00:03.125]c")
        assertEquals(listOf(1_500L, 2_250L, 3_125L), lines.map { it.atMs })
    }

    @Test
    fun `plain text becomes unsynced lines`() {
        val lines = LrcParser.fromPlainText("one\n\ntwo\n")
        assertEquals(listOf("one", "two"), lines.map { it.text })
        assertEquals(true, lines.all { it.atMs < 0 })
    }

    @Test
    fun `current line follows playback position`() {
        val lines = LrcParser.parse("[00:00.00]a\n[00:10.00]b\n[00:20.00]c")
        assertEquals(-1, LrcParser.currentLineIndex(lines, -5))
        assertEquals(0, LrcParser.currentLineIndex(lines, 0))
        assertEquals(0, LrcParser.currentLineIndex(lines, 9_999))
        assertEquals(1, LrcParser.currentLineIndex(lines, 10_000))
        assertEquals(2, LrcParser.currentLineIndex(lines, 99_000))
    }

    @Test
    fun `unsynced lyrics never highlight`() {
        val lines = LrcParser.fromPlainText("one\ntwo")
        assertEquals(-1, LrcParser.currentLineIndex(lines, 5_000))
    }

    @Test
    fun `lines are sorted by time`() {
        val lines = LrcParser.parse("[00:20.00]later\n[00:05.00]earlier")
        assertEquals(listOf("earlier", "later"), lines.map { it.text })
    }
}
