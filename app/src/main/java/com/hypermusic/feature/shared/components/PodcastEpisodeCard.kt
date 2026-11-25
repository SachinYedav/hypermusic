package com.hypermusic.feature.shared.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.designsystem.components.MediaArtwork
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.core.model.Track
import com.hypermusic.core.utils.ArtworkQuality
import com.hypermusic.core.designsystem.theme.AccentPurple
import com.hypermusic.core.designsystem.theme.ShapeXs

@Composable
fun PodcastEpisodeCard(
    track: Track,
    onPlayClick: () -> Unit,
    onSaveClick: () -> Unit,
    onMoreClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onRemoveDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Float? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPlayClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                MediaArtwork(
                    imageUrl = track.artwork,
                    contentDescription = track.title,
                    trackType = "podcast_episode",
                    contentScale = ContentScale.Crop,
                    quality = ArtworkQuality.CARD,
                    modifier = Modifier
                        .width(112.dp)
                        .aspectRatio(16f / 9f)
                        .clip(ShapeXs)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val metaParts = listOfNotNull(
                        track.releaseDate?.takeIf { it.isNotBlank() },
                        formatPodcastDuration(track.duration ?: 0L).takeIf { it.isNotBlank() }
                    )
                    val metadataText = metaParts.joinToString(" • ")

                    if (metadataText.isNotBlank()) {
                        Text(
                            text = metadataText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { onPlayClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppIcons.Play,
                            contentDescription = "Play Episode",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp)
                        )
                        val playDuration = formatPodcastDuration(track.duration ?: 0L)
                        if (playDuration.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = playDuration,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSaveClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isSaved) AppIcons.BookmarkFilled else AppIcons.Bookmark,
                            contentDescription = if (isSaved) "Remove from Saved" else "Save for Later",
                            tint = if (isSaved) AccentPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isDownloading) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(36.dp)
                        ) {
                            val progress = downloadProgress ?: 0f
                            if (progress > 0f) {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                            Icon(
                                imageVector = AppIcons.ArrowDownToLine,
                                contentDescription = "Downloading",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    } else if (isDownloaded) {
                        IconButton(
                            onClick = onRemoveDownloadClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Trash2,
                                contentDescription = "Remove Download",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onDownloadClick,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.ArrowDownToLine,
                                contentDescription = "Download Episode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.MoreVertical,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        )
    }
}

private fun formatPodcastDuration(durationSec: Long): String {
    if (durationSec <= 0L) return ""
    val hours = durationSec / 3600
    val minutes = (durationSec % 3600) / 60
    return if (hours > 0) {
        if (minutes > 0) "${hours} hr ${minutes} min" else "${hours} hr"
    } else {
        "${minutes} min"
    }
}
