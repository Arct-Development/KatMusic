package com.itzfallenme.katmusic.data.blend

import kotlinx.serialization.Serializable

/**
 * Compact, account-independent snapshot of a KatMusic listener's taste.
 *
 * Only the top signals are intended for cloud sync. Full play history stays
 * on-device. Keys are normalized title|artist and lowercase artist names.
 */
@Serializable
data class BlendTasteProfile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val artistAffinity: Map<String, Double> = emptyMap(),
    val trackAffinity: Map<String, Double> = emptyMap(),
    val tracks: List<BlendTrack> = emptyList(),
    val generatedAtEpochMs: Long = System.currentTimeMillis(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_ARTISTS = 40
        const val MAX_TRACKS = 100
    }
}

@Serializable
data class BlendTrack(
    val key: String,
    val title: String,
    val artist: String,
    val videoId: String? = null,
)

data class BlendMemberProfile(
    val userId: String,
    val displayName: String,
    val profile: BlendTasteProfile,
)

data class BlendArtistMatch(
    val artist: String,
    val memberCount: Int,
    val averageAffinity: Double,
)

data class BlendTrackRecommendation(
    val track: BlendTrack,
    /** Number of members whose profile contains this track. */
    val memberCoverage: Int,
    /** 0..1 score combining average affinity and group coverage. */
    val groupAffinity: Double,
)

data class GroupBlendResult(
    /** Mean pairwise taste similarity, 0..100. */
    val compatibilityPercent: Int,
    val sharedArtists: List<BlendArtistMatch>,
    val sharedTracks: List<BlendTrack>,
    val recommendations: List<BlendTrackRecommendation>,
)
