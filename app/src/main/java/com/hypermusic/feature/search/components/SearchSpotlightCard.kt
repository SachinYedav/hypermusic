package com.hypermusic.feature.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.model.Track
import com.hypermusic.core.utils.ArtworkQuality
import com.hypermusic.core.utils.rememberDominantColor
import com.hypermusic.core.designsystem.components.MediaArtwork
import com.hypermusic.core.designsystem.components.MediaMoreButton
import com.hypermusic.feature.shared.utils.MediaActionHandler
import com.hypermusic.core.designsystem.theme.ShapeLg
import com.hypermusic.core.designsystem.theme.ShapeMd
import com.hypermusic.core.designsystem.theme.ShapeXs

/** Prominent card for the top search result. */
@Composable
fun SearchSpotlightCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    downloadProgress: Float? = null,
    downloadStatus: String? = null
) {
    val context = LocalContext.current
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val dominantColor = rememberDominantColor(
        context = context,
        imageUrl = track.artwork,
        defaultColor = surfaceVariantColor
    )

    val cardBackgroundBrush = remember(dominantColor, isDark, surfaceVariantColor) {
        val startTint = dominantColor.copy(alpha = if (isDark) 0.35f else 0.20f)
        val endTint = surfaceVariantColor.copy(alpha = if (isDark) 0.55f else 0.40f)
        Brush.horizontalGradient(
            0.0f to startTint,
            0.55f to endTint.copy(alpha = if (isDark) 0.70f else 0.50f),
            1.0f to endTint
        )
    }

    val isPodcast = MediaActionHandler.isPodcastShow(track)
    val isArtist = track.trackType == "artist" || track.id.startsWith("UC") || track.id.startsWith("HC")
    val isPlaylist = track.trackType == "playlist" || track.id.startsWith("VL") || track.id.startsWith("PL") || track.id.startsWith("RD")
    val isPlayable = track.trackType in listOf("song", "video", "podcast_episode") || track.id.startsWith("MPED")
    val displayType = when {
        isPodcast -> "Podcast"
        isArtist -> "Artist"
        isPlaylist -> "Playlist"
        isPlayable -> if (track.trackType == "video") "Video" else if (track.trackType == "podcast_episode" || track.id.startsWith("MPED")) "Episode" else "Song"
        track.trackType == "album" -> "Album"
        else -> "Top Result"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "TOP RESULT",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShapeLg)
                .background(cardBackgroundBrush)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.20f else 0.12f),
                    shape = ShapeLg
                )
                .clickable { onClick() },
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            shape = ShapeLg
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MediaArtwork(
                    imageUrl = track.artwork,
                    contentDescription = track.title,
                    trackType = if (isPodcast) "podcast" else if (isArtist) "artist" else if (isPlaylist) "playlist" else track.trackType,
                    contentScale = ContentScale.Crop,
                    quality = ArtworkQuality.CARD,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(if (isArtist) CircleShape else ShapeMd)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = ShapeXs,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.75f else 0.85f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = displayType,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!track.artist.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (onMoreClick != null) {
                    MediaMoreButton(
                        onClick = onMoreClick,
                        downloadProgress = downloadProgress,
                        downloadStatus = downloadStatus
                    )
                }
            }
        }
    }
}
