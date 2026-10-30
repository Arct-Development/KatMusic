package com.itzfallenme.katmusic.data.music

import com.itzfallenme.katmusic.data.ytmusic.YtConnection
import kotlinx.coroutines.CancellationException

/** One player request and its cipher decoder must share this configuration and account. */
internal class PlaybackCipherContext(
    val videoId: String,
    val playerUrl: String,
    val signatureTimestamp: Int,
    private val account: YtConnection,
) {
    fun requireMatches(requestedVideoId: String, currentAccount: YtConnection) {
        if (requestedVideoId != videoId || currentAccount != account) {
            throw CancellationException("Playback cipher context changed")
        }
    }

    // Never include the account, its cookies, or cipher material in diagnostics.
    override fun toString(): String = "PlaybackCipherContext(timestampPresent=true)"
}
