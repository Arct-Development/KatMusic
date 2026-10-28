package com.itzfallenme.katmusic.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Thin, cached facade over [InnerTube] used by the UI layer. */
object YtMusic {

    private data class CacheEntry<T>(val value: T, val at: Long)

    private val homeCache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry<List<Shelf>>>()
    private val detailCache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry<DetailPage>>()
    private val resolveMutex = Mutex()

    private val _home = MutableStateFlow<List<Shelf>>(emptyList())
    val home: StateFlow<List<Shelf>> = _home.asStateFlow()

    private val _homeLoading = MutableStateFlow(false)
    val homeLoading: StateFlow<Boolean> = _homeLoading.asStateFlow()

    private val _homeError = MutableStateFlow<String?>(null)
    val homeError: StateFlow<String?> = _homeError.asStateFlow()

    private fun <T> cached(map: MutableMap<String, CacheEntry<T>>, key: String, ttlMs: Long): T? {
        val entry = map[key] ?: return null
        return if (System.currentTimeMillis() - entry.at < ttlMs) entry.value else null
    }

    suspend fun loadHome(force: Boolean = false) {
        if (_homeLoading.value) return
        if (!force) {
            cached(homeCache, "home", 10 * 60_000L)?.let {
                _home.value = it
                return
            }
        }
        _homeLoading.value = true
        _homeError.value = null
        runCatching { InnerTube.homeShelves() }
            .onSuccess { shelves ->
                _home.value = shelves
                homeCache["home"] = CacheEntry(shelves, System.currentTimeMillis())
            }
            .onFailure { _homeError.value = it.message ?: "Could not reach YouTube Music" }
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

    suspend fun lyricsFor(track: Track): Lyrics =
        LyricsApi.find(track.title, track.artist, track.album, track.durationMs / 1000)
}
