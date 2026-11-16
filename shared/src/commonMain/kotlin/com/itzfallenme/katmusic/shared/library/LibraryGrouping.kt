package com.itzfallenme.katmusic.shared.library

import com.itzfallenme.katmusic.shared.models.Track

/**
 * Albums and artists derived from whatever the platform library returned.
 *
 * Both platforms hand the shared UI a flat track list (MediaStore on Android,
 * a folder walk on desktop); grouping is UI-independent logic, so it lives here
 * once instead of being reimplemented per screen and per platform.
 */
object LibraryGrouping {

    /** Tracks that belong to one album, in disc/track order. */
    data class Album(
        val album: String,
        val artist: String,
        val artwork: String?,
        val tracks: List<Track>,
    ) {
        val key: String get() = "$artist\u0000$album"
        val durationMs: Long get() = tracks.sumOf { it.durationMs }
    }

    /** Every track by one artist, across albums, in album-then-track order. */
    data class Artist(
        val artist: String,
        val artwork: String?,
        val tracks: List<Track>,
    ) {
        val key: String get() = artist
        val albumCount: Int get() = tracks.map { it.album.ifBlank { UNKNOWN_ALBUM } }.distinct().size
        val durationMs: Long get() = tracks.sumOf { it.durationMs }
    }

    const val UNKNOWN_ALBUM = "Unknown album"
    const val UNKNOWN_ARTIST = "Unknown artist"

    /** One album card per album name + album artist. */
    fun albums(tracks: List<Track>): List<Album> =
        tracks
            .groupBy { "${it.artist.orUncredited()}\u0000${it.album.orUnknown()}" }
            .values
            .map { group ->
                Album(
                    album = group.first().album.orUnknown(),
                    artist = group.first().artist.orUncredited(),
                    artwork = group.firstNotNullOfOrNull { it.artwork?.takeIf(String::isNotBlank) },
                    tracks = group.sortedWith(
                        compareBy<Track> { it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
                    ),
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album })

    /** One artist card per artist name. */
    fun artists(tracks: List<Track>): List<Artist> =
        tracks
            .groupBy { it.artist.orUncredited() }
            .map { (artist, group) ->
                Artist(
                    artist = artist,
                    artwork = group.firstNotNullOfOrNull { it.artwork?.takeIf(String::isNotBlank) },
                    tracks = group.sortedWith(
                        compareBy<Track> { it.album.orUnknown() }
                            .thenBy { it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
                    ),
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })

    private fun String?.orUnknown(): String = this?.trim()?.takeIf { it.isNotBlank() } ?: UNKNOWN_ALBUM
    private fun String?.orUncredited(): String = this?.trim()?.takeIf { it.isNotBlank() } ?: UNKNOWN_ARTIST
}
