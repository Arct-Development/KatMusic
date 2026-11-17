package com.itzfallenme.katmusic.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class AppleMusicPlaylistTrack(
    val title: String,
    val artist: String,
)

data class AppleMusicPlaylistInfo(
    val id: String,
    val title: String,
    val tracks: List<AppleMusicPlaylistTrack>,
)

/** Reads public Apple Music playlist metadata; no account cookies are used. */
class DesktopAppleMusicPlaylistClient(
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchPlaylist(input: String): Result<AppleMusicPlaylistInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = input.trim()
                val id = extractPlaylistId(raw)
                    ?: error("Enter a public Apple Music playlist URL or playlist ID.")
                val url = when {
                    raw.startsWith("http://", ignoreCase = true) ||
                        raw.startsWith("https://", ignoreCase = true) -> raw
                    raw.contains("music.apple.com", ignoreCase = true) -> "https://$raw"
                    else -> "https://music.apple.com/us/playlist/$id"
                }
                val request = HttpRequest.newBuilder(URI(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .GET()
                    .build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                require(response.statusCode() in 200..299) {
                    "Apple Music playlist returned HTTP ${response.statusCode()}"
                }
                parsePage(response.body(), id)
            }
        }

    internal fun extractPlaylistId(input: String): String? {
        val value = input.trim()
        if (APPLE_ID.matches(value)) return value
        val uri = runCatching {
            URI(if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value")
        }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "music.apple.com" && host != "www.music.apple.com") return null
        val parts = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val index = parts.indexOfLast { it == "playlist" }
        val tail = parts.drop(index + 1)
        return tail.firstOrNull(APPLE_ID::matches)
            ?: tail.getOrNull(1)?.takeIf(APPLE_ID::matches)
    }

    internal fun parsePage(html: String, playlistId: String): AppleMusicPlaylistInfo {
        val rows = parseServerDataRows(html).ifEmpty { parseSchemaRows(html) }
        require(rows.isNotEmpty()) {
            "No public tracks were found. Private Apple Music playlists aren't supported."
        }
        return AppleMusicPlaylistInfo(
            id = playlistId,
            title = parseTitle(html),
            tracks = rows,
        )
    }

    private fun parseServerDataRows(html: String): List<AppleMusicPlaylistTrack> {
        val script = scriptContent(html, SERVER_DATA_ID) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(script) }.getOrNull() ?: return emptyList()
        val result = mutableListOf<AppleMusicPlaylistTrack>()
        val seen = mutableSetOf<String>()
        walk(root) { item ->
            val title = item.text("title")?.clean().orEmpty()
            val artist = item.text("artistName")?.clean().orEmpty()
            val key = "${title.lowercase()}|${artist.lowercase()}"
            if (title.isNotBlank() && artist.isNotBlank() && seen.add(key)) {
                result += AppleMusicPlaylistTrack(title, artist)
            }
        }
        return result
    }

    private fun parseSchemaRows(html: String): List<AppleMusicPlaylistTrack> {
        val content = scriptContent(html, SCHEMA_ID) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(content) }.getOrNull() ?: return emptyList()
        val tracks = findArray(root, "track") ?: return emptyList()
        val seen = mutableSetOf<String>()
        return tracks.mapNotNull { entry ->
            val title = (entry as? JsonObject)?.text("name")?.clean().orEmpty()
            if (title.isBlank() || !seen.add(title.lowercase())) null
            else AppleMusicPlaylistTrack(title, "")
        }
    }

    private fun parseTitle(html: String): String {
        val schema = scriptContent(html, SCHEMA_ID)
            ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
            ?.let { findText(it, "name") }
            ?.clean()
            ?.takeIf(String::isNotBlank)
        if (schema != null) return schema
        return TITLE_TAG.find(html)?.groupValues?.getOrNull(1)
            ?.let(::decodeEntities)
            ?.clean()
            ?.takeIf(String::isNotBlank)
            ?: "Apple Music playlist"
    }

    private fun scriptContent(html: String, id: String): String? {
        val idFirst = Regex(
            """<script[^>]*\bid=["']${Regex.escape(id)}["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)?.groupValues?.getOrNull(1)
        return idFirst?.let(::decodeEntities)
    }

    private fun walk(root: JsonElement, visit: (JsonObject) -> Unit) {
        val pending = ArrayDeque<JsonElement>().apply { add(root) }
        while (pending.isNotEmpty()) {
            when (val item = pending.removeFirst()) {
                is JsonObject -> {
                    visit(item)
                    item.values.forEach { pending.addLast(it) }
                }
                is JsonArray -> item.forEach { pending.addLast(it) }
                else -> Unit
            }
        }
    }

    private fun findArray(root: JsonElement, key: String): JsonArray? {
        val pending = ArrayDeque<JsonElement>().apply { add(root) }
        while (pending.isNotEmpty()) {
            when (val item = pending.removeFirst()) {
                is JsonObject -> {
                    (item[key] as? JsonArray)?.let { return it }
                    item.values.forEach { pending.addLast(it) }
                }
                is JsonArray -> item.forEach { pending.addLast(it) }
                else -> Unit
            }
        }
        return null
    }

    private fun findText(root: JsonElement, key: String): String? {
        val pending = ArrayDeque<JsonElement>().apply { add(root) }
        while (pending.isNotEmpty()) {
            when (val item = pending.removeFirst()) {
                is JsonObject -> {
                    item.text(key)?.let { return it }
                    item.values.forEach { pending.addLast(it) }
                }
                is JsonArray -> item.forEach { pending.addLast(it) }
                else -> Unit
            }
        }
        return null
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun String.clean(): String = replace('\u00a0', ' ').trim()

    private fun decodeEntities(value: String): String = value
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    private companion object {
        val APPLE_ID = Regex("pl\\.[A-Za-z0-9]+")
        val TITLE_TAG = Regex(
            """<title[^>]*>(.*?)</title>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        const val SERVER_DATA_ID = "serialized-server-data"
        const val SCHEMA_ID = "schema:music-playlist"
        const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36 KatMusic"
    }
}
