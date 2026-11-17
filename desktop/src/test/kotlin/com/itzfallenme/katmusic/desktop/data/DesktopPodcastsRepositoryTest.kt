package com.itzfallenme.katmusic.desktop.data

import com.itzfallenme.katmusic.shared.data.PodcastShow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopPodcastsRepositoryTest {

    @Test
    fun `parses podcast duration formats`() {
        assertEquals(3_723_000L, parsePodcastDuration("01:02:03"))
        assertEquals(125_000L, parsePodcastDuration("02:05"))
        assertEquals(90_000L, parsePodcastDuration("90"))
        assertEquals(0L, parsePodcastDuration(""))
    }

    @Test
    fun `strips html and keeps readable description`() {
        assertEquals(
            "Hello world & friends",
            stripHtml("<p>Hello <b>world</b> &amp; friends</p>"),
        )
    }

    @Test
    fun `parses channel metadata and audio enclosure from rss`() {
        val xml = """
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>Test Show</title>
                <itunes:author>Test Author</itunes:author>
                <itunes:image href="https://example.com/show.jpg"/>
                <item>
                  <title>Episode One</title>
                  <description><![CDATA[<p>A <b>great</b> episode.</p>]]></description>
                  <pubDate>Tue, 17 Nov 2026 10:00:00 GMT</pubDate>
                  <itunes:duration>01:02:03</itunes:duration>
                  <guid>episode-1</guid>
                  <enclosure url="https://example.com/episode.mp3" type="audio/mpeg" length="123"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val fallback = PodcastShow("1", "Fallback", "Fallback Author", null, "https://example.com/feed")
        val (show, episodes) = DesktopPodcastsRepository().parseFeed(xml, fallback)

        assertEquals("Test Show", show.title)
        assertEquals("Test Author", show.author)
        assertEquals("https://example.com/show.jpg", show.artworkUrl)
        assertEquals(1, episodes.size)
        assertEquals("episode-1", episodes[0].id)
        assertEquals(3_723_000L, episodes[0].durationMs)
        assertEquals("https://example.com/episode.mp3", episodes[0].audioUrl)
        assertEquals("A great episode.", episodes[0].description)
        assertTrue(episodes[0].publishedAt.startsWith("Nov 17, 2026"))
    }
}
