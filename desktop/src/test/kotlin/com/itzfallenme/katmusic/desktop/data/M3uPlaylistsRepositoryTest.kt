package com.itzfallenme.katmusic.desktop.data

import com.itzfallenme.katmusic.shared.models.Track
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M3U round-trip and repository behaviour against real files in a temp dir —
 * the same `.m3u` files any desktop player can open.
 */
class M3uPlaylistsRepositoryTest {

    @TempDir
    lateinit var dir: File

    private fun track(path: File, title: String = path.nameWithoutExtension, artist: String = "Test artist") =
        Track(
            id = path.absolutePath,
            title = title,
            artist = artist,
            durationMs = 180_000L,
            source = path.toURI().toString(),
            isLocal = true,
        )

    @Test
    fun `m3u entry parses back to the same path, title and artist`() {
        val audio = File(dir, "song.mp3").apply { writeText("x") }
        val entry = M3uPlaylistsRepository.m3uEntry(track(audio), audio.absolutePath)

        assertTrue(entry.startsWith("#EXTINF:180,Test artist - song\n"))
        val parsed = M3uPlaylistsRepository.parseM3u("#EXTM3U\n$entry", dir)

        assertEquals(1, parsed.size)
        assertEquals(audio.absolutePath, parsed[0].first)
        assertEquals("song", parsed[0].second?.title)
        assertEquals("Test artist", parsed[0].second?.artist)
        assertEquals(180_000L, parsed[0].second?.durationMs)
    }

    @Test
    fun `missing files survive the round trip without a track`() {
        val content = "#EXTM3U\n#EXTINF:200,Gone Artist - Lost Song\n${File(dir, "missing.mp3").absolutePath}\n"
        val parsed = M3uPlaylistsRepository.parseM3u(content, dir)

        assertEquals(1, parsed.size)
        assertEquals(null, parsed[0].second)
    }

    @Test
    fun `create, add and remove round-trip through the m3u file`() = kotlinx.coroutines.runBlocking {
        val repo = M3uPlaylistsRepository(dir.toPath())
        kotlinx.coroutines.delay(200) // init's load() finishes before we assert
        val audio = File(dir, "real.mp3").apply { writeText("x") }

        val playlist = repo.create("Road trip").getOrThrow()
        assertTrue(repo.playlists.value.any { it.title == "Road trip" })

        repo.addToPlaylist(playlist.id, track(audio)).getOrThrow()
        val withTrack = repo.playlists.value.first { it.id == playlist.id }
        assertEquals(1, withTrack.trackCount)

        repo.removeFromPlaylist(playlist.id, audio.absolutePath).getOrThrow()
        val emptied = repo.playlists.value.first { it.id == playlist.id }
        assertEquals(0, emptied.trackCount)

        // The file itself is a valid M3U the whole time.
        assertTrue(File(playlist.id).readText().startsWith("#EXTM3U"))
    }

    @Test
    fun `delete removes the m3u file`() = kotlinx.coroutines.runBlocking {
        val repo = M3uPlaylistsRepository(dir.toPath())
        kotlinx.coroutines.delay(200)
        val playlist = repo.create("Gone soon").getOrThrow()
        val file = File(playlist.id)

        assertTrue(file.isFile)
        repo.delete(playlist.id).getOrThrow()
        assertTrue(!file.exists())
    }

    @Test
    fun `rejects adding a stream track to a local m3u playlist`() = kotlinx.coroutines.runBlocking {
        val repo = M3uPlaylistsRepository(dir.toPath())
        kotlinx.coroutines.delay(200)
        val playlist = repo.create("Local only").getOrThrow()
        val stream = Track(
            id = "abc12345678",
            title = "Stream",
            artist = "Artist",
            source = "abc12345678",
            isLocal = false,
        )

        val result = repo.addToPlaylist(playlist.id, stream)

        assertTrue(result.isFailure)
        assertEquals(0, repo.playlists.value.first { it.id == playlist.id }.trackCount)
    }
}
