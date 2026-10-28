package com.itzfallenme.katmusic.data

/** Builds a playlist from seed artists by pulling each artist's popular tracks. */
object SmartMix {

    data class Seed(val name: String, val artistId: String? = null, val weight: Int = 1)

    suspend fun seedsFromHistory(library: LibraryRepository): List<Seed> {
        val history = library.state.value.history
        if (history.isEmpty()) return emptyList()
        return history.groupBy { it.track.artist }
            .filterKeys { it.isNotBlank() && it != "Unknown" }
            .map { (artist, plays) ->
                Seed(
                    name = artist,
                    artistId = plays.firstNotNullOfOrNull { it.track.artistId },
                    weight = plays.size
                )
            }
            .sortedByDescending { it.weight }
            .take(8)
    }

    suspend fun generate(seeds: List<Seed>, size: Int = 25): List<Track> {
        if (seeds.isEmpty()) return emptyList()
        val buckets = ArrayList<List<Track>>()
        seeds.forEach { seed ->
            val tracks = when {
                seed.artistId != null -> runCatching {
                    InnerTube.detail(seed.artistId).tracks
                }.getOrDefault(emptyList())
                else -> runCatching {
                    InnerTube.search("${seed.name} songs")
                }.getOrDefault(emptyList())
                    .flatMap { it.tracks }
                    .filter { it.artist.contains(seed.name, ignoreCase = true) }
            }
            if (tracks.isNotEmpty()) buckets.add(tracks.distinctBy { it.id })
        }
        if (buckets.isEmpty()) return emptyList()

        // Round-robin through artists so the mix alternates rather than clumping.
        val out = ArrayList<Track>()
        val seen = HashSet<String>()
        var index = 0
        while (out.size < size && buckets.any { index < it.size }) {
            buckets.forEach { bucket ->
                val track = bucket.getOrNull(index)
                if (track != null && seen.add(track.id) && out.size < size) out.add(track)
            }
            index++
            if (index > 100) break
        }
        return out
    }

    fun recapTracks(library: LibraryRepository, sinceMs: Long): List<Track> {
        val history = library.state.value.history.filter { it.playedAt >= sinceMs }
        return history.groupingBy { it.track.id }.eachCount()
            .entries.sortedByDescending { it.value }
            .mapNotNull { entry -> history.firstOrNull { it.track.id == entry.key }?.track }
            .take(50)
    }
}
