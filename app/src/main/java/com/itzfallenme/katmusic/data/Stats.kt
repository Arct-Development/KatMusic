package com.itzfallenme.katmusic.data

import java.util.Calendar

/** Windows offered by the stats screen, in the order they are shown. */
enum class StatsPeriod(val label: String) {
    TODAY("Today"),
    WEEK("This week"),
    MONTH("This month"),
    ALL("All time")
}

/** Start of [period] relative to [now], or 0 for all time. Weeks start on Monday. */
fun StatsPeriod.startMillis(now: Long = System.currentTimeMillis()): Long {
    if (this == StatsPeriod.ALL) return 0L
    val calendar = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    when (this) {
        StatsPeriod.TODAY -> Unit
        StatsPeriod.WEEK -> {
            calendar.firstDayOfWeek = Calendar.MONDAY
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        StatsPeriod.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
        StatsPeriod.ALL -> Unit
    }
    return calendar.timeInMillis
}

/** Monday-first index (0 = Monday … 6 = Sunday) for a timestamp. */
fun weekdayIndex(timeMs: Long): Int {
    val day = Calendar.getInstance().apply { timeInMillis = timeMs }.get(Calendar.DAY_OF_WEEK)
    return (day + 5) % 7
}

/** Short Monday-first labels, used by the weekday strip and the "most active" line. */
val weekdayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * Turns raw history into the numbers the stats screen shows, scoped to [period].
 *
 * [WrappedStats.previousMinutes] covers the immediately preceding window of the
 * same length so the screen can say "58 min more than last week" without keeping
 * a second query around.
 */
fun buildStats(
    history: List<HistoryEntry>,
    period: StatsPeriod,
    now: Long = System.currentTimeMillis()
): WrappedStats {
    if (history.isEmpty()) return WrappedStats()
    val start = period.startMillis(now)
    val scoped = if (start == 0L) history else history.filter { it.playedAt >= start }
    val previous = if (start == 0L) {
        emptyList()
    } else {
        val window = (now - start).coerceAtLeast(1L)
        val previousStart = start - window
        history.filter { it.playedAt in previousStart until start }
    }
    if (scoped.isEmpty()) {
        return WrappedStats(previousMinutes = previous.sumOf { it.listenedMs } / 60_000)
    }
    val byTrack = scoped.groupingBy { it.track.title }.eachCount()
    val byArtist = scoped.groupingBy { it.track.artist.ifBlank { "Unknown artist" } }.eachCount()
    val byAlbum = scoped.groupingBy { it.track.album?.takeIf(String::isNotBlank) ?: "Singles" }.eachCount()
    val hourly = MutableList(24) { 0 }
    val weekday = MutableList(7) { 0 }
    scoped.forEach { entry ->
        val calendar = Calendar.getInstance().apply { timeInMillis = entry.playedAt }
        hourly[calendar.get(Calendar.HOUR_OF_DAY)] += 1
        weekday[weekdayIndex(entry.playedAt)] += 1
    }
    return WrappedStats(
        totalMinutes = scoped.sumOf { it.listenedMs } / 60_000,
        totalPlays = scoped.size,
        topTracks = byTrack.entries.sortedByDescending { it.value }.take(10).map { it.key to it.value },
        topArtists = byArtist.entries.sortedByDescending { it.value }.take(10).map { it.key to it.value },
        topAlbums = byAlbum.entries.sortedByDescending { it.value }.take(10).map { it.key to it.value },
        hourly = hourly,
        byWeekday = weekday,
        previousMinutes = previous.sumOf { it.listenedMs } / 60_000
    )
}
