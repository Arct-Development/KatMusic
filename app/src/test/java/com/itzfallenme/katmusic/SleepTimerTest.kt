package com.itzfallenme.katmusic

import com.itzfallenme.katmusic.playback.SLEEP_FADE_MS
import com.itzfallenme.katmusic.playback.sleepFadeVolume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {

    @Test
    fun fullVolumeOutsideTheFadeWindow() {
        assertEquals(1f, sleepFadeVolume(SLEEP_FADE_MS + 60_000L), 0f)
        assertEquals(1f, sleepFadeVolume(SLEEP_FADE_MS), 0f)
    }

    @Test
    fun rampsDownInsideTheWindow() {
        val halfway = sleepFadeVolume(SLEEP_FADE_MS / 2)
        assertEquals(0.5f, halfway, 0.001f)
        assertTrue(sleepFadeVolume(SLEEP_FADE_MS / 4) < halfway)
    }

    @Test
    fun staysAudibleUntilItStops() {
        // A long fade that reaches zero early reads as a crashed player.
        assertTrue(sleepFadeVolume(1L) > 0f)
        assertEquals(0.05f, sleepFadeVolume(1L), 0.001f)
    }

    @Test
    fun silenceOnlyAtTheEnd() {
        assertEquals(0f, sleepFadeVolume(0L), 0f)
        assertEquals(0f, sleepFadeVolume(-2_000L), 0f)
    }
}
