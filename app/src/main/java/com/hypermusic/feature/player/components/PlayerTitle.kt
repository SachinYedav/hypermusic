package com.hypermusic.feature.player.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.hypermusic.core.designsystem.theme.ShapeSm
import com.hypermusic.core.designsystem.theme.ShapeXs
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.ChevronRight

@Composable
fun PlayerTitleSection(
    title: String,
    artist: String,
    titleX: () -> Int,
    titleY: () -> Int,
    maxWidth: Dp,
    titleStyle: TextStyle,
    artistStyle: TextStyle,
    titleColor: Color,
    artistColor: Color,
    artistTopPadding: Dp,
    chevronAlpha: Float = 0f,
    onClick: () -> Unit = {},
    onArtistClick: () -> Unit = {},
    onExpand: () -> Unit = {}
) {
    val columnInteractionSource = remember { MutableInteractionSource() }
    val titleInteractionSource = remember { MutableInteractionSource() }
    val artistInteractionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .offset { IntOffset(titleX(), titleY()) }
            .width(maxWidth)
            .clip(ShapeSm)
            .clickable(
                interactionSource = columnInteractionSource,
                indication = null
            ) {
                if (chevronAlpha <= 0.5f) {
                    onExpand()
                }
            },
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(ShapeXs)
                .clickable(
                    interactionSource = titleInteractionSource,
                    indication = null
                ) {
                    if (chevronAlpha > 0.5f) {
                        onClick()
                    } else {
                        onExpand()
                    }
                }
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 1500,
                        initialDelayMillis = 2000,
                        velocity = 30.dp
                    )
            )
            if (chevronAlpha > 0f) {
                Icon(
                    imageVector = AppIcons.ChevronRight,
                    contentDescription = "Related",
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp)
                        .alpha(chevronAlpha),
                    tint = titleColor.copy(alpha = 0.85f * chevronAlpha)
                )
            }
        }
        Text(
            text = artist,
            style = artistStyle,
            color = artistColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .padding(top = artistTopPadding)
                .clip(ShapeXs)
                .clickable(
                    interactionSource = artistInteractionSource,
                    indication = null
                ) {
                    if (chevronAlpha > 0.5f) {
                        onArtistClick()
                    } else {
                        onExpand()
                    }
                }
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    repeatDelayMillis = 2000,
                    initialDelayMillis = 2500,
                    velocity = 25.dp
                )
        )
    }
}
