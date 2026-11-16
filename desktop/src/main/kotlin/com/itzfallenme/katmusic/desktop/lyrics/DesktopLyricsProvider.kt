package com.itzfallenme.katmusic.desktop.lyrics

import com.itzfallenme.katmusic.shared.lyrics.LrcParser
import com.itzfallenme.katmusic.shared.lyrics.Lyrics
import com.itzfallenme.katmusic.shared.lyrics.LyricsProvider
import com.itzfallenme.katmusic.shared.lyrics.LyricsResult
import com.itzfallenme.katmusic.shared.models.Track
import java.io.File
import java.net.URI

/**
 * Desktop lyrics, in the order the Android app uses: a `.lrc` file sitting next
 * to the audio file wins, then the online provider.
 *
 * Local files are the normal case on desktop, and sidecar lyrics are how
 * people actually keep them, so this is a real lookup rather than a fallback
 * that never fires. Matching ignores case (`Song.LRC`) and accepts `.lrc`
 * beside the track only — no guessing across folders.
 */
class DesktopLyricsProvider(
    private val remote: LyricsProvider = LrclibLyricsProvider(),
) : LyricsProvider {

    override suspend fun lyricsFor(track: Track): LyricsResult {
        val local = readSidecar(track)
        if (local != null) return LyricsResult.Found(local)
        return remote.lyricsFor(track)
    }

    private fun readSidecar(track: Track): Lyrics? {
        val audio = runCatching { File(URI(track.source)) }.getOrNull()
            ?: runCatching { File(track.id) }.getOrNull()
            ?: return null
        if (!audio.isAbsolute) return null
        val directory = audio.parentFile ?: return null
        val baseName = audio.nameWithoutExtension
        val candidate = directory.listFiles()
            ?.firstOrNull {
                it.isFile &&
                    it.nameWithoutExtension.equals(baseName, ignoreCase = true) &&
                    it.extension.equals("lrc", ignoreCase = true)
            }
            ?: return null
        val text = runCatching { candidate.readText() }.getOrNull()
        if (text.isNullOrBlank()) return null
        val lines = LrcParser.parse(text)
        if (lines.isEmpty()) return null
        return Lyrics(
            lines = lines,
            plain = text,
            source = "Local .lrc",
        )
    }
}
