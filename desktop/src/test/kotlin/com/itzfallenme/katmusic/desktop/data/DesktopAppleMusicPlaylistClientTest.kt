package com.itzfallenme.katmusic.desktop.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DesktopAppleMusicPlaylistClientTest {
    private val client = DesktopAppleMusicPlaylistClient()
    private val playlistId = "pl.abc123XYZ"

    @Test
    fun `extracts public Apple Music playlist IDs`() {
        assertEquals(playlistId, client.extractPlaylistId(playlistId))
        assertEquals(
            playlistId,
            client.extractPlaylistId("https://music.apple.com/us/playlist/road-trip/$playlistId"),
        )
        assertEquals(
            playlistId,
            client.extractPlaylistId("music.apple.com/gb/playlist/$playlistId"),
        )
        assertNull(client.extractPlaylistId("https://example.com/us/playlist/$playlistId"))
    }

    @Test
    fun `parses Apple Music server data and playlist title in order`() {
        val html = """
            <title>Fallback</title>
            <script id="schema:music-playlist" type="application/ld+json">
            {"@type":"MusicPlaylist","name":"Apple Road Trip","track":[]}
            </script>
            <script id="serialized-server-data" type="application/json">
            {"data":{"tracks":[
              {"title":"First Song","artistName":"Artist One"},
              {"title":"Second Song","artistName":"Artist Two"},
              {"title":"First Song","artistName":"Artist One"}
            ]}}
            </script>
        """.trimIndent()

        val playlist = client.parsePage(html, playlistId)

        assertEquals("Apple Road Trip", playlist.title)
        assertEquals(2, playlist.tracks.size)
        assertEquals("First Song", playlist.tracks[0].title)
        assertEquals("Artist Two", playlist.tracks[1].artist)
    }

    @Test
    fun `falls back to schema playlist tracks when server data has no artist rows`() {
        val html = """
            <script id="schema:music-playlist" type="application/ld+json">
            {"@type":"MusicPlaylist","name":"Instrumentals","track":[{"name":"One"},{"name":"Two"}]}
            </script>
        """.trimIndent()

        val playlist = client.parsePage(html, playlistId)

        assertEquals("Instrumentals", playlist.title)
        assertEquals(listOf("One", "Two"), playlist.tracks.map { it.title })
        assertEquals("", playlist.tracks.first().artist)
    }

    @Test
    fun `rejects inaccessible public playlist HTML`() {
        assertFailsWith<IllegalArgumentException> {
            client.parsePage("<html><body>private</body></html>", playlistId)
        }
    }
}
