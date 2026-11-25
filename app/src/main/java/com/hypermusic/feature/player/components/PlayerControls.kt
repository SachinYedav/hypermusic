package com.hypermusic.feature.player.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.hypermusic.core.designsystem.theme.ShapeHandle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import com.hypermusic.core.designsystem.theme.DarkBackground
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControls(
    elementsAlpha: Float,
    currentX: Int = 0,
    width: Dp? = null,
    currentProgressY: Int,
    currentControlsY: Int,
    controlScale: Float,
    playbackPosition: Long = 0L,
    playbackPositionProvider: () -> Long = { playbackPosition },
    totalDuration: Long,
    isPlaying: Boolean,
    playbackState: Int,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    isPodcastOrVideo: Boolean = false,
    titleText: Color,
    bodyText: Color,
    dominantColor: Color,
    onSeek: (Long) -> Unit,
    onSeekForward: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    if (elementsAlpha > 0f) {
        val maxDuration = totalDuration.toFloat().coerceAtLeast(1f)
        var isDragging by remember { mutableStateOf(false) }
        var dragPosition by remember { mutableFloatStateOf(0f) }
        val currentSliderPos = if (isDragging) dragPosition else playbackPositionProvider().toFloat()

        Column(
            modifier = Modifier
                .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
                .offset { IntOffset(currentX, currentProgressY) }
                .padding(horizontal = 24.dp)
                .alpha(elementsAlpha)
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val isDragged by interactionSource.collectIsDraggedAsState()
            val isThumbActive = isDragging || isDragged
            val thumbSize by animateDpAsState(
                targetValue = if (isThumbActive) 16.dp else 11.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "thumbSize"
            )

            Slider(
                value = currentSliderPos.coerceIn(0f, maxDuration),
                onValueChange = {
                    isDragging = true
                    dragPosition = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    onSeek(dragPosition.toLong())
                },
                valueRange = 0f..maxDuration,
                interactionSource = interactionSource,
                thumb = {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(thumbSize)
                                .shadow(
                                    elevation = if (isThumbActive) 4.dp else 1.dp,
                                    shape = CircleShape
                                )
                                .clip(CircleShape)
                                .background(titleText)
                        )
                    }
                },
                track = { sliderState ->
                    val fraction = (sliderState.value / maxDuration).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .clip(ShapeHandle)
                            .background(titleText.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(ShapeHandle)
                                .background(titleText)
                        )
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-4).dp)
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentSliderPos.toLong()),
                    fontSize = 11.5.sp,
                    color = bodyText,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp
                )
                Text(
                    text = formatTime(totalDuration),
                    fontSize = 11.5.sp,
                    color = bodyText,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
                .offset { IntOffset(currentX, currentControlsY) }
                .padding(horizontal = 16.dp)
                .alpha(elementsAlpha)
                .graphicsLayer {
                    scaleX = controlScale
                    scaleY = controlScale
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (isPodcastOrVideo) {
                IconButton(onClick = onSeekBackward) {
                    Icon(
                        imageVector = AppIcons.SeekToBackward,
                        contentDescription = "Rewind 10 seconds",
                        modifier = Modifier.size(26.dp),
                        tint = titleText
                    )
                }
            } else {
                IconButton(onClick = onToggleShuffle) {
                    val shuffleBg = if (shuffleModeEnabled) titleText.copy(alpha = 0.15f) else Color.Transparent
                    val shuffleTint = if (shuffleModeEnabled) titleText else titleText.copy(alpha = 0.4f)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(shuffleBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Shuffle,
                            contentDescription = "Shuffle",
                            modifier = Modifier.size(24.dp),
                            tint = shuffleTint
                        )
                    }
                }
            }

            IconButton(onClick = onSkipPrevious) {
                Icon(
                    imageVector = AppIcons.SkipBack,
                    contentDescription = "Previous",
                    modifier = Modifier.size(32.dp),
                    tint = titleText
                )
            }

            val playPauseIconColor = if (titleText.luminance() > 0.5f) DarkBackground else Color.White
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(titleText)
            ) {
                if (playbackState == Player.STATE_BUFFERING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = playPauseIconColor,
                        strokeWidth = 3.dp
                    )
                } else {
                    Crossfade(
                        targetState = isPlaying,
                        animationSpec = tween(150),
                        label = "MainPlayPauseCrossfade"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) AppIcons.Pause else AppIcons.Play,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(32.dp),
                            tint = playPauseIconColor
                        )
                    }
                }
            }

            IconButton(onClick = onSkipNext) {
                Icon(
                    imageVector = AppIcons.SkipForward,
                    contentDescription = "Next",
                    modifier = Modifier.size(32.dp),
                    tint = titleText
                )
            }

            if (isPodcastOrVideo) {
                IconButton(onClick = onSeekForward) {
                    Icon(
                        imageVector = AppIcons.SeekToForward,
                        contentDescription = "Forward 10 seconds",
                        modifier = Modifier.size(26.dp),
                        tint = titleText
                    )
                }
            } else {
                IconButton(onClick = onToggleRepeat) {
                    val repeatVector = if (repeatMode == Player.REPEAT_MODE_ONE) {
                        AppIcons.Repeat1
                    } else {
                        AppIcons.Repeat
                    }
                    val isActive = repeatMode != Player.REPEAT_MODE_OFF
                    val repeatBg = if (isActive) titleText.copy(alpha = 0.15f) else Color.Transparent
                    val repeatTint = if (isActive) titleText else titleText.copy(alpha = 0.4f)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(repeatBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = repeatVector,
                            contentDescription = "Repeat",
                            modifier = Modifier.size(24.dp),
                            tint = repeatTint
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds)
}
