package com.itzfallenme.katmusic.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Builds the discovery feed: fresh tracks pulled from the artists the listener actually
 * plays, interleaved so the list alternates instead of clumping into one artist.
 *
 * The seeds are the same signals the smart-mix generator uses - listening history,
 * favourites and recently saved tracks - so the radar keeps improving as the library
 * grows. Everything comes from the public YouTube Music catalogue, no account required.
 */
object Discover {

    data class Result(
        val tracks: List<Track> = emptyList(),
        val seeds: List<String> = emptyList(),
        val dna: List<GenreResolver.GenreSlice> = emptyList()
    )

    private fun seedArtists(library: LibraryRepository): List<Pair<String, String?>> {
        val state = library.state.value
        val fromHistory = state.history
            .sortedByDescending { it.playedAt }
            .map { it.track.artist to it.track.artistId }
        val fromLiked = state.liked.map { it.artist to it.artistId }
        val fromPlays = state.history.map { it.track.artist to it.track.artistId }
        return (fromHistory + fromLiked + fromPlays)
            .filter { (artist, _) -> artist.isNotBlank() && !artist.equals("Unknown", true) }
            .distinctBy { it.first.lowercase() }
    }

    private fun seedTracks(library: LibraryRepository): List<Track> {
        val state = library.state.value
        return (state.liked + state.history.map { it.track })
            .filter { it.isPlayable }
            .distinctBy { it.id }
            .take(12)
    }

    suspend fun feed(library: LibraryRepository, size: Int = 40): Result = coroutineScope {
        val artists = seedArtists(library)
        val seeds = seedTracks(library)
        if (artists.isEmpty() && seeds.isEmpty()) return@coroutineScope Result()

        val known = library.state.value.history.map { it.track.id }.toHashSet()

        val buckets = (seeds.map { track ->
            async { runCatching { InnerTube.upNext(track.id) }.getOrDefault(emptyList()) }
        } + artists.take(6).map { (artist, artistId) ->
            async {
                if (artistId != null) {
                    runCatching { InnerTube.detail(artistId).tracks }.getOrDefault(emptyList())
                } else {
                    runCatching { InnerTube.search("$artist songs") }.getOrDefault(emptyList())
                        .flatMap { it.tracks }
                        .filter { it.artist.contains(artist, ignoreCase = true) }
                }
            }
        }).mapNotNull { it.await().takeIf { list -> list.isNotEmpty() } }

        val out = ArrayList<Track>(size)
        val seen = HashSet<String>()
        var index = 0
        while (out.size < size && buckets.any { index < it.size }) {
            buckets.forEach { bucket ->
                val track = bucket.getOrNull(index) ?: return@forEach
                if (seen.add(track.id) && out.size < size) {
                    // Slightly prefer unheard tracks, but never filter history out entirely.
                    if (track.id !in known || out.size > size / 2) out.add(track)
                }
            }
            index++
            if (index > 60) break
        }

        if (out.size < size / 2) {
            // Thin history: top up with the catalogue's own recommendations.
            runCatching { InnerTube.explore() }.getOrDefault(emptyList())
                .flatMap { it.tracks }
                .forEach { track ->
                    if (out.size < size && seen.add(track.id)) out.add(track)
                }
        }

        val dna = runCatching { GenreResolver.dnaFromHistory(library) }.getOrDefault(emptyList())
        Result(
            tracks = out,
            seeds = artists.map { it.first }.take(6),
            dna = dna
        )
    }

    /** A radio that starts from one genre name resolved on the listener's behalf. */
    suspend fun genreRadio(genre: String, size: Int = 30): List<Track> {
        if (genre.isBlank()) return emptyList()
        val direct = runCatching { InnerTube.search("$genre") }.getOrDefault(emptyList())
            .flatMap { it.tracks }
        if (direct.size >= size / 2) return direct.distinctBy { it.id }.take(size)

        // Too few tracks for the genre word alone: seed a radio from the best match.
        val seed = direct.firstOrNull() ?: return direct.distinctBy { it.id }.take(size)
        val radio = runCatching { InnerTube.upNext(seed.id) }.getOrDefault(emptyList())
        return (direct + radio).distinctBy { it.id }.take(size)
    }
}
