package com.itzfallenme.katmusic.desktop.lyrics

import com.itzfallenme.katmusic.shared.lyrics.LyricsProvider
import com.itzfallenme.katmusic.shared.lyrics.LyricsResult
import com.itzfallenme.katmusic.shared.models.Track
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopLyricsProviderTest {

    private class NeverCalledRemote : LyricsProvider {
        var calls = 0
        override suspend fun lyricsFor(track: Track): LyricsResult {
            calls++
            return LyricsResult.NotFound
        }
    }

    private fun track(file: File) = Track(
        id = file.absolutePath,
        title = file.nameWithoutExtension,
        artist = "Test artist",
        album = "Test album",
        durationMs = 180_000L,
        source = file.toURI().toString(),
    )

    @Test
    fun `sidecar lrc wins over the online provider`(@TempDir dir: File) = runBlocking {
        val audio = File(dir, "Song.mp3").apply { writeText("not really audio") }
        File(dir, "Song.lrc").writeText("[00:01.00]first\n[00:02.50]second\n")
        val remote = NeverCalledRemote()

        val result = DesktopLyricsProvider(remote).lyricsFor(track(audio))

        assertTrue(result is LyricsResult.Found)
        val lyrics = (result as LyricsResult.Found).lyrics
        assertEquals(listOf("first", "second"), lyrics.lines.map { it.text })
        assertEquals(listOf(1_000L, 2_500L), lyrics.lines.map { it.atMs })
        assertEquals("Local .lrc", lyrics.source)
        assertTrue(lyrics.isSynced)
        assertEquals(0, remote.calls)
    }

    @Test
    fun `sidecar matching ignores case`(@TempDir dir: File) = runBlocking {
        val audio = File(dir, "Song.mp3").apply { writeText("not really audio") }
        File(dir, "SONG.LRC").writeText("[00:00.50]line\n")
        val remote = NeverCalledRemote()

        val result = DesktopLyricsProvider(remote).lyricsFor(track(audio))

        assertTrue(result is LyricsResult.Found)
        assertEquals(0, remote.calls)
    }

    @Test
    fun `a missing sidecar falls through to the online provider`(@TempDir dir: File) = runBlocking {
        val audio = File(dir, "Song.mp3").apply { writeText("not really audio") }
        // A lyrics file for a different song must not be used.
        File(dir, "Other.lrc").writeText("[00:01.00]wrong song\n")
        val remote = NeverCalledRemote()

        val result = DesktopLyricsProvider(remote).lyricsFor(track(audio))

        assertEquals(LyricsResult.NotFound, result)
        assertEquals(1, remote.calls)
    }

    @Test
    fun `an empty sidecar falls through to the online provider`(@TempDir dir: File) = runBlocking {
        val audio = File(dir, "Song.mp3").apply { writeText("not really audio") }
        File(dir, "Song.lrc").writeText("   \n\n")
        val remote = NeverCalledRemote()

        val result = DesktopLyricsProvider(remote).lyricsFor(track(audio))

        assertEquals(LyricsResult.NotFound, result)
        assertEquals(1, remote.calls)
    }

    /**
     * Live LRCLIB lookup, skipped unless `KATMUSIC_NETWORK_TESTS=1` so the
     * regular test task stays offline-safe.
     */
    @Test
    fun `lrclib returns lyrics for a well-known track`() = runBlocking {
        assumeTrue(System.getenv("KATMUSIC_NETWORK_TESTS") == "1")
        val result = LrclibLyricsProvider().lyricsFor(
            Track(
                id = "lrclib-smoke",
                title = "Smells Like Teen Spirit",
                artist = "Nirvana",
                album = "Nevermind",
                durationMs = 301_000L,
                source = "https://example.invalid/none.mp3",
                isLocal = false,
            ),
        )
        assertTrue(result is LyricsResult.Found, "expected lyrics, got $result")
        val lyrics = (result as LyricsResult.Found).lyrics
        assertTrue(lyrics.lines.isNotEmpty())
        assertEquals("LRCLIB", lyrics.source)
    }
}
