package com.itzfallenme.katmusic.shared.library

import com.itzfallenme.katmusic.shared.models.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryGroupingTest {

    private fun track(
        title: String,
        artist: String,
        album: String = "",
        number: Int = 0,
        artwork: String? = null,
        duration: Long = 0L,
    ) = Track(
        id = "$artist-$album-$title",
        title = title,
        artist = artist,
        album = album,
        durationMs = duration,
        source = "file:///music/$title.mp3",
        artwork = artwork,
        trackNumber = number,
    )

    private val library = listOf(
        track("Jóga", "Björk", "Homogenic", 2, artwork = "cover:homogenic", duration = 300_000),
        track("Hunter", "Björk", "Homogenic", 1, artwork = "cover:homogenic", duration = 240_000),
        track("Unravel", "Björk", "Homogenic", 3, duration = 200_000),
        track("Everlong", "Foo Fighters", "The Colour and the Shape", 1),
        track("Loose", "No Album Artist", ""),
    )

    @Test
    fun `albums group by album and album artist`() {
        val albums = LibraryGrouping.albums(library)
        assertEquals(3, albums.size)
        assertEquals("Homogenic", albums.first().album)
        assertEquals(3, albums.first().tracks.size)
    }

    @Test
    fun `album tracks are ordered by track number`() {
        val homogenic = LibraryGrouping.albums(library).first { it.album == "Homogenic" }
        assertEquals(listOf("Hunter", "Jóga", "Unravel"), homogenic.tracks.map { it.title })
    }

    @Test
    fun `album duration sums its tracks`() {
        val homogenic = LibraryGrouping.albums(library).first { it.album == "Homogenic" }
        assertEquals(740_000, homogenic.durationMs)
    }

    @Test
    fun `album artwork is taken from whichever track has it`() {
        val homogenic = LibraryGrouping.albums(library).first { it.album == "Homogenic" }
        assertEquals("cover:homogenic", homogenic.artwork)
    }

    @Test
    fun `tracks without an album land in one unknown album`() {
        val unknown = LibraryGrouping.albums(library).first { it.album == LibraryGrouping.UNKNOWN_ALBUM }
        assertEquals(1, unknown.tracks.size)
        assertEquals("Loose", unknown.tracks.single().title)
    }

    @Test
    fun `artists group across albums and count distinct albums`() {
        val artists = LibraryGrouping.artists(library)
        assertEquals(3, artists.size)
        val bjork = artists.first { it.artist == "Björk" }
        assertEquals(3, bjork.tracks.size)
        assertEquals(1, bjork.albumCount)
    }

    @Test
    fun `artists are sorted case insensitively`() {
        val names = LibraryGrouping.artists(library).map { it.artist }
        assertEquals(listOf("Björk", "Foo Fighters", "No Album Artist"), names)
    }

    @Test
    fun `empty artist and album names get readable fallbacks`() {
        val blank = track("Ghost", "  ", "   ")
        val albums = LibraryGrouping.albums(listOf(blank))
        assertEquals(LibraryGrouping.UNKNOWN_ALBUM, albums.single().album)
        assertEquals(LibraryGrouping.UNKNOWN_ARTIST, albums.single().artist)
        assertTrue(LibraryGrouping.artists(listOf(blank)).single().artist == LibraryGrouping.UNKNOWN_ARTIST)
    }
}
