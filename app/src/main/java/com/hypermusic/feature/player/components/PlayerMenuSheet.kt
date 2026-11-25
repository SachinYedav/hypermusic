package com.hypermusic.feature.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hypermusic.core.designsystem.components.AppFloatingBottomSheet
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.core.model.Track
import com.hypermusic.core.model.isDeviceOrigin
import com.hypermusic.core.designsystem.components.MediaArtwork
import com.hypermusic.feature.shared.utils.MediaActionHandler
import com.hypermusic.core.designsystem.theme.ShapeXs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMenuSheet(
    track: Track?,
    onDismissRequest: () -> Unit,
    onOpenRelated: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onGoToArtist: (Track) -> Unit,
    onGoToAlbum: (Track) -> Unit,
    onGoToPodcastShow: ((Track) -> Unit)? = null,
    onClearQueue: () -> Unit,
    sleepTimerOption: String = "off",
    sleepTimerRemainingMs: Long? = null,
    onOpenSleepTimer: () -> Unit = {}
) {
    AppFloatingBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            if (track != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(ShapeXs)
                    ) {
                        MediaArtwork(
                            imageUrl = track.artwork,
                            contentDescription = track.title,
                            trackType = track.trackType,
                            isDeviceLocal = track.isDeviceLocal,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (MediaActionHandler.isPodcastEpisode(track) && !track.album.isNullOrBlank()) {
                                "Episode • ${track.album}"
                            } else {
                                track.artist
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
            }

            val isEpisode = track != null && MediaActionHandler.isPodcastEpisode(track)
            val isDeviceTrack = track?.isDeviceOrigin() == true
            LazyColumn {
                if (!isDeviceTrack) {
                    item(key = "related") {
                        PlayerMenuItemRow(
                            icon = AppIcons.AudioWaveform,
                            label = "Related & Recommendations",
                            onClick = onOpenRelated
                        )
                    }
                    if (isEpisode) {
                        item(key = "podcast_show") {
                            PlayerMenuItemRow(
                                icon = AppIcons.Podcast,
                                label = "Go to Podcast Show",
                                onClick = { track?.let { onGoToPodcastShow?.invoke(it) } }
                            )
                        }
                    } else {
                        item(key = "artist") {
                            PlayerMenuItemRow(
                                icon = AppIcons.User,
                                label = "Go to Artist",
                                onClick = { track?.let { onGoToArtist(it) } }
                            )
                        }
                        item(key = "album") {
                            PlayerMenuItemRow(
                                icon = AppIcons.Disc3,
                                label = "Go to Album",
                                onClick = { track?.let { onGoToAlbum(it) } }
                            )
                        }
                    }
                }
                item(key = "equalizer") {
                    PlayerMenuItemRow(
                        icon = AppIcons.Sliders,
                        label = "Equalizer",
                        onClick = onNavigateToEqualizer
                    )
                }
                item(key = "sleep_timer") {
                    val trailingLabel = when {
                        sleepTimerOption == "off" -> null
                        sleepTimerRemainingMs != null && sleepTimerRemainingMs > 0 -> {
                            val mins = (sleepTimerRemainingMs / 60_000).toInt()
                            val secs = ((sleepTimerRemainingMs % 60_000) / 1000).toInt()
                            if (mins > 0) "${mins}m left" else "${secs}s left"
                        }
                        sleepTimerOption == "end_of_track" -> "End of track"
                        else -> "${sleepTimerOption}m left"
                    }
                    PlayerMenuItemRow(
                        icon = AppIcons.Timer,
                        label = "Sleep Timer",
                        trailingText = trailingLabel,
                        onClick = onOpenSleepTimer
                    )
                }
                item(key = "clear_queue") {
                    PlayerMenuItemRow(
                        icon = AppIcons.Trash2,
                        label = "Clear Queue",
                        iconColor = MaterialTheme.colorScheme.error,
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = onClearQueue
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerMenuItemRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    iconColor: Color = MaterialTheme.colorScheme.onSurface,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    trailingText: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                color = textColor
            ),
            modifier = Modifier.weight(1f)
        )
        if (trailingText != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
