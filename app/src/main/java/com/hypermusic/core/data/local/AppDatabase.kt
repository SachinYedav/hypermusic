package com.hypermusic.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hypermusic.core.data.local.dao.*
import com.hypermusic.core.data.local.entity.*

@Database(
    entities = [
        TrackEntity::class,
        HistoryEntity::class,
        PlaybackCheckpoint::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        AlbumEntity::class,
        AlbumTrackCrossRef::class,
        ArtistEntity::class,
        DownloadEntity::class,
        DownloadQueueEntity::class,
        PodcastEntity::class,
        SavedEpisodeEntity::class,
        RecentCollectionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun downloadDao(): DownloadDao
    abstract fun podcastDao(): PodcastDao
    abstract fun recentCollectionDao(): RecentCollectionDao
}
