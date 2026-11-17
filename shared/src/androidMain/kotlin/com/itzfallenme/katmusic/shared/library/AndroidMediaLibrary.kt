package com.itzfallenme.katmusic.shared.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.itzfallenme.katmusic.shared.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Android [MediaLibrary]: the device's Music library through MediaStore,
 * the same source the app's own local-music screens read.
 *
 * It reports the real reason it cannot scan (permission not granted, MediaStore
 * unavailable) through [scan] instead of showing an empty library, so the
 * shared UI can prompt for access rather than pretend the device has no music.
 */
class AndroidMediaLibrary(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : MediaLibrary {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    override val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _scan = MutableStateFlow(ScanState())
    override val scan: StateFlow<ScanState> = _scan.asStateFlow()

    override suspend fun refresh() {
        _scan.value = ScanState(isScanning = true)
        val result = withContext(Dispatchers.IO) { query() }
        result.fold(
            onSuccess = { list ->
                _tracks.value = list
                _scan.value = ScanState(isScanning = false, message = "${list.size} tracks")
            },
            onFailure = { error ->
                _scan.value = ScanState(isScanning = false, error = error.message ?: "Library scan failed")
            },
        )
    }

    /** Fire-and-forget refresh for callers that are not in a coroutine. */
    fun refreshAsync() {
        scope.launch { refresh() }
    }

    private fun query(): Result<List<Track>> {
        if (!hasAudioPermission()) {
            return Result.failure(IllegalStateException("Audio permission not granted"))
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val order = "${MediaStore.Audio.Media.ARTIST} COLLATE NOCASE, " +
            "${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE, " +
            "${MediaStore.Audio.Media.TRACK}"

        val found = ArrayList<Track>()
        context.contentResolver.query(collection, projection, selection, null, order)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val rawTrack = cursor.getInt(trackCol)
                val title = cursor.getString(titleCol)?.trim().orEmpty().ifBlank { "Unknown title" }
                val artist = cursor.getString(artistCol)?.trim()
                    ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                    ?: "Unknown artist"
                found += Track(
                    id = "mediastore:$id",
                    title = title,
                    artist = artist,
                    album = cursor.getString(albumCol)?.trim().orEmpty(),
                    durationMs = cursor.getLong(durationCol).coerceAtLeast(0L),
                    source = ContentUris.withAppendedId(collection, id).toString(),
                    artwork = albumArtworkUri(albumId),
                    // MediaStore stores "disc-track" as track + 1000 * disc.
                    trackNumber = if (rawTrack >= 1000) rawTrack % 1000 else rawTrack,
                    year = cursor.getInt(yearCol),
                    isLocal = true,
                )
            }
        }
        return Result.success(found)
    }

    private fun albumArtworkUri(albumId: Long): String? =
        runCatching {
            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId).toString()
        }.getOrNull()

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Android's audio library is the system MediaStore, not a folder the user
     * picks, so there is no folder to choose: the shared UI hides that action.
     */
    companion object {
        val platformActions: PlatformActions = object : PlatformActions {
            override val canChooseFolder: Boolean = false
            override suspend fun chooseLibraryFolder(): String? = null
        }
    }
}
