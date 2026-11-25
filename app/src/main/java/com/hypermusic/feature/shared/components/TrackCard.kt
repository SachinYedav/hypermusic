package com.hypermusic.feature.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hypermusic.core.designsystem.components.AnimatedEQ
import com.hypermusic.core.designsystem.components.MediaArtwork
import com.hypermusic.core.designsystem.components.MediaMoreButton
import com.hypermusic.core.model.Track
import com.hypermusic.core.utils.ArtworkQuality
import com.hypermusic.feature.shared.utils.MediaActionHandler
import com.hypermusic.core.designsystem.theme.ShapeSm
import com.hypermusic.core.designsystem.theme.ShapeXs

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TrackCard(
    track: Track,
    onClick: (Track) -> Unit,
    onMoreClick: ((Track) -> Unit)? = null,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    downloadProgress: Float? = null,
    downloadStatus: String? = null
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    val isPodcastEpisode = MediaActionHandler.isPodcastEpisode(track)
    val isVideo = track.trackType == "video" || isPodcastEpisode
    val isArtist = MediaActionHandler.isArtist(track)

    val cardWidth = if (isVideo) minOf(screenWidth * 0.88f, 340.dp) else 160.dp
    val cardAspectRatio = if (isVideo) 16f / 9f else 1f

    Column(
        modifier = Modifier
            .width(cardWidth)
            .padding(end = 12.dp)
            .clip(ShapeSm)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = { onClick(track) },
                onLongClick = { onMoreClick?.invoke(track) }
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
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cardAspectRatio)
                .clip(if (isArtist) CircleShape else ShapeXs)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            MediaArtwork(
                imageUrl = track.artwork,
                contentDescription = track.title,
                trackType = track.trackType,
                isDeviceLocal = track.isDeviceLocal,
                contentScale = ContentScale.Crop,
                quality = if (isVideo) ArtworkQuality.VIDEO_CARD else ArtworkQuality.CARD,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrentTrack) {
                AnimatedEQ(
                    isPlaying = isPlaying,
                    color = Color.White,
                    modifier = Modifier.matchParentSize()
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = if (isArtist) TextAlign.Center else TextAlign.Start
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = if (isArtist) TextAlign.Center else TextAlign.Start
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (onMoreClick != null) {
                MediaMoreButton(
                    onClick = { onMoreClick(track) },
                    downloadProgress = downloadProgress,
                    downloadStatus = downloadStatus,
                    iconSize = 16.dp,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(24.dp)
                )
            }
        }
    }
}
