package com.hypermusic.core.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "Podcasts")
data class PodcastEntity(
    @PrimaryKey val podcastId: String,
    val title: String,
    val author: String,
    val authorId: String? = null,
    val artworkUrl: String? = null,
    val episodeCount: Int = 0,
    val savedAt: Long = System.currentTimeMillis()
)
