package com.hypermusic.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DynamicColors(
    val dominant: Color,
    val titleText: Color,
    val bodyText: Color
) {
    companion object {
        val DEFAULT = DynamicColors(
            dominant = Color(0xFF282020),
            titleText = Color(0xFFFFFFFF),
            bodyText = Color(0xB3FFFFFF)
        )
    }

    val dominantArgb: Int get() = dominant.toArgb()
    val titleTextArgb: Int get() = titleText.toArgb()
    val bodyTextArgb: Int get() = bodyText.toArgb()

    /** Desaturated top gradient color with brightness clamped for a dark background. */
    val gradientTopArgb: Int
        get() {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(dominantArgb, hsv)
            hsv[1] = (hsv[1] * 0.82f).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] * 0.70f).coerceIn(0.22f, 0.34f)
            return android.graphics.Color.HSVToColor(hsv)
        }

    val gradientTop: Color get() = Color(gradientTopArgb)

    val gradientMidArgb: Int
        get() {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(dominantArgb, hsv)
            hsv[1] = (hsv[1] * 0.85f).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] * 0.40f).coerceIn(0.12f, 0.18f)
            return android.graphics.Color.HSVToColor(hsv)
        }

    val gradientMid: Color get() = Color(gradientMidArgb)

    val gradientDarkArgb: Int
        get() {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(dominantArgb, hsv)
            hsv[1] = (hsv[1] * 0.85f).coerceIn(0f, 1f)
            hsv[2] = (hsv[2] * 0.20f).coerceIn(0.06f, 0.10f)
            return android.graphics.Color.HSVToColor(hsv)
        }

    val gradientDark: Color get() = Color(gradientDarkArgb)

    val gradientBottom: Color get() = Color(0xFF0A090A)

    /** Dark vertical gradient for the fullscreen player. */
    val playerBackgroundBrush: Brush
        get() = Brush.verticalGradient(
            0.00f to gradientTop,
            0.38f to gradientMid,
            0.78f to gradientDark,
            1.00f to gradientBottom
        )

    /** Queue background that darkens toward the bottom. */
    val queueBackgroundBrush: Brush
        get() = Brush.verticalGradient(
            0.00f to gradientMid,
            0.70f to gradientMid,
            1.00f to gradientDark
        )
}

object ColorExtractor {

    private val colorCache = LruCache<String, DynamicColors>(64)

    fun getCachedColors(imageUrl: String?): DynamicColors? {
        if (imageUrl.isNullOrEmpty()) return null
        return synchronized(colorCache) { colorCache.get(imageUrl) }
    }

    fun putCachedColors(imageUrl: String, colors: DynamicColors) {
        synchronized(colorCache) { colorCache.put(imageUrl, colors) }
    }

    suspend fun extractFromUrl(context: Context, imageUrl: String?): DynamicColors? = withContext(Dispatchers.IO) {
        if (imageUrl.isNullOrEmpty()) return@withContext null
        getCachedColors(imageUrl)?.let { return@withContext it }

        try {
            val resolvedUrl = ArtworkUrlResolver.resolveUrl(imageUrl, ArtworkQuality.THUMBNAIL)
            val request = ImageRequest.Builder(context)
                .data(resolvedUrl)
                .size(128, 128)
                .allowHardware(false) // Disable hardware bitmaps for Palette to work
                .build()

            val result = Coil.imageLoader(context).execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    ?: result.drawable.toBitmap()

                val colors = extract(bitmap)
                putCachedColors(imageUrl, colors)
                return@withContext colors
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Return null so the caller can retain its current or fallback colors.
        }
        null
    }

    fun extract(
        bitmap: Bitmap,
        defaultBackground: Color = Color(0xFF282020)
    ): DynamicColors {
        val palette = try {
            Palette.from(bitmap).generate()
        } catch (e: Exception) {
            null
        }

        val swatch = palette?.let {
            it.dominantSwatch
                ?: it.mutedSwatch
                ?: it.darkMutedSwatch
                ?: it.darkVibrantSwatch
                ?: it.vibrantSwatch
        }

        return if (swatch != null) {
            DynamicColors(
                dominant = Color(swatch.rgb),
                titleText = Color.White,
                bodyText = Color(0xCCFFFFFF)
            )
        } else {
            DynamicColors(
                dominant = defaultBackground,
                titleText = Color.White,
                bodyText = Color(0xB3FFFFFF)
            )
        }
    }

    fun createRoundedGradientBitmap(
        widthPx: Int,
        heightPx: Int,
        startColor: Int,
        endColor: Int,
        cornerRadiusPx: Float
    ): Bitmap {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            startColor, endColor,
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
        }

        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        if (cornerRadiusPx <= 0f || width < cornerRadiusPx * 2 || height < cornerRadiusPx * 2) {
            canvas.drawRect(rect, paint)
        } else {
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
        }
        return bitmap
    }

    fun createCapsuleGradientBitmap(
        widthPx: Int,
        heightPx: Int,
        startColor: Int,
        endColor: Int
    ): Bitmap {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val shader = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            startColor, endColor,
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
        }

        val radius = height.toFloat() / 2f
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)
        return bitmap
    }

    fun toRoundedBitmap(src: Bitmap, cornerRadiusPx: Float, targetSizePx: Int? = null): Bitmap {
        val size = targetSizePx ?: minOf(src.width, src.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())

        val minSrc = minOf(src.width, src.height)
        val srcX = (src.width - minSrc) / 2
        val srcY = (src.height - minSrc) / 2
        val srcRect = android.graphics.Rect(srcX, srcY, srcX + minSrc, srcY + minSrc)

        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, srcRect, rect, paint)
        return output
    }

    fun toCircleBitmap(src: Bitmap, targetSizePx: Int? = null): Bitmap {
        val size = targetSizePx ?: minOf(src.width, src.height)
        return toRoundedBitmap(src, size / 2f, size)
    }
}

@Composable
fun rememberDominantColor(
    context: Context,
    imageUrl: String?,
    defaultColor: Color = Color.Transparent
): Color {
    val dynamicColors = rememberDynamicColors(context, imageUrl, defaultColor)
    return dynamicColors.dominant
}

/**
 * Extracts dynamic colors from an image URL using Coil and AndroidX Palette.
 * Caches results in memory and smoothly animates the dominant color across track switches.
 */
@Composable
fun rememberDynamicColors(
    context: Context,
    imageUrl: String?,
    defaultBackground: Color = Color.DarkGray
): DynamicColors {
    val initialCached = remember(imageUrl) { ColorExtractor.getCachedColors(imageUrl) }
    var targetColors by remember {
        mutableStateOf(initialCached ?: DynamicColors(defaultBackground, Color.White, Color(0xB3FFFFFF)))
    }

    if (initialCached != null && targetColors != initialCached) {
        targetColors = initialCached
    }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrEmpty()) {
            targetColors = DynamicColors(defaultBackground, Color.White, Color(0xB3FFFFFF))
            return@LaunchedEffect
        }

        val cached = ColorExtractor.getCachedColors(imageUrl)
        if (cached != null) {
            targetColors = cached
        } else {
            val extracted = ColorExtractor.extractFromUrl(context, imageUrl)
            if (extracted != null) {
                targetColors = extracted
            }
        }
    }

    val animatedDominant by animateColorAsState(
        targetValue = targetColors.dominant,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "PlayerDominantColorAnimation"
    )

    return remember(animatedDominant, targetColors.titleText, targetColors.bodyText) {
        DynamicColors(
            dominant = animatedDominant,
            titleText = targetColors.titleText,
            bodyText = targetColors.bodyText
        )
    }
}
