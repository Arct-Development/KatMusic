package com.itzfallenme.katmusic

import com.itzfallenme.katmusic.data.Track
import com.itzfallenme.katmusic.playback.QueueStats
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueStatsTest {

    private fun track(id: String, durationMs: Long) =
        Track(id = id, title = "Track $id", durationMs = durationMs)

    @Test
    fun emptyQueue() {
        assertEquals("0 songs", QueueStats.format(emptyList()))
    }

    @Test
    fun singleUnknownDuration() {
        assertEquals("1 song", QueueStats.format(listOf(track("a", 0))))
    }

    @Test
    fun minutesOnly() {
        val tracks = listOf(track("a", 95_000), track("b", 60_000))
        assertEquals("2 songs · 2m", QueueStats.format(tracks))
    }

    @Test
    fun hoursAndMinutes() {
        val tracks = List(58) { track("t$it", 4L * 60_000L) }
        assertEquals("58 songs · 3h 52m", QueueStats.format(tracks))
    }

    @Test
    fun marksApproximateTotals() {
        val tracks = listOf(track("a", 180_000), track("b", 0))
        assertEquals("2 songs · 3m+", QueueStats.format(tracks))
    }

    @Test
    fun shortTrackRoundsUpToOneMinute() {
        assertEquals("1 song · 1m", QueueStats.format(listOf(track("a", 12_000))))
    }
}
