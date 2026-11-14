package com.itzfallenme.katmusic.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ChunkedRangeDataSourceTest {
    @Test fun failedChunkOpenIsClosedBeforeRetry() {
        val upstream = FailingUpstream()
        val source = ChunkedRangeDataSource(upstream, chunkSize = 4)
        val spec = DataSpec.Builder().setUri("https://media.googlevideo.com/videoplayback").build()
        try {
            source.open(spec)
            fail("Expected the original request failure")
        } catch (expected: IOException) {
            assertEquals("request failed", expected.message)
        }
        assertFalse(upstream.open)
        assertEquals(1, upstream.closeCount)
        source.open(spec)
        assertTrue(upstream.open)
        source.close()
        assertFalse(upstream.open)
    }

    @Test fun failedPassThroughOpenIsAlsoClosed() {
        val upstream = FailingUpstream()
        val source = ChunkedRangeDataSource(upstream)
        val spec = DataSpec.Builder().setUri("https://example.test/audio").build()
        try {
            source.open(spec)
            fail("Expected the original request failure")
        } catch (_: IOException) { }
        assertFalse(upstream.open)
        source.open(spec)
        source.close()
        assertEquals(2, upstream.closeCount)
    }

    @Test fun deadChunkUrlIsRetriedOnARefreshedOne() {
        val deadUri = Uri.parse("https://media.googlevideo.com/videoplayback?expire=1")
        val freshUri = Uri.parse("https://media.googlevideo.com/videoplayback?expire=2")
        val upstream = UriCheckingUpstream(accepted = freshUri)
        var refreshCalls = 0
        val source = ChunkedRangeDataSource(
            upstream = upstream,
            chunkSize = 4,
            urlRefresher = StreamUrlRefresher { spec, _, force ->
                // Only a real failure may spend a resolve; the healthy path
                // asks once per chunk boundary and must get a cheap null.
                if (!force) return@StreamUrlRefresher null
                refreshCalls++
                spec.buildUpon().setUri(freshUri).build()
            },
        )
        val spec = DataSpec.Builder().setUri(deadUri).setLength(8).build()

        assertEquals(8L, source.open(spec))
        assertEquals(1, refreshCalls)
        assertEquals(freshUri, upstream.openedUri)
        assertEquals(deadUri, spec.uri)
    }

    @Test fun cacheOnlyStreamEndsInsteadOfReachingTheNetwork() {
        val upstream = FailingUpstream()
        val source = ChunkedRangeDataSource(upstream)
        // Only a span the cache does not hold reaches this source, for a
        // cache-only item: the track must end there, not fail.
        val spec = DataSpec.Builder()
            .setUri("${ChunkedRangeDataSource.OFFLINE_CACHE_SCHEME}://youtube:abc:251:webm")
            .build()

        source.open(spec)
        assertEquals(-1, source.read(ByteArray(16), 0, 16))
        assertFalse(upstream.open)
        source.close()
    }

    private class UriCheckingUpstream(private val accepted: Uri) : DataSource {
        var openedUri: Uri? = null
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long {
            if (dataSpec.uri != accepted) throw IOException("403 for ${dataSpec.uri}")
            openedUri = dataSpec.uri
            return dataSpec.length
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = -1
        override fun getUri(): Uri? = openedUri
        override fun close() = Unit
    }

    private class FailingUpstream : DataSource {
        var open = false
        var closeCount = 0
        private var first = true
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long {
            check(!open) { "Previous source was not closed" }
            open = true
            if (first) {
                first = false
                throw IOException("request failed")
            }
            return dataSpec.length
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = -1
        override fun getUri(): Uri? = null
        override fun close() { open = false; closeCount++ }
    }
}
