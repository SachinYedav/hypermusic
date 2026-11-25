package com.hypermusic.core.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "Artists")
data class ArtistEntity(
    @PrimaryKey val artistId: String,
    val name: String,
    val avatarUrl: String? = null,
    val localAvatarPath: String? = null,
    val savedAt: Long = System.currentTimeMillis()
)
