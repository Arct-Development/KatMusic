package com.itzfallenme.katmusic.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Thin, cached facade over [InnerTube] used by the UI layer. */
object YtMusic {

    private data class CacheEntry<T>(val value: T, val at: Long)

    private val homeCache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry<List<Shelf>>>()
    private val detailCache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry<DetailPage>>()
    private val resolveMutex = Mutex()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var homeCacheFile: File? = null

    private val _home = MutableStateFlow<List<Shelf>>(emptyList())
    val home: StateFlow<List<Shelf>> = _home.asStateFlow()

    /** True when the visible feed came from disk because the network failed. */
    private val _homeStale = MutableStateFlow(false)
    val homeStale: StateFlow<Boolean> = _homeStale.asStateFlow()

    private val _homeLoading = MutableStateFlow(false)
    val homeLoading: StateFlow<Boolean> = _homeLoading.asStateFlow()

    private val _homeError = MutableStateFlow<String?>(null)
    val homeError: StateFlow<String?> = _homeError.asStateFlow()

    private fun <T> cached(map: MutableMap<String, CacheEntry<T>>, key: String, ttlMs: Long): T? {
        val entry = map[key] ?: return null
        return if (System.currentTimeMillis() - entry.at < ttlMs) entry.value else null
    }

    /**
     * Wires the on-disk feed snapshot. Called once from ServiceLocator; the last
     * successful home feed is restored on the next launch so the app opens with
     * content instead of an empty spinner, even offline.
     */
    fun attach(context: Context) {
        if (homeCacheFile != null) return
        homeCacheFile = File(context.filesDir, "katmusic-home-cache.json")
        ioScope.launch { restoreHomeSnapshot() }
    }

    private suspend fun restoreHomeSnapshot() = withContext(Dispatchers.IO) {
        val file = homeCacheFile ?: return@withContext
        if (!file.exists()) return@withContext
        val shelves = runCatching {
            json.decodeFromString<List<Shelf>>(file.readText())
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@withContext
        if (_home.value.isEmpty()) {
            _home.value = shelves
            _homeStale.value = true
        }
    }

    private suspend fun persistHomeSnapshot(shelves: List<Shelf>) = withContext(Dispatchers.IO) {
        val file = homeCacheFile ?: return@withContext
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Shelf.serializer()), shelves))
            tmp.renameTo(file)
        }
    }

    suspend fun loadHome(force: Boolean = false) {
        if (_homeLoading.value) return
        if (!force) {
            cached(homeCache, "home", 10 * 60_000L)?.let {
                _home.value = it
                _homeStale.value = false
                return
            }
        }
        _homeLoading.value = true
        _homeError.value = null
        runCatching { InnerTube.homeShelves() }
            .onSuccess { shelves ->
                _home.value = shelves
                _homeStale.value = false
                homeCache["home"] = CacheEntry(shelves, System.currentTimeMillis())
                persistHomeSnapshot(shelves)
            }
            .onFailure {
                _homeError.value = it.message ?: "Could not reach YouTube Music"
                if (_home.value.isEmpty()) {
                    restoreHomeSnapshot()
                }
                if (_home.value.isNotEmpty()) _homeStale.value = true
            }
        _homeLoading.value = false
    }

    suspend fun search(query: String): List<Shelf> {
        if (query.isBlank()) return emptyList()
        val key = "search:$query"
        cached(homeCache, key, 5 * 60_000L)?.let { return it }
        val shelves = InnerTube.search(query)
        homeCache[key] = CacheEntry(shelves, System.currentTimeMillis())
        return shelves
    }

    suspend fun detail(browseId: String, params: String? = null): DetailPage {
        val key = "$browseId|${params.orEmpty()}"
        cached(detailCache, key, 10 * 60_000L)?.let { return it }
        val page = InnerTube.detail(browseId, params)
        detailCache[key] = CacheEntry(page, System.currentTimeMillis())
        return page
    }

    suspend fun charts(): List<Shelf> {
        cached(homeCache, "charts", 30 * 60_000L)?.let { return it }
        val shelves = InnerTube.charts()
        homeCache["charts"] = CacheEntry(shelves, System.currentTimeMillis())
        return shelves
    }

    suspend fun moods(): List<Shelf> {
        cached(homeCache, "moods", 60 * 60_000L)?.let { return it }
        val shelves = InnerTube.moodsAndGenres()
        homeCache["moods"] = CacheEntry(shelves, System.currentTimeMillis())
        return shelves
    }

    suspend fun podcasts(): List<Shelf> {
        cached(homeCache, "podcasts", 30 * 60_000L)?.let { return it }
        val shelves = InnerTube.podcasts()
        homeCache["podcasts"] = CacheEntry(shelves, System.currentTimeMillis())
        return shelves
    }

    suspend fun newReleases(): List<Shelf> {
        cached(homeCache, "new", 30 * 60_000L)?.let { return it }
        val shelves = InnerTube.newReleases()
        homeCache["new"] = CacheEntry(shelves, System.currentTimeMillis())
        return shelves
    }

    suspend fun explore(): List<Shelf> {
        cached(homeCache, "explore", 30 * 60_000L)?.let { return it }
        val shelves = InnerTube.explore()
        homeCache["explore"] = CacheEntry(shelves, System.currentTimeMillis())
        return shelves
    }

    suspend fun radio(seed: Track): List<Track> = resolveMutex.withLock {
        runCatching { InnerTube.upNext(seed.id) }.getOrDefault(emptyList())
    }

    suspend fun suggestions(query: String): List<String> = runCatching {
        InnerTube.searchSuggestions(query)
    }.getOrDefault(emptyList())

    suspend fun lyricsFor(track: Track): Lyrics {
        val primary = LyricsApi.find(track.title, track.artist, track.album, track.durationMs / 1000)
        if (!primary.isEmpty) return primary
        val captions = runCatching { InnerTube.captionLyrics(track.id) }.getOrDefault(Lyrics())
        return if (captions.isEmpty) primary else captions
    }
}
