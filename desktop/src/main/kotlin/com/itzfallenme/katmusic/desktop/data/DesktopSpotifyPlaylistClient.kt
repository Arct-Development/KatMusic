package com.itzfallenme.katmusic.desktop.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class SpotifyPlaylistTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
)

data class SpotifyPlaylistInfo(
    val id: String,
    val title: String,
    val owner: String?,
    val artwork: String?,
    val tracks: List<SpotifyPlaylistTrack>,
)

/**
 * Public Spotify playlist metadata via its public embed page.
 *
 * This intentionally does not read browser cookies or private account data.
 * Spotify track rows are matched to playable YouTube Music entries separately
 * by the Desktop importer.
 */
class DesktopSpotifyPlaylistClient(
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchPlaylist(input: String): Result<SpotifyPlaylistInfo> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val expanded = resolveShortLink(input.trim())
                val playlistId = extractPlaylistId(expanded)
                    ?: error("Enter a public Spotify playlist URL or playlist ID.")
                val request = HttpRequest.newBuilder(
                    URI("https://open.spotify.com/embed/playlist/${enc(playlistId)}"),
                )
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html")
                    .GET()
                    .build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                require(response.statusCode() in 200..299) {
                    "Spotify playlist returned HTTP ${response.statusCode()}"
                }
                parseEmbedPage(response.body(), playlistId)
            }
        }

    private fun resolveShortLink(input: String): String {
        val uri = runCatching {
            URI(if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input")
        }.getOrNull() ?: return input
        val host = uri.host?.lowercase() ?: return input
        if (host != "spotify.link" && host != "www.spotify.link") return input
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.discarding())
        return response.uri().toString()
    }

    internal fun extractPlaylistId(raw: String): String? {
        val value = raw.trim()
        if (SPOTIFY_ID.matches(value)) return value
        SPOTIFY_URI.find(value)?.groupValues?.getOrNull(1)?.let { return it }
        val uri = runCatching {
            URI(if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value")
        }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "spotify.com" && !host.endsWith(".spotify.com") &&
            host != "spotify.link" && host != "www.spotify.link"
        ) return null
        val parts = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val playlistIndex = parts.indexOfLast { it == "playlist" }
        return parts.getOrNull(playlistIndex + 1)?.takeIf(SPOTIFY_ID::matches)
    }

    internal fun parseEmbedPage(html: String, playlistId: String): SpotifyPlaylistInfo {
        val payload = NEXT_DATA.find(html)?.groupValues?.getOrNull(1)
            ?.let(::unescapeHtml)
            ?: error("Spotify didn't expose a public playlist preview.")
        val root = json.parseToJsonElement(payload)
        var title: String? = null
        var owner: String? = null
        var artwork: String? = null
        val tracks = mutableListOf<SpotifyPlaylistTrack>()
        val seen = mutableSetOf<String>()
        walk(root) { item ->
            val uri = item.text("uri").orEmpty()
            val name = item.text("title")?.clean().orEmpty()
            if (uri.startsWith("spotify:playlist:")) {
                if (title == null && name.isNotBlank()) title = name
                if (owner == null) owner = item.text("subtitle")?.clean()?.takeIf(String::isNotBlank)
                if (artwork == null) {
                    artwork = item.text("coverArt") ?: item.text("imageUrl") ?: item.text("url")
                }
            } else if (uri.startsWith("spotify:track:") && seen.add(uri) && name.isNotBlank()) {
                tracks += SpotifyPlaylistTrack(
                    title = name,
                    artist = item.text("subtitle").orEmpty().clean(),
                )
            }
        }
        require(tracks.isNotEmpty()) {
            "No public tracks were found. Private playlists and Spotify Liked Songs aren't supported."
        }
        return SpotifyPlaylistInfo(
            id = playlistId,
            title = title?.takeIf(String::isNotBlank) ?: "Spotify playlist",
            owner = owner,
            artwork = artwork?.takeIf(String::isNotBlank),
            tracks = tracks,
        )
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

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun String.clean(): String = replace('\u00a0', ' ').trim()

    private fun unescapeHtml(value: String): String = value
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private companion object {
        val SPOTIFY_ID = Regex("[A-Za-z0-9]{22}")
        val SPOTIFY_URI = Regex("""spotify:playlist:([A-Za-z0-9]{22})""")
        val NEXT_DATA = Regex(
            """<script[^>]*\bid=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36 KatMusic"
    }
}
