package com.hypermusic.feature.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.hypermusic.core.designsystem.icon.AppIcons
import com.hypermusic.core.designsystem.icon.Music
import coil.Coil
import coil.compose.AsyncImage
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.hypermusic.core.utils.ArtworkQuality
import com.hypermusic.core.utils.ArtworkUrlResolver
import kotlinx.coroutines.launch

@Composable
fun PlayerArtwork(
    isVideoMode: Boolean,
    shouldKeepScreenOn: Boolean = false,
    highResUrl: String?,
    prevArtworkUrl: String? = null,
    nextArtworkUrl: String? = null,
    trackId: String? = null,
    canSwipePrev: Boolean = false,
    canSwipeNext: Boolean = false,
    isSwipeAllowed: Boolean = true,
    isLandscape: Boolean = false,
    availableWidth: Dp? = null,
    previewFactor: Float = 1f,
    mediaController: androidx.media3.common.Player?,
    currentX: Int,
    currentY: Int,
    currentWidth: Dp,
    currentHeight: Dp,
    currentShape: androidx.compose.ui.graphics.Shape,
    onClick: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var lastTrackId by remember { mutableStateOf(trackId) }

    val isTrackChanged = trackId != lastTrackId
    val effectiveOffsetX = if (isTrackChanged) 0f else offsetX.value

    // Only allow adjacent cards and horizontal swipe when artwork is large (expanded/resting)
    val isLargeArtwork = onClick == null && (currentWidth > 150.dp || (isLandscape && currentWidth > 90.dp))
    val isSwiping = isLargeArtwork && effectiveOffsetX != 0f
    val showPreviews = isLargeArtwork && (previewFactor > 0.01f || isSwiping)

    LaunchedEffect(trackId) {
        if (offsetX.value != 0f) {
            offsetX.snapTo(0f)
        }
        lastTrackId = trackId
    }

    LaunchedEffect(isLargeArtwork) {
        if (!isLargeArtwork && offsetX.value != 0f) {
            offsetX.snapTo(0f)
        }
    }

    val density = LocalDensity.current
    val cardWidthPx = with(density) { currentWidth.toPx() }
    val effectiveAvailableWidth = availableWidth ?: currentWidth
    val columnMargin = if (effectiveAvailableWidth > currentWidth) {
        (effectiveAvailableWidth - currentWidth) / 2f
    } else {
        with(density) { currentX.toDp() }
    }
    val columnMarginPx = with(density) { columnMargin.toPx() }

    val peekDp = if (isLandscape) {
        minOf(columnMargin * 0.40f, 28.dp).coerceAtLeast(14.dp)
    } else {
        14.dp
    }
    val peekPx = with(density) { peekDp.toPx() }
    val expandedDecodeSize = with(density) { effectiveAvailableWidth.roundToPx().coerceIn(1, 1200) }
    val miniDecodeSize = with(density) { 48.dp.roundToPx().coerceAtLeast(1) }

    val stepDistancePx = if (currentX > 0) {
        cardWidthPx + columnMarginPx - peekPx
    } else {
        cardWidthPx + with(density) { 16.dp.toPx() }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(currentX, currentY) }
            .size(width = currentWidth, height = currentHeight)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else if (isLargeArtwork && isSwipeAllowed && (onSwipeLeft != null || onSwipeRight != null)) {
                    Modifier.pointerInput(onSwipeLeft, onSwipeRight, stepDistancePx, canSwipePrev, canSwipeNext) {
                        val thresholdPx = currentWidth.toPx() * 0.28f

                        detectHorizontalDragGestures(
                            onDragStart = { },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val current = offsetX.value
                                    val isDraggingRight = dragAmount > 0
                                    val isDraggingLeft = dragAmount < 0

                                    val effectiveDrag = if (isDraggingRight && !canSwipePrev && current >= 0f) {
                                        dragAmount * 0.22f
                                    } else if (isDraggingLeft && !canSwipeNext && current <= 0f) {
                                        dragAmount * 0.22f
                                    } else {
                                        dragAmount
                                    }
                                    offsetX.snapTo(current + effectiveDrag)
                                }
                            },
                            onDragEnd = {
                                val currentVal = offsetX.value
                                coroutineScope.launch {
                                    if (currentVal < -thresholdPx && canSwipeNext) {
                                        offsetX.animateTo(
                                            targetValue = -stepDistancePx,
                                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                        )
                                        onSwipeLeft?.invoke()
                                    } else if (currentVal > thresholdPx && canSwipePrev) {
                                        offsetX.animateTo(
                                            targetValue = stepDistancePx,
                                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                        )
                                        onSwipeRight?.invoke()
                                    } else {
                                        offsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessMediumLow,
                                                dampingRatio = Spring.DampingRatioLowBouncy
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        val outwardPushPx = with(density) {
            ((1f - previewFactor).coerceIn(0f, 1f) * 200.dp.toPx())
        }
        val basePreviewAlpha = previewFactor.coerceIn(0f, 1f)

        val dragFraction = if (stepDistancePx > 0f) {
            (kotlin.math.abs(effectiveOffsetX) / stepDistancePx).coerceIn(0f, 1f)
        } else 0f

        val centerAlpha = 1f - dragFraction * 0.30f

        val isDraggingPrev = effectiveOffsetX > 0f
        val prevAlpha = if (isDraggingPrev) {
            (0.70f + dragFraction * 0.30f) * basePreviewAlpha
        } else {
            (0.70f * (1f - dragFraction)).coerceAtLeast(0f) * basePreviewAlpha
        }

        val isDraggingNext = effectiveOffsetX < 0f
        val nextAlpha = if (isDraggingNext) {
            (0.70f + dragFraction * 0.30f) * basePreviewAlpha
        } else {
            (0.70f * (1f - dragFraction)).coerceAtLeast(0f) * basePreviewAlpha
        }

        if (showPreviews && canSwipePrev && prevAlpha > 0.01f) {
            SingleArtworkCard(
                url = prevArtworkUrl,
                shape = currentShape,
                quality = ArtworkQuality.HERO,
                decodeSize = expandedDecodeSize,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = effectiveOffsetX - stepDistancePx - outwardPushPx
                        alpha = prevAlpha
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = isSwipeAllowed && onSwipeRight != null
                    ) {
                        onSwipeRight?.invoke()
                    }
            )
        }

        if (showPreviews && canSwipeNext && nextAlpha > 0.01f) {
            SingleArtworkCard(
                url = nextArtworkUrl,
                shape = currentShape,
                quality = ArtworkQuality.HERO,
                decodeSize = expandedDecodeSize,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = effectiveOffsetX + stepDistancePx + outwardPushPx
                        alpha = nextAlpha
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = isSwipeAllowed && onSwipeLeft != null
                    ) {
                        onSwipeLeft?.invoke()
                    }
            )
        }

        if (isVideoMode) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = mediaController
                        keepScreenOn = shouldKeepScreenOn
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.player = mediaController
                    if (view.keepScreenOn != shouldKeepScreenOn) {
                        view.keepScreenOn = shouldKeepScreenOn
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = if (isLargeArtwork) effectiveOffsetX else 0f
                        alpha = if (isLargeArtwork) centerAlpha else 1f
                    }
                    .clip(currentShape)
                    .background(Color.Black)
            )
        } else {
            SingleArtworkCard(
                url = highResUrl,
                shape = currentShape,
                quality = if (onClick == null) ArtworkQuality.HERO else ArtworkQuality.THUMBNAIL,
                decodeSize = if (onClick == null) expandedDecodeSize else miniDecodeSize,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = if (isLargeArtwork) effectiveOffsetX else 0f
                        alpha = if (isLargeArtwork) centerAlpha else 1f
                    }
            )
        }
    }
}

