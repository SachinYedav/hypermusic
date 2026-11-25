package com.hypermusic.feature.player.components

import androidx.compose.foundation.shape.RoundedCornerShape
import com.hypermusic.core.designsystem.theme.RadiusXs
import com.hypermusic.core.designsystem.theme.RadiusSm
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

data class PlayerGeometry(
    val bgRadius: Dp,
    val bgPaddingHorizontal: Dp,
    val bgPaddingTop: Dp,
    val bgPaddingBottom: Dp,
    val bgHeight: Dp,
    val bgY: Dp,
    
    val artworkX: Dp,
    val artworkY: Dp,
    val artworkWidth: Dp,
    val artworkHeight: Dp,
    val artworkShape: RoundedCornerShape,
    
    val topBarX: Dp,
    val topBarY: Dp,
    val topBarWidth: Dp?,
    val topBarAlpha: Float,
    
    val titleX: Dp,
    val titleY: Dp,
    val titleMaxWidth: Dp,
    val titleStyle: TextStyle,
    val artistStyle: TextStyle,
    val artistTopPadding: Dp,
    val titleAlpha: Float,
    
    val chipsX: Dp,
    val chipsY: Dp,
    val chipsWidth: Dp?,
    val chipsAlpha: Float,
    
    val controlsX: Dp,
    val controlsY: Dp,
    val progressY: Dp,
    val controlsWidth: Dp?,
    val controlsAlpha: Float,
    val controlsScale: Float = 1f,
    
    val topMiniAlpha: Float,
    val topMiniY: Dp,
    
    val queueY: Dp,
    val queueAlpha: Float
)

