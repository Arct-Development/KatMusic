package com.itzfallenme.katmusic.desktop.data

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipedClientTest {
    private val client = PipedClient(instances = emptyList())

    @Test
    fun `playlist importer accepts ids and common youtube playlist links`() {
        assertEquals("PL1234567890", client.playlistIdFromInput("PL1234567890"))
        assertEquals(
            "PL1234567890",
            client.playlistIdFromInput("https://www.youtube.com/playlist?list=PL1234567890"),
        )
        assertEquals(
            "PL1234567890",
            client.playlistIdFromInput("https://music.youtube.com/playlist?list=PL1234567890&si=share"),
        )
        assertEquals(
            "PL1234567890",
            client.playlistIdFromInput("https://youtu.be/dQw4w9WgXcQ?list=PL1234567890"),
        )
    }

    @Test
    fun `playlist importer rejects non youtube urls and malformed ids`() {
        assertNull(client.playlistIdFromInput("https://example.com/playlist?list=PL1234567890"))
        assertNull(client.playlistIdFromInput("https://notyoutube.com/playlist?list=PL1234567890"))
        assertNull(client.playlistIdFromInput("short"))
        assertNull(client.playlistIdFromInput(""))
    }

    @Test
    fun `video id parsing tolerates additional youtube query parameters`() {
        assertEquals(
            "dQw4w9WgXcQ",
            client.videoIdFromUrl("/watch?v=dQw4w9WgXcQ&list=PL1234567890"),
        )
        assertEquals(
            "dQw4w9WgXcQ",
            client.videoIdFromUrl("https://youtu.be/dQw4w9WgXcQ"),
        )
    }

    @Test
    fun `artist channel URL parsing accepts YouTube channel paths only`() {
        val channelId = "UC" + "A".repeat(22)
        assertEquals(channelId, client.channelIdFromUrl("/channel/$channelId"))
        assertEquals(channelId, client.channelIdFromUrl("https://www.youtube.com/channel/$channelId"))
        assertNull(client.channelIdFromUrl("https://example.com/channel/$channelId"))
        assertNull(client.channelIdFromUrl("/channel/not-a-channel"))
    }

    @Test
    fun `radio rejects malformed seed video ids without touching the network`() = runBlocking {
        assertTrue(client.radio("not-a-video").isFailure)
        assertTrue(client.radio("").isFailure)
    }
}