@Composable
private fun SingleArtworkCard(
    url: String?,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    quality: ArtworkQuality,
    decodeSize: Int
) {
    val context = LocalContext.current
    val targetUrl = remember(url, quality) {
        ArtworkUrlResolver.resolveUrl(url, quality)
    }
    val thumbnailUrl = remember(url) {
        ArtworkUrlResolver.resolveUrl(url, ArtworkQuality.THUMBNAIL)
    }
    val imageLoader = remember(context) { Coil.imageLoader(context) }
    val targetKey = remember(targetUrl) { targetUrl?.let { MemoryCache.Key(it) } }
    val thumbnailKey = remember(thumbnailUrl) { thumbnailUrl?.let { MemoryCache.Key(it) } }

    val placeholderKey = remember(targetKey, thumbnailKey, imageLoader) {
        val memoryCache = imageLoader.memoryCache
        when {
            targetKey != null && memoryCache?.get(targetKey) != null -> targetKey
            thumbnailKey != null && memoryCache?.get(thumbnailKey) != null -> thumbnailKey
            else -> targetKey
        }
    }

    val request = remember(context, targetUrl, placeholderKey, decodeSize) {
        ImageRequest.Builder(context)
            .data(targetUrl)
            .placeholderMemoryCacheKey(placeholderKey)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .size(decodeSize)
            .precision(coil.size.Precision.INEXACT)
            .crossfade(false)
            .build()
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppIcons.Music,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
            modifier = Modifier.fillMaxSize(0.35f)
        )
        if (!targetUrl.isNullOrEmpty()) {
            AsyncImage(
                model = request,
                contentDescription = "Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
