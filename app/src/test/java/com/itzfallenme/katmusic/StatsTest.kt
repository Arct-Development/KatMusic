package com.itzfallenme.katmusic

import com.itzfallenme.katmusic.data.HistoryEntry
import com.itzfallenme.katmusic.data.StatsPeriod
import com.itzfallenme.katmusic.data.Track
import com.itzfallenme.katmusic.data.buildStats
import com.itzfallenme.katmusic.data.startMillis
import com.itzfallenme.katmusic.data.weekdayIndex
import com.itzfallenme.katmusic.ui.screens.searchSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {

    private val now = 1_800_000_000_000L

    private fun entry(
        title: String,
        artist: String = "Artist",
        album: String? = null,
        at: Long,
        listenedMs: Long = 180_000L
    ) = HistoryEntry(
        track = Track(id = title, title = title, artist = artist, album = album),
        playedAt = at,
        listenedMs = listenedMs
    )

    @Test
    fun `today start is within the last day`() {
        val start = StatsPeriod.TODAY.startMillis(now)
        assertTrue(start <= now)
        assertTrue(now - start < 24 * 60 * 60 * 1000L)
    }

    @Test
    fun `all time has no lower bound`() {
        assertEquals(0L, StatsPeriod.ALL.startMillis(now))
    }

    @Test
    fun `period filtering keeps only plays inside the window`() {
        val start = StatsPeriod.TODAY.startMillis(now)
        val history = listOf(
            entry("inside", at = start + 1_000),
            entry("before", at = start - 1_000),
            entry("old", at = start - 3 * 24 * 60 * 60 * 1000L)
        )
        val stats = buildStats(history, StatsPeriod.TODAY, now)
        assertEquals(1, stats.totalPlays)
        assertEquals(listOf("inside" to 1), stats.topTracks)
    }

    @Test
    fun `all time keeps every play`() {
        val history = listOf(
            entry("a", at = now - 1_000),
            entry("b", at = now - 90L * 24 * 60 * 60 * 1000L)
        )
        val stats = buildStats(history, StatsPeriod.ALL, now)
        assertEquals(2, stats.totalPlays)
        assertEquals(6, stats.totalMinutes)
    }

    @Test
    fun `top albums group blank album titles under Singles`() {
        val start = StatsPeriod.TODAY.startMillis(now)
        val history = listOf(
            entry("one", album = "Blue", at = start + 1_000),
            entry("two", album = "Blue", at = start + 2_000),
            entry("three", album = null, at = start + 3_000)
        )
        val stats = buildStats(history, StatsPeriod.TODAY, now)
        assertEquals(listOf("Blue" to 2, "Singles" to 1), stats.topAlbums)
    }

    @Test
    fun `weekday histogram adds up to the number of plays`() {
        val start = StatsPeriod.ALL.startMillis(now)
        val history = (1..5).map { entry("t$it", at = start + it * 86_400_000L) }
        val stats = buildStats(history, StatsPeriod.ALL, now)
        assertEquals(5, stats.byWeekday.sum())
        assertEquals(7, stats.byWeekday.size)
        val expectedIndex = weekdayIndex(start + 86_400_000L)
        assertTrue(stats.byWeekday[expectedIndex] >= 1)
    }

    @Test
    fun `previous window minutes are reported for comparison`() {
        val start = StatsPeriod.TODAY.startMillis(now)
        val window = now - start
        val history = listOf(
            entry("current", at = start + 1_000, listenedMs = 120_000),
            entry("previous", at = start - window / 2, listenedMs = 600_000)
        )
        val stats = buildStats(history, StatsPeriod.TODAY, now)
        assertEquals(2, stats.totalMinutes)
        assertEquals(10, stats.previousMinutes)
    }

    @Test
    fun `settings search finds rows by name and by keyword`() {
        assertTrue(searchSettings("mono").any { it.title == "Mono audio" })
        assertTrue(searchSettings("volume limit").any { it.title.startsWith("Volume limit") })
        assertTrue(searchSettings("typography").isEmpty())
        assertTrue(searchSettings("   ").isEmpty())
        assertTrue(searchSettings("scrobble").any { it.category.id == "lastfm" })
    }
}
