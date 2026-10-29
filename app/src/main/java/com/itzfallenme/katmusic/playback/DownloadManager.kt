package com.itzfallenme.katmusic.playback

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.itzfallenme.katmusic.data.DownloadEntry
import com.itzfallenme.katmusic.data.InnerTube
import com.itzfallenme.katmusic.data.LibraryRepository
import com.itzfallenme.katmusic.data.LyricsApi
import com.itzfallenme.katmusic.data.SettingsRepository
import com.itzfallenme.katmusic.data.StreamQuality
import com.itzfallenme.katmusic.data.StreamResolver
import com.itzfallenme.katmusic.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.Collections
import kotlin.math.roundToInt

enum class DownloadState {
    /** Waiting in the queue. */
    QUEUED,

    /** Paused because only mobile data is available and Wi-Fi-only is on. */
    WAITING_NETWORK,

    RUNNING,

    DONE,

    /** Gave up after the retries; [DownloadProgress.error] says why. */
    FAILED
}

data class DownloadProgress(
    val state: DownloadState = DownloadState.QUEUED,
    val fraction: Float = 0f,
    val error: String? = null,
    val bytes: Long = 0L,
    val totalBytes: Long = 0L,
    val attempt: Int = 1,
    /** The queued item, so a failed task can be retried after it left the queue. */
    val track: Track? = null
) {
    val running: Boolean get() = state == DownloadState.RUNNING
    val done: Boolean get() = state == DownloadState.DONE
    val queued: Boolean get() = state == DownloadState.QUEUED || state == DownloadState.WAITING_NETWORK
    val active: Boolean get() = queued || state == DownloadState.RUNNING
}

/** Thrown inside the copy loop when the user cancels an item mid-transfer. */
private class DownloadCancelled : Exception("Cancelled")

/**
 * Offline downloads.
 *
 * The old manager started a socket per tap, so selecting an album opened a dozen
 * parallel streams, there was no way to cancel or retry, and the "Wi-Fi only"
 * setting was never actually read. Downloads are now a FIFO queue with a single
 * worker: one transfer at a time (friendlier to the network and to the server),
 * a visible queued/blocked state, cancellation, one automatic retry, a free-space
 * guard, and Wi-Fi-only enforcement that resumes on its own once Wi-Fi returns.
 */
