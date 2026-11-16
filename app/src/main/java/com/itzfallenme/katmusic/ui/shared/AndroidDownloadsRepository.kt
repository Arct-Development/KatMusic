package com.itzfallenme.katmusic.ui.shared

import android.content.Context
import com.itzfallenme.katmusic.data.download.TrackDownloadManager
import com.itzfallenme.katmusic.data.local.db.DownloadedTrackDao
import com.itzfallenme.katmusic.data.local.db.DownloadedTrackEntity
import com.itzfallenme.katmusic.shared.data.DownloadsRepository
import com.itzfallenme.katmusic.shared.data.DownloadedTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android [DownloadsRepository]: the app's real Room download table
 * ([DownloadedTrackDao]) — the exact rows the main Downloads screen manages —
 * with file deletion going through the app's own [TrackDownloadManager] so
 * MediaStore entries and sidecar `.lrc` files are removed the same way.
 */
@Singleton
class AndroidDownloadsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadedTrackDao,
    private val downloadManager: TrackDownloadManager,
) : DownloadsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<List<DownloadedTrack>>(emptyList())
    override val downloads: StateFlow<List<DownloadedTrack>> = _downloads.asStateFlow()

    private val _totalBytes = MutableStateFlow(0L)
    override val totalBytes: StateFlow<Long> = _totalBytes.asStateFlow()

    init {
        scope.launch {
            dao.getAll().collect { entities ->
                val mapped = entities.map { it.toDownloadedTrack() }
                _downloads.value = mapped
                _totalBytes.value = mapped.sumOf { it.fileSizeBytes }
            }
        }
    }

    override suspend fun remove(id: String): Result<Unit> = runCatching {
        val rowId = id.removePrefix("dl:").toLongOrNull()
            ?: error("Not a Room download id: $id")
        val entity = dao.findById(rowId) ?: error("Download not found")
        downloadManager.deleteDownloadedTrack(entity)
        Unit
    }

    override suspend fun clearAll(): Result<Unit> = runCatching {
        val all = dao.getAllList()
        all.forEach { entity ->
            runCatching { downloadManager.deleteDownloadedTrack(entity) }
        }
        dao.clearAll()
        Unit
    }

    private fun DownloadedTrackEntity.toDownloadedTrack(): DownloadedTrack = DownloadedTrack(
        id = "dl:$id",
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        source = when {
            filePath.isNotBlank() && java.io.File(filePath).exists() -> java.io.File(filePath).toURI().toString()
            !mediaStoreUri.isNullOrBlank() -> mediaStoreUri!!
            else -> filePath
        },
        durationMs = durationMs,
        fileSizeBytes = fileSizeBytes,
        downloadedAtMillis = downloadedAtMillis,
    )
}
