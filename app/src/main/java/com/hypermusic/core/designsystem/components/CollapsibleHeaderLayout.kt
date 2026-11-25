package com.hypermusic.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@Stable
class CollapsibleHeaderState {
    var headerHeightPx by mutableFloatStateOf(0f)
        internal set

    var headerOffset by mutableFloatStateOf(0f)
        internal set

    val headerFraction: Float
        get() = if (headerHeightPx > 0f) (-headerOffset / headerHeightPx).coerceIn(0f, 1f) else 0f

    val isCollapsed: Boolean
        get() = headerHeightPx > 0f && headerOffset <= -headerHeightPx * 0.95f

    private val animatable = Animatable(0f)

    fun snapOffset(newOffset: Float) {
        headerOffset = newOffset.coerceIn(-headerHeightPx, 0f)
    }

    suspend fun animateTo(target: Float) {
        val clampedTarget = target.coerceIn(-headerHeightPx, 0f)
        animatable.snapTo(headerOffset)
        animatable.animateTo(
            targetValue = clampedTarget,
            animationSpec = spring(stiffness = 600f, dampingRatio = Spring.DampingRatioNoBouncy)
        ) {
            headerOffset = value
        }
    }

    fun snapToExpanded() {
        headerOffset = 0f
    }

    fun snapToCollapsed() {
        if (headerHeightPx > 0f) {
            headerOffset = -headerHeightPx
        }
    }
}

@Composable
fun rememberCollapsibleHeaderState(): CollapsibleHeaderState {
    return remember { CollapsibleHeaderState() }
}

@Composable
fun CollapsibleHeaderLayout(
    state: CollapsibleHeaderState,
    modifier: Modifier = Modifier,
    headerBackground: Color = MaterialTheme.colorScheme.background,
    headerBrush: Brush? = null,
    scrollAlpha: () -> Float = { if (state.headerFraction > 0f) 1f else 0f },
    canScrollProvider: () -> Boolean = { true },
    header: @Composable () -> Unit,
    content: @Composable (contentPadding: PaddingValues) -> Unit
) {
    val density = LocalDensity.current
    val statusBarHeightDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val statusBarHeightPx = with(density) { statusBarHeightDp.toPx() }

    val nestedScrollConnection = remember(state, canScrollProvider) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val maxCollapse = -state.headerHeightPx

                if (state.headerHeightPx <= 0f) return Offset.Zero

                // 1. Scrolling Down (finger dragging up, delta < 0): collapse header
                if (delta < 0f) {
                    if (!canScrollProvider() && state.headerOffset == 0f) {
                        return Offset.Zero
                    }
                    if (state.headerOffset > maxCollapse) {
                        val newOffset = (state.headerOffset + delta).coerceIn(maxCollapse, 0f)
                        state.snapOffset(newOffset)
                        return Offset.Zero
                    }
                }

                // 2. Scrolling Up (finger dragging down, delta > 0): quick-return header
                if (delta > 0f && state.headerOffset < 0f) {
                    val newOffset = (state.headerOffset + delta).coerceIn(maxCollapse, 0f)
                    state.snapOffset(newOffset)
                    return Offset.Zero
                }

                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val maxCollapse = -state.headerHeightPx
                val offset = state.headerOffset

                if (state.headerHeightPx <= 0f) return super.onPostFling(consumed, available)

                if (offset < 0f && offset > maxCollapse) {
                    val midPoint = maxCollapse / 2f
                    val target = if (available.y > 300f || offset > midPoint) {
                        0f
                    } else {
                        maxCollapse
                    }
                    state.animateTo(target)
                }

                return super.onPostFling(consumed, available)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        val totalRestingHeaderDp = with(density) {
            (statusBarHeightPx + state.headerHeightPx).toDp()
        }

        content(PaddingValues(top = totalRestingHeaderDp))

        // Status bar background that transitions to solid when list items scroll behind it
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarHeightDp)
                .zIndex(5f)
                .drawBehind {
                    val alpha = scrollAlpha()
                    if (alpha > 0f) {
                        drawRect(headerBackground.copy(alpha = alpha))
                    }
                }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarHeightDp)
                .clipToBounds()
                .zIndex(4f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, state.headerOffset.roundToInt()) }
                    .onSizeChanged {
                        state.headerHeightPx = it.height.toFloat()
                    }
                    .drawBehind {
                        val alpha = scrollAlpha()
                        if (alpha > 0f) {
                            if (headerBrush != null) {
                                drawRect(brush = headerBrush)
                            } else {
                                drawRect(headerBackground.copy(alpha = alpha))
                            }
                        }
                    }
            ) {
                header()
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = scrollAlpha() },
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
            }
        }
    }
}