class DownloadManager(
    private val context: Context,
    private val library: LibraryRepository,
    private val settings: SettingsRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    /** Tracks waiting for their turn, in order. Does not include the running item. */
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _active = MutableStateFlow<String?>(null)
    val activeTrackId: StateFlow<String?> = _active.asStateFlow()

    private val cancelled: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val withLyrics: MutableMap<String, Boolean> = Collections.synchronizedMap(mutableMapOf())
    private val workerLock = Any()
    private var worker: Job? = null

    private val connectivity = runCatching {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }.getOrNull()

    private val _unmetered = MutableStateFlow(isUnmeteredNow())

    init {
        runCatching {
            connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _unmetered.value = isUnmeteredNow()
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _unmetered.value =
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }

                override fun onLost(network: Network) {
                    _unmetered.value = isUnmeteredNow()
                }
            })
        }
    }

    val root: File
        get() = (context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir)
            .resolve("KatMusic")
            .apply { mkdirs() }

    /** True when the current default network is unmetered (or unknown). */
    fun onWifi(): Boolean = _unmetered.value

    private fun isUnmeteredNow(): Boolean = runCatching {
        val caps = connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
            ?: return true
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }.getOrDefault(true)

    private fun safeName(value: String) = value.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()

    private fun progressFor(id: String) = _progress.value[id] ?: DownloadProgress()

    private fun update(id: String, block: (DownloadProgress) -> DownloadProgress) {
        _progress.value = _progress.value + (id to block(progressFor(id)))
    }

    private fun startWorker() {
        synchronized(workerLock) {
            if (worker?.isActive == true) return
            worker = scope.launch { drain() }
        }
    }

    /** Queues a track. Safe to call from list rows: duplicates are ignored. */
    fun download(track: Track, includeLyrics: Boolean = true) {
        if (!track.isPlayable) return
        if (track.isLocal) return
        if (library.downloadFor(track.id) != null) return
        val current = _progress.value[track.id]
        if (current?.active == true) return
        cancelled.remove(track.id)
        withLyrics[track.id] = includeLyrics
        update(track.id) { DownloadProgress(state = DownloadState.QUEUED, track = track) }
        if (_queue.value.none { it.id == track.id }) _queue.value = _queue.value + track
        startWorker()
    }

    fun downloadAll(tracks: List<Track>) = tracks.forEach { download(it) }

    /** Cancels a queued or running download and removes its partial file. */
    fun cancel(trackId: String) {
        cancelled.add(trackId)
        _queue.value = _queue.value.filterNot { it.id == trackId }
        if (_active.value != trackId) {
            _progress.value = _progress.value - trackId
        }
    }

    fun cancelAll() {
        _queue.value.forEach { cancelled.add(it.id) }
        _active.value?.let { cancelled.add(it) }
        _queue.value = emptyList()
        _progress.value = _progress.value.filterValues { it.done }
    }

    /** Re-queues a failed download. */
    fun retry(track: Track) {
        _progress.value = _progress.value - track.id
        cancelled.remove(track.id)
        download(track, withLyrics[track.id] ?: true)
    }

    /** Failed items in queue order, ready for a retry. */
    fun failedTracks(): List<Track> = _progress.value.values
        .filter { it.state == DownloadState.FAILED }
        .mapNotNull { it.track }

    private suspend fun drain() {
        while (true) {
            val track = _queue.value.firstOrNull() ?: break
            if (!awaitNetwork(track)) {
                _queue.value = _queue.value.filterNot { it.id == track.id }
                cancelled.remove(track.id)
                _progress.value = _progress.value - track.id
                continue
            }
            _queue.value = _queue.value.filterNot { it.id == track.id }
            _active.value = track.id

            var attempt = 0
            var lastError: String? = null
            while (attempt < 2) {
                attempt++
                update(track.id) {
                    it.copy(state = DownloadState.RUNNING, error = null, fraction = 0f, attempt = attempt)
                }
                val outcome = runCatching { fetch(track, withLyrics[track.id] ?: true) }
                val error = outcome.exceptionOrNull()
                if (error == null) {
                    lastError = null
                    break
                }
                if (error is DownloadCancelled) {
                    lastError = null
                    break
                }
                lastError = error.message ?: "Download failed"
                if (attempt < 2) delay(1_500)
            }

            _active.value = null
            if (cancelled.remove(track.id)) {
                _progress.value = _progress.value - track.id
                continue
            }
            update(track.id) { current ->
                if (lastError == null) {
                    current.copy(state = DownloadState.DONE, fraction = 1f, error = null)
                } else {
                    current.copy(state = DownloadState.FAILED, error = lastError)
                }
            }
        }
    }

    /**
     * Blocks the queue while Wi-Fi-only is on and the device is on mobile data,
     * checking every couple of seconds so cancelling still works and the worker
     * resumes without user action once Wi-Fi is back.
     */
    private suspend fun awaitNetwork(track: Track): Boolean {
        var blocked = false
        while (true) {
            if (cancelled.contains(track.id)) return false
            if (!settings.state.value.wifiOnlyDownloads || _unmetered.value) {
                if (blocked) update(track.id) { it.copy(state = DownloadState.QUEUED) }
                return true
            }
            if (!blocked) {
                blocked = true
                update(track.id) { it.copy(state = DownloadState.WAITING_NETWORK) }
            }
            delay(2_000)
        }
    }

    private fun checkCancelled(id: String) {
        if (cancelled.contains(id)) throw DownloadCancelled()
    }

    private suspend fun fetch(track: Track, includeLyrics: Boolean) = withContext(Dispatchers.IO) {
        checkCancelled(track.id)
        val free = runCatching { root.usableSpace }.getOrDefault(0L)
        if (free in 1 until 32L * 1024 * 1024) {
            error("Not enough free space (${humanBytes(free)} left)")
        }
        val highQuality = settings.state.value.highQualityStream
        val quality = when {
            settings.state.value.streamQuality == StreamQuality.SAVER.id ->
                StreamQuality.SAVER
            highQuality -> StreamQuality.MAX
            else -> StreamQuality.HIGH
        }
        val info = StreamResolver.resolve(track.id, quality)
        val extension = if (info.mimeType.contains("webm")) "webm" else "m4a"
        val fileName = "${safeName(track.artist)} - ${safeName(track.title)}"
            .ifBlank { track.id }
            .take(90) + " [${track.id}].$extension"
        val target = File(root, fileName)
        val temp = File(root, fileName + ".part")

        try {
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
                            checkCancelled(track.id)
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (System.currentTimeMillis() - lastUpdate > 180) {
                                lastUpdate = System.currentTimeMillis()
                                val fraction = if (total > 0) {
                                    (written.toFloat() / total).coerceIn(0f, 1f)
                                } else 0f
                                update(track.id) {
                                    it.copy(fraction = fraction, bytes = written, totalBytes = total)
                                }
                            }
                        }
                    }
                }
            }

            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                error("Could not save the file")
            }

            var lyricsPath: String? = null
            if (includeLyrics) {
                checkCancelled(track.id)
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
                    runCatching { lrcFile.writeText(lrc) }
                    if (lrcFile.exists()) lyricsPath = lrcFile.absolutePath
                }
            }

            val entry = DownloadEntry(
                track = track,
                path = target.absolutePath,
                lyricsPath = lyricsPath,
                downloadedAt = System.currentTimeMillis(),
                quality = quality.id,
                bytes = target.length()
            )
            library.addDownload(entry)
            withLyrics.remove(track.id)
            cancelled.remove(track.id)
        } catch (error: Throwable) {
            runCatching { temp.delete() }
            runCatching { if (target.exists() && target.length() == 0L) target.delete() }
            throw error
        }
    }

    fun delete(trackId: String) {
        cancel(trackId)
        val entry = library.downloadFor(trackId)
        runCatching { entry?.path?.let { File(it).delete() } }
        runCatching { entry?.lyricsPath?.let { File(it).delete() } }
        library.removeDownload(trackId)
        _progress.value = _progress.value - trackId
        withLyrics.remove(trackId)
    }

    /**
     * Drops download records whose file vanished (cleared data, deleted from a file
     * manager, failed write). Without this the downloads list looked complete while
     * tapping a row played nothing.
     */
    fun verifyDownloads() {
        scope.launch {
            val missing = library.state.value.downloads.filterNot { File(it.path).exists() }
            missing.forEach { library.removeDownload(it.track.id) }
        }
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
    suspend fun exportToMediaStore(entry: DownloadEntry): Result<android.net.Uri> = withContext(Dispatchers.IO) {
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

    fun freeBytes(): Long = runCatching { root.usableSpace }.getOrDefault(0L)

    fun humanTotalSize(): String = humanBytes(totalBytes())

    fun humanFreeSpace(): String = humanBytes(freeBytes())

    fun fmt(bytes: Long): String = humanBytes(bytes)

    fun percent(trackId: String): Int = (progressFor(trackId).fraction * 100).roundToInt()

    private fun humanBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return when {
            mb >= 1024 -> "%.1f GB".format(mb / 1024)
            mb >= 10 -> "%.0f MB".format(mb)
            else -> "%.1f MB".format(mb)
        }
    }
}
