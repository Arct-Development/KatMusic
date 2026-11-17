package com.itzfallenme.katmusic.desktop.data

import com.itzfallenme.katmusic.shared.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

data class ListeningStats(
    val totalPlays: Int = 0,
    val uniqueTracks: Int = 0,
    val last7Days: Int = 0,
    val topArtists: List<Pair<String, Int>> = emptyList(),
    val topTracks: List<Pair<String, Int>> = emptyList(),
    val topGenres: List<Pair<String, Int>> = emptyList(),
)

/**
 * Desktop listening history: every track start is appended to a small JSON
 * file under `~/KatMusic`, and the derived totals are published for the
 * settings screen. This is real local history, not a placeholder.
 */
class DesktopListeningStats(
    baseDir: File = File(System.getProperty("user.home"), "KatMusic"),
) {
    @Serializable
    private data class PlayRecord(
        val trackId: String,
        val title: String,
        val artist: String,
        val genre: String,
        val playedAtMillis: Long,
    )

    private val file = File(baseDir, "listening-stats.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val plays = mutableListOf<PlayRecord>()

    private val _state = MutableStateFlow(ListeningStats())
    val state: StateFlow<ListeningStats> = _state.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                load()
                _state.value = summarize()
            }
        }
    }

    fun record(track: Track) {
        scope.launch {
            mutex.withLock {
                plays += PlayRecord(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    genre = track.genre,
                    playedAtMillis = System.currentTimeMillis(),
                )
                if (plays.size > MAX_RECORDS) {
                    plays.subList(0, plays.size - MAX_RECORDS).clear()
                }
                persist()
                _state.value = summarize()
            }
        }
    }

    private fun load() {
        if (!file.isFile) return
        runCatching {
            plays += json.decodeFromString(ListSerializer(PlayRecord.serializer()), file.readText())
        }
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(ListSerializer(PlayRecord.serializer()), plays))
        }
    }

    private fun summarize(): ListeningStats {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1_000
        return ListeningStats(
            totalPlays = plays.size,
            uniqueTracks = plays.map { it.trackId }.distinct().size,
            last7Days = plays.count { it.playedAtMillis >= weekAgo },
            topArtists = plays.groupingBy { it.artist }.eachCount()
                .entries.sortedByDescending { it.value }.take(5)
                .map { it.key to it.value },
            topTracks = plays.groupingBy { "${it.artist} — ${it.title}" }.eachCount()
                .entries.sortedByDescending { it.value }.take(5)
                .map { it.key to it.value },
            topGenres = plays.flatMap { record ->
                record.genre.split('/', ';', ',').map { it.trim() }.filter { it.isNotBlank() }
            }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.take(5)
                .map { it.key to it.value },
        )
    }

    private companion object {
        const val MAX_RECORDS = 5_000
    }
}
