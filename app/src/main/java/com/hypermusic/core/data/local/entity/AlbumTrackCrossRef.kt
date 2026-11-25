package com.hypermusic.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "AlbumTracks",
    primaryKeys = ["albumId", "trackId"],
    indices = [
        Index("albumId"),
        Index("trackId")
    ]
)
data class AlbumTrackCrossRef(
    val albumId: String,
    val trackId: String,
    val position: Int
)
