package com.hypermusic.core.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "RecentCollections",
    indices = [Index(value = ["lastVisitedAt"], name = "idx_recent_collections_visited")]
)
data class RecentCollectionEntity(
    @PrimaryKey val id: String,
    val type: String, // "playlist", "album", "podcast", "artist"
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val previewTracksJson: String? = null,
    val lastVisitedAt: Long = System.currentTimeMillis(),
    val visitCount: Int = 1
)
