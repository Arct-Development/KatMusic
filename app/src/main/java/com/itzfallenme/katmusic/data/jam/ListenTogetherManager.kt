package com.itzfallenme.katmusic.data.jam

// SPDX-License-Identifier: GPL-3.0-or-later

import com.itzfallenme.katmusic.playback.MusicPlayer
import com.itzfallenme.katmusic.playback.PlayableTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * App-wide owner of the Listen Together session.
 *
 * The screen's [androidx.lifecycle.ViewModel] used to own the socket and the
 * playback bridge, so closing the screen closed the socket: the room dropped
 * the listener, the host role was reassigned, and a host who browsed to another
 * screen stopped driving shared playback. The manager lives for the process
 * instead, which keeps publishing the host's playback and applying the host's
 * state for guests until they explicitly leave.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ListenTogetherManager @Inject constructor(
    private val player: MusicPlayer,
    val client: ListenTogetherWebSocketClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastRemoteTrackId: String? = null

    init {
        scope.launch {
            player.state.sample(1_000).collect { state ->
                if (!client.isHost.value) return@collect
                val track = state.current ?: return@collect
                client.publishPlayback(
                    title = track.title,
                    artist = track.artist,
                    videoId = track.videoId.orEmpty(),
                    artworkUrl = track.artworkUrl.orEmpty(),
                    isPlaying = state.isPlaying,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                )
            }
        }
        scope.launch {
            client.playback.collect { remote -> syncRemotePlayback(remote) }
        }
        scope.launch {
            player.state.map { it.queue }.distinctUntilChanged().collect { queue ->
                if (client.isHost.value) client.publishQueue(queue)
            }
        }
        scope.launch {
            client.isHost.collect { host ->
                if (host) client.publishQueue(player.state.value.queue)
            }
        }
    }

    private fun syncRemotePlayback(remote: JamPlayback?) {
        if (remote == null || client.isHost.value || remote.videoId.isBlank()) return
        val current = player.state.value
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
                startPositionMs = remote.positionMs + 350L,
            )
            if (!remote.isPlaying) player.pause()
        } else if (current.current?.videoId == remote.videoId) {
            if (kotlin.math.abs(current.positionMs - remote.positionMs) > 2_000L) {
                player.seekTo(remote.positionMs + 350L)
            }
            if (remote.isPlaying && !current.isPlaying) player.togglePlayPause()
            if (!remote.isPlaying && current.isPlaying) player.pause()
        }
    }

    fun leaveRoom() {
        client.leaveRoom()
        lastRemoteTrackId = null
    }
}
