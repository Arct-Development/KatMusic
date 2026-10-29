package com.itzfallenme.katmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Genre tags for artists and tracks.
 *
 * YouTube Music carries no genre field, so this follows the LastWave approach: Last.fm
 * `track.getInfo` tags when the user has configured an API key, and the iTunes search
 * catalogue as the keyless fallback - the only public source that labels mainstream
 * artists consistently without credentials. Results are cached for the session because
 * the charts resolve the same handful of artists repeatedly.
 */
object GenreResolver {

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val CACHE_MAX = 400

    private val cache = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > CACHE_MAX
    }

    private val lock = Any()

    /** Genres that say nothing about taste, so they never reach the DNA chart. */
    private val generic = setOf("music", "singer/songwriter", "soundtrack", "spoken word")

    fun cachedGenre(query: String): String? = synchronized(lock) {
        cache[query.lowercase()]?.takeIf { it.isNotBlank() }
    }

    /** Primary genre for an artist, or "" when nothing usable is known. */
    suspend fun artistGenre(artist: String): String {
        if (artist.isBlank()) return ""
        val key = "artist:${artist.lowercase()}"
        synchronized(lock) { cache[key]?.let { return it } }
        if (artist.equals("Unknown", true) || artist.equals("Various Artists", true)) return ""

        val fromTags = lastFmTags(artist = artist)
        val genre = fromTags.firstOrNull { it.lowercase() !in generic }
            ?: iTunesGenre(artist)
        if (genre.isNotBlank()) synchronized(lock) { cache[key] = genre }
        return genre
    }

    /** Genre for a track: Last.fm tags first, then its artist, then iTunes' own match. */
    suspend fun trackGenre(title: String, artist: String): String {
        if (title.isBlank()) return ""
        val key = "track:${title.lowercase()}|${artist.lowercase()}"
        synchronized(lock) { cache[key]?.let { return it } }
        val tags = lastFmTags(artist = artist, track = title)
        var genre = tags.firstOrNull { it.lowercase() !in generic }.orEmpty()
        if (genre.isBlank()) genre = artistGenre(artist)
        if (genre.isBlank()) genre = iTunesGenre("$title $artist", song = true)
        if (genre.isNotBlank()) synchronized(lock) { cache[key] = genre }
        return genre
    }

    private suspend fun lastFmTags(artist: String, track: String? = null): List<String> {
        val client = runCatching { com.itzfallenme.katmusic.ServiceLocator.lastFm() }
            .getOrNull() ?: return emptyList()
        return runCatching { client.topTags(artist, track) }.getOrDefault(emptyList())
    }

    private suspend fun iTunesGenre(term: String, song: Boolean = false): String = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext ""
        val query = URLEncoder.encode(term, "UTF-8")
        val entity = if (song) "song" else "musicArtist"
        val url = "https://itunes.apple.com/search?term=$query&entity=$entity&limit=3&country=us"
        val body = runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", InnerTube.WEB_UA)
                .build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull() ?: return@withContext ""
        runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val results = (root["results"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { it as? JsonObject }.orEmpty()
            results.asSequence()
                .mapNotNull { it["primaryGenreName"]?.jsonPrimitive?.content }
                .firstOrNull { it.isNotBlank() && it.lowercase() !in generic }
                .orEmpty()
        }.getOrDefault("")
    }

    data class GenreSlice(val genre: String, val weight: Int)

    /**
     * Turns listening history into a genre breakdown: every artist is resolved once and
     * weighted by its play count, then the genres are merged and sorted.
     */
    suspend fun dnaFromHistory(
        library: LibraryRepository,
        maxArtists: Int = 8
    ): List<GenreSlice> = coroutineScope {
        val top = library.state.value.history
            .groupBy { it.track.artist }
            .filterKeys { it.isNotBlank() }
            .map { (artist, plays) -> artist to plays.size }
            .sortedByDescending { it.second }
            .take(maxArtists)

        val resolved = top.map { (artist, plays) ->
            async { artistGenre(artist) to plays }
        }.awaitAll()

        resolved.filter { it.first.isNotBlank() }
            .groupBy { it.first }
            .map { (genre, entries) -> GenreSlice(genre, entries.sumOf { it.second }) }
            .sortedByDescending { it.weight }
    }

    /** Genre-aware radio seeds: the artist's own radio for each top genre. */
    suspend fun radioForGenre(genre: String, size: Int = 25): List<Track> {
        if (genre.isBlank()) return emptyList()
        val tracks = runCatching {
            InnerTube.search("$genre mix")
        }.getOrDefault(emptyList()).flatMap { it.tracks }
        if (tracks.isEmpty()) return emptyList()
        return tracks.distinctBy { it.id }.take(size)
    }
}
