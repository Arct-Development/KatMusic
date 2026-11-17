package com.itzfallenme.katmusic.shared.data

import com.itzfallenme.katmusic.shared.models.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * A track or collection card inside a home-feed section.
 *
 * [videoId] marks streamed content; [source] is only set for cards that can
 * start playing right away (a stream URL or a local file).
 */
data class HomeCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String? = null,
    val videoId: String? = null,
    val playlistId: String? = null,
) {
    val isPlaylist: Boolean get() = playlistId != null && videoId == null

    fun toTrack(durationMs: Long = 0L): Track? = videoId?.let { vid ->
        Track(
            id = vid,
            title = title,
            artist = subtitle,
            durationMs = durationMs,
            source = vid,
            artwork = artworkUrl,
            isLocal = false,
            videoId = vid,
        )
    }
}

/** One row of the home feed: a titled, scrollable section of cards. */
data class HomeSection(
    val title: String,
    val cards: List<HomeCard>,
)

/** State the shared Home screen renders. */
data class HomeFeedState(
    val isLoading: Boolean = false,
    val sections: List<HomeSection> = emptyList(),
    val error: String? = null,
)

/**
 * The Home feed, shared by Android and desktop.
 *
 * Android bridges the app's real InnerTube home (`StreamApi.fetchHomeShelves`)
 * including its BotGuard/auth infrastructure; desktop uses the Piped public
 * API for the feed and SimpMusic's extractor method for playback resolution
 * (`NewPipeStreamResolver`: PipePipe + BravePipe tiers, Piped as fallback).
 * Streamed tracks play through the platform player; when every provider
 * refuses anonymous playback the error surfaces in the UI — never silently.
 */
interface HomeFeedRepository {
    val state: StateFlow<HomeFeedState>

    suspend fun searchMusic(query: String): Result<List<HomeCard>>

    /**
     * Resolves a card into a immediately playable [Track].
     *
     * Implementations may do real network work here (desktop extractors) or
     * hand the player a videoId it resolves itself (Android Media3).
     */
    suspend fun resolvePlayback(card: HomeCard): Result<Track> =
        card.toTrack().let { track ->
            if (track != null) Result.success(track)
            else Result.failure(IllegalStateException("Card is not a playable track"))
        }

    suspend fun refresh()
}

/** Turns search/home results into a queue of [Track]s the player can use. */
fun List<HomeCard>.toTracks(durationMsById: Map<String, Long> = emptyMap()): List<Track> =
    mapNotNull { card -> card.toTrack(durationMsById[card.id] ?: 0L) }
