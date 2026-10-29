package com.itzfallenme.katmusic

import com.itzfallenme.katmusic.data.parseRecentTracks
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFmFeedTest {

    private fun parse(payload: String) =
        parseRecentTracks(Json.parseToJsonElement(payload).jsonObject)

    @Test
    fun `extended payload yields artist names, artwork and timestamps`() {
        val feed = parse(
            """
            {"recenttracks":{"track":[
              {"name":"Nightcall","artist":{"#text":"Kavinsky","name":"Kavinsky"},
               "album":{"#text":"OutRun"},
               "image":[{"size":"small","#text":"https://img/s.png"},
                        {"size":"extralarge","#text":"https://img/xl.png"}],
               "date":{"uts":"1720000000"}},
              {"name":"Genesis","artist":{"#text":"Justice","name":"Justice"},
               "album":{"#text":"Cross"},
               "image":[],
               "@attr":{"nowplaying":"true"}}
            ]}}
            """.trimIndent()
        )
        assertEquals(2, feed.size)
        assertEquals("Nightcall", feed[0].title)
        assertEquals("Kavinsky", feed[0].artist)
        assertEquals("OutRun", feed[0].album)
        assertEquals("https://img/xl.png", feed[0].artworkUrl)
        assertEquals(1_720_000_000_000L, feed[0].playedAtMs)
        assertFalse(feed[0].nowPlaying)
        assertTrue(feed[1].nowPlaying)
    }

    @Test
    fun `single track object is accepted instead of an array`() {
        val feed = parse(
            """
            {"recenttracks":{"track":{"name":"One","artist":{"#text":"A"},"date":{"uts":"5"}}}}
            """.trimIndent()
        )
        assertEquals(1, feed.size)
        assertEquals(5_000L, feed[0].playedAtMs)
    }

    @Test
    fun `entries without a title or artist are dropped`() {
        val feed = parse(
            """
            {"recenttracks":{"track":[{"name":"","artist":{"#text":"A"}},
                                      {"name":"Fine","artist":{"#text":""}}]}}
            """.trimIndent()
        )
        assertTrue(feed.isEmpty())
    }

    @Test
    fun `missing container yields an empty feed instead of throwing`() {
        assertTrue(parse("""{"error":6}""").isEmpty())
    }
}
