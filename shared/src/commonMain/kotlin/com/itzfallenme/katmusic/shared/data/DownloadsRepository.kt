package com.itzfallenme.katmusic.shared.data

import com.itzfallenme.katmusic.shared.models.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * One offline-downloaded track, in the shape the shared UI understands.
 *
 * [source] is always a playable location the platform player can open: a
 * `file://` URI on desktop, a `file://` or `content://` URI on Android.
 */
data class DownloadedTrack(
    /** Stable platform id ("dl:<rowId>" on Android, absolute path on desktop). */
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String? = null,
    /** Playable location. */
    val source: String,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val downloadedAtMillis: Long = 0L,
) {
    fun toTrack(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        source = source,
        artwork = artworkUrl,
        isLocal = true,
    )
}

/**
 * The offline library, shared by Android and desktop.
 *
 * Android stores real downloads in Room and deletes files through the app's
 * own [TrackDownloadManager]; desktop keeps an index plus the audio files in
 * its app-data folder. Both expose the same observable state so the shared
 * Downloads screen shows the real thing on each platform.
 */
interface DownloadsRepository {
    val downloads: StateFlow<List<DownloadedTrack>>
    val totalBytes: StateFlow<Long>

    /** Removes one downloaded track (record + underlying file(s)) for good. */
    suspend fun remove(id: String): Result<Unit>

    /** Removes every downloaded track. */
    suspend fun clearAll(): Result<Unit>

    fun formatBytes(bytes: Long): String = formatByteCount(bytes)
}

/**
 * Kilo/mega/giga formatting for download sizes, shared by every platform.
 *
 * Implemented with plain arithmetic instead of `String.format` so the exact
 * same result compiles on JVM, Android and Kotlin/Native (iOS).
 */
fun formatByteCount(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "${oneDecimal(bytes / (1L shl 30).toDouble())} GB"
    bytes >= 1L shl 20 -> "${oneDecimal(bytes / (1L shl 20).toDouble())} MB"
    bytes >= 1L shl 10 -> "${whole(bytes / (1L shl 10).toDouble())} KB"
    else -> "$bytes B"
}

private fun oneDecimal(value: Double): String {
    val tenths = kotlin.math.round(value * 10.0).toLong()
    return "${tenths / 10}.${tenths % 10}"
}

private fun whole(value: Double): String = kotlin.math.round(value).toLong().toString()
