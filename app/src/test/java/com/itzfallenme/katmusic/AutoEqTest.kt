package com.itzfallenme.katmusic

import com.itzfallenme.katmusic.data.AutoEq
import com.itzfallenme.katmusic.data.PlaylistImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqTest {

    private val parametric = """
        # Sennheiser HD 600
        Preamp: -6.2 dB
        Filter 1: ON PK Fc 105 Hz Gain 5.5 dB Q 0.70
        Filter 2: ON PK Fc 1200 Hz Gain -3.1 dB Q 1.40
        Filter 3: ON PK Fc 6000 Hz Gain 2.0 dB Q 2.00
    """.trimIndent()

    @Test
    fun `parametric profile reads preamp filters and name`() {
        val profile = AutoEq.parse(parametric)

        assertFalse(profile.isEmpty)
        assertEquals(3, profile.filterCount)
        assertEquals(-6.2f, profile.preampDb, 0.001f)
        assertEquals("Sennheiser HD 600", profile.name)
        assertEquals(105f, profile.filters.first().frequencyHz, 0.001f)
        assertEquals(5.5f, profile.filters.first().gainDb, 0.001f)
    }

    @Test
    fun `response peaks at the filter frequency`() {
        val profile = AutoEq.parse(parametric)

        val atPeak = AutoEq.responseAt(profile, 105f)
        val away = AutoEq.responseAt(profile, 10_000f)

        assertEquals(5.5f, atPeak, 0.6f)
        assertTrue("far from the peak the boost fades", away < 1f)
    }

    @Test
    fun `graphic profile interpolates between points`() {
        val graphic = "GraphicEQ: 20 4.0; 100 2.0; 1000 0.0; 5000 -2.0; 10000 -4.0"
        val profile = AutoEq.parse(graphic)

        assertEquals(5, profile.curve.size)
        assertEquals(0f, AutoEq.responseAt(profile, 1000f), 0.01f)
        val midway = AutoEq.responseAt(profile, 500f)
        assertTrue("midway value sits between its neighbours", midway in -0.2f..2.0f)
    }

    @Test
    fun `csv profile is recognised`() {
        val csv = """
            frequency,gain
            20,3
            100,2
            500,0
            2000,-1
            8000,-3
        """.trimIndent()
        val profile = AutoEq.parse(csv)

        assertEquals(5, profile.curve.size)
        assertTrue(AutoEq.looksLikeProfile(csv))
    }

    @Test
    fun `gains are mapped onto every band and never clip`() {
        val profile = AutoEq.parse(parametric)
        val bands = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        val gains = AutoEq.gainsFor(profile, bands)

        assertEquals(bands.size, gains.size)
        assertTrue(gains.all { it in -12f..0.5f })
        assertTrue("the correction keeps its shape", gains.max() - gains.min() > 2f)
        assertTrue("the 105 Hz band is boosted above its neighbours", gains[2] > gains.last())
    }

    @Test
    fun `empty text yields nothing`() {
        assertTrue(AutoEq.parse("hello world").isEmpty)
        assertFalse(AutoEq.looksLikeProfile("hello world"))
    }

    @Test
    fun `text playlist parser splits artist and title`() {
        val text = """
            1. Radiohead - Everything In Its Right Place
            - Massive Attack - Teardrop
            https://open.spotify.com/track/abc
            just a heading

            Portishead – Roads
        """.trimIndent()

        val entries = PlaylistImporter.parseTrackList(text)

        assertEquals(3, entries.size)
        assertEquals("Radiohead" to "Everything In Its Right Place", entries[0])
        assertEquals("Massive Attack" to "Teardrop", entries[1])
        assertEquals("Portishead" to "Roads", entries[2])
    }

    @Test
    fun `playlist ids are recognised from links and bare ids`() {
        assertNotNull(
            PlaylistImporter.extractPlaylistId(
                "https://music.youtube.com/playlist?list=PLabcDEF1234567890"
            )
        )
        assertEquals("PLabcDEF1234567890", PlaylistImporter.extractPlaylistId("VLPLabcDEF1234567890"))
        assertEquals(null, PlaylistImporter.extractPlaylistId("not a playlist"))
    }
}
