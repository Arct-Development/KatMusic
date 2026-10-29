package com.itzfallenme.katmusic.playback

import com.itzfallenme.katmusic.data.Track

/**
 * Compact queue summary, modelled on LastWave's QueueStatsFormatter.
 *
 * "0 songs", "1 song · 3m", "58 songs · 42m", "58 songs · 3h 37m", with a
 * trailing "+" when some entries have no known duration so the total is a
 * lower bound rather than a lie.
 */
object QueueStats {

    fun format(tracks: List<Track>): String {
        if (tracks.isEmpty()) return "0 songs"
        val count = if (tracks.size == 1) "1 song" else "${tracks.size} songs"
        val known = tracks.map { it.durationMs }.filter { it > 0 }
        if (known.isEmpty()) return count

        val totalSeconds = known.sum() / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val duration = if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${(totalSeconds / 60).coerceAtLeast(1)}m"
        }
        val approximate = if (known.size < tracks.size) "+" else ""
        return "$count · $duration$approximate"
    }
}
