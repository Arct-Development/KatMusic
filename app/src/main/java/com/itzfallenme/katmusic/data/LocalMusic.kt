package com.itzfallenme.katmusic.data

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A track stored on the device, discovered through MediaStore. */
data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val path: String,
    val mimeType: String
) {
    fun toTrack(): Track = Track(
        id = "local-$id",
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artwork = if (albumId > 0) "content://media/external/audio/albumart/$albumId" else "",
        localPath = path
    )
}

/**
 * Reads the device's music collection. Nothing is copied or uploaded - the player opens
 * the file directly, which also lets downloaded KatMusic files play back offline.
 */
class LocalMusicRepository(private val context: Context) {

    suspend fun scan(): List<LocalTrack> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} > 10000"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val results = ArrayList<LocalTrack>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sort
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn) ?: continue
                    val artist = cursor.getString(artistColumn).orEmpty()
                    results.add(
                        LocalTrack(
                            id = cursor.getLong(idColumn),
                            title = cursor.getString(titleColumn) ?: continue,
                            artist = artist.takeUnless { it == "<unknown>" }.orEmpty(),
                            album = cursor.getString(albumColumn).orEmpty(),
                            albumId = cursor.getLong(albumIdColumn),
                            durationMs = cursor.getLong(durationColumn),
                            path = path,
                            mimeType = cursor.getString(mimeColumn).orEmpty()
                        )
                    )
                }
            }
        }
        results.sortedBy { it.title.lowercase() }
    }
}
