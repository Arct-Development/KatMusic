package com.itzfallenme.katmusic.data

import com.itzfallenme.katmusic.data.YtJson.artwork
import com.itzfallenme.katmusic.data.YtJson.find
import com.itzfallenme.katmusic.data.YtJson.findAll
import com.itzfallenme.katmusic.data.YtJson.json
import com.itzfallenme.katmusic.data.YtJson.parseDurationMs
import com.itzfallenme.katmusic.data.YtJson.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * A from-scratch client for the public InnerTube endpoints that power YouTube Music.
 * Only read-only browse/search/player calls are used.
 */
object InnerTube {

    const val WEB_REMIX_VERSION = "1.20240715.01.00"
    const val VR_VERSION = "1.61.48"
    const val VR_UA =
        "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; GB) gzip"
    const val WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Safari/537.36"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    /** Optional signed-in session headers, supplied by [AccountStore]. */
    @Volatile
    var authHeaders: () -> Map<String, String> = { emptyMap() }

    private fun webContext(hl: String = "en", gl: String = "US") = buildJsonObject {
        putJsonObject("client") {
            put("clientName", "WEB_REMIX")
            put("clientVersion", WEB_REMIX_VERSION)
            put("hl", hl)
            put("gl", gl)
        }
        putJsonObject("user") { put("lockedSafetyMode", false) }
    }

    private fun vrContext(hl: String = "en", gl: String = "US") = buildJsonObject {
        putJsonObject("client") {
            put("clientName", "ANDROID_VR")
            put("clientVersion", VR_VERSION)
            put("androidSdkVersion", 32)
            put("osName", "Android")
            put("osVersion", "12")
            put("deviceMake", "Oculus")
            put("deviceModel", "Quest 3")
            put("userAgent", VR_UA)
            put("hl", hl)
            put("gl", gl)
        }
        putJsonObject("user") { put("lockedSafetyMode", false) }
    }

