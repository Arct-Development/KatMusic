package com.itzfallenme.katmusic.ui.shared

import androidx.media3.common.Player
import com.itzfallenme.katmusic.playback.MediaType
import com.itzfallenme.katmusic.playback.MusicPlayer
import com.itzfallenme.katmusic.playback.PlayableTrack
import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerController
import com.itzfallenme.katmusic.shared.player.PlayerState
import com.itzfallenme.katmusic.shared.player.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Android [PlayerController]: the shared UI driving the app's real
 * Media3 engine ([MusicPlayer]) — the same instance the MediaSession,
 * notification, widgets and USB output already use. Nothing is duplicated or
 * stubbed: every call here lands on the process-wide player.
 *
 * Queue order and repeat semantics come from the shared [com.itzfallenme.katmusic.shared.player.QueueNavigator]
 * only where the engine delegates; Media3 owns its own queue transitions, so
 * this controller reports Media3's state and asks Media3 to advance, which
 * keeps one source of truth for what is actually playing.
 */
@Singleton
class Media3PlayerController @Inject constructor(
    private val player: MusicPlayer,
) : PlayerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastVolume: Float = 1f

    override val state: StateFlow<PlayerState> = player.state
        .map { it.toSharedState(lastVolume) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = player.state.value.toSharedState(lastVolume),
        )

    override fun play(tracks: List<Track>, startIndex: Int, startPositionMs: Long) {
        if (tracks.isEmpty()) return
        val queue = tracks.map { it.toPlayableTrack() }
        player.playQueue(
            tracks = queue,
            startIndex = startIndex.coerceIn(0, queue.lastIndex),
            sourceLabel = "KatMusic",
        )
        if (startPositionMs > 0L) player.seekTo(startPositionMs.coerceAtLeast(0L))
    }

    override fun togglePlayPause() = player.togglePlayPause()

    override fun next() = player.next()

    override fun previous() = player.previous()

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs.coerceAtLeast(0L))

    override fun setVolume(volume: Float) {
        lastVolume = volume.coerceIn(0f, 1f)
        player.setOutputVolume(lastVolume)
    }

    override fun setRepeatMode(mode: RepeatMode) = player.setRepeatMode(
        when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        },
    )

    override fun setShuffle(enabled: Boolean) = player.setShuffleEnabled(enabled)

    override fun cycleRepeatMode() = player.cycleRepeatMode()

    override fun stop() = player.stopAndClear()
}

/** Shared model -> the engine's playable item. */
private fun Track.toPlayableTrack(): PlayableTrack = PlayableTrack(
    title = title,
    artist = artist,
    album = album.ifBlank { null },
    artworkUrl = artwork,
    videoId = id.takeIf { it.length == 11 && id.none { c -> c == ':' } },
    internalTrackId = id,
    playbackUrl = source,
    durationMs = durationMs.takeIf { it > 0L },
    mediaType = MediaType.SONG,
    trackNumber = trackNumber.takeIf { it > 0 },
)

/** Engine state -> the shape the shared UI draws. */
private fun com.itzfallenme.katmusic.playback.MusicPlayerState.toSharedState(volume: Float): PlayerState =
    PlayerState(
        queue = queue.map { it.toSharedTrack() },
        index = currentIndex,
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        positionMs = positionMs.coerceAtLeast(0L),
        durationMs = durationMs.coerceAtLeast(0L),
        volume = volume,
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        },
        shuffle = shuffleEnabled,
        error = error,
    )

private fun PlayableTrack.toSharedTrack(): Track = Track(
    id = internalTrackId ?: videoId ?: "$artist|$title",
    title = title,
    artist = artist,
    album = album.orEmpty(),
    durationMs = durationMs ?: 0L,
    source = playbackUrl ?: videoId.orEmpty(),
    artwork = artworkUrl,
    trackNumber = trackNumber ?: 0,
    isLocal = playbackUrl?.startsWith("content://") == true,
)
