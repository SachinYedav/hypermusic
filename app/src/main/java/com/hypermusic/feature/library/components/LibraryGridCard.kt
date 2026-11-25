package com.hypermusic.feature.library.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hypermusic.core.designsystem.components.MediaGridCard

@Composable
fun LibraryGridCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCircular: Boolean = false,
    onLongClick: () -> Unit = {},
    trackType: String? = null,
    isDeviceLocal: Boolean = false,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    downloadProgress: Float? = null,
    downloadStatus: String? = null
) {
    MediaGridCard(
        title = title,
        subtitle = subtitle,
        imageUrl = imageUrl,
        onClick = onClick,
        onLongClick = onLongClick,
        isCircular = isCircular,
        trackType = trackType,
        isDeviceLocal = isDeviceLocal,
        isCurrentTrack = isCurrentTrack,
        isPlaying = isPlaying,
        downloadProgress = downloadProgress,
        downloadStatus = downloadStatus,
        modifier = modifier
    )
}
