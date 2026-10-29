package com.itzfallenme.katmusic.playback

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.withContext
import com.itzfallenme.katmusic.data.DownloadEntry
import com.itzfallenme.katmusic.data.InnerTube
import com.itzfallenme.katmusic.data.LibraryRepository
import com.itzfallenme.katmusic.data.LyricsApi
import com.itzfallenme.katmusic.data.SettingsRepository
import com.itzfallenme.katmusic.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import kotlin.math.roundToInt

data class DownloadProgress(
    val fraction: Float = 0f,
    val running: Boolean = false,
    val done: Boolean = false,
    val error: String? = null
)

class DownloadManager(
    private val context: Context,
    private val library: LibraryRepository,
    private val settings: SettingsRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    val root: File
        get() = (context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir)
            .resolve("KatMusic")
            .apply { mkdirs() }

    private fun safeName(value: String) = value.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()

    private fun progressFor(id: String) = _progress.value[id] ?: DownloadProgress()

    private fun update(id: String, block: (DownloadProgress) -> DownloadProgress) {
        _progress.value = _progress.value + (id to block(progressFor(id)))
    }

    fun download(track: Track, includeLyrics: Boolean = true) {
        if (progressFor(track.id).running) return
        update(track.id) { it.copy(running = true, done = false, error = null, fraction = 0f) }
        scope.launch {
            runCatching {
                val quality = if (settings.state.value.highQualityStream) {
                    com.itzfallenme.katmusic.data.StreamQuality.MAX
                } else {
                    com.itzfallenme.katmusic.data.StreamQuality.HIGH
                }
                val info = com.itzfallenme.katmusic.data.StreamResolver.resolve(track.id, quality)
                val extension = if (info.mimeType.contains("webm")) "webm" else "m4a"
                val fileName = "${safeName(track.artist)} - ${safeName(track.title)}"
                    .ifBlank { track.id }
                    .take(90) + " [${track.id}].$extension"
                val target = File(root, fileName)
                val temp = File(root, fileName + ".part")

                val request = Request.Builder()
                    .url(info.url)
                    .header("User-Agent", InnerTube.VR_UA)
                    .build()
                InnerTube.streamHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Download failed (${response.code})")
                    val body = response.body ?: error("Empty download stream")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var written = 0L
                            var lastUpdate = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total > 0 && System.currentTimeMillis() - lastUpdate > 180) {
                                    lastUpdate = System.currentTimeMillis()
                                    val fraction = (written.toFloat() / total).coerceIn(0f, 1f)
                                    update(track.id) { it.copy(fraction = fraction) }
                                }
                            }
                        }
                    }
                }
                if (target.exists()) target.delete()
                temp.renameTo(target)

                var lyricsPath: String? = null
                if (includeLyrics) {
                    val lyrics = runCatching {
                        LyricsApi.find(
                            track.title,
                            track.artist,
                            track.album,
                            track.durationMs / 1000
                        )
                    }.getOrNull()
                    if (lyrics != null && !lyrics.isEmpty) {
                        val lrc = if (lyrics.isSynced) {
                            lyrics.synced.joinToString("\n") { line ->
                                val minutes = line.timeMs / 60_000
                                val seconds = (line.timeMs % 60_000) / 1000
                                val hundredths = (line.timeMs % 1000) / 10
                                "[%02d:%02d.%02d]%s".format(minutes, seconds, hundredths, line.text)
                            }
                        } else lyrics.plain
                        val lrcFile = File(root, target.nameWithoutExtension + ".lrc")
                        lrcFile.writeText(lrc)
                        lyricsPath = lrcFile.absolutePath
                    }
                }

                val entry = DownloadEntry(
                    track = track,
                    path = target.absolutePath,
                    lyricsPath = lyricsPath,
                    downloadedAt = System.currentTimeMillis(),
                    quality = if (settings.state.value.highQualityStream) "high" else "normal",
                    bytes = target.length()
                )
                library.addDownload(entry)
                update(track.id) { DownloadProgress(fraction = 1f, running = false, done = true) }
            }.onFailure { error ->
                update(track.id) {
                    DownloadProgress(running = false, done = false, error = error.message ?: "Download failed")
                }
            }
        }
    }

    fun delete(trackId: String) {
        val entry = library.downloadFor(trackId)
        runCatching { entry?.path?.let { File(it).delete() } }
        runCatching { entry?.lyricsPath?.let { File(it).delete() } }
        library.removeDownload(trackId)
        _progress.value = _progress.value - trackId
    }

    fun existingFile(trackId: String): File? {
        val entry = library.downloadFor(trackId) ?: return null
        val file = File(entry.path)
        return file.takeIf { it.exists() }
    }

    /**
     * Copies a downloaded file into the shared Music collection, tagged with title, artist
     * and album so other players show it correctly. Files are copied, not moved, so the
     * offline copy keeps working when the download is later removed.
     */
    suspend fun exportToMediaStore(entry: DownloadEntry): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                error("Exporting needs Android 10 or newer")
            }
            val source = File(entry.path)
            if (!source.exists()) error("The downloaded file is missing")
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, source.name)
                put(MediaStore.Audio.Media.TITLE, entry.track.title)
                put(MediaStore.Audio.Media.ARTIST, entry.track.artist)
                put(MediaStore.Audio.Media.ALBUM, entry.track.album ?: "KatMusic")
                put(MediaStore.Audio.Media.DURATION, entry.track.durationMs)
                put(
                    MediaStore.Audio.Media.MIME_TYPE,
                    if (source.extension.equals("webm", true)) "audio/webm" else "audio/mp4"
                )
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MUSIC + "/KatMusic"
                )
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create a media entry")
            runCatching {
                resolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Could not write the exported file")
            }.onFailure {
                resolver.delete(uri, null, null)
                throw it
            }
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            uri
        }
    }

    fun totalBytes(): Long = library.state.value.downloads.sumOf { it.bytes }

    fun humanTotalSize(): String {
        val bytes = totalBytes()
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.0f MB".format(mb)
    }

    fun percent(trackId: String): Int = (progressFor(trackId).fraction * 100).roundToInt()
}
