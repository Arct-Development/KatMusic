package com.itzfallenme.katmusic.data.blend

import com.itzfallenme.katmusic.data.local.db.SongPlayStatsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlendTasteProfileBuilderTest {
    @Test
    fun builderNormalizesArtistAndTrackAffinity() {
        val now = 2_000_000_000_000L
        val stats = listOf(
            SongPlayStatsEntity(
                trackKey = "track one|artist one",
                title = "Track One",
                artist = "Artist One",
                totalPlayTimeMs = 30 * 60_000L,
                playCount = 8,
                lastPlayedAtMillis = now - 86_400_000L,
            ),
            SongPlayStatsEntity(
                trackKey = "track two|artist two",
                title = "Track Two",
                artist = "Artist Two",
                totalPlayTimeMs = 5 * 60_000L,
                playCount = 1,
                lastPlayedAtMillis = now - 86_400_000L,
            ),
        )

        val profile = BlendTasteProfileBuilder.build(stats, now)

        assertEquals("artist one", profile.artistAffinity.keys.first())
        assertEquals(1.0, profile.artistAffinity.values.maxOrNull()!!, 0.0)
        assertEquals(2, profile.tracks.size)
        assertTrue(profile.trackAffinity.values.all { it in 0.0..1.0 })
    }

    @Test
    fun builderReturnsEmptyProfileWhenHistoryIsEmpty() {
        val profile = BlendTasteProfileBuilder.build(emptyList(), 100L)
        assertTrue(profile.artistAffinity.isEmpty())
        assertTrue(profile.trackAffinity.isEmpty())
        assertTrue(profile.tracks.isEmpty())
    }
}
