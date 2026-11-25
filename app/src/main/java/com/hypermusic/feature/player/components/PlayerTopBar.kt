package com.hypermusic.feature.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*
import com.hypermusic.core.designsystem.theme.ShapeFull
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

@Composable
fun PlayerTopBar(
    topBarAlpha: Float,
    currentX: Int = 0,
    width: Dp? = null,
    currentY: Int = 0,
    titleText: Color,
    isVideoMode: Boolean = false,
    onCollapse: () -> Unit,
    onEqualizerClick: () -> Unit = {},
    onModeToggleClick: () -> Unit = {},
    onMoreClick: () -> Unit
) {
    if (topBarAlpha > 0f) {
        Row(
            modifier = Modifier
                .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
                .offset { IntOffset(currentX, currentY) }
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .alpha(topBarAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = AppIcons.ChevronDown,
                    contentDescription = "Collapse",
                    modifier = Modifier.size(32.dp),
                    tint = titleText
                )
            }
            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .clip(ShapeFull)
                    .background(titleText.copy(alpha = 0.08f))
                    .semantics {
                        contentDescription = "Playback mode"
                        stateDescription = if (isVideoMode) "Video mode active" else "Audio mode active"
                    }
                    .clickable(
                        onClickLabel = "Show playback mode details"
                    ) { onModeToggleClick() }
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(ShapeFull)
                        .background(if (!isVideoMode) titleText.copy(alpha = 0.22f) else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Headphones,
                        contentDescription = null,
                        tint = if (!isVideoMode) titleText else titleText.copy(alpha = 0.45f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                Box(
                    modifier = Modifier
                        .clip(ShapeFull)
                        .background(if (isVideoMode) titleText.copy(alpha = 0.22f) else Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Video,
                        contentDescription = null,
                        tint = if (isVideoMode) titleText else titleText.copy(alpha = 0.45f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onEqualizerClick) {
                Icon(
                    imageVector = AppIcons.Sliders,
                    contentDescription = "Equalizer",
                    modifier = Modifier.size(22.dp),
                    tint = titleText
                )
            }
            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = AppIcons.MoreVertical,
                    contentDescription = "More",
                    modifier = Modifier.size(22.dp),
                    tint = titleText
                )
            }
        }
    }
}
