package com.itzfallenme.katmusic.data.blend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupBlendEngineTest {

    @Test
    fun identicalProfilesHaveFullCompatibility() {
        val profile = BlendTasteProfile(
            artistAffinity = mapOf("radiohead" to 1.0, "massive attack" to 0.6),
            trackAffinity = mapOf("weird fishes|radiohead" to 1.0),
            tracks = listOf(BlendTrack("weird fishes|radiohead", "Weird Fishes", "Radiohead")),
        )

        val result = GroupBlendEngine.calculate(
            listOf(
                BlendMemberProfile("a", "A", profile),
                BlendMemberProfile("b", "B", profile),
            ),
        )

        assertEquals(100, result.compatibilityPercent)
        assertEquals(1, result.sharedTracks.size)
        assertEquals(2, result.recommendations.single().memberCoverage)
    }

    @Test
    fun groupRecommendationsRewardCoverageAcrossMembers() {
        val shared = BlendTrack("song|artist", "Song", "Artist")
        val oneSided = BlendTrack("other|artist", "Other", "Artist")
        val members = listOf(
            BlendMemberProfile(
                "a", "A",
                BlendTasteProfile(
                    artistAffinity = mapOf("artist" to 1.0),
                    trackAffinity = mapOf(shared.key to 1.0, oneSided.key to 0.9),
                    tracks = listOf(shared, oneSided),
                ),
            ),
            BlendMemberProfile(
                "b", "B",
                BlendTasteProfile(
                    artistAffinity = mapOf("artist" to 1.0),
                    trackAffinity = mapOf(shared.key to 0.8),
                    tracks = listOf(shared),
                ),
            ),
            BlendMemberProfile(
                "c", "C",
                BlendTasteProfile(
                    artistAffinity = mapOf("different artist" to 1.0),
                    trackAffinity = emptyMap(),
                ),
            ),
        )

        val result = GroupBlendEngine.calculate(members)

        assertTrue(result.sharedTracks.isEmpty())
        assertEquals(shared.key, result.recommendations.first().track.key)
        assertEquals(2, result.recommendations.first().memberCoverage)
    }

    @Test
    fun emptyGroupReturnsEmptyResult() {
        val result = GroupBlendEngine.calculate(emptyList())

        assertEquals(0, result.compatibilityPercent)
        assertTrue(result.sharedArtists.isEmpty())
        assertTrue(result.sharedTracks.isEmpty())
        assertTrue(result.recommendations.isEmpty())
    }
}
