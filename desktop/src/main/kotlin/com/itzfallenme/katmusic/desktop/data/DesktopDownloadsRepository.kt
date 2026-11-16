package com.itzfallenme.katmusic.desktop.data

import com.itzfallenme.katmusic.shared.data.DownloadedTrack
import com.itzfallenme.katmusic.shared.data.DownloadsRepository
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Desktop offline library: a real file store under `~/KatMusic/Downloads`.
 * The index is a small JSON sidecar next to the audio files, so downloads
 * survive restarts and the files stay directly usable in any player.
 *
 * [download] resolves audio through SimpMusic's extractor method
 * ([NewPipeStreamResolver]) with Piped as fallback; it fails with the
 * provider's real error when no source serves audio, never with a fake
 * success.
 */
class DesktopDownloadsRepository(
    private val piped: PipedClient = PipedClient(),
    private val resolver: NewPipeStreamResolver = NewPipeStreamResolver(),
    baseDir: Path = Paths.get(System.getProperty("user.home"), "KatMusic", "Downloads"),
) : DownloadsRepository {

    @Serializable
    private data class IndexEntry(
        val path: String,
        val title: String,
        val artist: String,
        val album: String = "",
        val durationMs: Long = 0L,
        val fileSizeBytes: Long = 0L,
        val downloadedAtMillis: Long = 0L,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val dir: Path = baseDir
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val indexFile: File get() = dir.resolve("index.json").toFile()

    private val _downloads = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    override val downloads: StateFlow<List<DownloadedTrack>> = _downloads.asStateFlow()

    private val _totalBytes = MutableStateFlow(0L)
    override val totalBytes: StateFlow<Long> = _totalBytes.asStateFlow()

    init {
        scope.launch { load() }
    }

    /**
     * Downloads [videoId] using SimpMusic's extractors (Piped as fallback)
     * and stores the audio file. Fails when resolution or the transfer
     * fails; nothing partial is kept.
     */
    suspend fun download(videoId: String, title: String, artist: String): Result<DownloadedTrack> =
        withContext(Dispatchers.IO) {
            val url = runCatching { resolver.resolveAudioUrl(videoId).getOrThrow() }
                .getOrElse { piped.resolveStream(videoId).getOrThrow() }
            runCatching {
                val extension = if (url.contains("opus", ignoreCase = true)) "webm" else "m4a"
                val file = dir.resolve("${safeFileName("$artist - $title")}.$extension").toFile()
                file.parentFile?.mkdirs()
                val connection = java.net.URI(url).toURL().openConnection().apply {
                    setRequestProperty("User-Agent", PipedClient.USER_AGENT)
                    setConnectTimeout(15_000)
                    setReadTimeout(120_000)
                }
                connection.getInputStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                val bytes = file.length()
                check(bytes >= MIN_VALID_AUDIO_BYTES) { "Downloaded file is empty; download failed" }
                val track = DownloadedTrack(
                    id = file.toURI().toString(),
                    title = title,
                    artist = artist,
                    album = "",
                    source = file.toURI().toString(),
                    fileSizeBytes = bytes,
                    durationMs = 0L,
                    downloadedAtMillis = System.currentTimeMillis(),
                )
                addToIndex(track)
                track
            }
        }

    override suspend fun remove(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val track = _downloads.value.firstOrNull { it.id == id }
                    ?: error("Download not found")
                runCatching { Files.deleteIfExists(pathOf(track.source)) }
                persistIndex(_downloads.value.filterNot { it.id == id })
                Unit
            }
        }
    }

    override suspend fun clearAll(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                _downloads.value.forEach { track ->
                    runCatching { Files.deleteIfExists(pathOf(track.source)) }
                }
                persistIndex(emptyList())
                Unit
            }
        }
    }

    private fun load() {
        val index = indexFile
        val entries = if (index.isFile) {
            runCatching {
                json.decodeFromString(ListSerializer(IndexEntry.serializer()), index.readText())
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val live = entries.mapNotNull { entry ->
            val file = File(entry.path)
            if (!file.isFile) return@mapNotNull null
            DownloadedTrack(
                id = file.toURI().toString(),
                title = entry.title,
                artist = entry.artist,
                album = entry.album,
                source = file.toURI().toString(),
                durationMs = entry.durationMs,
                fileSizeBytes = entry.fileSizeBytes,
                downloadedAtMillis = entry.downloadedAtMillis,
            )
        }
        _downloads.value = live.sortedByDescending { it.downloadedAtMillis }
        _totalBytes.value = live.sumOf { it.fileSizeBytes }
    }

    private suspend fun addToIndex(track: DownloadedTrack) = mutex.withLock {
        val next = (_downloads.value.filterNot { it.id == track.id } + track)
            .sortedByDescending { it.downloadedAtMillis }
        _downloads.value = next
        _totalBytes.value = next.sumOf { it.fileSizeBytes }
        persistIndex(next)
    }

    private fun persistIndex(tracks: List<DownloadedTrack>) {
        runCatching {
            dir.toFile().mkdirs()
            indexFile.writeText(
                json.encodeToString(
                    ListSerializer(IndexEntry.serializer()),
                    tracks.map {
                        IndexEntry(
                            path = sourceToPath(it.source),
                            title = it.title,
                            artist = it.artist,
                            album = it.album,
                            durationMs = it.durationMs,
                            fileSizeBytes = it.fileSizeBytes,
                            downloadedAtMillis = it.downloadedAtMillis,
                        )
                    },
                ),
            )
        }
    }

    private fun recomputeTotals() {
        _totalBytes.value = _downloads.value.sumOf { it.fileSizeBytes }
    }

    private fun pathOf(source: String): Path = Paths.get(sourceToPath(source))

    private fun sourceToPath(source: String): String = when {
        source.startsWith("file:") -> runCatching { Paths.get(java.net.URI(source)).toFile().absolutePath }.getOrDefault(source)
        else -> source
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120).ifBlank { "download" }

    private companion object {
        const val MIN_VALID_AUDIO_BYTES = 1_024L
    }
}
