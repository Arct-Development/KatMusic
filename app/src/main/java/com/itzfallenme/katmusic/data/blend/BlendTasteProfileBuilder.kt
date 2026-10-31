package com.itzfallenme.katmusic.data.blend

import com.itzfallenme.katmusic.data.local.db.SongPlayStatsEntity
import kotlin.math.ln
import kotlin.math.max

/**
 * Converts local play statistics into a small profile suitable for sharing.
 * Raw listening history never leaves the device through this builder.
 */
object BlendTasteProfileBuilder {
    fun build(
        stats: List<SongPlayStatsEntity>,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): BlendTasteProfile {
        if (stats.isEmpty()) return BlendTasteProfile(generatedAtEpochMs = nowEpochMs)

        val artistScores = linkedMapOf<String, Double>()
        val trackScores = linkedMapOf<String, Double>()
        val tracks = linkedMapOf<String, BlendTrack>()

        stats.asSequence()
            .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
            .sortedByDescending { it.totalPlayTimeMs }
            .take(500)
            .forEach { stat ->
                val artistKey = stat.artist.trim().lowercase()
                val trackKey = stat.trackKey.ifBlank {
                    "${stat.title.trim()}|${stat.artist.trim()}".lowercase()
                }
                val ageDays = if (stat.lastPlayedAtMillis > 0L) {
                    max(0L, nowEpochMs - stat.lastPlayedAtMillis) / DAY_MS.toDouble()
                } else {
                    365.0
                }
                val recency = 0.35 + 0.65 * kotlin.math.exp(-ageDays / 120.0)
                val listeningMinutes = stat.totalPlayTimeMs.coerceAtLeast(0L) / 60_000.0
                val engagement = ln(1.0 + listeningMinutes) +
                    stat.playCount.coerceAtLeast(0) * 0.45
                val skipPenalty = 1.0 / (1.0 + stat.skipCount.coerceAtLeast(0) * 0.30)
                val score = (engagement * recency * skipPenalty).coerceAtLeast(0.0)

                if (score > 0.0) {
                    artistScores[artistKey] = (artistScores[artistKey] ?: 0.0) + score
                    trackScores[trackKey] = score
                    tracks[trackKey] = BlendTrack(
                        key = trackKey,
                        title = stat.title.trim(),
                        artist = stat.artist.trim(),
                        videoId = stat.videoId?.takeIf(String::isNotBlank),
                    )
                }
            }

        return BlendTasteProfile(
            artistAffinity = artistScores.normalizedTop(BlendTasteProfile.MAX_ARTISTS),
            trackAffinity = trackScores.normalizedTop(BlendTasteProfile.MAX_TRACKS),
            tracks = tracks.values
                .sortedByDescending { trackScores[it.key] ?: 0.0 }
                .take(BlendTasteProfile.MAX_TRACKS),
            generatedAtEpochMs = nowEpochMs,
        )
    }

    private fun Map<String, Double>.normalizedTop(limit: Int): Map<String, Double> {
        val maxScore = values.maxOrNull()?.takeIf { it > 0.0 } ?: return emptyMap()
        return entries
            .sortedByDescending { it.value }
            .take(limit)
            .associate { it.key to (it.value / maxScore).coerceIn(0.0, 1.0) }
    }

    private const val DAY_MS = 86_400_000L
}
