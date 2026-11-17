package com.itzfallenme.katmusic.shared.data

import com.itzfallenme.katmusic.shared.models.Track
import kotlinx.coroutines.flow.StateFlow

/** A podcast show that owns an RSS feed. */
data class PodcastShow(
    val id: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val feedUrl: String,
    val episodeCount: Int = 0,
)

/** One playable episode from a podcast RSS feed. */
data class PodcastEpisode(
    val id: String,
    val title: String,
    val description: String,
    val publishedAt: String,
    val durationMs: Long,
    val audioUrl: String,
    val artworkUrl: String?,
) {
    fun toTrack(show: PodcastShow): Track = Track(
        id = id,
        title = title,
        artist = show.title,
        album = show.title,
        genre = "Podcast",
        durationMs = durationMs,
        source = audioUrl,
        artwork = artworkUrl ?: show.artworkUrl,
        isLocal = false,
    )
}

/** What the shared Podcasts screen renders. */
data class PodcastsState(
    val isLoading: Boolean = false,
    val query: String = "",
    val shows: List<PodcastShow> = emptyList(),
    val selectedShow: PodcastShow? = null,
    val episodes: List<PodcastEpisode> = emptyList(),
    val error: String? = null,
)

/**
 * Podcast discovery and episode playback.
 *
 * Android already has its own podcast shelves; desktop implements this with
 * the iTunes directory plus the show's real RSS feed, so every list and every
 * episode URL comes from a live podcast source.
 */
interface PodcastsRepository {
    val state: StateFlow<PodcastsState>

    suspend fun search(query: String)
    suspend fun openShow(show: PodcastShow)
    fun backToResults()
}

/** Converts the visible episode list into the queue behind one episode. */
fun List<PodcastEpisode>.toQueue(show: PodcastShow): List<Track> = map { it.toTrack(show) }
