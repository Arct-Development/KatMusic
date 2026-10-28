package com.itzfallenme.katmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Synced lyrics from the community LRCLIB database. */
object LyricsApi {

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = LinkedHashMap<String, Lyrics>()

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KatMusic/1.0 (https://github.com/ItzFallenMe)")
                .build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    suspend fun find(
        title: String,
        artist: String,
        album: String?,
        durationSec: Long
    ): Lyrics {
        val key = "$title|$artist|$durationSec"
        cache[key]?.let { return it }
        val cleanTitle = clean(title)
        val cleanArtist = clean(artist.substringBefore(","))

        val searchUrl = buildString {
            append("https://lrclib.net/api/search?track_name=").append(enc(cleanTitle))
            if (cleanArtist.isNotBlank()) append("&artist_name=").append(enc(cleanArtist))
            if (!album.isNullOrBlank()) append("&album_name=").append(enc(clean(album)))
        }
        val body = get(searchUrl)
        if (body != null) {
            runCatching {
                val results = json.parseToJsonElement(body).jsonArray
                val candidates = results.mapNotNull { it as? JsonObject }
                val best = candidates
                    .filter { it.str("syncedLyrics") != null || it.str("plainLyrics") != null }
                    .minByOrNull { item ->
                        val duration = (item as JsonObject).str("duration")?.toDoubleOrNull()?.toLong() ?: 0L
                        var score = if (durationSec > 0 && duration > 0) kotlin.math.abs(duration - durationSec) else 30L
                        val itemArtist = item.str("artistName").orEmpty()
                        if (!itemArtist.contains(cleanArtist, ignoreCase = true) &&
                            !cleanArtist.contains(itemArtist, ignoreCase = true)
                        ) score += 40
                        score
                    }
                if (best != null) {
                    val synced = parseLrc(best.str("syncedLyrics").orEmpty())
                    val lyrics = Lyrics(
                        synced = synced,
                        plain = best.str("plainLyrics").orEmpty(),
                        source = "LRCLIB"
                    )
                    if (!lyrics.isEmpty) {
                        cache[key] = lyrics
                        return lyrics
                    }
                }
            }.onFailure { /* fall through to empty lyrics */ }
        }
        val empty = Lyrics()
        cache[key] = empty
        return empty
    }

    private fun clean(value: String): String =
        value.replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
            .replace(Regex("(?i)(official\\s+)?(music\\s+)?video|lyric(s)?\\s+video|audio|visualizer|hd|4k"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

    /** Parses standard LRC, including lines that carry several timestamps. */
    fun parseLrc(lrc: String): List<LyricLine> {
        if (lrc.isBlank()) return emptyList()
        val out = ArrayList<LyricLine>()
        val stamp = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
        lrc.lineSequence().forEach { raw ->
            val matches = stamp.findAll(raw).toList()
            if (matches.isEmpty()) return@forEach
            val content = raw.substring(matches.last().range.last + 1).trim()
            if (content.isEmpty()) return@forEach
            matches.forEach { m ->
                val minutes = m.groupValues[1].toLongOrNull() ?: 0
                val seconds = m.groupValues[2].toLongOrNull() ?: 0
                val fractionRaw = m.groupValues[3]
                val fraction = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> fractionRaw.toLong() * 100
                    2 -> fractionRaw.toLong() * 10
                    else -> fractionRaw.take(3).toLong()
                }
                out.add(LyricLine(minutes * 60_000 + seconds * 1000 + fraction, content))
            }
        }
        return out.sortedBy { it.timeMs }.distinctBy { it.timeMs to it.text }
    }
}
