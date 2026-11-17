package com.itzfallenme.katmusic.desktop.data

import com.itzfallenme.katmusic.shared.data.Playlist
import com.itzfallenme.katmusic.shared.data.PlaylistsRepository
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
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** A track's playable local path (or null when the source is a stream). */
internal fun Track.toLocalPath(): String? = when {
    source.startsWith("file:") -> runCatching { Paths.get(java.net.URI(source)).toFile().normalize().absolutePath }.getOrNull()
    else -> source.takeIf { it.startsWith("/") }
}


/**
 * Desktop playlists as real, portable `.m3u` files (with `#EXTM3U` /
 * `#EXTINF` headers) inside `~/KatMusic/Playlists`. Any other desktop player
 * can open them; KatMusic reads them back with tags refreshed from disk.
 */
class M3uPlaylistsRepository(
    baseDir: Path = Paths.get(System.getProperty("user.home"), "KatMusic", "Playlists"),
) : PlaylistsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val baseDir: Path = baseDir

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    override val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        scope.launch { load() }
    }

    override suspend fun create(title: String): Result<Playlist> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                baseDir.toFile().mkdirs()
                val name = safeFileName(title)
                require(name.isNotBlank()) { "Playlist name is empty" }
                var file = baseDir.resolve("$name.m3u").toFile()
                var counter = 2
                while (file.exists()) {
                    file = baseDir.resolve("$name ($counter).m3u").toFile()
                    counter++
                }
                file.writeText("#EXTM3U\n")
                val playlist = Playlist(id = file.absolutePath, title = title.trim(), tracks = emptyList(), createdAtMillis = file.lastModified())
                _playlists.value = (_playlists.value + playlist).sortedBy { it.title.lowercase() }
                playlist
            }
        }
    }

    /** Copies an external `.m3u`/`.m3u8` into the library and refreshes its tags. */
    suspend fun importM3u(file: File): Result<Playlist> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val extension = file.extension.lowercase()
                require(file.isFile && (extension == "m3u" || extension == "m3u8")) {
                    "Choose an .m3u or .m3u8 playlist"
                }
                baseDir.toFile().mkdirs()
                val name = safeFileName(file.nameWithoutExtension)
                var target = baseDir.resolve("$name.m3u").toFile()
                var counter = 2
                while (target.exists()) {
                    target = baseDir.resolve("$name ($counter).m3u").toFile()
                    counter++
                }
                file.copyTo(target, overwrite = false)
                val playlist = parsePlaylistFile(target)
                _playlists.value = (_playlists.value + playlist).sortedBy { it.title.lowercase() }
                playlist
            }
        }
    }

    override suspend fun delete(playlistId: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val file = File(playlistId)
                require(file.parentFile?.canonicalPath == baseDir.toFile().canonicalPath) {
                    "Playlist is not part of this library"
                }
                Files.deleteIfExists(file.toPath())
                _playlists.value = _playlists.value.filterNot { it.id == playlistId }
                Unit
            }
        }
    }

    override suspend fun addToPlaylist(playlistId: String, track: Track): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val file = File(playlistId)
                    require(file.isFile) { "Playlist file is missing" }
                    val existing = parseM3u(file.readText(), baseDir.toFile())
                    val path = track.toLocalPath()
                        ?: error("“${track.title}” is a stream — desktop playlists hold local files")
                    if (existing.any { it.first == path }) return@runCatching Unit
                    file.appendText(m3uEntry(track, path))
                    reloadOne(file)
                    Unit
                }
            }
        }

    override suspend fun removeFromPlaylist(playlistId: String, trackId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val file = File(playlistId)
                    require(file.isFile) { "Playlist file is missing" }
                    val existing = parseM3u(file.readText(), baseDir.toFile())
                    val target = existing.firstOrNull { it.second?.id == trackId }
                        ?: error("Track not in playlist")
                    val remaining = existing.filterNot { it === target }
                    file.writeText(buildString {
                        append("#EXTM3U\n")
                        remaining.forEach { (path, track) ->
                            if (track != null) append(m3uEntry(track, path))
                            else append(path).append('\n')
                        }
                    })
                    reloadOne(file)
                    Unit
                }
            }
        }

    private fun reloadOne(file: File) {
        val playlist = parsePlaylistFile(file)
        _playlists.value = _playlists.value.filterNot { it.id == file.absolutePath } + playlist
        _playlists.value = _playlists.value.sortedBy { it.title.lowercase() }
    }

    private fun load() {
        mutex.tryLock().let { locked ->
            if (!locked) return
            try {
                baseDir.toFile().mkdirs()
                val files = baseDir.toFile().listFiles { f -> f.isFile && f.extension.equals("m3u", true) }
                    ?: return
                _playlists.value = files.map(::parsePlaylistFile).sortedBy { it.title.lowercase() }
            } finally {
                mutex.unlock()
            }
        }
    }

    private fun parsePlaylistFile(file: File): Playlist {
        val parsed = runCatching { parseM3u(file.readText(), baseDir.toFile()) }.getOrDefault(emptyList())
        val tracks = parsed.mapNotNull { (_, track) -> track }
        return Playlist(
            id = file.absolutePath,
            title = file.nameWithoutExtension,
            tracks = tracks,
            createdAtMillis = file.lastModified(),
        )
    }

    companion object {
        /**
         * Parses M3U content into (path, track) pairs. The first entry of each
         * `#EXTINF` pair is the resolved absolute file path; the track carries
         * tags refreshed from the file when it still exists on disk.
         */
        internal fun parseM3u(content: String, baseDir: File): List<Pair<String, Track?>> {
            val entries = mutableListOf<Pair<String, Track?>>()
            var pendingTitle: String? = null
            var pendingArtist: String? = null
            var pendingDurationMs = 0L
            content.lines().forEach { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("#EXTINF:") -> {
                        val meta = line.removePrefix("#EXTINF:")
                        val comma = meta.indexOf(',')
                        val durationPart = if (comma >= 0) meta.substring(0, comma) else meta
                        val label = if (comma >= 0) meta.substring(comma + 1) else ""
                        pendingDurationMs = (durationPart.toDoubleOrNull()?.times(1000))?.toLong() ?: 0L
                        val parts = label.split(" - ", limit = 2)
                        pendingArtist = parts.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() }
                        pendingTitle = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                            ?: label.trim().takeIf { it.isNotBlank() }
                    }
                    line.isNotEmpty() && !line.startsWith("#") -> {
                        val resolved = resolvePath(line, baseDir)
                        val file = File(resolved)
                        val track = if (file.isFile) {
                            Track(
                                id = file.absolutePath,
                                title = pendingTitle ?: file.nameWithoutExtension,
                                artist = pendingArtist ?: "Unknown artist",
                                source = file.toURI().toString(),
                                durationMs = pendingDurationMs,
                                isLocal = true,
                            )
                        } else null
                        entries.add(resolved to track)
                        pendingTitle = null
                        pendingArtist = null
                        pendingDurationMs = 0L
                    }
                }
            }
            return entries
        }

        internal fun m3uEntry(track: Track, absolutePath: String): String {
            val seconds = (track.durationMs / 1000L).coerceAtLeast(0L)
            val label = listOf(track.artist, track.title)
                .filter { it.isNotBlank() }
                .joinToString(" - ")
            return "#EXTINF:$seconds,$label\n$absolutePath\n"
        }

        private fun resolvePath(line: String, baseDir: File): String {
            val raw = line.substringBefore('#')
            val file = File(raw)
            return if (file.isAbsolute) file.normalize().absolutePath
            else baseDir.resolve(raw).normalize().absolutePath
        }

        private fun safeFileName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120)
    }
}
