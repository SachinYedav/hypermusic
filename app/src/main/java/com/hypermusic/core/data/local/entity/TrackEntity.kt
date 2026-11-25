package com.hypermusic.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val artistsJson: String? = null,
    val duration: Int? = null,
    val artworkUrl: String? = null,
    val localFilePath: String? = null,
    val localArtworkPath: String? = null,
    val isLiked: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val trackType: String = "song"
)
