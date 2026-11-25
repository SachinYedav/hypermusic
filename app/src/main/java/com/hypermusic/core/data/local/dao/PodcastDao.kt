package com.hypermusic.core.data.local.dao

import androidx.room.*
import com.hypermusic.core.data.local.entity.PodcastEntity
import com.hypermusic.core.data.local.entity.SavedEpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcast(podcast: PodcastEntity)

    @Delete
    suspend fun deletePodcast(podcast: PodcastEntity)

    @Query("DELETE FROM Podcasts WHERE podcastId = :podcastId")
    suspend fun deletePodcastById(podcastId: String)

    @Query("SELECT * FROM Podcasts ORDER BY savedAt DESC")
    fun getAllPodcasts(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM Podcasts WHERE podcastId = :podcastId LIMIT 1")
    suspend fun getPodcastById(podcastId: String): PodcastEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM Podcasts WHERE podcastId = :podcastId)")
    fun isPodcastSaved(podcastId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedEpisode(episode: SavedEpisodeEntity)

    @Delete
    suspend fun deleteSavedEpisode(episode: SavedEpisodeEntity)

    @Query("DELETE FROM SavedEpisodes WHERE episodeId = :episodeId")
    suspend fun deleteSavedEpisodeById(episodeId: String)

    @Query("SELECT * FROM SavedEpisodes ORDER BY savedAt DESC")
    fun getAllSavedEpisodes(): Flow<List<SavedEpisodeEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM SavedEpisodes WHERE episodeId = :episodeId)")
    fun isEpisodeSaved(episodeId: String): Flow<Boolean>
}
