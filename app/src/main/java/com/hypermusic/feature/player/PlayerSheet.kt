package com.hypermusic.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.hypermusic.navigation.NavigationEventBus
import com.hypermusic.navigation.navigateToCollection
import com.hypermusic.MainActivity
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.feature.player.components.PlayerScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.max
import kotlin.math.roundToInt

enum class UnifiedPlayerState { Hidden, Mini, Fullscreen, QueueHalf, QueueFull }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerSheet(
    playerRepository: PlayerRepository = koinInject(),
    navigationEventBus: NavigationEventBus = koinInject(),
    navController: NavController? = null,
    bottomBarHeight: Float = 0f,
    onNavigateToEqualizer: () -> Unit = {},
    bottomBar: @Composable (Modifier) -> Unit = {}
) {
    val currentTrack by playerRepository.currentTrack.collectAsState()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val screenHeightPx = constraints.maxHeight.toFloat()
        val miniPlayerHeightPx = with(density) { 64.dp.toPx() }
        val screenWidthPx = constraints.maxWidth.toFloat()

        val hiddenY = screenHeightPx + 200f
        val miniY = max(screenHeightPx - bottomBarHeight - miniPlayerHeightPx, 0f)
        val fullscreenY = 0f

        val isLandscape = screenWidthPx > screenHeightPx

        val navBarBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val queuePeekHeightPx = with(density) { 68.dp.toPx() } + navBarBottomPx
        val queueCollapsedY = screenHeightPx - queuePeekHeightPx
        val queueHalfY = screenHeightPx * 0.55f
        val queueFullY = with(density) { 110.dp.toPx() } // Target 110 dp of clearance for the compact player header.

        val safeQueueHalfY = minOf(queueHalfY, queueCollapsedY)
        val safeQueueFullY = minOf(queueFullY, safeQueueHalfY)

        val dragDistanceToHalf = maxOf(0f, queueCollapsedY - safeQueueHalfY)
        val dragDistanceToFull = maxOf(0f, queueCollapsedY - safeQueueFullY)

        // In landscape, skip QueueHalf by using QueueFull distance for both
        val effectiveDragDistanceToHalf = if (isLandscape) dragDistanceToFull else dragDistanceToHalf

        val queueHalfAnchor = if (isLandscape) -dragDistanceToFull else -dragDistanceToHalf
        val queueFullAnchor = -dragDistanceToFull

        val currentTrackState = rememberUpdatedState(currentTrack)
        val decaySpec = remember { exponentialDecay<Float>() }

        @Suppress("DEPRECATION")
        val state = remember {
            AnchoredDraggableState<UnifiedPlayerState>(
                initialValue = UnifiedPlayerState.Hidden,
                positionalThreshold = { distance: Float -> distance * 0.4f },
                velocityThreshold = { with(density) { 300.dp.toPx() } },
                snapAnimationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                decayAnimationSpec = decaySpec,
                confirmValueChange = { newValue ->
                    if (newValue == UnifiedPlayerState.Hidden && currentTrackState.value != null) false
                    else true
                }
            )
        }

        val hasTrack = currentTrack != null
        val isHidden = state.currentValue == UnifiedPlayerState.Hidden
        val activeOverlaySheet by playerRepository.activeOverlaySheet.collectAsState()

        LaunchedEffect(screenHeightPx, bottomBarHeight, isLandscape) {
            val anchors = DraggableAnchors {
                UnifiedPlayerState.Hidden at hiddenY
                UnifiedPlayerState.Mini at miniY
                UnifiedPlayerState.Fullscreen at fullscreenY
                if (!isLandscape) {
                    UnifiedPlayerState.QueueHalf at queueHalfAnchor
                }
                UnifiedPlayerState.QueueFull at queueFullAnchor
            }
            state.updateAnchors(anchors)
        }

        LaunchedEffect(currentTrack) {
            if (currentTrack != null && state.currentValue == UnifiedPlayerState.Hidden) {
                state.animateTo(UnifiedPlayerState.Mini)
            } else if (currentTrack == null && state.currentValue != UnifiedPlayerState.Hidden) {
                state.animateTo(UnifiedPlayerState.Hidden)
            }
        }

        LaunchedEffect(state.currentValue) {
            playerRepository.updatePlayerSheetState(state.currentValue.ordinal)
            if (state.currentValue == UnifiedPlayerState.Mini || state.currentValue == UnifiedPlayerState.Hidden) {
                if (playerRepository.activeOverlaySheet.value != com.hypermusic.core.player.PlayerOverlaySheet.NONE) {
                    playerRepository.closeOverlaySheet()
                }
            }
        }

        val scope = rememberCoroutineScope()

        // Collapse the player when navigation changes so the destination is visible.
        if (navController != null) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            var lastObservedRoute by remember { mutableStateOf(currentRoute) }

            LaunchedEffect(currentRoute) {
                if (currentRoute != null) {
                    if (lastObservedRoute != null && currentRoute != lastObservedRoute) {
                        playerRepository.closeOverlaySheet()
                        if (state.currentValue != UnifiedPlayerState.Mini && 
                            state.currentValue != UnifiedPlayerState.Hidden) {
                            state.animateTo(UnifiedPlayerState.Mini)
                        }
                    }
                    lastObservedRoute = currentRoute
                }
            }
        }

        // Reveal search before an external autofocus request opens the keyboard.
        val autoFocusTrigger by navigationEventBus.autoFocusSearchTrigger.collectAsState()
        LaunchedEffect(autoFocusTrigger) {
            if (autoFocusTrigger > 0L) {
                playerRepository.closeOverlaySheet()
                if (state.currentValue != UnifiedPlayerState.Mini && 
                    state.currentValue != UnifiedPlayerState.Hidden) {
                    state.animateTo(UnifiedPlayerState.Mini)
                }
            }
        }

        if (activeOverlaySheet != com.hypermusic.core.player.PlayerOverlaySheet.NONE) {
            BackHandler {
                playerRepository.closeOverlaySheet()
            }
        } else if (state.currentValue == UnifiedPlayerState.QueueFull || state.currentValue == UnifiedPlayerState.QueueHalf) {
            BackHandler {
                scope.launch {
                    state.animateTo(UnifiedPlayerState.Fullscreen)
                }
            }
        } else if (state.currentValue != UnifiedPlayerState.Mini && state.targetValue != UnifiedPlayerState.Mini && state.currentValue != UnifiedPlayerState.Hidden) {
            BackHandler {
                scope.launch {
                    state.animateTo(UnifiedPlayerState.Mini)
                }
            }
        }

        val playerProgress by remember(miniY, fullscreenY) {
            derivedStateOf {
                val currentOffset = if (state.offset.isNaN()) hiddenY else state.offset
                if (currentOffset <= fullscreenY) 1f
                else if (miniY == fullscreenY) 0f
                else ((miniY - currentOffset) / (miniY - fullscreenY)).coerceIn(0f, 1f)
            }
        }

        val queueDragProgress by remember(fullscreenY, queueFullAnchor) {
            derivedStateOf {
                val currentOffset = if (state.offset.isNaN()) hiddenY else state.offset
                if (currentOffset >= fullscreenY) 0f
                else (-currentOffset).coerceIn(0f, dragDistanceToFull)
            }
        }

        if (state.offset.isNaN()) return@BoxWithConstraints

        bottomBar(
            Modifier
                .align(Alignment.BottomCenter)
                .zIndex(2f) // Always stay on top so PlayerSheet slides from behind it
                .offset { IntOffset(0, (bottomBarHeight * playerProgress).roundToInt()) }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { (screenHeightPx + dragDistanceToFull).toDp() })
                .offset {
                    val currentOffset = if (state.offset.isNaN()) hiddenY else state.offset
                    IntOffset(0, currentOffset.roundToInt())
                }
                .anchoredDraggable(
                    state = state,
                    orientation = Orientation.Vertical,
                    enabled = activeOverlaySheet == com.hypermusic.core.player.PlayerOverlaySheet.NONE
                )
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                PlayerScreen(
                    progress = playerProgress,
                    queueDragProgress = queueDragProgress,
                    dragDistanceToHalf = effectiveDragDistanceToHalf,
                    dragDistanceToFull = dragDistanceToFull,
                    viewportHeight = screenHeight,
                    viewportHeightPx = screenHeightPx,
                    onCollapse = {
                        playerRepository.closeOverlaySheet()
                        scope.launch { state.animateTo(UnifiedPlayerState.Mini) }
                    },
                    onExpand = {
                        scope.launch { state.animateTo(UnifiedPlayerState.Fullscreen) }
                    },
                    onToggleQueue = {
                        scope.launch {
                            if (state.currentValue == UnifiedPlayerState.QueueFull || state.currentValue == UnifiedPlayerState.QueueHalf) {
                                state.animateTo(UnifiedPlayerState.Fullscreen)
                            } else {
                                state.animateTo(if (isLandscape) UnifiedPlayerState.QueueFull else UnifiedPlayerState.QueueHalf)
                            }
                        }
                    },
                    onOpenLyrics = {
                        playerRepository.openLyrics(currentTrack?.id)
                    },
                    onOpenRelated = {
                        playerRepository.openRelated(currentTrack?.id)
                    },
                    onCollapseDrawer = {
                        if (activeOverlaySheet != com.hypermusic.core.player.PlayerOverlaySheet.NONE) {
                            playerRepository.closeOverlaySheet()
                        } else {
                            scope.launch {
                                state.animateTo(UnifiedPlayerState.Fullscreen)
                            }
                        }
                    },
                    onClearQueue = {
                        scope.launch {
                            playerRepository.pause()
                            state.animateTo(UnifiedPlayerState.Hidden)
                            playerRepository.clearQueue()
                        }
                    },
                    onNavigateToEqualizer = onNavigateToEqualizer,
                    onNavigateToCollection = { type, id, title, subtitle, artworkUrl ->
                        if (navController != null) {
                            val navigated = navController.navigateToCollection(type, id, title, subtitle, artworkUrl)
                            if (navigated) {
                                scope.launch {
                                    state.animateTo(UnifiedPlayerState.Mini)
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

