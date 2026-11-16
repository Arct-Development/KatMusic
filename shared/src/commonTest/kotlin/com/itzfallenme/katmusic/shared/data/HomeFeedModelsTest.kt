package com.itzfallenme.katmusic.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeFeedModelsTest {

    @Test
    fun `playlist cards are not tracks`() {
        val card = HomeCard(id = "PL1", title = "Mix", subtitle = "Playlist", playlistId = "PL1")
        assertTrue(card.isPlaylist)
        assertNull(card.toTrack())
    }

    @Test
    fun `stream cards map onto playable shared tracks`() {
        val card = HomeCard(id = "dQw4w9WgXcQ", title = "Song", subtitle = "Artist", videoId = "dQw4w9WgXcQ")
        assertFalse(card.isPlaylist)
        val track = card.toTrack(durationMs = 213_000L)
        assertEquals("dQw4w9WgXcQ", track?.id)
        assertEquals("Song", track?.title)
        assertEquals(213_000L, track?.durationMs)
        assertFalse(track?.isLocal == true)
    }

    @Test
    fun `toTracks keeps order and skips playlists`() {
        val cards = listOf(
            HomeCard(id = "a", title = "A", subtitle = "x", videoId = "aaaaaaaaaaa"),
            HomeCard(id = "p", title = "Mix", subtitle = "x", playlistId = "p"),
            HomeCard(id = "b", title = "B", subtitle = "x", videoId = "bbbbbbbbbbb"),
        )
        val tracks = cards.toTracks(mapOf("a" to 10L))
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), tracks.map { it.id })
        assertEquals(10L, tracks[0].durationMs)
        assertEquals(0L, tracks[1].durationMs)
    }
}
