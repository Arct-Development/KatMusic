package com.itzfallenme.katmusic.desktop.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Offline tests for the extraction result selection: which itag becomes the
 * playable URL, and that video/muxed/manifest entries are never chosen.
 */
class NewPipeStreamResolverTest {

    @Test
    fun `prefers opus over aac and ignores video itags`() {
        val streams = listOf(
            137 to "https://example.com/video-1080.mp4",
            251 to "https://example.com/audio-opus-251",
            140 to "https://example.com/audio-aac-140",
            96 to "https://example.com/manifest.m3u8",
        )
        assertEquals("https://example.com/audio-opus-251", pickAudioUrl(streams))
    }

    @Test
    fun `highest audio itag wins when several are present`() {
        val streams = listOf(
            250 to "https://example.com/opus-250",
            251 to "https://example.com/opus-251",
            141 to "https://example.com/aac-141",
        )
        assertEquals("https://example.com/opus-251", pickAudioUrl(streams))
    }

    @Test
    fun `returns null when only video or manifest itags exist`() {
        assertNull(pickAudioUrl(listOf(137 to "https://example.com/v.mp4", 96 to "https://example.com/m.m3u8")))
        assertNull(pickAudioUrl(emptyList()))
    }

    @Test
    fun `blank audio urls are rejected`() {
        assertNull(pickAudioUrl(listOf(251 to "   ")))
    }
}
