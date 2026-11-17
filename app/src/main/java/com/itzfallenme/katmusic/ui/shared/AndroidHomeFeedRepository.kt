package com.itzfallenme.katmusic.ui.shared

import com.itzfallenme.katmusic.data.music.HomeShelf
import com.itzfallenme.katmusic.data.music.HomeShelfItem
import com.itzfallenme.katmusic.data.music.StreamApi
import com.itzfallenme.katmusic.shared.data.HomeCard
import com.itzfallenme.katmusic.shared.data.HomeFeedRepository
import com.itzfallenme.katmusic.shared.data.HomeFeedState
import com.itzfallenme.katmusic.shared.data.HomeSection
import com.itzfallenme.katmusic.shared.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android [HomeFeedRepository]: the app's real InnerTube home shelves
 * ([StreamApi.fetchHomeShelves]) and song search ([StreamApi.searchSongs]).
 *
 * Playback of a streamed card goes through the same MusicPlayer pipeline the
 * main app uses (BotGuard/PO token resolution included), so the shell can
 * start real YouTube Music playback — not a placeholder.
 */
@Singleton
class AndroidHomeFeedRepository @Inject constructor(
    private val streamApi: StreamApi,
) : HomeFeedRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(HomeFeedState())
    override val state: StateFlow<HomeFeedState> = _state.asStateFlow()

    init {
        scope.launch { refresh() }
    }

    override suspend fun refresh() {
        _state.value = HomeFeedState(isLoading = true)
        runCatching { streamApi.fetchHomeShelves() }
            .onSuccess { shelves ->
                _state.value = HomeFeedState(sections = shelves.map { it.toSection() }.filter { it.cards.isNotEmpty() })
            }
            .onFailure { error ->
                _state.value = HomeFeedState(error = error.message ?: "Couldn't reach YouTube Music")
            }
    }

    override suspend fun searchMusic(query: String): Result<List<HomeCard>> =
        runCatching {
            streamApi.searchSongs(query, limit = 24).map { track ->
                HomeCard(
                    id = track.videoId,
                    title = track.title,
                    subtitle = track.artist,
                    artworkUrl = track.artworkUrl,
                    videoId = track.videoId,
                )
            }
        }

    override suspend fun resolvePlayback(card: HomeCard): Result<Track> {
        val videoId = card.videoId
            ?: return Result.failure(IllegalStateException("“${card.title}” is not a stream"))
        // The Media3 engine resolves the stream itself from the videoId; the
        // shared Track just carries that id as its source.
        return Result.success(
            Track(
                id = videoId,
                title = card.title,
                artist = card.subtitle,
                source = videoId,
                artwork = card.artworkUrl,
                isLocal = false,
                videoId = videoId,
            ),
        )
    }

    private fun HomeShelf.toSection(): HomeSection = HomeSection(        title = title,
        cards = items.mapNotNull { item ->
            when (item) {
                is HomeShelfItem.Track -> HomeCard(
                    id = item.track.videoId,
                    title = item.track.title,
                    subtitle = item.track.artist,
                    artworkUrl = item.track.artworkUrl,
                    videoId = item.track.videoId,
                )
                is HomeShelfItem.Playlist -> HomeCard(
                    id = item.playlist.id,
                    title = item.playlist.title,
                    subtitle = item.playlist.author ?: "Playlist",
                    artworkUrl = item.playlist.artworkUrl,
                    playlistId = item.playlist.id,
                )
                else -> null
            }
        },
    )
}
