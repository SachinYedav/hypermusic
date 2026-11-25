package com.hypermusic.core.designsystem.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import com.hypermusic.core.designsystem.theme.ShapeXs
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WaveLoader(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    val scales = (0 until 5).map { index ->
        val delay = index * 150
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale$index"
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(40.dp).padding(bottom = 8.dp)
        ) {
            scales.forEach { scale ->
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(20.dp)
                        .graphicsLayer { scaleY = scale.value }
                        .background(color = MaterialTheme.colorScheme.onBackground, shape = ShapeXs)
                )
            }
        }
        
        Text(
            text = "Wait a sec...",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )
    }
}

