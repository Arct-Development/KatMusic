package com.itzfallenme.katmusic.shared.library

import com.itzfallenme.katmusic.shared.models.Track
import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySearchTest {

    private fun track(title: String, artist: String, album: String = "") = Track(
        id = "$artist-$title",
        title = title,
        artist = artist,
        album = album,
        source = "file:///music/$artist-$title.mp3",
    )

    private val library = listOf(
        track("Hunter", "Björk", "Homogenic"),
        track("Jóga", "Björk", "Homogenic"),
        track("Everlong", "Foo Fighters", "The Colour and the Shape"),
        track("Águas de Março", "Elis Regina", "Elis & Tom"),
    )

    @Test
    fun `blank query matches nothing`() {
        assertEquals(emptyList(), LibrarySearch.filter(library, "   "))
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(listOf("Foo Fighters-Everlong"), LibrarySearch.filter(library, "EVERLONG").map { it.id })
    }

    @Test
    fun `diacritics are ignored in both directions`() {
        val byAscii = LibrarySearch.filter(library, "bjork").map { it.title }
        val byAccent = LibrarySearch.filter(library, "björk").map { it.title }
        assertEquals(listOf("Hunter", "Jóga"), byAscii)
        assertEquals(byAscii, byAccent)
    }

    @Test
    fun `every token must match`() {
        assertEquals(
            listOf("Elis Regina-Águas de Março"),
            LibrarySearch.filter(library, "elis aguas").map { it.id },
        )
        assertEquals(emptyList(), LibrarySearch.filter(library, "elis foo"))
    }

    @Test
    fun `album names are searchable`() {
        assertEquals(
            listOf("Foo Fighters-Everlong"),
            LibrarySearch.filter(library, "colour and the shape").map { it.id },
        )
    }
}
