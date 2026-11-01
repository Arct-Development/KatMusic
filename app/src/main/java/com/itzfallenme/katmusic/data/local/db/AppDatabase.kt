package com.itzfallenme.katmusic.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.itzfallenme.katmusic.data.podcast.PodcastProgressDao
import com.itzfallenme.katmusic.data.podcast.PodcastProgressEntity

@Database(
    entities = [
        ArtworkCacheEntity::class,
        RecommendationExclusionEntity::class,
        SavedPlaylistEntity::class,
        DownloadedTrackEntity::class,
        SongPlayStatsEntity::class,
        RelatedGraphEdgeEntity::class,
        PodcastProgressEntity::class,
    ],
    version = 15,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun artworkCacheDao(): ArtworkCacheDao
    abstract fun recommendationExclusionDao(): RecommendationExclusionDao
    abstract fun savedPlaylistDao(): SavedPlaylistDao
    abstract fun downloadedTrackDao(): DownloadedTrackDao
    abstract fun songPlayStatsDao(): SongPlayStatsDao
    abstract fun relatedGraphDao(): RelatedGraphDao
    abstract fun podcastProgressDao(): PodcastProgressDao

    /** Clears all retained listening-derived and metadata cache rows. Download
     * records are deliberately excluded: TrackDownloadManager must delete
     * their actual files before their database rows can be removed. */
    @Transaction
    open suspend fun clearListeningHistoryAndCaches() {
        songPlayStatsDao().clearAll()
        artworkCacheDao().clearAll()
        relatedGraphDao().clearAll()
    }
}
