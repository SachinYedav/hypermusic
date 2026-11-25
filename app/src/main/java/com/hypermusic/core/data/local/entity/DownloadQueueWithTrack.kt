package com.hypermusic.core.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Relation

@Immutable
data class DownloadQueueWithTrack(
    @Embedded val queueEntity: DownloadQueueEntity,
    @Relation(
        parentColumn = "trackId",
        entityColumn = "id"
    )
    val track: TrackEntity?
)
