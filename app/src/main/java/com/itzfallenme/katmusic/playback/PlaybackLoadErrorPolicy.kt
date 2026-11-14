package com.itzfallenme.katmusic.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException

/**
 * Load retry policy for progressive audio, borrowed from the way SimpMusic
 * treats stream failures.
 *
 * A dead signed URL is not a dead track: [ChunkedRangeDataSource] mints a fresh
 * URL on every open that follows a failure, so an `IOException` deserves an
 * immediate retry instead of Media3's one-second-then-doubling default. The
 * listener hears a short gap in the worst case, where the default schedule
 * spends several seconds failing against the same dead URL before the track is
 * parked.
 *
 * Anything that is not an [IOException] cannot be fixed by retrying — a codec
 * that rejects the container, a malformed item — so it is handed back at once
 * and the player moves on instead of sitting through backoff.
 */
@OptIn(UnstableApi::class)
internal class PlaybackLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (loadErrorInfo.exception !is IOException) return C.TIME_UNSET
        return when (loadErrorInfo.errorCount) {
            1 -> 0L
            2 -> 300L
            3 -> 900L
            else -> super.getRetryDelayMsFor(loadErrorInfo)
        }
    }

    /**
     * More attempts than the default three: each one opens a freshly resolved
     * URL, which is exactly the mechanism that keeps the song alive.
     */
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = RETRY_COUNT

    private companion object {
        const val RETRY_COUNT = 5
    }
}
