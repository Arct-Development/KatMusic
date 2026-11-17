package com.itzfallenme.katmusic.desktop.library

import com.itzfallenme.katmusic.desktop.DesktopSettingsStore
import com.itzfallenme.katmusic.shared.library.MediaLibrary
import com.itzfallenme.katmusic.shared.library.ScanState
import com.itzfallenme.katmusic.shared.models.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * The desktop library: the audio files in the folders the user picked.
 *
 * Files are indexed in place (nothing is copied), tags are read with
 * jaudiotagger, and artwork is the folder's cover image when one exists —
 * the same "local files win over network lookups" rule the Android app uses.
 */
class DesktopMediaLibrary(
    private val store: DesktopSettingsStore,
) : MediaLibrary {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    override val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _scan = MutableStateFlow(ScanState())
    override val scan: StateFlow<ScanState> = _scan.asStateFlow()

    override suspend fun refresh() = withContext(Dispatchers.IO) {
        _scan.value = ScanState(isScanning = true, message = "Scanning…")
        val roots = store.libraryFolders().map(::File).filter { it.isDirectory }
        if (roots.isEmpty()) {
            _tracks.value = emptyList()
            _scan.value = ScanState(isScanning = false, message = "No music folder chosen")
            return@withContext
        }
        val files = roots.flatMap { root ->
            runCatching {
                root.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
                    .toList()
            }.getOrDefault(emptyList())
        }
        val parsed = files
            .mapNotNull { readTrack(it) }
            .distinctBy { it.id }
            .sortedWith(
                compareBy(
                    { it.artist.lowercase() },
                    { it.album.lowercase() },
                    { it.trackNumber },
                    { it.title.lowercase() },
                ),
            )
        _tracks.value = parsed
        _scan.value = ScanState(
            isScanning = false,
            message = when {
                parsed.isEmpty() -> "No playable audio in ${roots.size} folder(s)"
                roots.size == 1 -> "${parsed.size} tracks · ${roots.first().name}"
                else -> "${parsed.size} tracks · ${roots.size} folders"
            },
        )
    }

    private fun readTrack(file: File): Track? {
        val fallbackTitle = file.nameWithoutExtension
        return runCatching {
            val audioFile = AudioFileIO.read(file)
            val tag = runCatching { audioFile.tag }.getOrNull()
            val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
                ?: parseFromFileName(fallbackTitle)
            val artist = tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() }
                ?: "Unknown artist"
            val album = runCatching { tag?.getFirst(FieldKey.ALBUM) }.getOrNull().orEmpty()
            val genre = runCatching { tag?.getFirst(FieldKey.GENRE) }.getOrNull().orEmpty()
            val trackNumber = runCatching { tag?.getFirst(FieldKey.TRACK)?.toIntOrNull() }.getOrNull() ?: 0
            val year = runCatching { tag?.getFirst(FieldKey.YEAR)?.take(4)?.toIntOrNull() }.getOrNull() ?: 0
            val durationSeconds = runCatching { audioFile.audioHeader?.trackLength ?: 0 }.getOrDefault(0)
            Track(
                id = file.absolutePath,
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                durationMs = durationSeconds * 1000L,
                source = file.toURI().toString(),
                artwork = coverArt(file.parentFile)?.toURI()?.toString(),
                trackNumber = trackNumber,
                year = year,
                isLocal = true,
            )
        }.getOrNull()
    }

    /** "Artist - Title" file names are common in hand-curated folders. */
    private fun parseFromFileName(name: String): String {
        val trimmed = name.trim()
        val separator = trimmed.indexOf(" - ")
        return if (separator > 0) trimmed.substring(separator + 3).trim() else trimmed
    }

    private fun coverArt(folder: File?): File? {
        if (folder == null || !folder.isDirectory) return null
        val candidates = folder.listFiles { file ->
            file.isFile && file.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
        } ?: return null
        return candidates.firstOrNull { it.nameWithoutExtension.lowercase() in COVER_NAMES }
            ?: candidates.firstOrNull()
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("mp3", "ogg", "oga", "wav", "aiff", "aif")
        val COVER_NAMES = setOf("cover", "folder", "front", "album", "artwork")
    }
}
