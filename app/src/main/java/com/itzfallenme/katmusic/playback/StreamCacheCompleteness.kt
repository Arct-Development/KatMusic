package com.itzfallenme.katmusic.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata

/**
 * True only when every byte of [key] is on disk *right now* and [position] is
 * inside the resource. Ported from SimpMusic's cache guard, where it protects
 * the same two failure modes:
 *
 * `KEY_CONTENT_LENGTH` alone is not proof. CacheDataSource records the length
 * as soon as an unbounded request learns it, long before the last byte lands,
 * so the range check is what actually proves completeness — dropping it lets a
 * half-downloaded resource pass as complete.
 *
 * [position] is checked because the recorded length can itself be short: an
 * upstream that closes its body early makes CacheDataSource store the truncated
 * length as if it were the whole thing. Serving that as complete means the
 * reader eventually asks for a position past the end, where CacheDataSource
 * throws `ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE` — another error Media3
 * refuses to retry. Falling back to a real resolve instead costs one request.
 *
 * The answer is a snapshot, not a lease: an evictor or a "clear cache" tap can
 * delete the spans immediately afterwards, so callers must keep the window they
 * trust this for short.
 */
@OptIn(UnstableApi::class)
internal fun Cache.isFullyCached(key: String, position: Long = 0L): Boolean {
    val total = ContentMetadata.getContentLength(getContentMetadata(key))
    return total > 0L && position < total && isCached(key, position, total - position)
}
