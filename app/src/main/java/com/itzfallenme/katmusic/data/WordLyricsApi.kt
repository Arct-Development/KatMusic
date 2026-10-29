package com.itzfallenme.katmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Word-by-word lyrics.
 *
 * The Better Lyrics API serves the Apple Music TTML catalogue, where every word carries
 * its own `<span begin end>` timing - the same source LastWave reads for its karaoke
 * display. Cached songs are free and keyless; a cache miss answers `401`, so the exact
 * parameter combination matters: the cache key includes album and duration, and the
 * browser extension that fills the cache sends all four. Missing combinations are
 * therefore retried without the album and then without metadata at all, which is how the
 * entries originally created by earlier requests are found.
 */
object WordLyricsApi {

    private const val ENDPOINT = "https://api.betterlyrics.org/getLyrics"

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(14, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val cache = object : LinkedHashMap<String, Lyrics>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>) = size > 24
    }

    private val misses = object : LinkedHashMap<String, Boolean>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) = size > 48
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun cleanTitle(raw: String): String {
        var title = raw
        listOf(
            Regex("\\s*[\\[(].*?(official|lyric|video|audio|remaster|live|hd|hq|mv|m/v).*?[)\\]]", RegexOption.IGNORE_CASE),
            Regex("\\s*[\\[(](feat|ft|with)\\..*?[)\\]]", RegexOption.IGNORE_CASE),
            Regex("\\s+-\\s+(topic|official).*$", RegexOption.IGNORE_CASE)
        ).forEach { title = title.replace(it, "") }
        return title.replace(Regex("\\s{2,}"), " ").trim()
    }

    private fun cleanArtist(raw: String): String = raw
        .substringBefore(",")
        .replace(Regex("\\s*[\\[(]?(feat|ft|with)\\..*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+-\\s+topic$", RegexOption.IGNORE_CASE), "")
        .trim()

    private data class Query(
        val title: String,
        val artist: String,
        val album: String?,
        val duration: Long
    )

    /** Full metadata first, then progressively looser combinations. */
    private fun queries(
        title: String,
        artist: String,
        album: String?,
        durationSec: Long
    ): List<Query> {
        val cleanT = cleanTitle(title)
        val cleanA = cleanArtist(artist)
        val primary = artist.substringBefore(',').substringBefore('&').trim()
        val pairs = buildList {
            add(title.trim() to artist.trim())
            if (cleanA != artist.trim() || cleanT != title.trim()) add(cleanT to cleanA)
            if (primary.isNotBlank() && primary != artist.trim()) add(title.trim() to primary)
        }.filter { (t, a) -> t.isNotBlank() && a.isNotBlank() }.distinct()

        val combos = buildList {
            if (!album.isNullOrBlank() && durationSec > 0) add(album.trim() to durationSec)
            if (durationSec > 0) add(null to durationSec)
            if (!album.isNullOrBlank()) add(album.trim() to 0L)
            add(null to 0L)
        }
        return pairs.flatMap { (t, a) ->
            combos.map { (al, d) -> Query(t, a, al, d) }
        }
    }

    private fun url(query: Query): String = buildString {
        append(ENDPOINT).append("?s=").append(enc(query.title))
        append("&a=").append(enc(query.artist))
        if (!query.album.isNullOrBlank()) append("&al=").append(enc(query.album))
        if (query.duration > 0) append("&d=").append(query.duration)
    }

    private suspend fun body(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KatMusic/1.1 (https://github.com/ItzFallenMe/KatMusic)")
                .header("Accept", "application/json")
                .build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    /** Extracts the TTML string from the JSON envelope, ignoring error payloads. */
    private fun unwrap(body: String?): String? {
        val trimmed = body?.replace("\uFEFF", "")?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return trimmed
        return runCatching {
            val root = json.parseToJsonElement(trimmed) as? JsonObject ?: return@runCatching null
            if (root["error"] != null) return@runCatching null
            val candidate = root["ttml"] ?: root["lyrics"] ?: root["data"] ?: root["result"]
            candidate?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** Word-timed lyrics for a track, or null when the catalogue has nothing cached. */
    suspend fun find(
        title: String,
        artist: String,
        album: String? = null,
        durationSec: Long = 0L
    ): Lyrics? {
        if (title.isBlank() || artist.isBlank()) return null
        val key = "${title.lowercase()}|${artist.lowercase()}|$durationSec"
        synchronized(cache) { cache[key] }?.let { return it }
        synchronized(misses) { if (misses[key] == true) return null }

        for (query in queries(title, artist, album, durationSec)) {
            val payload = unwrap(body(url(query))) ?: continue
            // Parsing thousands of spans is cheap but not free; keep it off the main thread.
            val lines = withContext(Dispatchers.Default) {
                TtmlParser.parse(payload).ifEmpty {
                    LyricsApi.parseLrc(payload).filter { it.hasSyllables }
                }
            }
            if (lines.any { it.hasSyllables }) {
                val lyrics = Lyrics(
                    synced = lines,
                    source = "BetterLyrics",
                    wordSynced = true
                )
                synchronized(cache) { cache[key] = lyrics }
                return lyrics
            }
        }
        synchronized(misses) { misses[key] = true }
        return null
    }
}