    private suspend fun post(
        path: String,
        body: JsonObject,
        host: String = "https://music.youtube.com",
        ua: String = WEB_UA
    ): JsonObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("$host/youtubei/v1/$path?prettyPrint=false")
            .header("User-Agent", ua)
            .header("Content-Type", "application/json")
            .header("X-Goog-Api-Format-Version", "2")
        authHeaders().forEach { (key, value) -> builder.header(key, value) }
        val request = builder.post(body.toString().toRequestBody(JSON_TYPE)).build()
        http.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful || payload.isBlank()) {
                error("InnerTube $path failed (${response.code})")
            }
            json.parseToJsonElement(payload) as? JsonObject
                ?: error("InnerTube $path returned unexpected payload")
        }
    }

    // --------------------------------------------------------------------- parsing

    private val durationRegex = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$")
    private val yearRegex = Regex("^\\d{4}$")

    private fun runsOf(textNode: JsonObject?): List<JsonObject> =
        textNode?.arr("runs")?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun trackFromResponsive(item: JsonObject): Track? {
        val flex = item.arr("flexColumns")?.mapNotNull { it as? JsonObject }.orEmpty()
        fun columnText(index: Int): String =
            flex.getOrNull(index)
                ?.obj("musicResponsiveListItemFlexColumnRenderer")
                ?.obj("text")
                ?.let { text(it) }
                .orEmpty()

        val title = columnText(0)
        if (title.isBlank()) return null

        val subtitleNode = flex.getOrNull(1)
            ?.obj("musicResponsiveListItemFlexColumnRenderer")
            ?.obj("text")
        val subtitleRuns = runsOf(subtitleNode)

        var artist = ""
        var artistId: String? = null
        var album: String? = null
        var albumId: String? = null
        var year: String? = null
        subtitleRuns.forEach { run ->
            val runText = run.str("text").orEmpty()
            val browse = run.obj("navigationEndpoint")?.obj("browseEndpoint")
            val browseId = browse?.str("browseId")
            when {
                browseId == null -> Unit
                browseId.startsWith("UC") && artist.isBlank() -> {
                    artist = runText
                    artistId = browseId
                }
                browseId.startsWith("MPREb_") -> {
                    album = runText
                    albumId = browseId
                }
            }
            runText.takeIf { yearRegex.matches(it) }?.let { year = it }
        }
        if (artist.isBlank()) {
            artist = subtitleRuns.mapNotNull { run ->
                val value = run.str("text").orEmpty().trim()
                value.takeIf {
                    it.isNotBlank() && it != "•" && it != "&" &&
                        !durationRegex.matches(it) && !yearRegex.matches(it)
                }
            }.take(2).joinToString(", ")
        }

        var durationText = ""
        item.arr("fixedColumns")?.firstOrNull()?.let { fixed ->
            val obj = fixed as? JsonObject
            durationText = text(obj?.obj("musicResponsiveListItemFixedColumnRenderer")?.obj("text"))
        }
        if (durationText.isBlank()) {
            durationText = subtitleRuns.map { it.str("text").orEmpty() }
                .lastOrNull { durationRegex.matches(it) }.orEmpty()
        }

        val videoId = item.obj("playlistItemData")?.str("videoId")
            ?: item.obj("overlay")?.obj("musicItemThumbnailOverlayRenderer")
                ?.obj("content")?.obj("musicPlayButtonRenderer")
                ?.obj("playNavigationEndpoint")?.obj("watchEndpoint")?.str("videoId")
            ?: item.watchIdOf()
            ?: find(item, "watchEndpoint")?.str("videoId")
            ?: return null

        val thumbs = item.obj("thumbnail")?.obj("musicThumbnailRenderer")?.obj("thumbnail")
        val isExplicit = item.arr("badges")?.toString()?.contains("MUSIC_EXPLICIT_BADGE") == true

        return Track(
            id = videoId,
            title = title,
            artist = artist,
            artistId = artistId,
            album = album,
            albumId = albumId,
            durationMs = YtJson.parseDurationMs(durationText),
            artwork = artwork(thumbs ?: item.obj("thumbnail")),
            explicit = isExplicit
        )
    }

    private fun cardFromTwoRow(item: JsonObject): MediaCard? {
        val title = text(item.obj("title"))
        if (title.isBlank()) return null
        val browseId = item.browseIdOf() ?: ""
        val params = item.browseParamsOf()
        val watchId = item.watchIdOf()
        val subtitle = text(item.obj("subtitle"))
        val thumbs = item.obj("thumbnailRenderer")?.obj("musicThumbnailRenderer")?.obj("thumbnail")
            ?: item.obj("thumbnail")
        val kind = when {
            browseId.startsWith("MPREb_") -> CardKind.ALBUM
            browseId.startsWith("UC") -> CardKind.ARTIST
            browseId.startsWith("MPSP") || browseId.startsWith("VL") -> CardKind.PLAYLIST
            browseId.startsWith("RD") || browseId.startsWith("FEmusic") -> CardKind.MIX
            watchId != null -> CardKind.SONG
            else -> CardKind.PLAYLIST
        }
        val resolvedId = browseId.ifBlank { watchId.orEmpty() }
        if (resolvedId.isBlank()) return null
        return MediaCard(
            title = title,
            subtitle = subtitle,
            artwork = artwork(thumbs),
            browseId = resolvedId,
            params = params,
            kind = kind
        )
    }

    private fun shelfTitle(shelf: JsonObject): String? {
        val header = shelf.obj("header")
        val headerTitle = header?.obj("musicCarouselShelfBasicHeaderRenderer")?.obj("title")
            ?: header?.obj("musicCarouselShelfBasicHeaderRenderer")?.obj("strapline")
        val direct = shelf.obj("title") ?: headerTitle
        return text(direct).takeIf { it.isNotBlank() }
    }

    private fun parseShelf(shelf: JsonObject): Shelf {
        val contents = shelf.arr("contents")?.mapNotNull { it as? JsonObject }.orEmpty()
        val cards = ArrayList<MediaCard>()
        val tracks = ArrayList<Track>()
        contents.forEach { entry ->
            entry.obj("musicResponsiveListItemRenderer")?.let { trackFromResponsive(it)?.let(tracks::add) }
            entry.obj("musicTwoRowItemRenderer")?.let { cardFromTwoRow(it)?.let(cards::add) }
            entry.obj("musicMultiRowListItemRenderer")?.let { row ->
                row.obj("onTap")?.obj("watchEndpoint")?.let { watch ->
                    val id = watch.str("videoId") ?: return@let
                    tracks.add(
                        Track(
                            id = id,
                            title = text(row.obj("title")),
                            artist = text(row.arr("subtitle")?.firstOrNull() as? JsonObject),
                            artwork = artwork(row.obj("thumbnail"))
                        )
                    )
                }
            }
        }
        return Shelf(title = shelfTitle(shelf), cards = cards, tracks = tracks)
    }

    internal fun parseShelves(root: JsonObject): List<Shelf> {
        val out = ArrayList<Shelf>()
        val seen = HashSet<String>()
        findAll(root, "musicCarouselShelfRenderer").forEach { shelf ->
            val parsed = parseShelf(shelf)
            if (parsed.isEmpty) return@forEach
            val key = parsed.title + "|" + parsed.cards.take(3).joinToString { it.title }
            if (seen.add(key)) out.add(parsed)
        }
        findAll(root, "musicShelfRenderer").forEach { shelf ->
            val parsed = parseShelf(shelf)
            if (parsed.isEmpty) return@forEach
            val key = parsed.title + "|" + parsed.tracks.take(3).joinToString { it.title } +
                parsed.cards.take(3).joinToString { it.title }
            if (seen.add(key)) out.add(parsed)
        }
        findAll(root, "musicGridRenderer").forEach { grid ->
            val items = grid.arr("items")?.mapNotNull { it as? JsonObject }.orEmpty()
            val cards = items.mapNotNull { entry ->
                entry.obj("musicTwoRowItemRenderer")?.let { cardFromTwoRow(it) }
            }
            if (cards.isNotEmpty()) out.add(Shelf(title = null, cards = cards))
        }
        return out
    }

    private fun parseHeader(root: JsonObject): DetailPage? {
        val headerKeys = listOf(
            "musicResponsiveHeaderRenderer",
            "musicDetailHeaderRenderer",
            "musicEditablePlaylistDetailHeaderRenderer",
            "musicImmersiveHeaderRenderer",
            "musicVisualHeaderRenderer"
        )
        val header = headerKeys.firstNotNullOfOrNull { find(root, it) } ?: return null
        val inner = header.obj("header")?.let { it } ?: header
        var title = text(inner.obj("title"))
        if (title.isBlank()) title = text(inner.obj("straplineTextOne"))
        val subtitle = listOfNotNull(
            text(inner.obj("subtitle")),
            text(inner.obj("secondSubtitle")),
            text(inner.obj("straplineTextOne"))
        ).filter { it.isNotBlank() }.joinToString(" • ")
        val description = text(inner.obj("description"))
        val artworkUrl = artwork(inner.obj("thumbnail"))
        val tracks = findAll(inner, "musicResponsiveListItemRenderer")
            .mapNotNull { trackFromResponsive(it) }
            .distinctBy { it.id }
        val year = subtitle.split("•").map { it.trim() }.firstOrNull { yearRegex.matches(it) }
        return DetailPage(
            title = title,
            subtitle = subtitle,
            description = description,
            artwork = artworkUrl,
            tracks = tracks,
            year = year
        )
    }

    // ------------------------------------------------------------------- endpoints

    suspend fun search(query: String, params: String? = null): List<Shelf> {
        val body = buildJsonObject {
            put("context", webContext())
            put("query", query)
            if (params != null) put("params", params)
        }
        val root = post("search", body)
        return parseShelves(root).ifEmpty {
            val cards = findAll(root, "musicCardShelfRenderer").mapNotNull { node ->
                val title = text(node.obj("title"))
                val browseId = node.browseIdOf() ?: return@mapNotNull null
                MediaCard(
                    title = title,
                    subtitle = text(node.obj("subtitle")),
                    artwork = artwork(node.obj("thumbnail")),
                    browseId = browseId,
                    kind = if (browseId.startsWith("UC")) CardKind.ARTIST else CardKind.ALBUM
                )
            }
            if (cards.isEmpty()) emptyList() else listOf(Shelf(title = "Top result", cards = cards))
        }
    }

    suspend fun browse(browseId: String, params: String? = null): JsonObject {
        val body = buildJsonObject {
            put("context", webContext())
            put("browseId", browseId)
            if (params != null) put("params", params)
        }
        return post("browse", body)
    }

    suspend fun homeShelves(): List<Shelf> = parseShelves(browse("FEmusic_home"))

    suspend fun charts(): List<Shelf> = parseShelves(browse("FEmusic_charts"))

    suspend fun moodsAndGenres(): List<Shelf> = parseShelves(browse("FEmusic_moods_and_genres"))

    suspend fun newReleases(): List<Shelf> = parseShelves(browse("FEmusic_new_releases"))

    suspend fun podcasts(): List<Shelf> = parseShelves(browse("FEmusic_podcasts"))

    suspend fun explore(): List<Shelf> = parseShelves(browse("FEmusic_explore"))

    /** Album / playlist / artist destination. */
    suspend fun detail(browseId: String, params: String? = null): DetailPage {
        val root = browse(browseId, params)
        val shelves = parseShelves(root)
        val header = parseHeader(root)
        val tracks = ArrayList<Track>()
        header?.tracks?.let(tracks::addAll)
        shelves.forEach { tracks.addAll(it.tracks) }
        val distinct = tracks.distinctBy { it.id }
        val kind = when {
            browseId.startsWith("MPREb_") -> CardKind.ALBUM
            browseId.startsWith("UC") -> CardKind.ARTIST
            browseId.startsWith("MPSP") || browseId.startsWith("VL") -> CardKind.PLAYLIST
            browseId.startsWith("RD") || browseId.startsWith("FEmusic") -> CardKind.MIX
            else -> CardKind.PLAYLIST
        }
        return DetailPage(
            title = header?.title ?: shelves.firstOrNull()?.title.orEmpty(),
            subtitle = header?.subtitle.orEmpty(),
            description = header?.description.orEmpty(),
            artwork = header?.artwork ?: shelves.flatMap { it.cards }.firstOrNull()?.artwork.orEmpty(),
            tracks = distinct,
            shelves = shelves.filter { it.tracks.isEmpty() && it.cards.isNotEmpty() },
            kind = kind,
            year = header?.year,
            trackCount = distinct.size.takeIf { it > 0 }?.toString()
        )
    }

    /** Radio / autoplay queue for a video. */
    suspend fun upNext(videoId: String): List<Track> {
        val body = buildJsonObject {
            put("context", webContext())
            put("videoId", videoId)
            put("enablePersistentPlaybackControls", false)
        }
        val root = post("next", body)
        val panelTracks = ArrayList<Track>()
        findAll(root, "playlistPanelVideoRenderer").forEach { item ->
            val id = item.str("videoId") ?: return@forEach
            val title = text(item.obj("title"))
            if (title.isBlank()) return@forEach
            val runs = runsOf(item.obj("longBylineText") ?: item.obj("shortBylineText"))
            panelTracks.add(
                Track(
                    id = id,
                    title = title,
                    artist = runs.firstOrNull()?.let { text(it) }.orEmpty(),
                    artistId = runs.firstOrNull()?.browseIdOf(),
                    durationMs = YtJson.parseDurationMs(text(item.obj("lengthText"))),
                    artwork = artwork(item.obj("thumbnail"))
                )
            )
        }
        if (panelTracks.isNotEmpty()) return panelTracks
        return findAll(root, "musicResponsiveListItemRenderer").mapNotNull { trackFromResponsive(it) }
    }

    private data class PlayerAttempt(val clientName: String, val body: JsonObject, val ua: String)

    private fun playerAttempts(videoId: String): List<PlayerAttempt> = listOf(
        PlayerAttempt("ANDROID_VR", buildJsonObject {
            put("context", vrContext())
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }, VR_UA),
        PlayerAttempt("IOS", buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "IOS")
                    put("clientVersion", "19.45.4")
                    put("deviceModel", "iPhone16,2")
                    put("userAgent", "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X)")
                    put("osName", "iPhone")
                    put("osVersion", "17.5.1.21F90")
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }, "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X)"),
        PlayerAttempt("ANDROID_TESTSUITE", buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID_TESTSUITE")
                    put("clientVersion", "1.9")
                    put("androidSdkVersion", 30)
                    put("osName", "Android")
                    put("osVersion", "11")
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }, "com.google.android.apps.youtube.app.application.test/1.9 (Linux; U; Android 11)")
    )

    /** Resolves a progressive audio stream URL straight from the player endpoint. */
    suspend fun streamUrl(videoId: String, preferHighQuality: Boolean = true): StreamInfo {
        var lastError: String? = null
        for (attempt in playerAttempts(videoId)) {
            val response = runCatching {
                post("player", attempt.body, host = "https://www.youtube.com", ua = attempt.ua)
            }.getOrNull()
            if (response == null) {
                lastError = "Player request failed"
                continue
            }
            val status = response.obj("playabilityStatus")?.str("status")
            if (status != "OK") {
                lastError = response.obj("playabilityStatus")?.str("reason") ?: status
                continue
            }
            val data = response.obj("streamingData") ?: continue
            val formats = (data.arr("adaptiveFormats") ?: JsonArray(emptyList()))
                .mapNotNull { it as? JsonObject }
                .filter { it.str("mimeType")?.startsWith("audio/") == true }
            val chosen = pickAudio(formats, preferHighQuality) ?: continue
            val url = chosen.str("url") ?: continue
            return StreamInfo(
                url = url,
                mimeType = chosen.str("mimeType").orEmpty(),
                bitrate = chosen.long("bitrate")?.toInt() ?: 0,
                lengthMs = response.obj("videoDetails")?.let { details ->
                    (details.str("lengthSeconds")?.toLongOrNull() ?: 0L) * 1000
                } ?: 0L
            )
        }
        error(lastError ?: "No playable audio stream found")
    }

    private fun pickAudio(formats: List<JsonObject>, highQuality: Boolean): JsonObject? {
        val opus = formats.filter { it.str("mimeType")?.contains("opus") == true }
        val aac = formats.filter { it.str("mimeType")?.contains("mp4a") == true }
        val pool = if (highQuality) (opus + aac) else (aac + opus)
        return pool.maxByOrNull { it.long("bitrate") ?: 0L } ?: formats.firstOrNull()
    }

    suspend fun lyricsSearch(title: String, artist: String, album: String?, durationSec: Long): Lyrics {
        return LyricsApi.find(title, artist, album, durationSec)
    }

    /**
     * Fallback lyrics source: the video's own caption track. Useful for live sessions,
     * covers and videos that LRCLIB has never seen.
     */
    suspend fun captionLyrics(videoId: String): Lyrics {
        val body = buildJsonObject {
            put("context", vrContext())
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        val root = runCatching {
            post("player", body, host = "https://www.youtube.com", ua = VR_UA)
        }.getOrNull() ?: return Lyrics()
        val tracklist = find(root, "playerCaptionsTracklistRenderer") ?: return Lyrics()
        val tracks = tracklist.arr("captionTracks")?.mapNotNull { it as? JsonObject }.orEmpty()
        val track = tracks.firstOrNull { it.str("kind") != "asr" } ?: tracks.firstOrNull()
        val baseUrl = track?.str("baseUrl") ?: return Lyrics()
        val payload = getText(baseUrl + "&fmt=json3") ?: return Lyrics()
        val parsed = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: return Lyrics()
        val events = parsed.arr("events")?.mapNotNull { it as? JsonObject }.orEmpty()
        val lines = ArrayList<LyricLine>()
        events.forEach { event ->
            val start = event.long("tStartMs") ?: return@forEach
            val text = event.arr("segs")
                ?.mapNotNull { seg -> (seg as? JsonObject)?.str("utf8") }
                ?.joinToString("")
                ?.replace("\n", " ")
                ?.trim()
                .orEmpty()
            if (text.isBlank()) return@forEach
            lines.add(LyricLine(start, text))
        }
        if (lines.isEmpty()) return Lyrics()
        return Lyrics(
            synced = lines.distinctBy { it.timeMs },
            plain = lines.joinToString("\n") { it.text },
            source = "YouTube captions"
        )
    }

    private suspend fun getText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("User-Agent", VR_UA).build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    /** Typeahead suggestions from the music search box. */
    /** Signed-in profile from the account menu. Returns name to avatar url. */
    suspend fun accountInfo(): Pair<String, String>? = runCatching {
        val root = post("account/account_menu", buildJsonObject { put("context", webContext()) })
        val name = findAll(root, "accountName").firstOrNull()?.let { text(it) }
        val photo = findAll(root, "accountPhoto").firstOrNull()?.let { artwork(it) }
        if (name.isNullOrBlank()) null else name to photo.orEmpty()
    }.getOrNull()

    suspend fun searchSuggestions(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        val body = buildJsonObject {
            put("context", webContext())
            put("input", query)
        }
        val root = runCatching { post("music/get_search_suggestions", body) }.getOrNull()
            ?: return emptyList()
        return findAll(root, "searchSuggestionRenderer").mapNotNull { item ->
            text(item.obj("suggestion")).takeIf { it.isNotBlank() }
        }.distinct().take(10)
    }

    fun isExplicitBadge(node: JsonObject?): Boolean =
        node?.arr("badges")?.toString()?.contains("MUSIC_EXPLICIT_BADGE") == true
}
