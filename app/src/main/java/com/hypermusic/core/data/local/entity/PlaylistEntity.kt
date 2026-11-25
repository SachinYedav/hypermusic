package com.hypermusic.core.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "Playlists")
data class PlaylistEntity(
    @PrimaryKey val playlistId: String,
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val localArtworkPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isLocal: Boolean = true // true for user-created, false for extracted
)
