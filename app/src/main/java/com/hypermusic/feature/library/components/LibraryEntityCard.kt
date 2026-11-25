package com.hypermusic.feature.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hypermusic.core.designsystem.components.MediaListItem

@Composable
fun LibraryEntityCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCircular: Boolean = false,
    trackId: String? = null,
    trackType: String? = null,
    isDeviceLocal: Boolean = false,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    downloadProgress: Float? = null,
    downloadStatus: String? = null
) {
    MediaListItem(
        title = title,
        subtitle = subtitle,
        imageUrl = imageUrl,
        onClick = onClick,
        onLongClick = onMoreClick,
        onMoreClick = onMoreClick,
        trackId = trackId,
        trackType = trackType,
        isCircular = isCircular,
        isDeviceLocal = isDeviceLocal,
        isCurrentTrack = isCurrentTrack,
        isPlaying = isPlaying,
        downloadProgress = downloadProgress,
        downloadStatus = downloadStatus,
        imageSize = 56.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier
    )
}
