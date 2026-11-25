package com.hypermusic.core.designsystem.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PausedBarHeight = object : State<Float> {
    override val value: Float = 4f
}

@Composable
fun AnimatedEQ(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    isPlaying: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
    alignment: Alignment = Alignment.Center
) {
    if (!isActive) return

    val h1: State<Float>
    val h2: State<Float>
    val h3: State<Float>
    if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "eq")
        h1 = animateEqBar(infiniteTransition, targetPlayingHeight = 14f, durationMs = 350)
        h2 = animateEqBar(infiniteTransition, targetPlayingHeight = 18f, durationMs = 400)
        h3 = animateEqBar(infiniteTransition, targetPlayingHeight = 12f, durationMs = 300)
    } else {
        h1 = PausedBarHeight
        h2 = PausedBarHeight
        h3 = PausedBarHeight
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
        contentAlignment = alignment
    ) {
        Canvas(
            modifier = Modifier
                .width(16.dp)
                .height(18.dp)
        ) {
            val barWidth = 3.dp.toPx()
            val gap = 2.5.dp.toPx()
            val corner = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())

            val height1 = h1.value.dp.toPx()
            val height2 = h2.value.dp.toPx()
            val height3 = h3.value.dp.toPx()

            val totalBarsWidth = (barWidth * 3) + (gap * 2)
            val startX = (size.width - totalBarsWidth) / 2f

            drawRoundRect(
                color = color,
                topLeft = Offset(startX, size.height - height1),
                size = Size(barWidth, height1),
                cornerRadius = corner
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + barWidth + gap, size.height - height2),
                size = Size(barWidth, height2),
                cornerRadius = corner
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + (barWidth + gap) * 2, size.height - height3),
                size = Size(barWidth, height3),
                cornerRadius = corner
            )
        }
    }
}

@Composable
private fun animateEqBar(
    transition: InfiniteTransition,
    targetPlayingHeight: Float,
    durationMs: Int
): State<Float> {
    return transition.animateFloat(
        initialValue = 4f,
        targetValue = targetPlayingHeight,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar"
    )
}
