package com.itzfallenme.katmusic.ui.shared

import com.itzfallenme.katmusic.data.lyrics.LyricsRepository
import com.itzfallenme.katmusic.data.lyrics.LyricsResult as AppLyricsResult
import com.itzfallenme.katmusic.shared.lyrics.Lyrics
import com.itzfallenme.katmusic.shared.lyrics.LyricsLine
import com.itzfallenme.katmusic.shared.lyrics.LyricsProvider
import com.itzfallenme.katmusic.shared.lyrics.LyricsResult
import com.itzfallenme.katmusic.shared.models.Track
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Android [LyricsProvider]: the app's own [LyricsRepository], so the shared
 * lyrics screen gets the full provider chain (local `.lrc`, Apple Music, LRCLIB,
 * YouTube captions, and the rest) rather than a second, weaker client.
 *
 * Word-by-word syllables are not part of the shared model yet, so the request
 * asks for line-level timing; the Android-only lyrics page keeps the full
 * syllable-synced presentation.
 */
@Singleton
class AndroidLyricsProvider @Inject constructor(
    private val repository: LyricsRepository,
) : LyricsProvider {

    override suspend fun lyricsFor(track: Track): LyricsResult {
        val result = repository.getLyrics(
            title = track.title,
            artist = track.artist,
            album = track.album.takeIf { it.isNotBlank() },
            durationSeconds = (track.durationMs / 1000L).toInt().takeIf { it > 0 },
            wordByWord = false,
            videoId = track.id.takeIf { it.length == 11 && !it.contains(':') },
        )
        return when (result) {
            is AppLyricsResult.Success -> LyricsResult.Found(
                Lyrics(
                    lines = result.lines.map { LyricsLine(atMs = it.timeMs, text = it.text) },
                    plain = result.plainLyrics,
                    isSynced = result.isSynced,
                    isInstrumental = result.isInstrumental,
                    source = result.source,
                ),
            )
            AppLyricsResult.Empty -> LyricsResult.NotFound
            is AppLyricsResult.Error -> LyricsResult.Failed(result.message)
        }
    }
}
