package com.itzfallenme.katmusic.desktop.data

import com.itzfallenme.katmusic.shared.data.PodcastEpisode
import com.itzfallenme.katmusic.shared.data.PodcastShow
import com.itzfallenme.katmusic.shared.data.PodcastsRepository
import com.itzfallenme.katmusic.shared.data.PodcastsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Desktop podcasts: show discovery through the public iTunes directory, then
 * the show's own RSS feed for episode metadata and enclosure URLs. Nothing is
 * mocked — search results and playable episodes are live directory/feed data.
 */
class DesktopPodcastsRepository : PodcastsRepository {

    @Serializable
    private data class ItunesResponse(val results: List<ItunesShow> = emptyList())

    @Serializable
    private data class ItunesShow(
        val collectionId: Long? = null,
        val collectionName: String? = null,
        val artistName: String? = null,
        val artworkUrl600: String? = null,
        val artworkUrl100: String? = null,
        val feedUrl: String? = null,
        val trackCount: Int? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val _state = MutableStateFlow(PodcastsState())
    override val state: StateFlow<PodcastsState> = _state.asStateFlow()

    override suspend fun search(query: String): Unit = withContext(Dispatchers.IO) {
        val term = query.trim().ifBlank { "podcasts" }
        _state.value = PodcastsState(isLoading = true, query = term)
        runCatching {
            val encoded = URLEncoder.encode(term, Charsets.UTF_8)
            val body = fetch("https://itunes.apple.com/search?media=podcast&entity=podcast&limit=40&term=$encoded")
            val response = json.decodeFromString<ItunesResponse>(body)
            response.results.mapNotNull { it.toShow() }
        }.onSuccess { shows ->
            _state.value = PodcastsState(
                query = term,
                shows = shows,
                error = if (shows.isEmpty()) "No shows found for “$term”." else null,
            )
        }.onFailure { error ->
            _state.value = PodcastsState(
                query = term,
                error = error.message ?: "Podcast search failed",
            )
        }
        Unit
    }

    override suspend fun openShow(show: PodcastShow): Unit = withContext(Dispatchers.IO) {
        _state.update {
            it.copy(
                isLoading = true,
                selectedShow = show,
                episodes = emptyList(),
                error = null,
            )
        }
        runCatching {
            val xml = fetch(show.feedUrl)
            parseFeed(xml, show)
        }.onSuccess { (resolvedShow, episodes) ->
            _state.update {
                it.copy(
                    isLoading = false,
                    selectedShow = resolvedShow,
                    episodes = episodes,
                    error = if (episodes.isEmpty()) "This feed has no playable audio episodes." else null,
                )
            }
        }.onFailure { error ->
            _state.update {
                it.copy(
                    isLoading = false,
                    error = error.message ?: "Couldn't read this podcast feed",
                )
            }
        }
        Unit
    }

    override fun backToResults() {
        _state.update {
            it.copy(selectedShow = null, episodes = emptyList(), error = null)
        }
    }

    private fun ItunesShow.toShow(): PodcastShow? {
        val feed = feedUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
        val name = collectionName?.trim().orEmpty().ifBlank { return null }
        return PodcastShow(
            id = collectionId?.toString() ?: feed,
            title = name,
            author = artistName?.trim().orEmpty().ifBlank { "Podcast" },
            artworkUrl = artworkUrl600 ?: artworkUrl100,
            feedUrl = feed,
            episodeCount = trackCount ?: 0,
        )
    }

    /**
     * Parses one RSS feed. iTunes podcasts use the standard RSS `<item>` +
     * `<enclosure>` shape; the feed's own artwork and author win over the
     * directory's copy because they are the show's canonical metadata.
     */
    internal fun parseFeed(xml: String, fallbackShow: PodcastShow): Pair<PodcastShow, List<PodcastEpisode>> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val channel = document.getElementsByTagName("channel").item(0) as? Element

        val feedTitle = channel?.textOf("title")?.takeIf { it.isNotBlank() }
        val feedAuthor = channel?.textOf("itunes:author")?.takeIf { it.isNotBlank() }
            ?: channel?.textOf("author")?.takeIf { it.isNotBlank() }
        val feedArtwork = channel?.firstImageHref()
        val show = fallbackShow.copy(
            title = feedTitle ?: fallbackShow.title,
            author = feedAuthor ?: fallbackShow.author,
            artworkUrl = feedArtwork ?: fallbackShow.artworkUrl,
        )

        val items = document.getElementsByTagName("item")
        val episodes = buildList {
            for (index in 0 until items.length) {
                val item = items.item(index) as? Element ?: continue
                val audioUrl = item.enclosureUrl() ?: continue
                val title = item.textOf("title").takeIf { it.isNotBlank() } ?: "Episode ${index + 1}"
                val guid = item.textOf("guid").takeIf { it.isNotBlank() }
                add(
                    PodcastEpisode(
                        id = guid ?: audioUrl,
                        title = title,
                        description = stripHtml(item.textOf("itunes:summary").ifBlank { item.textOf("description") }),
                        publishedAt = formatPublished(item.textOf("pubDate")),
                        durationMs = parsePodcastDuration(
                            item.textOf("itunes:duration").ifBlank {
                                item.textOf("duration")
                            },
                        ),
                        audioUrl = audioUrl,
                        artworkUrl = item.firstImageHref() ?: show.artworkUrl,
                    ),
                )
                if (size >= MAX_EPISODES) break
            }
        }
        return show to episodes
    }

    private fun Element.textOf(tag: String): String {
        val nodes = getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0)?.textContent?.trim().orEmpty() else ""
    }

    private fun Element.firstImageHref(): String? {
        val images = getElementsByTagName("itunes:image")
        for (index in 0 until images.length) {
            val href = (images.item(index) as? Element)?.getAttribute("href")?.trim()
            if (!href.isNullOrBlank()) return href
        }
        return null
    }

    private fun Element.enclosureUrl(): String? {
        val enclosures = getElementsByTagName("enclosure")
        for (index in 0 until enclosures.length) {
            val node = enclosures.item(index) as? Element ?: continue
            val url = node.getAttribute("url").trim()
            if (url.startsWith("http://") || url.startsWith("https://")) return url
        }
        return null
    }

    private fun fetch(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(25))
            .header("Accept", "application/json, application/rss+xml, application/xml, text/xml, */*")
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("HTTP ${response.statusCode()} for ${URI.create(url).host}")
        }
        return response.body()
    }

    private companion object {
        const val MAX_EPISODES = 200
        const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36 KatMusic/2.6"
    }
}

/** `HH:MM:SS`, `MM:SS`, or a bare number of seconds, as seen in podcast feeds. */
internal fun parsePodcastDuration(raw: String): Long {
    val value = raw.trim()
    if (value.isBlank()) return 0L
    val parts = value.split(':').mapNotNull { it.trim().toLongOrNull() }
    if (parts.isEmpty()) return 0L
    return when (parts.size) {
        1 -> parts[0] * 1_000L
        2 -> (parts[0] * 60 + parts[1]) * 1_000L
        else -> (parts[0] * 3_600 + parts[1] * 60 + parts[2]) * 1_000L
    }
}

/** Feed descriptions are HTML; the shared list is plain text. */
internal fun stripHtml(raw: String): String {
    if (raw.isBlank()) return ""
    val text = raw
        .replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (text.length > 480) text.take(477) + "…" else text
}

private fun formatPublished(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching {
        ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
    }.getOrDefault(raw)
}
