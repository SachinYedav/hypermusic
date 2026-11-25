package com.hypermusic.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "PlaybackHistory",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["lastPlayedAt"], name = "idx_history_lastPlayed")
    ]
)
data class HistoryEntity(
    @PrimaryKey val trackId: String,
    val playCount: Int = 0,
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val totalListenTimeMs: Long = 0L
)

@Entity(tableName = "PlaybackCheckpoints")
data class PlaybackCheckpoint(
    @PrimaryKey val sessionId: String,
    val trackId: String,
    val creditedMs: Long,
    val updatedAt: Long
)
