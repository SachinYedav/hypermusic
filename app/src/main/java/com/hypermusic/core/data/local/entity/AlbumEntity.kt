package com.hypermusic.core.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "Albums")
data class AlbumEntity(
    @PrimaryKey val albumId: String,
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val artworkUrl: String? = null,
    val localArtworkPath: String? = null,
    val year: String? = null,
    val savedAt: Long = System.currentTimeMillis()
)
