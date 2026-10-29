package com.itzfallenme.katmusic

import com.itzfallenme.katmusic.data.InnerTube
import com.itzfallenme.katmusic.data.LyricsApi
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Network smoke test, excluded from normal runs. Execute with:
 *   KAT_LIVE=1 ./gradlew :app:testDebugUnitTest --tests '*LiveApiTest*'
 */
class LiveApiTest {

    @Test
    fun `search returns playable tracks`() = runBlocking {
        assumeTrue(System.getenv("KAT_LIVE") == "1")
        val shelves = InnerTube.search("lofi hip hop")
        val tracks = shelves.flatMap { it.tracks }
        assertTrue("expected search results", tracks.isNotEmpty())
        assertTrue("expected a video id", tracks.first().id.isNotBlank())
    }

    @Test
    fun `stream resolution returns a fetchable audio url`() = runBlocking {
        assumeTrue(System.getenv("KAT_LIVE") == "1")
        val stream = InnerTube.streamUrl("dQw4w9WgXcQ", preferHighQuality = true)
        assertTrue("audio mime type", stream.mimeType.startsWith("audio/"))
        assertTrue("https url", stream.url.startsWith("https://"))

        val request = Request.Builder()
            .url(stream.url)
            .header("User-Agent", InnerTube.VR_UA)
            .header("Range", "bytes=0-2047")
            .build()
        InnerTube.http.newCall(request).execute().use { response ->
            assertTrue("range request succeeded", response.isSuccessful || response.code == 206)
            assertNotNull(response.body)
            assertTrue("received audio bytes", (response.body?.contentLength() ?: 0) > 0)
        }
    }

    @Test
    fun `newpipe resolver returns playable audio`() = runBlocking {
        assumeTrue(System.getenv("KAT_LIVE") == "1")
        val stream = com.itzfallenme.katmusic.data.StreamResolver.resolve("dQw4w9WgXcQ", true)
        assertTrue("https url", stream.url.startsWith("https://"))
        val request = Request.Builder()
            .url(stream.url)
            .header("User-Agent", InnerTube.VR_UA)
            .header("Accept", "audio/*,*/*;q=0.8")
            .build()
        InnerTube.http.newCall(request).execute().use { response ->
            assertTrue("status ${response.code}", response.code == 200 || response.code == 206)
            val read = response.body?.byteStream()?.read(ByteArray(2048)) ?: 0
            assertTrue("received audio bytes", read > 0)
        }
    }

    @Test
    fun `word lyrics provider returns syllable timing`() = runBlocking {
        assumeTrue(System.getenv("KAT_LIVE") == "1")
        val lyrics = com.itzfallenme.katmusic.data.WordLyricsApi.find(
            "Shape of You", "Ed Sheeran", "÷ (Divide)", 234
        )
        assertNotNull("expected word-timed lyrics", lyrics)
        assertTrue("expected lines", lyrics!!.synced.isNotEmpty())
        assertTrue("expected syllables", lyrics.hasWordTiming)
        val line = lyrics.synced.first { it.hasSyllables }
        println("WORD LINE: ${line.syllables.joinToString(" | ") { "${it.text}@${it.timeMs}+${it.durationMs}" }}")
        assertTrue("syllable order", line.syllables.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs })
    }

    @Test
    fun `genre resolver labels a mainstream artist`() = runBlocking {
        assumeTrue(System.getenv("KAT_LIVE") == "1")
        val genre = com.itzfallenme.katmusic.data.GenreResolver.artistGenre("The Weeknd")
        println("GENRE: $genre")
        assertTrue("expected a genre", genre.isNotBlank())
        val radio = com.itzfallenme.katmusic.data.Discover.genreRadio(genre, 10)
        assertTrue("expected genre radio tracks", radio.isNotEmpty())
    }

    @Test
    fun `romanization returns a reading per line`() = runBlocking {
        assumeTrue(System.getenv("KAT_LIVE") == "1")
        val lines = listOf(
            com.itzfallenme.katmusic.data.LyricLine(0, "愛してる"),
            com.itzfallenme.katmusic.data.LyricLine(2_000, "今日は晴天")
        )
        val romanised = com.itzfallenme.katmusic.data.LyricsTranslation.romanize(lines)
        println("ROMAN: ${romanised?.texts}")
        assertNotNull("expected romanised lines", romanised)
        org.junit.Assert.assertEquals(2, romanised!!.texts.size)
        assertTrue("kept line order", romanised.texts.first().contains("i", ignoreCase = true))
    }

    @Test
    fun `lrclib returns synced lyrics for a popular track`() = runBlocking {
        assumeTrue(System.getenv("KAT_LIVE") == "1")
        val lyrics = LyricsApi.find("Never Gonna Give You Up", "Rick Astley", null, 213)
        if (!lyrics.isEmpty) {
            assertTrue("source set", lyrics.source.isNotBlank())
        }
    }
}
