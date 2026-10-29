package com.itzfallenme.katmusic

import com.itzfallenme.katmusic.data.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamQualityTest {

    @Test
    fun `max accepts any bitrate`() {
        assertTrue(StreamQuality.MAX.allows(0))
        assertTrue(StreamQuality.MAX.allows(320_000))
    }

    @Test
    fun `high caps at two hundred fifty six kilobits`() {
        assertTrue(StreamQuality.HIGH.allows(256_000))
        assertFalse(StreamQuality.HIGH.allows(256_001))
    }

    @Test
    fun `data saver caps at one hundred twenty eight kilobits`() {
        assertTrue(StreamQuality.SAVER.allows(128_000))
        assertFalse(StreamQuality.SAVER.allows(160_000))
    }

    @Test
    fun `unknown bitrates are always allowed`() {
        assertTrue(StreamQuality.SAVER.allows(0))
    }

    @Test
    fun `unknown ids fall back to high`() {
        assertEquals(StreamQuality.HIGH, StreamQuality.fromId("lossless"))
        assertEquals(StreamQuality.HIGH, StreamQuality.fromId(null))
        assertEquals(StreamQuality.SAVER, StreamQuality.fromId("saver"))
    }
}
