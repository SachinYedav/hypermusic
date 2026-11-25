package com.hypermusic.feature.widget.data

import android.graphics.Bitmap
import com.hypermusic.core.utils.ColorExtractor
import com.hypermusic.core.utils.DynamicColors

data class WidgetPlaybackState(
    val title: String = "HyperMusic",
    val artist: String = "Turn on the HyperMusic",
    val album: String = "",
    val artwork: Bitmap? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val isLiked: Boolean = false,
    val canLike: Boolean = false,
    val repeatMode: Int = 0,
    val hasTrack: Boolean = false,
    val dynamicColors: DynamicColors = artwork?.let { ColorExtractor.extract(it) } ?: DynamicColors.DEFAULT
)
