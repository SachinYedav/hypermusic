package com.hypermusic.core.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "Downloads",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("trackId")]
)
data class DownloadEntity(
    @PrimaryKey val trackId: String,
    val downloadedAt: Long,
    val sizeBytes: Long,
    val status: String = "COMPLETED",
    val mimeType: String? = null,
    val formatId: String? = null,
    val hasVideo: Boolean? = null
)
