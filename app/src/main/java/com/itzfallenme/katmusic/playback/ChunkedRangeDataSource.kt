package com.itzfallenme.katmusic.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.EOFException

/**
 * Wraps an upstream [DataSource] to issue bounded HTTP Range requests for
 * YouTube / googlevideo media. Bounded requests avoid open-ended range
 * throttling and client profiles that reject large or unbounded reads.
 */
class ChunkedRangeDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
) : DataSource {

    private var currentDataSpec: DataSpec? = null
    private var currentPosition: Long = 0L
    private var requestedLength: Long = C.LENGTH_UNSET.toLong()
    private var bytesReadTotal: Long = 0L
    private var currentChunkRemaining: Long = 0L
    private var isCurrentChunkOpen: Boolean = false
    private var emptyChunkRetries: Int = 0

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        // A DataSource may be reused after an interrupted open/read. Never let
        // the previous request remain attached to the next media item.
        closeCurrentChunk()
        currentDataSpec = dataSpec
        currentPosition = dataSpec.position
        requestedLength = dataSpec.length
        bytesReadTotal = 0L
        currentChunkRemaining = 0L
        emptyChunkRetries = 0

        if (!dataSpec.isYouTubeMedia() ||
            (dataSpec.length != C.LENGTH_UNSET.toLong() && dataSpec.length <= chunkSize)
        ) {
            return openUpstream(dataSpec)
        }

        openNextChunk()
        return if (requestedLength != C.LENGTH_UNSET.toLong()) requestedLength else C.LENGTH_UNSET.toLong()
    }

    private fun openNextChunk() {
        closeCurrentChunk()
        val spec = currentDataSpec ?: return
        val chunkLength = if (requestedLength != C.LENGTH_UNSET.toLong()) {
            val remaining = requestedLength - bytesReadTotal
            if (remaining <= 0L) return
            minOf(chunkSize, remaining)
        } else {
            chunkSize
        }

        val chunkSpec = spec.buildUpon()
            .setPosition(currentPosition)
            .setLength(chunkLength)
            .build()

        val opened = openUpstream(chunkSpec)
        currentChunkRemaining = if (opened != C.LENGTH_UNSET.toLong()) {
            opened.coerceAtLeast(0L)
        } else {
            chunkLength
        }
    }

    /**
     * 416 is the normal response when the next bounded range starts exactly
     * at EOF and the server does not expose the total length in the response.
     * Other HTTP errors (especially 403) must reach ExoPlayer so playback
     * recovery can resolve a fresh URL instead of silently ending the song.
     */
    private fun openNextChunkOrEnd(): Boolean {
        return try {
            openNextChunk()
            isCurrentChunkOpen && currentChunkRemaining > 0L
        } catch (error: HttpDataSource.InvalidResponseCodeException) {
            if (error.responseCode != 416) throw error
            closeCurrentChunk()
            false
        }
    }

    private fun openUpstream(dataSpec: DataSpec): Long {
        closeCurrentChunk()
        // DefaultDataSource can set its active source before open() throws, so
        // close on failure as well as before a normal range transition.
        isCurrentChunkOpen = true
        return try {
            upstream.open(dataSpec)
        } catch (error: Exception) {
            closeCurrentChunk()
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val spec = currentDataSpec ?: return C.RESULT_END_OF_INPUT

        if (!spec.isYouTubeMedia() ||
            (requestedLength != C.LENGTH_UNSET.toLong() && requestedLength <= chunkSize)
        ) {
            return upstream.read(buffer, offset, length)
        }
        if (length == 0) return 0
        if (requestedLength != C.LENGTH_UNSET.toLong() && bytesReadTotal >= requestedLength) {
            return C.RESULT_END_OF_INPUT
        }

        while (true) {
            if (currentChunkRemaining <= 0L && !openNextChunkOrEnd()) {
                return C.RESULT_END_OF_INPUT
            }

            val bytesToRead = minOf(length.toLong(), currentChunkRemaining).toInt()
            val bytesRead = upstream.read(buffer, offset, bytesToRead)
            if (bytesRead == C.RESULT_END_OF_INPUT) {
                if (requestedLength != C.LENGTH_UNSET.toLong() && bytesReadTotal >= requestedLength) {
                    return C.RESULT_END_OF_INPUT
                }

                // An early EOF from a bounded request is not necessarily the
                // end of the media. Re-open from the last confirmed byte once;
                // if the same range is empty again, surface an error so the
                // player can refresh the signed URL instead of stopping early.
                emptyChunkRetries++
                if (emptyChunkRetries > MAX_EMPTY_CHUNK_RETRIES) {
                    throw EOFException("YouTube range ended before the requested chunk was complete")
                }
                if (!openNextChunkOrEnd()) return C.RESULT_END_OF_INPUT
                continue
            }
            if (bytesRead <= 0) return bytesRead

            emptyChunkRetries = 0
            currentPosition += bytesRead
            bytesReadTotal += bytesRead
            currentChunkRemaining -= bytesRead
            return bytesRead
        }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        closeCurrentChunk()
        currentDataSpec = null
        currentPosition = 0L
        requestedLength = C.LENGTH_UNSET.toLong()
        bytesReadTotal = 0L
        currentChunkRemaining = 0L
        emptyChunkRetries = 0
    }

    private fun closeCurrentChunk() {
        if (isCurrentChunkOpen) {
            runCatching { upstream.close() }
            isCurrentChunkOpen = false
        }
        currentChunkRemaining = 0L
    }

    private fun DataSpec.isYouTubeMedia(): Boolean =
        uri.host?.lowercase()?.let { host ->
            host.contains("googlevideo.com") || host.contains("youtube.com")
        } == true

    companion object {
        const val DEFAULT_CHUNK_SIZE = 1024 * 1024L
        private const val MAX_EMPTY_CHUNK_RETRIES = 1
    }
}
