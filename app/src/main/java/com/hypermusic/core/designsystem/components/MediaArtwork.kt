package com.hypermusic.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.hypermusic.core.utils.ArtworkQuality
import com.hypermusic.core.utils.ArtworkUrlResolver

/** Media artwork with a placeholder underneath loading or failed images. */
@Composable
fun MediaArtwork(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    trackType: String? = null,
    isDeviceLocal: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
    quality: ArtworkQuality = ArtworkQuality.THUMBNAIL
) {
    val fallbackIcon = getFallbackIcon(trackType, isDeviceLocal)
    val resolvedUrl = remember(imageUrl, quality) {
        ArtworkUrlResolver.resolveUrl(imageUrl, quality)
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxSize(0.5f)
        )

        if (!resolvedUrl.isNullOrEmpty()) {
            AsyncImage(
                model = resolvedUrl,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun getFallbackIcon(trackType: String?, isDeviceLocal: Boolean): ImageVector {
    if (isDeviceLocal) {
        return AppIcons.Folder
    }

    if (trackType.isNullOrEmpty()) {
        return AppIcons.Music
    }

    return when (trackType.lowercase()) {
        "video" -> AppIcons.Video
        "playlist" -> AppIcons.ListMusic
        "album" -> AppIcons.Disc3
        "podcast", "podcast_show", "podcast_episode" -> AppIcons.Podcast
        "artist" -> AppIcons.User
        "song" -> AppIcons.Music
        else -> AppIcons.Music
    }
}
