package com.hypermusic.core.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "SavedEpisodes")
data class SavedEpisodeEntity(
    @PrimaryKey val episodeId: String,
    val showId: String? = null,
    val showTitle: String? = null,
    val title: String,
    val author: String,
    val artworkUrl: String? = null,
    val duration: Long = 0L,
    val durationText: String? = null,
    val description: String? = null,
    val releaseDate: String? = null,
    val savedAt: Long = System.currentTimeMillis()
)
