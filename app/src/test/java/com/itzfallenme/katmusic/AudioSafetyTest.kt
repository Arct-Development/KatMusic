package com.itzfallenme.katmusic

import com.itzfallenme.katmusic.playback.stereoToMono
import com.itzfallenme.katmusic.playback.volumeCapIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSafetyTest {

    @Test
    fun `eighty percent of a fifteen step stream is twelve`() {
        assertEquals(12, volumeCapIndex(80, 15))
    }

    @Test
    fun `full percentage keeps the device maximum`() {
        assertEquals(15, volumeCapIndex(100, 15))
        assertEquals(3, volumeCapIndex(100, 3))
    }

    @Test
    fun `percentages are clamped into a usable range`() {
        assertEquals(0, volumeCapIndex(40, 0))
        // 1% of a 15-step stream rounds to zero steps, which would mute; keep one step.
        assertEquals(1, volumeCapIndex(1, 15))
    }

    @Test
    fun `mono downmix averages both channels`() {
        assertEquals(0.toShort(), stereoToMono(1000, -1000))
        assertEquals(1000.toShort(), stereoToMono(1000, 1000))
        assertEquals(0.5f, stereoToMono(1f, 0f), 0.0001f)
    }

    @Test
    fun `cap never exceeds the maximum step`() {
        (1..100).forEach { percent ->
            val cap = volumeCapIndex(percent, 10)
            assertEquals(true, cap in 0..10)
        }
    }
}
