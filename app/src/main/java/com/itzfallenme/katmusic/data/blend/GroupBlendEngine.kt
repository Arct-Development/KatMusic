package com.itzfallenme.katmusic.data.blend

import kotlin.math.sqrt

/**
 * Deterministic, offline group taste matching. No Spotify or AI API required.
 * Inputs are compact profiles; full listening histories are not needed.
 */
object GroupBlendEngine {
    fun calculate(
        members: List<BlendMemberProfile>,
        recommendationLimit: Int = 40,
    ): GroupBlendResult {
        if (members.isEmpty()) {
            return GroupBlendResult(0, emptyList(), emptyList(), emptyList())
        }

        val profiles = members.map { it.profile }
        val compatibility = if (profiles.size < 2) {
            0
        } else {
            val pairScores = mutableListOf<Double>()
            for (left in 0 until profiles.lastIndex) {
                for (right in left + 1 until profiles.size) {
                    val artistScore = cosine(
                        profiles[left].artistAffinity,
                        profiles[right].artistAffinity,
                    )
                    val trackScore = cosine(
                        profiles[left].trackAffinity,
                        profiles[right].trackAffinity,
                    )
                    pairScores += (artistScore * 0.75 + trackScore * 0.25)
                        .coerceIn(0.0, 1.0)
                }
            }
            ((pairScores.average().takeIf(Double::isFinite) ?: 0.0) * 100.0)
                .toInt().coerceIn(0, 100)
        }

        val sharedArtists = profiles
            .flatMap { it.artistAffinity.keys }
            .toSet()
            .mapNotNull { artist ->
                val values = profiles.mapNotNull { it.artistAffinity[artist] }
                if (values.size < 2) null else BlendArtistMatch(
                    artist = artist,
                    memberCount = values.size,
                    averageAffinity = values.average(),
                )
            }
            .sortedWith(
                compareByDescending<BlendArtistMatch> { it.memberCount }
                    .thenByDescending { it.averageAffinity },
            )
            .take(12)

        val trackByKey = profiles.flatMap { it.tracks }.associateBy { it.key }
        val trackScores = trackByKey.mapNotNull { (key, track) ->
            val affinities = profiles.mapNotNull { it.trackAffinity[key] }
            if (affinities.isEmpty()) null else {
                val coverage = affinities.size
                val averageAcrossGroup = affinities.sum() / profiles.size
                val coverageRatio = coverage.toDouble() / profiles.size
                val score = (averageAcrossGroup * 0.65 + coverageRatio * 0.35)
                    .coerceIn(0.0, 1.0)
                BlendTrackRecommendation(track, coverage, score)
            }
        }.sortedByDescending { it.groupAffinity }

        val sharedTracks = trackScores
            .filter { it.memberCoverage == profiles.size }
            .map { it.track }
            .take(20)

        val limit = recommendationLimit.coerceIn(1, 100)
        // Anchor with tracks everyone shares, then alternate picks from each
        // member. This keeps one person's heavy rotation from filling the
        // whole mix, which is the useful part of a group Blend.
        val anchors = sharedTracks.map { BlendTrackRecommendation(it, profiles.size, 1.0) }
        val memberPicks = interleaveMemberPicks(
            profiles = profiles,
            tracks = trackScores,
            excluded = sharedTracks.mapTo(mutableSetOf()) { it.key },
            limit = limit,
        )

        return GroupBlendResult(
            compatibilityPercent = compatibility,
            sharedArtists = sharedArtists,
            sharedTracks = sharedTracks,
            recommendations = diversify(anchors + memberPicks, limit),
        )
    }

    private fun cosine(left: Map<String, Double>, right: Map<String, Double>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val keys = left.keys + right.keys
        var dot = 0.0
        var leftMagnitude = 0.0
        var rightMagnitude = 0.0
        for (key in keys) {
            val a = left[key] ?: 0.0
            val b = right[key] ?: 0.0
            dot += a * b
            leftMagnitude += a * a
            rightMagnitude += b * b
        }
        val denominator = sqrt(leftMagnitude) * sqrt(rightMagnitude)
        return if (denominator <= 0.0) 0.0 else (dot / denominator).coerceIn(0.0, 1.0)
    }

    /**
     * Round-robin the strongest unused track from every member, so the first
     * rows of a Blend visibly contain more than one person's taste. Tracks
     * already chosen as group anchors are skipped.
     */
    private fun interleaveMemberPicks(
        profiles: List<BlendTasteProfile>,
        tracks: List<BlendTrackRecommendation>,
        excluded: MutableSet<String>,
        limit: Int,
    ): List<BlendTrackRecommendation> {
        val queues = profiles.map { profile ->
            tracks
                .filter { it.track.key !in excluded && profile.trackAffinity.containsKey(it.track.key) }
                .sortedByDescending { profile.trackAffinity[it.track.key] ?: 0.0 }
                .toMutableList()
        }
        val used = excluded
        val result = mutableListOf<BlendTrackRecommendation>()
        var added = true
        while (result.size < limit && added) {
            added = false
            for (queue in queues) {
                while (queue.isNotEmpty() && !used.add(queue.first().track.key)) {
                    queue.removeAt(0)
                }
                if (queue.isNotEmpty()) {
                    result += queue.removeAt(0)
                    added = true
                    if (result.size >= limit) break
                }
            }
        }
        return result
    }

    /** Avoid letting one artist dominate the first page of the group mix. */
    private fun diversify(
        tracks: List<BlendTrackRecommendation>,
        limit: Int,
    ): List<BlendTrackRecommendation> {
        val remaining = tracks.toMutableList()
        val result = mutableListOf<BlendTrackRecommendation>()
        val artistCounts = mutableMapOf<String, Int>()
        while (remaining.isNotEmpty() && result.size < limit) {
            val nextIndex = remaining.indexOfFirst {
                (artistCounts[it.track.artist.lowercase()] ?: 0) < 2
            }.let { if (it < 0) 0 else it }
            val next = remaining.removeAt(nextIndex)
            result += next
            val artist = next.track.artist.lowercase()
            artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
        }
        return result
    }
}
