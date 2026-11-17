package com.itzfallenme.katmusic.desktop.jam

import com.itzfallenme.katmusic.shared.models.Track
import com.itzfallenme.katmusic.shared.player.PlayerController
import com.itzfallenme.katmusic.shared.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Desktop owner of a Listen Together room.
 *
 * It uses the exact room protocol Android uses. Joining a room mints a fresh
 * stream URL for the shared YouTube track through the desktop resolver, then
 * seeks to the room position; host changes are sent only for real track,
 * play/pause and seek changes, never for the one-second position tick.
 */
class DesktopJamManager(
    private val player: PlayerController,
    /** Desktop needs a direct audio URL; Android's Media3 accepts the video id. */
    private val resolveAudio: suspend (String) -> Result<String>,
    val client: ListenTogetherWebSocketClient = ListenTogetherWebSocketClient(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var lastRemoteTrackId: String? = null
    private var resolvingTrackId: String? = null
    private var lastPublishedTrackId: String? = null
    private var lastPublishedPlaying = false
    private var lastPublishedPosition = 0L
    private var lastPublishedAt = 0L

    @Volatile
    private var suppressPublishUntil = 0L

    init {
        scope.launch {
            player.state.collect(::publishIfChanged)
        }
        scope.launch {
            client.playback.collect(::syncRemotePlayback)
        }
        scope.launch {
            player.state.map { it.queue }.distinctUntilChanged().collect { queue ->
                if (client.isHost.value && !client.isCoHost.value) client.publishQueue(queue)
            }
        }
        scope.launch {
            client.isHost.collect { isHost ->
                if (isHost && !client.isCoHost.value) {
                    client.publishQueue(player.state.value.queue)
                    publishIfChanged(player.state.value)
                }
            }
        }
    }

    fun createRoom(endpoint: String, displayName: String, collaborative: Boolean = false) {
        _error.value = null
        client.createRoom(endpoint, displayName, collaborative)
    }

    fun joinRoom(endpoint: String, code: String, displayName: String) {
        _error.value = null
        client.joinRoom(endpoint, code, displayName)
    }

    /** Ask the host to add this device's current YouTube-backed track. */
    fun suggestCurrentTrack() {
        val track = player.state.value.current ?: return
        val videoId = track.videoId?.takeIf(String::isNotBlank) ?: return
        client.suggestTrack(track.title, track.artist, videoId, track.artwork.orEmpty())
    }

    fun leaveRoom() {
        client.leaveRoom()
        lastRemoteTrackId = null
        lastPublishedTrackId = null
    }

    fun close() {
        client.close()
        scope.cancel()
    }

    private fun publishIfChanged(state: PlayerState) {
        val now = System.currentTimeMillis()
        if (now < suppressPublishUntil) {
            rememberPublished(state, now)
            return
        }
        if (client.roomCode.value.isBlank()) return
        val canControl = client.isHost.value || client.roomMode.value == "collaborative"
        if (!canControl) return
        val track = state.current ?: return
        val videoId = track.videoId.orEmpty()
        if (videoId.isBlank()) return

        val expected = if (lastPublishedPlaying) {
            lastPublishedPosition + (now - lastPublishedAt).coerceIn(0L, 10_000L)
        } else {
            lastPublishedPosition
        }
        val action = when {
            videoId != lastPublishedTrackId -> "change_track"
            state.isPlaying != lastPublishedPlaying -> if (state.isPlaying) "play" else "pause"
            abs(state.positionMs - expected) > 2_000L -> "seek"
            else -> return
        }
        client.selectPlaybackAction(
            action = action,
            title = track.title,
            artist = track.artist,
            videoId = videoId,
            artworkUrl = track.artwork.orEmpty(),
            isPlaying = state.isPlaying,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
        )
        rememberPublished(state, now)
    }

    private fun rememberPublished(state: PlayerState, now: Long) {
        lastPublishedTrackId = state.current?.videoId
        lastPublishedPlaying = state.isPlaying
        lastPublishedPosition = state.positionMs
        lastPublishedAt = now
    }

    private fun syncRemotePlayback(remote: JamPlayback?) {
        if (remote == null || remote.videoId.isBlank()) return
        if (remote.fromUserId.isNotBlank() && remote.fromUserId == client.memberId.value) return
        val now = System.currentTimeMillis()
        val target = remote.positionAt(now) + 350L
        val current = player.state.value
        suppressPublishUntil = now + 3_000L

        if (lastRemoteTrackId != remote.videoId || current.current?.videoId != remote.videoId) {
            if (resolvingTrackId == remote.videoId) return
            resolvingTrackId = remote.videoId
            lastRemoteTrackId = remote.videoId
            scope.launch {
                val result = resolveAudio(remote.videoId)
                val url = result.getOrNull()
                if (url.isNullOrBlank()) {
                    resolvingTrackId = null
                    _error.value = "Jam couldn't resolve this track: " +
                        (result.exceptionOrNull()?.message ?: "no audio stream")
                    return@launch
                }
                _error.value = null
                // Extraction can take seconds; use the room clock at the
                // moment playback actually starts, not when the event arrived.
                val resumeAt = remote.positionAt(System.currentTimeMillis()) + 350L
                suppressPublishUntil = System.currentTimeMillis() + 3_000L
                player.play(
                    listOf(
                        Track(
                            id = remote.videoId,
                            title = remote.title,
                            artist = remote.artist,
                            durationMs = remote.durationMs,
                            source = url,
                            artwork = remote.artworkUrl.takeIf(String::isNotBlank),
                            isLocal = false,
                            videoId = remote.videoId,
                        ),
                    ),
                    startIndex = 0,
                    startPositionMs = resumeAt.coerceAtLeast(0L),
                )
                if (!remote.isPlaying) player.togglePlayPause()
                rememberPublished(player.state.value, System.currentTimeMillis())
                resolvingTrackId = null
            }
            return
        }

        if (abs(current.positionMs - target) > 2_000L) {
            player.seekTo(target.coerceAtLeast(0L))
        }
        if (remote.isPlaying != current.isPlaying) {
            player.togglePlayPause()
        }
        rememberPublished(player.state.value, now)
    }
}
