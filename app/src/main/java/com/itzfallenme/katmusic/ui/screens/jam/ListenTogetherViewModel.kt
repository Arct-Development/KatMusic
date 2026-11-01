package com.itzfallenme.katmusic.ui.screens.jam

// SPDX-License-Identifier: GPL-3.0-or-later

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itzfallenme.katmusic.data.jam.ListenTogetherWebSocketClient
import com.itzfallenme.katmusic.data.jam.JamTrackSuggestion
import com.itzfallenme.katmusic.data.jam.JamPlayback
import kotlinx.coroutines.flow.collect
import com.itzfallenme.katmusic.playback.MusicPlayer
import com.itzfallenme.katmusic.playback.PlayableTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListenTogetherViewModel @Inject constructor(val player: MusicPlayer) : ViewModel() {
    val client = ListenTogetherWebSocketClient()
    private var lastRemoteTrackId: String? = null

    init {
        viewModelScope.launch {
            player.state.sample(1_000).collect { state ->
                if (!client.isHost.value) return@collect
                val track = state.current ?: return@collect
                client.publishPlayback(
                    title = track.title, artist = track.artist, videoId = track.videoId.orEmpty(),
                    artworkUrl = track.artworkUrl.orEmpty(), isPlaying = state.isPlaying,
                    positionMs = state.positionMs, durationMs = state.durationMs,
                )
            }
        }
        viewModelScope.launch {
            client.playback.collect { remote -> syncRemotePlayback(remote) }
        }
        viewModelScope.launch {
            client.isHost.collect { host ->
                if (host) client.publishQueue(player.state.value.queue)
            }
        }
        viewModelScope.launch {
            player.state.map { it.queue }.distinctUntilChanged().collect { queue ->
                if (client.isHost.value) client.publishQueue(queue)
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
            if (kotlin.math.abs(current.positionMs - remote.positionMs) > 2_000L) player.seekTo(remote.positionMs + 350L)
            if (remote.isPlaying && !current.isPlaying) player.togglePlayPause()
            if (!remote.isPlaying && current.isPlaying) player.pause()
        }
    }

    fun createRoom(endpoint: String, name: String) = client.createRoom(endpoint, name)
    fun joinRoom(endpoint: String, code: String, name: String) = client.joinRoom(endpoint, code, name)
    fun suggestCurrentTrack() {
        val track = player.state.value.current ?: return
        val videoId = track.videoId?.takeIf(String::isNotBlank) ?: return
        client.suggestTrack(track.title, track.artist, videoId, track.artworkUrl.orEmpty())
    }

    fun playSuggestion(suggestion: JamTrackSuggestion) {
        if (suggestion.videoId.isBlank()) return
        player.play(
            PlayableTrack(title = suggestion.title, artist = suggestion.artist, videoId = suggestion.videoId, artworkUrl = suggestion.artworkUrl),
            sourceLabel = "Listen Together suggestion",
            startRadio = false,
        )
        client.dismissSuggestion(suggestion.videoId)
    }

    fun leaveRoom() { client.leaveRoom(); lastRemoteTrackId = null }

    override fun onCleared() {
        client.close()
        super.onCleared()
    }
}
