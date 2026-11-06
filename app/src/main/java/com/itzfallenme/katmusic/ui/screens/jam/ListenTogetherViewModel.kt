package com.itzfallenme.katmusic.ui.screens.jam

// SPDX-License-Identifier: GPL-3.0-or-later

import androidx.lifecycle.ViewModel
import com.itzfallenme.katmusic.data.jam.JamTrackSuggestion
import com.itzfallenme.katmusic.data.jam.ListenTogetherManager
import com.itzfallenme.katmusic.playback.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ListenTogetherViewModel @Inject constructor(
    val player: MusicPlayer,
    manager: ListenTogetherManager,
) : ViewModel() {
    private val manager = manager
    val client = manager.client

    fun createRoom(endpoint: String, name: String, collaborative: Boolean) =
        client.createRoom(endpoint, name, collaborative)

    fun joinRoom(endpoint: String, code: String, name: String) =
        client.joinRoom(endpoint, code, name)

    fun suggestCurrentTrack() {
        val track = player.state.value.current ?: return
        val videoId = track.videoId?.takeIf(String::isNotBlank) ?: return
        client.suggestTrack(track.title, track.artist, videoId, track.artworkUrl.orEmpty())
    }

    fun approveSuggestion(suggestion: JamTrackSuggestion) =
        client.approveSuggestion(suggestion.suggestionId)

    fun rejectSuggestion(suggestion: JamTrackSuggestion) =
        client.rejectSuggestion(suggestion.suggestionId)

    fun setCollaborative(enabled: Boolean) = client.setCollaborative(enabled)
    fun makeCoHost(memberId: String) = client.grantCoHost(memberId)
    fun removeCoHost(memberId: String) = client.revokeCoHost(memberId)
    fun leaveRoom() = manager.leaveRoom()
}