object PlayerLayoutMath {
    fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp = start + (stop - start) * fraction
    fun lerpFloat(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

    /** Queue drag and half/full anchor distances are non-negative pixel values. */
    fun calculatePortraitGeometry(
        progress: Float,
        queueDragProgress: Float,
        queueDragDp: Dp,
        dragDistanceToHalf: Float,
        dragDistanceToFull: Float,
        isVideoMode: Boolean,
        screenWidth: Dp,
        screenHeight: Dp,
        screenHeightPx: Float,
        topSafeArea: Dp,
        navBarBottom: Dp = 0.dp,
        overlayProgress: Float = 0f
    ): PlayerGeometry {
        val rawQHalfProgress = (queueDragProgress / dragDistanceToHalf.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val rawQFullProgress = ((queueDragProgress - dragDistanceToHalf) / (dragDistanceToFull - dragDistanceToHalf).coerceAtLeast(1f)).coerceIn(0f, 1f)

        val qHalfProgress = maxOf(rawQHalfProgress, overlayProgress)
        val qFullProgress = maxOf(rawQFullProgress, overlayProgress)

        fun lerpDpState(mini: Dp, full: Dp, half: Dp, qFull: Dp): Dp {
            if (progress < 1f) return androidx.compose.ui.unit.lerp(mini, full, progress)
            if (overlayProgress > 0f && queueDragProgress == 0f) {
                return androidx.compose.ui.unit.lerp(full, qFull, overlayProgress)
            }
            return if (qFullProgress == 0f) androidx.compose.ui.unit.lerp(full, half, qHalfProgress)
            else androidx.compose.ui.unit.lerp(half, qFull, qFullProgress)
        }

        fun lerpFloatState(mini: Float, full: Float, half: Float, qFull: Float): Float {
            if (progress < 1f) return (mini + (full - mini) * progress)
            if (overlayProgress > 0f && queueDragProgress == 0f) {
                return (full + (qFull - full) * overlayProgress)
            }
            return if (qFullProgress == 0f) (full + (half - full) * qHalfProgress)
            else (half + (qFull - half) * qFullProgress)
        }

        val currentBgPaddingHorizontal = lerpDp(8.dp, 0.dp, progress)
        val currentBgPaddingTop = 0.dp
        val currentBgPaddingBottom = 0.dp
        val currentBgRadius = lerpDp(RadiusSm, 0.dp, progress)
        val currentBgHeight = lerpDp(64.dp, screenHeight, progress)
        val bgY = queueDragDp
        
        val miniSize = 48.dp
        val miniWidth = if (isVideoMode) miniSize * (16f / 9f) else miniSize
        val miniHeight = miniSize
        val miniX = 16.dp
        val miniY = 8.dp
        val safeMiniY = miniY + topSafeArea
        
        val fullWidth = if (isVideoMode) screenWidth else screenWidth - 48.dp
        val fullHeight = if (isVideoMode) fullWidth * (9f / 16f) else fullWidth
        val fullX = if (isVideoMode) 0.dp else 24.dp
        val effectiveFullY = if (isVideoMode) 100.dp + (screenWidth - 48.dp - fullHeight).coerceAtLeast(0.dp) else 100.dp
        
        val artworkX = lerpDpState(miniX, fullX, 0.dp, miniX)
        val artworkY = lerpDpState(miniY, effectiveFullY, 0.dp, safeMiniY) + queueDragDp
        val artworkWidth = lerpDpState(miniWidth, fullWidth, screenWidth, miniWidth)
        val artworkHeight = lerpDpState(miniHeight, fullHeight, fullHeight, miniHeight)
        
        val miniRadius = RadiusXs
        val fullRadius = if (isVideoMode) 0.dp else RadiusSm
        val topStartRadius = lerpDpState(miniRadius, fullRadius, 0.dp, miniRadius)
        val rightRadius = lerpDpState(miniRadius, fullRadius, 0.dp, miniRadius)
        val artworkShape = RoundedCornerShape(
            topStart = topStartRadius, topEnd = rightRadius,
            bottomEnd = rightRadius, bottomStart = topStartRadius
        )

        val topBarAlpha = (((progress - 0.7f) / 0.3f).coerceIn(0f, 1f) * (1f - qFullProgress)).coerceIn(0f, 1f)
        val topBarY = topSafeArea + queueDragDp
        
        val queuePeekHeight = 68.dp + navBarBottom
        val queueCollapsedY = screenHeight - queuePeekHeight
        val artworkBottom = effectiveFullY + fullHeight
        val rightSideStartY = artworkBottom + 32.dp
        val fullTitleY = rightSideStartY
        
        val topMiniTitleX = 16.dp + miniWidth + 12.dp
        val fullTitleX = 24.dp
        val currentTitleX = lerpDpState(topMiniTitleX, fullTitleX, fullTitleX, topMiniTitleX)
        val halfTitleY = if (isVideoMode) fullHeight + 16.dp else (fullHeight - 64.dp)
        val currentTitleY = lerpDpState(16.dp, fullTitleY, halfTitleY, 16.dp + topSafeArea) + queueDragDp
        val currentTitleEndPadding = lerpDpState(110.dp, 24.dp, 24.dp, 110.dp)
        
        val titleStyle = TextStyle(
            fontSize = lerpDpState(14.dp, 24.dp, 20.dp, 14.dp).value.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = lerpDpState(20.dp, 32.dp, 28.dp, 20.dp).value.sp,
            letterSpacing = 0.sp
        )
        val artistStyle = TextStyle(
            fontSize = lerpDpState(12.dp, 16.dp, 14.dp, 12.dp).value.sp,
            lineHeight = lerpDpState(16.dp, 24.dp, 20.dp, 16.dp).value.sp,
            letterSpacing = 0.sp
        )
        val artistTopPadding = lerpDpState(0.dp, 4.dp, 2.dp, 0.dp)
        
        val fullChipsY = fullTitleY + 72.dp
        val chipsBottom = fullChipsY + 44.dp
        val availableControlsHeight = queueCollapsedY - chipsBottom
        val controlsBlockHeight = 124.dp

        val blockTop = if (availableControlsHeight > controlsBlockHeight) {
            chipsBottom + (availableControlsHeight - controlsBlockHeight) * 0.42f
        } else {
            chipsBottom + 8.dp
        }

        val fullProgressY = blockTop
        val fullControlsY = blockTop + 60.dp
        val halfChipsY = fullHeight + 16.dp

        val chipsY = lerpDpState(fullChipsY, fullChipsY, halfChipsY, halfChipsY) + queueDragDp
        val chipsFade = (1f - qHalfProgress * 1.35f).coerceIn(0f, 1f)
        val chipsAlpha = (progress * chipsFade * (1f - qFullProgress)).coerceIn(0f, 1f)

        val safeQueueHalfY = screenHeight * 0.55f
        val halfControlsY = (safeQueueHalfY - 90.dp).coerceAtLeast(fullHeight + 70.dp)
        val halfProgressY = (halfControlsY - 60.dp).coerceAtLeast(fullHeight + 10.dp)

        val currentControlsY = lerpDpState(fullControlsY, fullControlsY, halfControlsY, halfControlsY) + queueDragDp
        val currentProgressY = lerpDpState(fullProgressY, fullProgressY, halfProgressY, halfProgressY) + queueDragDp
        
        val controlsFade = (1f - qFullProgress * 2f).coerceIn(0f, 1f)
        val elementsAlpha = progress * controlsFade
        
        val miniAlpha = ((1f - (progress * 3)).coerceIn(0f, 1f) + qFullProgress).coerceIn(0f, 1f)
        val topMiniOffset = if (qFullProgress > 0f) topSafeArea else 0.dp
        val topMiniY = topMiniOffset + queueDragDp
        
        return PlayerGeometry(
            bgRadius = currentBgRadius,
            bgPaddingHorizontal = currentBgPaddingHorizontal,
            bgPaddingTop = currentBgPaddingTop,
            bgPaddingBottom = currentBgPaddingBottom,
            bgHeight = currentBgHeight,
            bgY = bgY,
            artworkX = artworkX,
            artworkY = artworkY,
            artworkWidth = artworkWidth,
            artworkHeight = artworkHeight,
            artworkShape = artworkShape,
            topBarX = 0.dp,
            topBarY = topBarY,
            topBarWidth = null,
            topBarAlpha = topBarAlpha,
            titleX = currentTitleX,
            titleY = currentTitleY,
            titleMaxWidth = screenWidth - currentTitleX - currentTitleEndPadding,
            titleStyle = titleStyle,
            artistStyle = artistStyle,
            artistTopPadding = artistTopPadding,
            titleAlpha = 1f,
            chipsX = 0.dp,
            chipsY = chipsY,
            chipsWidth = null,
            chipsAlpha = chipsAlpha,
            controlsX = 0.dp,
            controlsY = currentControlsY,
            progressY = currentProgressY,
            controlsWidth = null,
            controlsAlpha = elementsAlpha,
            topMiniAlpha = miniAlpha,
            topMiniY = topMiniY,
            queueY = queueCollapsedY,
            queueAlpha = progress
        )
    }

    /** Queue drag and full-anchor distances are non-negative pixel values. */
    fun calculateLandscapeGeometry(
        progress: Float,
        queueDragProgress: Float,
        queueDragDp: Dp,
        dragDistanceToFull: Float,
        isVideoMode: Boolean,
        screenWidth: Dp,
        screenHeight: Dp,
        screenHeightPx: Float,
        topSafeArea: Dp,
        navBarBottom: Dp = 0.dp,
        overlayProgress: Float = 0f
    ): PlayerGeometry {
        val rawQFullProgress = (queueDragProgress / dragDistanceToFull.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val qFullProgress = maxOf(rawQFullProgress, overlayProgress)

        val currentBgPaddingHorizontal = lerpDp(8.dp, 0.dp, progress)
        val currentBgPaddingTop = 0.dp
        val currentBgPaddingBottom = 0.dp
        val currentBgRadius = lerpDp(RadiusSm, 0.dp, progress)
        val currentBgHeight = lerpDp(64.dp, screenHeight, progress)
        val bgY = queueDragDp

        val miniSize = 48.dp
        val miniWidth = if (isVideoMode) miniSize * (16f / 9f) else miniSize
        val miniHeight = miniSize
        val miniX = 16.dp
        val miniY = 8.dp
        val safeMiniY = miniY + topSafeArea

        val queuePeekHeight = 64.dp + navBarBottom
        val queueCollapsedY = screenHeight - queuePeekHeight

        val contentTop = topSafeArea + 52.dp
        val availableContentHeight = (queueCollapsedY - contentTop - 8.dp).coerceAtLeast(160.dp)

        val maxMediaWidth = (screenWidth / 2f) - 48.dp
        val maxMediaHeight = (availableContentHeight - 48.dp).coerceAtLeast(100.dp)

        val fullArtworkWidth = if (isVideoMode) {
            minOf(maxMediaWidth, maxMediaHeight * (16f / 9f)).coerceAtLeast(100.dp)
        } else {
            minOf(maxMediaWidth, maxMediaHeight).coerceAtLeast(100.dp)
        }
        val fullArtworkHeight = if (isVideoMode) {
            fullArtworkWidth * (9f / 16f)
        } else {
            fullArtworkWidth
        }

        val fullArtworkX = 24.dp + ((maxMediaWidth - fullArtworkWidth) / 2f).coerceAtLeast(0.dp)
        val leftVerticalOffset = ((availableContentHeight - (fullArtworkHeight + 44.dp)) / 2f).coerceAtLeast(0.dp)
        val fullArtworkY = contentTop + leftVerticalOffset
        val fullChipsY = fullArtworkY + fullArtworkHeight + 8.dp

        val rightSideX = screenWidth / 2f + 16.dp
        val rightSideWidth = (screenWidth / 2f) - 40.dp

        val rightVerticalOffset = ((availableContentHeight - 180.dp) / 2f).coerceAtLeast(0.dp)
        val fullTitleY = contentTop + rightVerticalOffset
        val fullProgressY = fullTitleY + 58.dp
        val fullControlsY = fullProgressY + 46.dp

        fun lerpDpState(mini: Dp, full: Dp, qFull: Dp): Dp {
            return if (progress < 1f) androidx.compose.ui.unit.lerp(mini, full, progress)
            else androidx.compose.ui.unit.lerp(full, qFull, qFullProgress)
        }
        fun lerpFloatState(mini: Float, full: Float, qFull: Float): Float {
            return if (progress < 1f) (mini + (full - mini) * progress)
            else (full + (qFull - full) * qFullProgress)
        }

        val artworkX = lerpDpState(miniX, fullArtworkX, miniX)
        val artworkY = lerpDpState(miniY, fullArtworkY, safeMiniY) + queueDragDp
        val artworkWidth = lerpDpState(miniWidth, fullArtworkWidth, miniWidth)
        val artworkHeight = lerpDpState(miniHeight, fullArtworkHeight, miniHeight)

        val topStartRadius = lerpDpState(RadiusXs, RadiusSm, RadiusXs)
        val artworkShape = RoundedCornerShape(topStartRadius)

        val topBarAlpha = lerpFloatState(0f, 1f, 0f)
        val topBarY = topSafeArea + queueDragDp

        val topMiniTitleX = 16.dp + miniWidth + 12.dp
        val fullTitleX = rightSideX
        val currentTitleX = lerpDpState(topMiniTitleX, fullTitleX, topMiniTitleX)
        val currentTitleY = lerpDpState(16.dp, fullTitleY, 16.dp + topSafeArea) + queueDragDp
        val currentTitleEndPadding = lerpDpState(110.dp, 0.dp, 110.dp)

        val titleStyle = TextStyle(
            fontSize = lerpDpState(14.dp, 20.dp, 14.dp).value.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = lerpDpState(20.dp, 26.dp, 20.dp).value.sp,
            letterSpacing = 0.sp
        )
        val artistStyle = TextStyle(
            fontSize = lerpDpState(12.dp, 15.dp, 12.dp).value.sp,
            lineHeight = lerpDpState(16.dp, 20.dp, 16.dp).value.sp,
            letterSpacing = 0.sp
        )
        val artistTopPadding = lerpDpState(0.dp, 4.dp, 0.dp)

        val chipsX = 0.dp
        val chipsY = fullChipsY + queueDragDp
        val chipsWidth = screenWidth / 2f
        val chipsAlpha = lerpFloatState(0f, 1f, 0f)

        val currentProgressY = fullProgressY + queueDragDp
        val currentControlsY = fullControlsY + queueDragDp

        val elementsAlpha = lerpFloatState(0f, 1f, 0f)

        val miniAlpha = ((1f - (progress * 3)).coerceIn(0f, 1f) + qFullProgress).coerceIn(0f, 1f)
        val topMiniOffset = if (qFullProgress > 0f) topSafeArea else 0.dp
        val topMiniY = topMiniOffset + queueDragDp

        return PlayerGeometry(
            bgRadius = currentBgRadius,
            bgPaddingHorizontal = currentBgPaddingHorizontal,
            bgPaddingTop = currentBgPaddingTop,
            bgPaddingBottom = currentBgPaddingBottom,
            bgHeight = currentBgHeight,
            bgY = bgY,
            artworkX = artworkX,
            artworkY = artworkY,
            artworkWidth = artworkWidth,
            artworkHeight = artworkHeight,
            artworkShape = artworkShape,
            topBarX = 0.dp,
            topBarY = topBarY,
            topBarWidth = null,
            topBarAlpha = topBarAlpha,
            titleX = currentTitleX,
            titleY = currentTitleY,
            titleMaxWidth = screenWidth - currentTitleX - currentTitleEndPadding,
            titleStyle = titleStyle,
            artistStyle = artistStyle,
            artistTopPadding = artistTopPadding,
            titleAlpha = 1f,
            chipsX = chipsX,
            chipsY = chipsY,
            chipsWidth = chipsWidth,
            chipsAlpha = chipsAlpha,
            controlsX = rightSideX,
            controlsY = currentControlsY,
            progressY = currentProgressY,
            controlsWidth = rightSideWidth,
            controlsAlpha = elementsAlpha,
            topMiniAlpha = miniAlpha,
            topMiniY = topMiniY,
            queueY = queueCollapsedY,
            queueAlpha = progress
        )
    }
}
