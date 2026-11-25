package com.hypermusic.feature.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import com.hypermusic.core.designsystem.theme.ShapeXl
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*

@Composable
fun PlayerActionChips(
    elementsAlpha: Float,
    currentX: Int = 0,
    width: Dp? = null,
    chipsY: Int,
    isLiked: Boolean,
    isSaved: Boolean = false,
    isDownloaded: Boolean = false,
    isPodcastEpisode: Boolean = false,
    titleText: Color,
    sleepTimerOption: String = "off",
    sleepTimerRemainingMs: Long? = null,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleSaveEpisode: () -> Unit = {},
    onGoToPodcastShow: () -> Unit = {},
    onDownloadClick: () -> Unit,
    onShareClick: () -> Unit,
    onLyricsClick: () -> Unit = {},
    onRelatedClick: () -> Unit = {},
    onTimerClick: () -> Unit = {}
) {
    if (elementsAlpha > 0f) {
        LazyRow(
            modifier = Modifier
                .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
                .offset { IntOffset(currentX, chipsY) }
                .alpha(elementsAlpha),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "like") {
                ActionChip(
                    icon = AppIcons.ThumbsUp,
                    label = if (isLiked) "Liked" else "Like",
                    titleText = titleText,
                    isActive = isLiked,
                    onClick = onToggleLike
                )
            }
            if (isPodcastEpisode) {
                item(key = "show") {
                    ActionChip(
                        icon = AppIcons.Podcast,
                        label = "Show",
                        titleText = titleText,
                        onClick = onGoToPodcastShow
                    )
                }
            } else {
                item(key = "lyrics") {
                    ActionChip(
                        icon = AppIcons.Lyrics,
                        label = "Lyrics",
                        titleText = titleText,
                        onClick = onLyricsClick
                    )
                }
            }
            item(key = "timer") {
                val isTimerActive = sleepTimerOption != "off"
                val timerLabel = when {
                    !isTimerActive -> "Timer"
                    sleepTimerOption == "end_of_track" -> "Track end"
                    sleepTimerRemainingMs != null && sleepTimerRemainingMs > 0 -> {
                        val mins = (sleepTimerRemainingMs / 60_000).toInt()
                        val secs = ((sleepTimerRemainingMs % 60_000) / 1000).toInt()
                        if (mins > 0) "${mins}m" else "${secs}s"
                    }
                    else -> "${sleepTimerOption}m"
                }
                ActionChip(
                    icon = AppIcons.Timer,
                    label = timerLabel,
                    titleText = titleText,
                    isActive = isTimerActive,
                    onClick = onTimerClick
                )
            }
            item(key = "download") {
                ActionChip(
                    icon = if (isDownloaded) AppIcons.Check else AppIcons.ArrowDownToLine,
                    label = if (isDownloaded) "Downloaded" else "Download",
                    titleText = titleText,
                    isActive = isDownloaded,
                    onClick = onDownloadClick
                )
            }
            item(key = "save") {
                if (isPodcastEpisode) {
                    ActionChip(
                        icon = if (isSaved) AppIcons.BookmarkFilled else AppIcons.Bookmark,
                        label = if (isSaved) "Saved" else "Save",
                        titleText = titleText,
                        isActive = isSaved,
                        onClick = onToggleSaveEpisode
                    )
                } else {
                    ActionChip(
                        icon = AppIcons.BookmarkPlus,
                        label = "Save",
                        titleText = titleText,
                        onClick = onAddToPlaylist
                    )
                }
            }
            item(key = "share") {
                ActionChip(
                    icon = AppIcons.Share2,
                    label = "Share",
                    titleText = titleText,
                    onClick = onShareClick
                )
            }
        }
    }
}

@Composable
fun ActionChip(
    icon: ImageVector,
    label: String,
    titleText: Color,
    isActive: Boolean = false,
    onClick: () -> Unit = {}
) {
    val bgColor = if (isActive) titleText else titleText.copy(alpha = 0.1f)
    val contentColor = if (isActive) Color.Black else titleText.copy(alpha = 0.9f)
    
    Row(
        modifier = Modifier
            .clip(ShapeXl)
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}
