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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64

/** A track's playable local path (or null when the source is a stream). */
internal fun Track.toLocalPath(): String? = when {
    source.startsWith("file:") -> runCatching { Paths.get(java.net.URI(source)).toFile().normalize().absolutePath }.getOrNull()
    else -> source.takeIf { it.startsWith("/") }
}


/**
 * Desktop playlists as real, portable `.m3u` files (with `#EXTM3U` /
 * `#EXTINF` headers) inside `~/KatMusic/Playlists`. Local files are portable
 * paths; online tracks are standard YouTube watch URLs with optional KatMusic
 * metadata comments so signed, expiring media URLs are never persisted.
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
        addTracksToPlaylist(playlistId, listOf(track))

    override suspend fun addTracksToPlaylist(playlistId: String, tracks: List<Track>): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val file = File(playlistId)
                    require(file.isFile) { "Playlist file is missing" }
                    val existing = parseM3u(file.readText(), baseDir.toFile())
                    val locations = existing.mapTo(mutableSetOf()) { it.first }
                    val additions = tracks.mapNotNull { track ->
                        val path = track.toLocalPath() ?: track.toM3uStreamUrl()
                            ?: error("“${track.title}” has no portable playlist location")
                        if (locations.add(path)) m3uEntry(track, path) else null
                    }
                    if (additions.isNotEmpty()) {
                        file.appendText(additions.joinToString(separator = ""))
                        reloadOne(file)
                    }
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
        private const val KATMUSIC_METADATA_PREFIX = "#KATMUSIC:1:"
        private val metadataJson = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class StreamMetadata(
            val videoId: String,
            val title: String,
            val artist: String,
            val album: String,
            val genre: String,
            val durationMs: Long,
            val artwork: String?,
            val trackNumber: Int,
            val year: Int,
        )

        /**
         * Parses M3U content into (location, track) pairs. Local paths are
         * resolved against [baseDir]; public YouTube URLs become unresolved
         * tracks that the Desktop player can resolve on demand.
         */
        internal fun parseM3u(content: String, baseDir: File): List<Pair<String, Track?>> {
            val entries = mutableListOf<Pair<String, Track?>>()
            var pendingTitle: String? = null
            var pendingArtist: String? = null
            var pendingDurationMs = 0L
            var pendingStreamMetadata: StreamMetadata? = null
            content.lines().forEach { raw ->
                val line = raw.trim()
                when {
                    line.startsWith(KATMUSIC_METADATA_PREFIX) -> {
                        val encoded = line.removePrefix(KATMUSIC_METADATA_PREFIX)
                        pendingStreamMetadata = runCatching {
                            val bytes = Base64.getUrlDecoder().decode(encoded)
                            metadataJson.decodeFromString<StreamMetadata>(bytes.toString(StandardCharsets.UTF_8))
                        }.getOrNull()
                    }
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
                        val stream = pendingStreamMetadata
                        val videoId = stream?.videoId ?: videoIdFromPlaylistUrl(line)
                        val (resolved, track) = if (videoId != null) {
                            line to Track(
                                id = videoId,
                                title = stream?.title ?: pendingTitle ?: "YouTube track",
                                artist = stream?.artist ?: pendingArtist ?: "Unknown artist",
                                album = stream?.album.orEmpty(),
                                genre = stream?.genre.orEmpty(),
                                durationMs = stream?.durationMs?.takeIf { it > 0L } ?: pendingDurationMs,
                                source = videoId,
                                artwork = stream?.artwork,
                                trackNumber = stream?.trackNumber ?: 0,
                                year = stream?.year ?: 0,
                                isLocal = false,
                                videoId = videoId,
                            )
                        } else {
                            val resolvedPath = resolvePath(line, baseDir)
                            val file = File(resolvedPath)
                            val localTrack = if (file.isFile) {
                                Track(
                                    id = file.absolutePath,
                                    title = pendingTitle ?: file.nameWithoutExtension,
                                    artist = pendingArtist ?: "Unknown artist",
                                    source = file.toURI().toString(),
                                    durationMs = pendingDurationMs,
                                    isLocal = true,
                                )
                            } else null
                            resolvedPath to localTrack
                        }
                        entries.add(resolved to track)
                        pendingTitle = null
                        pendingArtist = null
                        pendingDurationMs = 0L
                        pendingStreamMetadata = null
                    }
                }
            }
            return entries
        }

        internal fun m3uEntry(track: Track, location: String): String {
            val seconds = (track.durationMs / 1000L).coerceAtLeast(0L)
            val label = listOf(track.artist, track.title)
                .filter { it.isNotBlank() }
                .joinToString(" - ")
            val streamId = track.videoId ?: track.id.takeIf { it.matches(YOUTUBE_ID) }
            val metadata = if (!track.isLocal && streamId != null) {
                val encoded = metadataJson.encodeToString(
                    StreamMetadata.serializer(),
                    StreamMetadata(
                        videoId = streamId,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        genre = track.genre,
                        durationMs = track.durationMs,
                        artwork = track.artwork,
                        trackNumber = track.trackNumber,
                        year = track.year,
                    ),
                ).toByteArray(StandardCharsets.UTF_8)
                KATMUSIC_METADATA_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(encoded) + "\n"
            } else {
                ""
            }
            return metadata + "#EXTINF:$seconds,$label\n$location\n"
        }

        private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")

        private fun videoIdFromPlaylistUrl(value: String): String? {
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            val validHost = host == "youtu.be" ||
                host == "youtube.com" || host.endsWith(".youtube.com") ||
                host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
            if (!validHost) return null
            val id = if (host == "youtu.be") {
                uri.path.orEmpty().trim('/').substringBefore('/')
            } else {
                uri.rawQuery.orEmpty()
                    .split('&')
                    .firstOrNull { it.startsWith("v=") }
                    ?.substringAfter("v=")
            }
            return id?.takeIf { YOUTUBE_ID.matches(it) }
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

private fun Track.toM3uStreamUrl(): String? {
    if (isLocal) return null
    val id = (videoId ?: id).takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: return null
    return "https://www.youtube.com/watch?v=$id"
}
