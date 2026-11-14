package com.itzfallenme.katmusic.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * Mints a replacement signed URL for the stream behind a chunk.
 *
 * Called on the player's loader thread with [force] set when the last read or
 * open of that URL failed, and with `force = false` at every chunk boundary so
 * an implementation can cheaply renew a URL that is about to expire. Returning
 * null means "keep the URL you have".
 */
fun interface StreamUrlRefresher {
    fun refresh(dataSpec: DataSpec, position: Long, force: Boolean): DataSpec?
}

/**
 * Wraps an upstream [DataSource] to issue chunked HTTP Range requests (~1 MB range chunks)
 * for YouTube / googlevideo media.
 *
 * This prevents YouTube's server-side bandwidth throttling on open-ended Range requests
 * and prevents HTTP 403 on client profiles (such as IOS) that strictly mandate bounded Range headers.
 *
 * A chunked request per megabyte also gives the player a natural place to renew the
 * URL: YouTube signs each `googlevideo` URL for a limited time and can start answering
 * 403 once it is throttled or rotated. Reusing a single signed URL for a whole track
 * meant the first dead chunk took the song down with it, which is what the optional
 * [urlRefresher] fixes — the failing window is retried on a freshly minted URL instead
 * of reaching ExoPlayer as a fatal load error.
 */
class ChunkedRangeDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
    private val urlRefresher: StreamUrlRefresher? = null,
) : DataSource {

    private var currentDataSpec: DataSpec? = null
    private var currentPosition: Long = 0L
    private var requestedLength: Long = C.LENGTH_UNSET.toLong()
    private var bytesReadTotal: Long = 0L
    private var currentChunkRemaining: Long = 0L
    private var isCurrentChunkOpen: Boolean = false

    /** The current URL failed; the next attempt must mint a new one first. */
    private var urlRefreshRequired: Boolean = false

    /** The stream is being served from cache alone and has no more to give. */
    private var offlineEndOfInput: Boolean = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        currentPosition = dataSpec.position
        requestedLength = dataSpec.length
        bytesReadTotal = 0L
        currentChunkRemaining = 0L
        offlineEndOfInput = false

        // Cache-only playback of a song whose audio is already on disk. Only a
        // span the cache does not hold reaches this source at all, so the song
        // ends at the end of the cached audio instead of failing the track.
        if (dataSpec.uri.scheme == OFFLINE_CACHE_SCHEME) {
            offlineEndOfInput = true
            return C.LENGTH_UNSET.toLong()
        }

        // A previous attempt on this URL failed. ExoPlayer's own retry comes
        // back through open() at the same position, so this is the one chance
        // to hand it a URL that can actually answer.
        if (urlRefreshRequired) {
            refreshCurrentUrl(force = true)
            urlRefreshRequired = false
        }
        val spec = currentDataSpec ?: dataSpec

        if (!isYouTube(spec) || (spec.length != C.LENGTH_UNSET.toLong() && spec.length <= chunkSize)) {
            // Non-YouTube requests or small requests pass through directly
            return openUpstream(spec)
        }

        openNextChunk()
        return if (requestedLength != C.LENGTH_UNSET.toLong()) requestedLength else C.LENGTH_UNSET.toLong()
    }

    private fun openNextChunk() {
        closeCurrentChunk()
        var spec = currentDataSpec ?: return

        // Renew before the next window is requested: an expired URL would fail
        // the chunk outright. While the URL is healthy the refresher answers
        // with null immediately, so this costs no round trip.
        if (refreshCurrentUrl(force = false)) {
            spec = currentDataSpec ?: spec
        }

        val chunkSpec = buildChunkSpec(spec) ?: run {
            currentChunkRemaining = 0L
            return
        }

        try {
            currentChunkRemaining = openChunk(chunkSpec)
            urlRefreshRequired = false
            return
        } catch (failure: IOException) {
            // The signed URL died mid-track (rotated, 403, throttle cut-off).
            // Mint a new one and retry this same window once: without this the
            // failure reaches ExoPlayer, whose retry re-opened the same dead
            // URL until the track was parked — the stop users hear a minute in.
            if (!refreshCurrentUrl(force = true)) {
                urlRefreshRequired = true
                throw failure
            }
        }

        val retriedSpec = buildChunkSpec(currentDataSpec ?: spec) ?: throw IOException("Chunk refresh left no data to read")
        currentChunkRemaining = openChunk(retriedSpec)
        urlRefreshRequired = false
    }

    private fun buildChunkSpec(spec: DataSpec): DataSpec? {
        val chunkLength = if (requestedLength != C.LENGTH_UNSET.toLong()) {
            val remaining = requestedLength - bytesReadTotal
            if (remaining <= 0) return null
            minOf(chunkSize, remaining)
        } else {
            chunkSize
        }

        return spec.buildUpon()
            .setPosition(currentPosition)
            .setLength(chunkLength)
            .build()
    }

    private fun openChunk(chunkSpec: DataSpec): Long {
        val opened = openUpstream(chunkSpec)
        return if (opened != C.LENGTH_UNSET.toLong()) opened else chunkSpec.length
    }

    private fun refreshCurrentUrl(force: Boolean): Boolean {
        val refresher = urlRefresher ?: return false
        val spec = currentDataSpec ?: return false
        if (spec.uri.scheme != "http" && spec.uri.scheme != "https") return false
        // A refresher that throws must never take playback down with it.
        val refreshed = runCatching { refresher.refresh(spec, currentPosition, force) }.getOrNull()
            ?: return false
        currentDataSpec = refreshed
        return true
    }

    private fun openUpstream(dataSpec: DataSpec): Long {
        // DefaultDataSource assigns its active source before open() completes.
        // Even a failed open therefore needs close() before the next request.
        isCurrentChunkOpen = true
        return try {
            upstream.open(dataSpec)
        } catch (error: Exception) {
            runCatching { upstream.close() }
            isCurrentChunkOpen = false
            currentChunkRemaining = 0L
            if (urlRefresher != null) urlRefreshRequired = true
            throw error
        }
    }

    private fun readUpstream(buffer: ByteArray, offset: Int, length: Int): Int =
        try {
            upstream.read(buffer, offset, length)
        } catch (error: IOException) {
            // A cut mid-window (stall, connection reset) is also a URL
            // problem: let the next open mint a fresh one.
            if (urlRefresher != null) urlRefreshRequired = true
            throw error
        }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offlineEndOfInput) return C.RESULT_END_OF_INPUT
        val spec = currentDataSpec ?: return C.RESULT_END_OF_INPUT

        if (!isYouTube(spec) || (requestedLength != C.LENGTH_UNSET.toLong() && requestedLength <= chunkSize)) {
            return readUpstream(buffer, offset, length)
        }

        if (requestedLength != C.LENGTH_UNSET.toLong() && bytesReadTotal >= requestedLength) {
            return C.RESULT_END_OF_INPUT
        }

        if (currentChunkRemaining <= 0) {
            openNextChunk()
            if (!isCurrentChunkOpen || currentChunkRemaining <= 0) {
                return C.RESULT_END_OF_INPUT
            }
        }

        val bytesToRead = minOf(length.toLong(), currentChunkRemaining).toInt()
        val bytesRead = readUpstream(buffer, offset, bytesToRead)
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            if (requestedLength == C.LENGTH_UNSET.toLong()) {
                return C.RESULT_END_OF_INPUT
            }
            openNextChunk()
            if (!isCurrentChunkOpen) return C.RESULT_END_OF_INPUT
            return readUpstream(buffer, offset, minOf(length.toLong(), currentChunkRemaining).toInt())
        }

        currentPosition += bytesRead
        bytesReadTotal += bytesRead
        currentChunkRemaining -= bytesRead
        return bytesRead
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        closeCurrentChunk()
        currentDataSpec = null
    }

    private fun closeCurrentChunk() {
        if (isCurrentChunkOpen) {
            runCatching { upstream.close() }
            isCurrentChunkOpen = false
        }
    }

    private fun isYouTube(dataSpec: DataSpec): Boolean = dataSpec.uri.isYouTubeMedia()

    private fun Uri.isYouTubeMedia(): Boolean =
        host?.let { host -> host.contains("googlevideo.com") || host.contains("youtube.com") } == true

    companion object {
        const val DEFAULT_CHUNK_SIZE = 1024 * 1024L // 1 MB chunk (within ~512 KB–2 MB)

        /** URI scheme for a track played only from [androidx.media3.datasource.cache.Cache] bytes. */
        const val OFFLINE_CACHE_SCHEME = "katmusic-cache"
    }
}
