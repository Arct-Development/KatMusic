package com.itzfallenme.katmusic.ui.screens.jam

// SPDX-License-Identifier: GPL-3.0-or-later

import androidx.lifecycle.ViewModel
import com.itzfallenme.katmusic.data.jam.JamTrackSuggestion
import com.itzfallenme.katmusic.data.jam.ListenTogetherManager
import com.itzfallenme.katmusic.playback.MusicPlayer
import com.itzfallenme.katmusic.playback.PlayableTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ListenTogetherViewModel @Inject constructor(
    val player: MusicPlayer,
    manager: ListenTogetherManager,
) : ViewModel() {
    private val manager = manager
    val client = manager.client

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

    fun leaveRoom() = manager.leaveRoom()
}
