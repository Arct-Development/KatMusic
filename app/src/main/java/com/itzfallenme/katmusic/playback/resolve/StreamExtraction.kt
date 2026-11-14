package com.itzfallenme.katmusic.playback.resolve

import android.util.Log
import com.itzfallenme.katmusic.data.music.YouTubeAudioStream
import com.itzfallenme.katmusic.data.music.audioCodecFamily
import com.itzfallenme.katmusic.data.music.containedAudioResolutionFailure
import com.itzfallenme.katmusic.data.music.normalizeCodecFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentHashMap

/**
 * One player extract per YouTube video id.
 * A second caller waits on the same job. Cancelling the caller does not
 * cancel the job, so a prefetch that the user skips still writes the URL.
 */
class StreamExtraction(
    parent: CoroutineScope,
    private val cache: PlaybackStreamCache,
    private val extract: suspend (youtubeVideoId: String, preferredCodec: String?) -> YouTubeAudioStream,
) {
    // Independent of the caller's job. Cancelling prefetch or playback
    // must not cancel an extract that is about to write the URL.
    // The scope ends when the player scope ends.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + containedAudioResolutionFailure(TAG),
    )

    init {
        parent.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
            scope.cancel()
        }
    }
    private val flights = ConcurrentHashMap<String, Deferred<YouTubeAudioStream>>()
    private val lock = Any()

    suspend fun await(
        youtubeVideoId: String,
        preferredCodec: String? = null,
    ): YouTubeAudioStream {
        // A flight pinned to a codec family must not be satisfied by one that
        // was started without it (or with the other family): the caller would
        // receive a different rendition than the one it is trying to keep.
        val flightKey = preferredCodec
            ?.let { normalizeCodecFamily(it) }
            ?.let { family -> "$youtubeVideoId|$family" }
            ?: youtubeVideoId
        val flight = synchronized(lock) {
            val current = flights[flightKey]
            if (current != null && current.isActive) {
                Log.i(TAG, "[EXTRACT_JOIN] youtubeVideoId=$youtubeVideoId")
                current
            } else {
                val created = scope.async { execute(youtubeVideoId, preferredCodec) }
                flights[flightKey] = created
                created.invokeOnCompletion { flights.remove(flightKey, created) }
                created
            }
        }
        return flight.await()
    }

    private suspend fun execute(
        youtubeVideoId: String,
        preferredCodec: String?,
    ): YouTubeAudioStream {
        val started = System.nanoTime()
        Log.i(TAG, "[EXTRACT_START] youtubeVideoId=$youtubeVideoId")
        try {
            cache.get(TrackIdentity(youtubeVideoId, youtubeVideoId))?.let { hit ->
                val hitStream = hit.toYouTubeAudioStream()
                val wanted = normalizeCodecFamily(preferredCodec)
                val matches = wanted == null ||
                    audioCodecFamily(hitStream.mimeType, hitStream.codec) == wanted
                if (matches) {
                    Log.i(
                        TAG,
                        "[EXTRACT_END] youtubeVideoId=$youtubeVideoId elapsedMs=${elapsedSince(started)} cached=true",
                    )
                    return hitStream
                }
            }
            val stream = extract(youtubeVideoId, preferredCodec)
            if (stream.videoId == youtubeVideoId && stream.url.isNotBlank()) {
                cache.putIfAbsent(stream.toStreamCache(TrackIdentity(youtubeVideoId, youtubeVideoId)))
            }
            Log.i(
                TAG,
                "[EXTRACT_END] youtubeVideoId=$youtubeVideoId elapsedMs=${elapsedSince(started)} " +
                    "cached=${stream.videoId == youtubeVideoId && stream.url.isNotBlank()}",
            )
            return stream
        } catch (error: Throwable) {
            Log.i(
                TAG,
                "[EXTRACT_END] youtubeVideoId=$youtubeVideoId elapsedMs=${elapsedSince(started)} " +
                    "cached=false error=${error::class.java.simpleName}",
            )
            throw error
        }
    }

    private companion object {
        const val TAG = "KatMusicResolver"
    }
}
