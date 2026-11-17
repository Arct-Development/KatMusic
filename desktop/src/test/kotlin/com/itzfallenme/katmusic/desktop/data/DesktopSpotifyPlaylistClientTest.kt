package com.itzfallenme.katmusic.desktop.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DesktopSpotifyPlaylistClientTest {
    private val client = DesktopSpotifyPlaylistClient()
    private val playlistId = "0123456789ABCDEFGHIJKL"

    @Test
    fun `playlist id accepts spotify URL URI and bare id`() {
        assertEquals(playlistId, client.extractPlaylistId(playlistId))
        assertEquals(
            playlistId,
            client.extractPlaylistId("https://open.spotify.com/playlist/$playlistId?si=share"),
        )
        assertEquals(
            playlistId,
            client.extractPlaylistId("https://open.spotify.com/intl-de/playlist/$playlistId"),
        )
        assertEquals(playlistId, client.extractPlaylistId("spotify:playlist:$playlistId"))
    }

    @Test
    fun `playlist id rejects unrelated hosts`() {
        assertNull(client.extractPlaylistId("https://notspotify.com/playlist/$playlistId"))
        assertNull(client.extractPlaylistId("https://example.com/playlist/$playlistId"))
    }

    @Test
    fun `public embed parser extracts deduplicated playlist tracks`() {
        val html = """
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"playlist":{"uri":"spotify:playlist:$playlistId","title":"Road Trip","subtitle":"Kat"},
            "one":{"uri":"spotify:track:AAAAAAAAAAAAAAAAAAAAAA","title":"First Song","subtitle":"Artist One"},
            "duplicate":{"uri":"spotify:track:AAAAAAAAAAAAAAAAAAAAAA","title":"First Song","subtitle":"Artist One"},
            "two":{"uri":"spotify:track:BBBBBBBBBBBBBBBBBBBBBB","title":"Second Song","subtitle":"Artist Two"}}}}
            </script>
        """.trimIndent()

        val playlist = client.parseEmbedPage(html, playlistId)

        assertEquals("Road Trip", playlist.title)
        assertEquals(2, playlist.tracks.size)
        assertEquals("First Song", playlist.tracks.first().title)
        assertEquals("Artist Two", playlist.tracks.last().artist)
    }

    @Test
    fun `public embed parser rejects empty or inaccessible playlists`() {
        assertFailsWith<IllegalStateException> {
            client.parseEmbedPage("<html><body>private playlist</body></html>", playlistId)
        }
        assertFailsWith<IllegalArgumentException> {
            client.parseEmbedPage(
                """<script id="__NEXT_DATA__">{"uri":"spotify:playlist:$playlistId","title":"Empty"}</script>""",
                playlistId,
            )
        }
    }
}
