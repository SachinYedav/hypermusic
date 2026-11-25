package com.hypermusic.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "DownloadQueue",
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
data class DownloadQueueEntity(
    @PrimaryKey val id: String, // Track ID used as the queue key.
    val trackId: String,
    val status: String = "PENDING",
    val progress: Int = 0,
    val priority: Int = 0,
    val retryCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)
