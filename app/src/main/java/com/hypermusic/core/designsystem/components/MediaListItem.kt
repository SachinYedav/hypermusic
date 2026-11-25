package com.hypermusic.core.designsystem.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.core.designsystem.theme.ShapeXs

/** Media row with artwork and playback/download status. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaListItem(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    trackId: String? = null,
    trackType: String? = null,
    isCircular: Boolean = false,
    isDeviceLocal: Boolean = false,
    imageSize: Dp = 56.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    rippleColor: Color = Color.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    downloadProgress: Float? = null,
    downloadStatus: String? = null
) {
    val indication = if (rippleColor != Color.Unspecified) ripple(color = rippleColor) else ripple()
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = indication,
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = indication,
            onClick = onClick
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .then(
                if (isCurrentTrack) {
                    Modifier.semantics {
                        stateDescription = if (isPlaying) "Playing" else "Current track, paused"
                    }
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(modifier = Modifier.width(12.dp))
        }

        val artworkShape = if (isCircular) CircleShape else ShapeXs
        Box(
            modifier = Modifier
                .size(imageSize)
                .clip(artworkShape)
        ) {
            MediaArtwork(
                imageUrl = imageUrl,
                contentDescription = title,
                trackType = trackType,
                isDeviceLocal = isDeviceLocal,
                contentScale = ContentScale.Crop,
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

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        } else if (onMoreClick != null) {
            MediaMoreButton(
                onClick = onMoreClick,
                downloadProgress = downloadProgress,
                downloadStatus = downloadStatus
            )
        }
    }
}

/** Keeps More available while displaying the shared per-track download state. */
@Composable
fun MediaMoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    downloadProgress: Float? = null,
    downloadStatus: String? = null
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "More options" }
    ) {
        MediaDownloadIndicator(downloadProgress, downloadStatus, Modifier.size(iconSize), tint)
    }
}

@Composable
fun MediaDownloadIndicator(
    downloadProgress: Float?,
    downloadStatus: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val progress = downloadProgress?.takeIf { it in 0f..1f }
    val isDownloading = downloadStatus == "DOWNLOADING" || (downloadStatus == null && progress != null)

    if (isDownloading) {
        val statusDescription = if (progress == null || progress == 0f) {
            "Preparing download"
        } else {
            "Downloading ${(progress * 100).toInt()} percent"
        }
        val statusModifier = modifier.semantics {
            stateDescription = statusDescription
        }
        if (progress == null || progress == 0f) {
            CircularProgressIndicator(
                modifier = statusModifier,
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            CircularProgressIndicator(
                progress = { progress },
                modifier = statusModifier,
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    } else {
        Icon(
            imageVector = AppIcons.MoreVertical,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}
