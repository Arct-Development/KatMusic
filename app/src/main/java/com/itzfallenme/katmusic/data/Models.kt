package com.itzfallenme.katmusic.data

import kotlinx.serialization.Serializable

/** A playable audio item. Everything is optional except id/title so that partial
 *  InnerTube payloads and persisted library entries can share one shape. */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String = "",
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val durationMs: Long = 0L,
    val artwork: String = "",
    val explicit: Boolean = false
) {
    val isPlayable: Boolean get() = id.isNotBlank()
}

enum class CardKind { ALBUM, PLAYLIST, ARTIST, MIX, SONG, SHOW }

/** A browse card (album / playlist / artist / mix) shown in any shelf. */
@Serializable
data class MediaCard(
    val title: String,
    val subtitle: String = "",
    val artwork: String = "",
    val browseId: String = "",
    val params: String? = null,
    val kind: CardKind = CardKind.ALBUM
) {
    val id: String get() = browseId
}

data class Shelf(
    val title: String? = null,
    val cards: List<MediaCard> = emptyList(),
    val tracks: List<Track> = emptyList()
) {
    val isEmpty: Boolean get() = cards.isEmpty() && tracks.isEmpty()
}

/** Page payload for album / playlist / artist style destinations. */
data class DetailPage(
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val artwork: String = "",
    val tracks: List<Track> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val kind: CardKind = CardKind.ALBUM,
    val year: String? = null,
    val trackCount: String? = null
)

data class StreamInfo(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val lengthMs: Long,
    val expiresAt: Long = 0L
)

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(
    val synced: List<LyricLine> = emptyList(),
    val plain: String = "",
    val source: String = ""
) {
    val isEmpty: Boolean get() = synced.isEmpty() && plain.isBlank()
    val isSynced: Boolean get() = synced.isNotEmpty()
}
