package com.hypermusic.core.designsystem.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hypermusic.core.utils.ArtworkQuality
import com.hypermusic.core.designsystem.theme.ShapeSm
import com.hypermusic.core.designsystem.theme.ShapeXs

/** Grid counterpart to [MediaListItem], with artwork and playback/download status. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trackType: String? = null,
    isCircular: Boolean = false,
    isDeviceLocal: Boolean = false,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    downloadProgress: Float? = null,
    downloadStatus: String? = null
) {
    val artworkShape = if (isCircular) CircleShape else ShapeXs
    val textAlignment = if (isCircular) Alignment.CenterHorizontally else Alignment.Start

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeSm)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(
                if (isCurrentTrack) {
                    Modifier.semantics {
                        stateDescription = if (isPlaying) "Playing" else "Current track, paused"
                    }
                } else {
                    Modifier
                }
            )
            .padding(4.dp),
        horizontalAlignment = textAlignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(artworkShape)
        ) {
            MediaArtwork(
                imageUrl = imageUrl,
                contentDescription = title,
                trackType = if (isCircular) "artist" else trackType,
                isDeviceLocal = isDeviceLocal,
                contentScale = ContentScale.Crop,
                quality = ArtworkQuality.CARD,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrentTrack) {
                AnimatedEQ(
                    isPlaying = isPlaying,
                    color = Color.White,
                    modifier = Modifier.matchParentSize()
                )
            }
            val isDownloading = downloadStatus == "DOWNLOADING" || (downloadStatus == null && downloadProgress != null && downloadProgress in 0f..1f)
            if (isDownloading) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .size(28.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    MediaDownloadIndicator(downloadProgress, downloadStatus, Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
