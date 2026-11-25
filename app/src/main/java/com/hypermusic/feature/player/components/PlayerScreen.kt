package com.hypermusic.feature.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.runtime.LaunchedEffect
import com.hypermusic.core.model.isDeviceOrigin
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.res.painterResource
import com.hypermusic.R
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.*

import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.core.model.Track
import com.hypermusic.feature.shared.state.GlobalToastViewModel
import com.hypermusic.feature.shared.state.ToastAction
import com.hypermusic.feature.shared.state.ToastType
import com.hypermusic.core.utils.DynamicColors
import com.hypermusic.core.utils.rememberDynamicColors
import com.hypermusic.feature.shared.state.ActionSheetViewModel
import com.hypermusic.feature.player.PlayerViewModel
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.TextUnit
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun PlayerScreen(
    progress: Float,
    queueDragProgress: Float,
    dragDistanceToHalf: Float,
    dragDistanceToFull: Float,
    viewportHeight: Dp = Dp.Unspecified,
    viewportHeightPx: Float = 0f,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onToggleQueue: () -> Unit,
    onOpenLyrics: () -> Unit = {},
    onOpenRelated: () -> Unit = {},
    onCollapseDrawer: () -> Unit = {},
    onClearQueue: () -> Unit = {},
    onSaveQueue: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    onNavigateToCollection: ((String, String, String?, String?, String?) -> Unit)? = null,
    isClosingDrawer: Boolean = false,
    playerViewModel: PlayerViewModel = koinViewModel(),
    actionSheetViewModel: ActionSheetViewModel = koinInject(),
    toastViewModel: GlobalToastViewModel = koinInject()
) {
    var showPlayerMenu by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    val uiState by playerViewModel.uiState.collectAsState()
    val playbackPositionState = playerViewModel.playbackPosition.collectAsState()
    val playbackPositionProvider: () -> Long = remember(playbackPositionState) { { playbackPositionState.value } }

    val currentTrack = uiState.currentTrack
    val isPlaying = uiState.isPlaying
    val playbackState = uiState.playbackState
    val totalDuration = uiState.totalDuration
    val shuffleModeEnabled = uiState.shuffleModeEnabled
    val repeatMode = uiState.repeatMode
    val isVideoMode = uiState.isVideoMode
    val isPodcastOrVideo = isVideoMode || currentTrack?.trackType == "podcast" || currentTrack?.trackType == "video"
    val mediaController = uiState.mediaController
    val lyricsState = uiState.lyricsState
    val relatedState = uiState.relatedState
    val isLiked = uiState.isLiked
    val isSavedEpisode = uiState.isSavedEpisode
    val isDownloaded = uiState.isDownloaded
    val userSettings = uiState.userSettings
    val queue = uiState.queue
    val isFetchingRadio = uiState.isFetchingRadio
    val currentMediaItemIndex = uiState.currentMediaItemIndex
    val isTranslationEnabled = uiState.isTranslationEnabled
    val isTranslatingLyrics = uiState.isTranslatingLyrics
    val targetTranslationLang = uiState.targetTranslationLang

    val sleepTimerOption by playerViewModel.sleepTimerActiveOption.collectAsState()
    val sleepTimerRemainingMs by playerViewModel.sleepTimerRemainingMs.collectAsState()

    val context = LocalContext.current
    val density = LocalDensity.current

    val highResUrl = currentTrack?.artwork
    val colors = rememberDynamicColors(context, currentTrack?.artwork, MaterialTheme.colorScheme.surfaceVariant)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = if (viewportHeight != Dp.Unspecified) viewportHeight else maxHeight
        val screenHeightPx = if (viewportHeightPx > 0f) viewportHeightPx else constraints.maxHeight.toFloat()
        val topSafeArea = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val navBarBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
        val isLandscape = screenWidth > screenHeight
        val isExpanded = progress > 0.8f || isLandscape
        val isActivelyPlaying = isPlaying || playbackState == androidx.media3.common.Player.STATE_BUFFERING
        val shouldKeepScreenOn = isVideoMode && isActivelyPlaying && isExpanded && userSettings.keepScreenAwakeInVideo

        val activity = context as? android.app.Activity
        androidx.compose.runtime.DisposableEffect(shouldKeepScreenOn) {
            if (shouldKeepScreenOn) {
                activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        val queueDragDp = with(density) { queueDragProgress.toDp() }
        
        val activeOverlaySheet = uiState.activeOverlaySheet
        val isOverlayActive = activeOverlaySheet != com.hypermusic.core.player.PlayerOverlaySheet.NONE
        val pullDownAnim = remember { Animatable(0f) }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(activeOverlaySheet) {
            if (activeOverlaySheet != com.hypermusic.core.player.PlayerOverlaySheet.NONE) {
                pullDownAnim.snapTo(0f)
            }
        }

        val topMiniHeight = if (isLandscape) topSafeArea + 52.dp else topSafeArea + 64.dp
        val sheetHeight = screenHeight - topMiniHeight
        val totalTravelPx = with(density) { (screenHeightPx - topMiniHeight.toPx()).coerceAtLeast(1f) }

        val pullDownFraction = (pullDownAnim.value / totalTravelPx).coerceIn(0f, 1f)

        val baseOverlayProgress by animateFloatAsState(
            targetValue = if (isOverlayActive) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "overlayProgress"
        )
        val liveOverlayProgress = (baseOverlayProgress * (1f - pullDownFraction)).coerceIn(0f, 1f)

        val geometry = if (isLandscape) {
            PlayerLayoutMath.calculateLandscapeGeometry(
                progress = progress,
                queueDragProgress = queueDragProgress,
                queueDragDp = queueDragDp,
                dragDistanceToFull = dragDistanceToFull,
                isVideoMode = isVideoMode,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                screenHeightPx = screenHeightPx,
                topSafeArea = topSafeArea,
                navBarBottom = navBarBottom,
                overlayProgress = liveOverlayProgress
            )
        } else {
            PlayerLayoutMath.calculatePortraitGeometry(
                progress = progress,
                queueDragProgress = queueDragProgress,
                queueDragDp = queueDragDp,
                dragDistanceToHalf = dragDistanceToHalf,
                dragDistanceToFull = dragDistanceToFull,
                isVideoMode = isVideoMode,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                screenHeightPx = screenHeightPx,
                topSafeArea = topSafeArea,
                navBarBottom = navBarBottom,
                overlayProgress = liveOverlayProgress
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, with(density) { geometry.bgY.roundToPx() }) }
                .padding(horizontal = geometry.bgPaddingHorizontal)
                .padding(top = geometry.bgPaddingTop, bottom = geometry.bgPaddingBottom)
                .height(geometry.bgHeight)
                .clip(RoundedCornerShape(geometry.bgRadius))
                .background(colors.playerBackgroundBrush)
                .clickable(enabled = progress < 0.5f) { onExpand() }
        ) {
            if (geometry.topMiniAlpha > 0f) {
                val trackProgress = {
                    val pos = playbackPositionProvider()
                    if (totalDuration > 0) pos.toFloat() / totalDuration else 0f
                }
                LinearProgressIndicator(
                    progress = trackProgress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .alpha(geometry.topMiniAlpha),
                    color = colors.titleText,
                    trackColor = colors.titleText.copy(alpha = 0.2f)
                )
            }
        }

        val currentTrackId = currentTrack?.id
        val prevTrack = uiState.previousTrack
        val nextTrack = uiState.nextTrack
        val hasPrev = prevTrack != null
        val hasNext = nextTrack != null

        val prevArtworkUrl = prevTrack?.artwork
        val nextArtworkUrl = nextTrack?.artwork

        val isFullResting = progress >= 0.99f && queueDragProgress <= 0.01f && liveOverlayProgress <= 0.01f
        val isQueueHalfResting = !isLandscape && progress >= 0.99f && dragDistanceToHalf > 0f &&
            kotlin.math.abs(queueDragProgress - dragDistanceToHalf) < with(density) { 12.dp.toPx() } &&
            liveOverlayProgress <= 0.01f

        val isSwipeAllowed = isFullResting || isQueueHalfResting

        val previewFactor = if (isSwipeAllowed) {
            1f
        } else {
            val verticalFactor = ((progress - 0.88f) / 0.12f).coerceIn(0f, 1f)
            val overlayFactor = (1f - (liveOverlayProgress / 0.12f)).coerceIn(0f, 1f)
            val qDistanceBeyondHalf = if (dragDistanceToHalf > 0f) {
                (queueDragProgress - dragDistanceToHalf).coerceAtLeast(0f)
            } else queueDragProgress
            val qFactor = (1f - (qDistanceBeyondHalf / with(density) { 60.dp.toPx() })).coerceIn(0f, 1f)
            (verticalFactor * overlayFactor * qFactor).coerceIn(0f, 1f)
        }

        Box(
            modifier = if (isLandscape) {
                Modifier
                    .fillMaxHeight()
                    .width(screenWidth / 2f)
                    .clipToBounds()
            } else {
                Modifier.fillMaxSize()
            }
        ) {
            PlayerArtwork(
                isVideoMode = isVideoMode,
                shouldKeepScreenOn = shouldKeepScreenOn,
                highResUrl = highResUrl,
                prevArtworkUrl = prevArtworkUrl,
                nextArtworkUrl = nextArtworkUrl,
                trackId = currentTrackId,
                canSwipePrev = hasPrev,
                canSwipeNext = hasNext,
                isSwipeAllowed = isSwipeAllowed,
                isLandscape = isLandscape,
                availableWidth = if (isLandscape) screenWidth / 2f else screenWidth,
                previewFactor = previewFactor,
                mediaController = mediaController,
                currentX = with(density) { geometry.artworkX.roundToPx() },
                currentY = with(density) { geometry.artworkY.roundToPx() },
                currentWidth = geometry.artworkWidth,
                currentHeight = geometry.artworkHeight,
                currentShape = geometry.artworkShape,
                onClick = if (progress < 0.5f) onExpand else null,
                onSwipeLeft = { playerViewModel.skipNext() },
                onSwipeRight = { playerViewModel.skipPrevious() }
            )
        }

        PlayerTopBar(
            topBarAlpha = geometry.topBarAlpha,
            currentX = with(density) { geometry.topBarX.roundToPx() },
            currentY = with(density) { geometry.topBarY.roundToPx() },
            width = geometry.topBarWidth,
            titleText = colors.titleText,
            isVideoMode = isVideoMode,
            onCollapse = onCollapse,
            onEqualizerClick = onNavigateToEqualizer,
            onModeToggleClick = {
                val msg = if (isVideoMode) "Video playback active" else "Audio mode active • Video stream unavailable"
                toastViewModel.showToast(
                    message = msg,
                    type = ToastType.INFO
                )
            },
            onMoreClick = { 
                showPlayerMenu = true
            }
        )
        
        PlayerTitleSection(
            title = currentTrack?.title ?: "Unknown Title",
            artist = currentTrack?.artist ?: "Unknown Artist",
            titleX = { with(density) { geometry.titleX.roundToPx() } },
            titleY = { with(density) { geometry.titleY.roundToPx() } },
            maxWidth = geometry.titleMaxWidth,
            titleStyle = geometry.titleStyle,
            artistStyle = geometry.artistStyle,
            titleColor = colors.titleText,
            artistColor = colors.bodyText,
            artistTopPadding = geometry.artistTopPadding,
            chevronAlpha = if (currentTrack?.isDeviceOrigin() == true) 0f else geometry.topBarAlpha,
            onClick = {
                if (currentTrack?.isDeviceOrigin() != true) {
                    onOpenRelated()
                }
            },
            onArtistClick = {
                val primaryArtist = currentTrack?.artists?.firstOrNull { it.id.isNotBlank() }
                if (primaryArtist != null && primaryArtist.id.isNotBlank()) {
                    onNavigateToCollection?.invoke("artist", primaryArtist.id, primaryArtist.name, null, null)
                } else {
                    toastViewModel.showToast("Artist information not available", com.hypermusic.feature.shared.state.ToastType.INFO)
                }
            },
            onExpand = onExpand
        )
        
        if (geometry.topMiniAlpha > 0f) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .offset { IntOffset(0, with(density) { geometry.topMiniY.roundToPx() }) }
                    .height(64.dp)
                    .alpha(geometry.topMiniAlpha),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { playerViewModel.togglePlayPause() },
                    modifier = Modifier.size(40.dp)
                ) {
                    if (playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = colors.titleText,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Crossfade(
                            targetState = isPlaying,
                            animationSpec = tween(150),
                            label = "MiniPlayPauseCrossfade"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) AppIcons.Pause else AppIcons.Play,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(24.dp),
                                tint = colors.titleText
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { playerViewModel.skipNext() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.SkipForward,
                        contentDescription = "Next",
                        modifier = Modifier.size(24.dp),
                        tint = colors.titleText
                    )
                }
            }
        }

        if (progress > 0f && geometry.chipsAlpha > 0f) {
            val isEpisode = currentTrack != null && com.hypermusic.feature.shared.utils.MediaActionHandler.isPodcastEpisode(currentTrack)

            PlayerActionChips(
                elementsAlpha = geometry.chipsAlpha,
                currentX = with(density) { geometry.chipsX.roundToPx() },
                width = geometry.chipsWidth,
                chipsY = with(density) { geometry.chipsY.roundToPx() },
                isLiked = isLiked,
                isSaved = isSavedEpisode,
                isDownloaded = isDownloaded,
                isPodcastEpisode = isEpisode,
                titleText = colors.titleText,
                sleepTimerOption = sleepTimerOption,
                sleepTimerRemainingMs = sleepTimerRemainingMs,
                onToggleLike = {
                    currentTrack?.let {
                        playerViewModel.toggleLike(it, isLiked, toastViewModel::showToast)
                    }
                },
                onAddToPlaylist = {
                    if (currentTrack?.isDeviceOrigin() == true) {
                        toastViewModel.showToast("Unsupported for local files", com.hypermusic.feature.shared.state.ToastType.INFO)
                    } else {
                        currentTrack?.let { actionSheetViewModel.openPlaylistSelector(listOf(it)) }
                    }
                },
                onToggleSaveEpisode = {
                    currentTrack?.let {
                        playerViewModel.toggleSaveEpisode(it, isSavedEpisode, toastViewModel::showToast)
                    }
                },
                onGoToPodcastShow = {
                    currentTrack?.let { tr ->
                        val showId = tr.albumId
                        if (!showId.isNullOrEmpty()) {
                            onCollapse()
                            val showTitle = tr.album ?: "Podcast"
                            onNavigateToCollection?.invoke("podcast", showId, showTitle, tr.artist, tr.artwork)
                        } else {
                            toastViewModel.showToast("Podcast show not available", com.hypermusic.feature.shared.state.ToastType.INFO)
                        }
                    }
                },
                onDownloadClick = {
                    currentTrack?.let { track ->
                        playerViewModel.downloadTrack(track) { msg, type, action ->
                            val wrappedAction = action?.let { act ->
                                ToastAction(act.label) {
                                    onCollapse()
                                    act.onClick()
                                }
                            }
                            toastViewModel.showToast(msg, type, wrappedAction)
                        }
                    }
                },
                onShareClick = {
                    if (currentTrack?.isDeviceOrigin() == true) {
                        toastViewModel.showToast("Unsupported for local files", com.hypermusic.feature.shared.state.ToastType.INFO)
                    } else {
                        currentTrack?.let {
                            com.hypermusic.core.utils.ShareHelper.shareContent(
                                context = context,
                                type = com.hypermusic.core.utils.ShareType.TRACK,
                                id = it.id,
                                name = it.title
                            )
                        }
                    }
                },
                onLyricsClick = onOpenLyrics,
                onTimerClick = { showSleepTimerSheet = true }
            )
        }
            
        if (progress > 0f && geometry.controlsAlpha > 0f) {
            PlayerControls(
                elementsAlpha = geometry.controlsAlpha,
                currentX = with(density) { geometry.controlsX.roundToPx() },
                width = geometry.controlsWidth,
                currentProgressY = with(density) { geometry.progressY.roundToPx() },
                currentControlsY = with(density) { geometry.controlsY.roundToPx() },
                controlScale = geometry.controlsScale,
                playbackPositionProvider = playbackPositionProvider,
                totalDuration = totalDuration,
                isPlaying = isPlaying,
                playbackState = playbackState,
                shuffleModeEnabled = shuffleModeEnabled,
                repeatMode = repeatMode,
                isPodcastOrVideo = isPodcastOrVideo,
                titleText = colors.titleText,
                bodyText = colors.bodyText,
                dominantColor = colors.dominant,
                onSeek = { playerViewModel.seekTo(it) },
                onSeekForward = { playerViewModel.seekForward() },
                onSeekBackward = { playerViewModel.seekBackward() },
                onTogglePlay = { playerViewModel.togglePlayPause() },
                onSkipNext = { playerViewModel.skipNext() },
                onSkipPrevious = { playerViewModel.skipPrevious() },
                onToggleShuffle = { playerViewModel.toggleShuffle() },
                onToggleRepeat = { playerViewModel.toggleRepeat() }
            )
        }
        
        // QUEUE DRAWER (BASE LAYER)
        val queueHeight = screenHeight - 110.dp
        if (progress > 0f && geometry.queueAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(queueHeight)
                    .offset { IntOffset(0, with(density) { geometry.queueY.roundToPx() }) }
                    .alpha(geometry.queueAlpha)
            ) {
                val offscreenBottomDp = with(density) { 
                    (dragDistanceToFull - queueDragProgress).coerceAtLeast(0f).toDp() 
                }

                QueueSheet(
                    queue = queue,
                    colors = colors,
                    onTrackClick = { track -> playerViewModel.playTrack(track) },
                    onCloseClick = onToggleQueue,
                    onSaveQueue = {
                        val explicitTracks = queue.filter { !it.isImplicit }
                        val eligibleTracks = explicitTracks.filter { !it.isDeviceOrigin() }
                        val excludedCount = explicitTracks.size - eligibleTracks.size
                        if (eligibleTracks.isEmpty()) {
                            if (excludedCount > 0) {
                                toastViewModel.showToast("Unsupported for local files", com.hypermusic.feature.shared.state.ToastType.INFO)
                            }
                        } else {
                            if (excludedCount > 0) {
                                val label = if (excludedCount == 1) "1 device track excluded" else "$excludedCount device tracks excluded"
                                toastViewModel.showToast(label, com.hypermusic.feature.shared.state.ToastType.INFO)
                            }
                            actionSheetViewModel.openPlaylistSelector(eligibleTracks)
                            onSaveQueue()
                        }
                    },
                    parentProgress = progress,
                    queueDragProgress = queueDragProgress,
                    dragDistanceToHalf = dragDistanceToHalf,
                    isAutoplayEnabled = userSettings.isAutoplayEnabled,
                    onAutoplayToggle = { enabled -> 
                        playerViewModel.toggleAutoplay(enabled)
                    },
                    onMoveTrack = { from, to -> playerViewModel.moveQueueItem(from, to) },
                    onRemoveTrack = { track, index -> 
                        playerViewModel.removeQueueItem(track.queueEntryId ?: "", index) 
                    },
                    currentTrack = currentTrack,
                    currentEntryId = uiState.currentEntryId ?: currentTrack?.queueEntryId,
                    currentMediaItemIndex = currentMediaItemIndex,
                    isFetchingRadio = isFetchingRadio,
                    autoplayStatus = uiState.autoplayStatus,
                    onRetryAutoplay = { playerViewModel.retryAutoplay() },
                    isPlaying = isPlaying,
                    bottomPadding = offscreenBottomDp + navBarBottom
                )
            }
        }

        var lastActiveOverlay by remember { mutableStateOf(com.hypermusic.core.player.PlayerOverlaySheet.NONE) }
        if (activeOverlaySheet != com.hypermusic.core.player.PlayerOverlaySheet.NONE) {
            lastActiveOverlay = activeOverlaySheet
        }
        val overlayToDisplay = if (activeOverlaySheet != com.hypermusic.core.player.PlayerOverlaySheet.NONE) {
            activeOverlaySheet
        } else {
            lastActiveOverlay
        }

        if (progress > 0f && (baseOverlayProgress > 0f || isOverlayActive)) {
            val sheetOffsetY = with(density) {
                (
                    screenHeightPx * (1f - baseOverlayProgress) +
                    topMiniHeight.toPx() * baseOverlayProgress +
                    pullDownAnim.value +
                    queueDragProgress
                ).roundToInt()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .offset { IntOffset(0, sheetOffsetY) }
            ) {
                when (overlayToDisplay) {
                    com.hypermusic.core.player.PlayerOverlaySheet.LYRICS -> {
                        LyricsSheet(
                            track = currentTrack,
                            lyricsState = lyricsState,
                            colors = colors,
                            playbackPositionProvider = playbackPositionProvider,
                            bottomPadding = navBarBottom,
                            isTranslationEnabled = isTranslationEnabled,
                            isTranslatingLyrics = isTranslatingLyrics,
                            targetTranslationLang = targetTranslationLang,
                            onToggleTranslation = { playerViewModel.toggleLyricsTranslation() },
                            onSelectTranslationLanguage = { playerViewModel.setLyricsTranslationLanguage(it) },
                            onSeek = { playerViewModel.seekTo(it) },
                            onCloseClick = { playerViewModel.closeOverlaySheet() },
                            onRetry = { currentTrack?.let { playerViewModel.fetchLyrics(it.id, forceRefresh = true) } }
                        )
                    }
                    com.hypermusic.core.player.PlayerOverlaySheet.RELATED -> {
                        RelatedSheet(
                            track = currentTrack,
                            relatedState = relatedState,
                            colors = colors,
                            bottomPadding = navBarBottom,
                            onTrackClick = { track -> playerViewModel.playTrack(track) },
                            onNavigateToCollection = onNavigateToCollection,
                            onCloseClick = { playerViewModel.closeOverlaySheet() },
                            onRetry = { currentTrack?.let { playerViewModel.fetchRelated(it.id, forceRefresh = true) } }
                        )
                    }
                    com.hypermusic.core.player.PlayerOverlaySheet.NONE -> {}
                }

                // Dismiss the overlay when its drag handle is pulled past the threshold.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .align(Alignment.TopCenter)
                        .pointerInput(Unit) {
                            val thresholdPx = 80.dp.toPx()
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    if (dragAmount > 0 || pullDownAnim.value > 0f) {
                                        change.consume()
                                        coroutineScope.launch {
                                            pullDownAnim.snapTo((pullDownAnim.value + dragAmount).coerceAtLeast(0f))
                                        }
                                    }
                                },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        if (pullDownAnim.value > thresholdPx) {
                                            pullDownAnim.animateTo(
                                                targetValue = totalTravelPx,
                                                animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                                            )
                                            playerViewModel.closeOverlaySheet()
                                        } else {
                                            pullDownAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                        }
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        pullDownAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                    }
                                }
                            )
                        }
                )
            }
        }

        if (showPlayerMenu && currentTrack != null) {
            PlayerMenuSheet(
                track = currentTrack,
                onDismissRequest = { showPlayerMenu = false },
                onOpenRelated = {
                    showPlayerMenu = false
                    onOpenRelated()
                },
                onNavigateToEqualizer = {
                    showPlayerMenu = false
                    onNavigateToEqualizer()
                },
                onOpenSleepTimer = {
                    showPlayerMenu = false
                    showSleepTimerSheet = true
                },
                sleepTimerOption = sleepTimerOption,
                sleepTimerRemainingMs = sleepTimerRemainingMs,
                onGoToArtist = { tr ->
                    showPlayerMenu = false
                    if (tr.artists != null && tr.artists.isNotEmpty()) {
                        if (tr.artists.size == 1) {
                            onNavigateToCollection?.invoke("artist", tr.artists[0].id, tr.artists[0].name, null, null)
                        } else {
                            actionSheetViewModel.openArtistSelector(tr.artists)
                        }
                    } else {
                        toastViewModel.showToast("Artist information not available", com.hypermusic.feature.shared.state.ToastType.INFO)
                    }
                },
                onGoToAlbum = { track ->
                    showPlayerMenu = false
                    if (!track.albumId.isNullOrEmpty()) {
                        val albumTitle = track.album ?: "Album"
                        onNavigateToCollection?.invoke("album", track.albumId, albumTitle, track.artist, track.artwork)
                    } else {
                        toastViewModel.showToast("Album information not available", com.hypermusic.feature.shared.state.ToastType.INFO)
                    }
                },
                onGoToPodcastShow = { tr ->
                    showPlayerMenu = false
                    val showId = tr.albumId
                    if (!showId.isNullOrEmpty()) {
                        onCollapse()
                        val showTitle = tr.album ?: "Podcast"
                        onNavigateToCollection?.invoke("podcast", showId, showTitle, tr.artist, tr.artwork)
                    } else {
                        toastViewModel.showToast("Podcast show not available", com.hypermusic.feature.shared.state.ToastType.INFO)
                    }
                },
                onClearQueue = {
                    showPlayerMenu = false
                    onClearQueue()
                }
            )
        }

        if (showSleepTimerSheet) {
            SleepTimerSheet(
                visible = true,
                onClose = { showSleepTimerSheet = false },
                activeOption = sleepTimerOption,
                remainingMs = sleepTimerRemainingMs,
                onSelect = { option ->
                    playerViewModel.setSleepTimer(option)
                    when (option) {
                        "off" -> toastViewModel.showToast("Sleep timer turned off", com.hypermusic.feature.shared.state.ToastType.INFO)
                        "end_of_track" -> toastViewModel.showToast("Music will stop at end of current track", com.hypermusic.feature.shared.state.ToastType.INFO)
                        else -> toastViewModel.showToast("Sleep timer set for $option minutes", com.hypermusic.feature.shared.state.ToastType.INFO)
                    }
                }
            )
        }
    }
}
