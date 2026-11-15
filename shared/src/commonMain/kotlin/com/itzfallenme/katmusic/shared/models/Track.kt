package com.itzfallenme.katmusic.shared.models

import kotlinx.serialization.Serializable

/**
 * One playable item, in the shape the shared UI understands.
 *
 * Platform code owns how it is produced (Android: MediaStore / streaming
 * resolution; desktop: local files), but the UI, queue and player state all
 * speak this type on both platforms.
 */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    /** Duration in milliseconds, 0 when unknown. */
    val durationMs: Long = 0L,
    /** Platform URI — a file:// URI on desktop, a content:// or https:// URI on Android. */
    val source: String,
    /** Optional artwork URI (file://, content:// or https://). */
    val artwork: String? = null,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val isLocal: Boolean = true,
) {
    val hasArtwork: Boolean get() = !artwork.isNullOrBlank()
}
