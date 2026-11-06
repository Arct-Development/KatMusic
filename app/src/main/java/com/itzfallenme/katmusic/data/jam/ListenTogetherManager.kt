package com.itzfallenme.katmusic.data.jam

// SPDX-License-Identifier: GPL-3.0-or-later

import com.itzfallenme.katmusic.playback.MusicPlayer
import com.itzfallenme.katmusic.playback.PlayableTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * App-wide owner of the Listen Together session.
 *
 * The socket outlives the screen. Only meaningful playback changes are sent:
 * a track change, a play/pause transition, or a real seek. A one-second sample
 * of a naturally advancing position used to flood the room with updates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ListenTogetherManager @Inject constructor(
    private val player: MusicPlayer,
    val client: ListenTogetherWebSocketClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastRemoteTrackId: String? = null
    private var lastPublishedTrackId: String? = null
    private var lastPublishedPlaying = false
    private var lastPublishedPosition = 0L
    private var lastPublishedAt = 0L
    @Volatile private var suppressPublishUntil = 0L

    init {
        scope.launch {
            player.state.collect { state -> publishIfChanged(state) }
        }
        scope.launch {
            client.playback.collect { remote -> syncRemotePlayback(remote) }
        }
        scope.launch {
            player.state.map { it.queue }.distinctUntilChanged().collect { queue ->
                if (client.isHost.value && !client.isCoHost.value) client.publishQueue(queue)
            }
        }
        scope.launch {
            client.isHost.collect { host ->
                if (host && !client.isCoHost.value) {
                    client.publishQueue(player.state.value.queue)
                    publishIfChanged(player.state.value)
                }
            }
        }
    }

    private fun publishIfChanged(state: com.itzfallenme.katmusic.playback.MusicPlayerState) {
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
            artworkUrl = track.artworkUrl.orEmpty(),
            isPlaying = state.isPlaying,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
        )
        rememberPublished(state, now)
    }

    private fun rememberPublished(state: com.itzfallenme.katmusic.playback.MusicPlayerState, now: Long) {
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
        suppressPublishUntil = now + 1_500L

        if (lastRemoteTrackId != remote.videoId || current.current?.videoId != remote.videoId) {
            lastRemoteTrackId = remote.videoId
            player.play(
                PlayableTrack(
                    title = remote.title,
                    artist = remote.artist,
                    artworkUrl = remote.artworkUrl.takeIf(String::isNotBlank),
                    videoId = remote.videoId,
                    durationMs = remote.durationMs.takeIf { it > 0 },
                ),
                sourceLabel = "Listen Together",
                startRadio = false,
                startPositionMs = target.coerceAtLeast(0L),
            )
            if (!remote.isPlaying) player.pause()
            rememberPublished(player.state.value, now)
            return
        }

        if (abs(current.positionMs - target) > 2_000L) {
            player.seekTo(target.coerceAtLeast(0L))
        }
        if (remote.isPlaying && !current.isPlaying) {
            player.togglePlayPause()
        } else if (!remote.isPlaying && current.isPlaying) {
            player.pause()
        }
        rememberPublished(player.state.value, now)
    }

    fun leaveRoom() {
        client.leaveRoom()
        lastRemoteTrackId = null
        lastPublishedTrackId = null
    }
}
