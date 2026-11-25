package com.hypermusic.feature.search.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hypermusic.core.designsystem.components.MediaListItem
import com.hypermusic.core.model.Track

@Composable
fun TrackResultCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    downloadProgress: Float? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    leadingContent: (@Composable () -> Unit)? = null,
    downloadStatus: String? = null
) {
    val subtitle = when {
        track.trackType == "artist" -> {
            val count = track.artist.ifBlank { null }
            if (count != null && !count.equals("Artist", ignoreCase = true)) "Artist • $count" else "Artist"
        }
        else -> track.artist.ifBlank { null }
            ?: track.trackType?.replaceFirstChar { it.uppercase() }
            ?: ""
    }

    MediaListItem(
        title = track.title,
        subtitle = subtitle,
        imageUrl = track.artwork,
        onClick = onClick,
        onMoreClick = onMoreClick,
        trackId = track.id,
        trackType = track.trackType,
        isCircular = track.trackType == "artist",
        isDeviceLocal = track.isDeviceLocal,
        isCurrentTrack = isCurrentTrack,
        isPlaying = isPlaying,
        downloadProgress = downloadProgress,
        downloadStatus = downloadStatus,
        imageSize = 56.dp,
        modifier = modifier,
        contentPadding = contentPadding,
        leadingContent = leadingContent
    )
}
