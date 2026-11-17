package com.itzfallenme.katmusic.desktop.data

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
import kotlinx.coroutines.withContext

/**
 * Desktop [HomeFeedRepository]: trending rows and music search over
 * [PipedClient]; playback resolved through [NewPipeStreamResolver] —
 * SimpMusic's desktop method (NewPipe-fork extractors that decode YouTube's
 * signature/n challenges and return real googlevideo URLs) — with Piped
 * instances as fallback. When both paths refuse, the error carries each
 * provider's own reason.
 */
class DesktopHomeFeedRepository(
    private val piped: PipedClient = PipedClient(),
    private val resolver: NewPipeStreamResolver = NewPipeStreamResolver(),
    private val spotifyPlaylists: DesktopSpotifyPlaylistClient = DesktopSpotifyPlaylistClient(),
    private val applePlaylists: DesktopAppleMusicPlaylistClient = DesktopAppleMusicPlaylistClient(),
) : HomeFeedRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(HomeFeedState())
    override val state: StateFlow<HomeFeedState> = _state.asStateFlow()

    /** Region currently driving the trending row (ISO 3166-1 alpha-2). */
    var activeRegion: String = "US"
        private set

    init {
        scope.launch { refreshAsync() }
    }

    override suspend fun refresh() {
        refreshRegion(activeRegion)
    }

    /**
     * Rebuilds the online rows for [region]. Piped's trending endpoint is
     * region-aware, so this is Desktop's real equivalent of Android's
     * regional release/discovery feed rather than a static "global" list.
     */
    suspend fun refreshRegion(region: String) {
        activeRegion = region.trim().uppercase().takeIf { it.length == 2 } ?: activeRegion
        _state.value = HomeFeedState(isLoading = true)
        piped.trending(activeRegion)
            .onSuccess { tracks ->
                _state.value = HomeFeedState(
                    sections = listOf(
                        HomeSection(
                            title = "Regional chart",
                            cards = tracks.map { it.toCard() },
                        ),
                    ),
                )
            }
            .onFailure { error ->
                _state.value = HomeFeedState(error = error.message ?: "Feed unavailable")
            }
    }

    override suspend fun searchMusic(query: String): Result<List<HomeCard>> =
        piped.searchMusic(query).map { tracks -> tracks.map { it.toCard() } }

    suspend fun fetchPlaylist(input: String): Result<PipedClient.PlaylistInfo> = piped.playlist(input)

    suspend fun fetchSpotifyPlaylist(input: String): Result<SpotifyPlaylistInfo> =
        spotifyPlaylists.fetchPlaylist(input)

    suspend fun fetchAppleMusicPlaylist(input: String): Result<AppleMusicPlaylistInfo> =
        applePlaylists.fetchPlaylist(input)

    suspend fun searchAlbums(query: String): Result<List<PipedClient.PlaylistSummary>> =
        piped.searchAlbums(query)

    suspend fun searchArtists(query: String): Result<List<PipedClient.ArtistSummary>> =
        piped.searchArtists(query)

    suspend fun fetchArtist(channelId: String): Result<PipedClient.ArtistInfo> =
        piped.artist(channelId)

    /**
     * Artist radio: provider-recommended tracks around a seed video. Used by
     * the artist page so desktop users get the same endless "more like this"
     * flow Android exposes from artists and tracks. Tracks keep the video id
     * as their source so the player resolves a fresh URL on playback.
     */
    suspend fun radio(seedVideoId: String): Result<List<Track>> =
        piped.radio(seedVideoId).map { tracks ->
            tracks.map { item ->
                Track(
                    id = item.videoId,
                    title = item.title,
                    artist = item.artist,
                    durationMs = item.durationMs,
                    source = item.videoId,
                    artwork = item.thumbnail,
                    isLocal = false,
                    videoId = item.videoId,
                )
            }
        }

    /**
     * Playback resolution uses SimpMusic's desktop method first — NewPipe-fork
     * extractors that decode the signature/n challenges and return real
     * googlevideo URLs — and falls back to Piped instances. When both paths
     * fail the error carries each provider's own reason.
     *
     * Runs on [Dispatchers.IO]: extraction is blocking network work.
     */
    override suspend fun resolvePlayback(card: HomeCard): Result<Track> = withContext(Dispatchers.IO) {
        val videoId = card.videoId
            ?: return@withContext Result.failure(IllegalStateException("“${card.title}” is not a stream"))
        val newPipe = runCatching { resolver.resolveAudioUrl(videoId).getOrThrow() }
        if (newPipe.isSuccess) {
            return@withContext Result.success(
                Track(
                    id = videoId,
                    title = card.title,
                    artist = card.subtitle,
                    source = newPipe.getOrThrow(),
                    artwork = card.artworkUrl,
                    isLocal = false,
                    videoId = videoId,
                ),
            )
        }
        val piped = piped.resolveStream(videoId)
        if (piped.isSuccess) {
            return@withContext Result.success(
                Track(
                    id = videoId,
                    title = card.title,
                    artist = card.subtitle,
                    source = piped.getOrThrow(),
                    artwork = card.artworkUrl,
                    isLocal = false,
                    videoId = videoId,
                ),
            )
        }
        Result.failure(
            IllegalStateException(
                listOfNotNull(
                    newPipe.exceptionOrNull()?.message?.let { "Extractors: $it" },
                    piped.exceptionOrNull()?.message?.let { "Piped: $it" },
                ).joinToString(" · "),
            ),
        )
    }

    private fun PipedClient.FeedTrack.toCard() = HomeCard(
        id = videoId,
        title = title,
        subtitle = artist,
        artworkUrl = thumbnail,
        videoId = videoId,
    )

    /** Fire-and-forget refresh for non-suspending callers. */
    fun refreshAsync() {
        scope.launch { refresh() }
    }
}
