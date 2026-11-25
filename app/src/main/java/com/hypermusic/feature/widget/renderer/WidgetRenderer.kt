package com.hypermusic.feature.widget.renderer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.hypermusic.MainActivity
import com.hypermusic.R
import com.hypermusic.core.utils.ColorExtractor
import com.hypermusic.feature.widget.data.WidgetActions
import com.hypermusic.feature.widget.data.WidgetPlaybackState
import com.hypermusic.feature.widget.receiver.CapsuleWidgetProvider
import com.hypermusic.feature.widget.receiver.LargePlayerWidgetProvider
import com.hypermusic.feature.widget.receiver.RecommendedWidgetProvider
import com.hypermusic.feature.widget.receiver.SearchAndPlayWidgetProvider
import com.hypermusic.feature.widget.receiver.WidgetControlReceiver

enum class WidgetType {
    SEARCH_AND_PLAY,
    CAPSULE,
    RECOMMENDED,
    LARGE_PLAYER
}

object WidgetRenderer {

    fun updateAllWidgets(context: Context, state: WidgetPlaybackState) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return

        val searchIds = appWidgetManager.getAppWidgetIds(ComponentName(context, SearchAndPlayWidgetProvider::class.java))
        if (searchIds.isNotEmpty()) {
            val rv = createRemoteViews(context, WidgetType.SEARCH_AND_PLAY, state)
            appWidgetManager.updateAppWidget(searchIds, rv)
        }

        val capsuleIds = appWidgetManager.getAppWidgetIds(ComponentName(context, CapsuleWidgetProvider::class.java))
        if (capsuleIds.isNotEmpty()) {
            val rv = createRemoteViews(context, WidgetType.CAPSULE, state)
            appWidgetManager.updateAppWidget(capsuleIds, rv)
        }

        val recIds = appWidgetManager.getAppWidgetIds(ComponentName(context, RecommendedWidgetProvider::class.java))
        if (recIds.isNotEmpty()) {
            val rv = createRemoteViews(context, WidgetType.RECOMMENDED, state)
            appWidgetManager.updateAppWidget(recIds, rv)
        }

        val largeIds = appWidgetManager.getAppWidgetIds(ComponentName(context, LargePlayerWidgetProvider::class.java))
        if (largeIds.isNotEmpty()) {
            val rv = createRemoteViews(context, WidgetType.LARGE_PLAYER, state)
            appWidgetManager.updateAppWidget(largeIds, rv)
        }
    }

    fun createRemoteViews(context: Context, type: WidgetType, state: WidgetPlaybackState): RemoteViews {
        val layoutId = when (type) {
            WidgetType.SEARCH_AND_PLAY -> R.layout.widget_search_and_play
            WidgetType.CAPSULE -> R.layout.widget_capsule
            WidgetType.RECOMMENDED -> R.layout.widget_recommended_player
            WidgetType.LARGE_PLAYER -> R.layout.widget_large_player
        }

        val rv = RemoteViews(context.packageName, layoutId)
        val density = context.resources.displayMetrics.density

        // Bound background bitmap dimensions to reduce the widget IPC payload.
        val bgBitmap = when (type) {
            WidgetType.CAPSULE -> ColorExtractor.createCapsuleGradientBitmap(
                widthPx = 300,
                heightPx = 50,
                startColor = state.dynamicColors.gradientTopArgb,
                endColor = state.dynamicColors.gradientDarkArgb
            )
            WidgetType.LARGE_PLAYER -> ColorExtractor.createRoundedGradientBitmap(
                widthPx = 140,
                heightPx = 180,
                startColor = state.dynamicColors.gradientTopArgb,
                endColor = state.dynamicColors.gradientDarkArgb,
                cornerRadiusPx = 12f
            )
            else -> ColorExtractor.createRoundedGradientBitmap(
                widthPx = 180,
                heightPx = 80,
                startColor = state.dynamicColors.gradientTopArgb,
                endColor = state.dynamicColors.gradientDarkArgb,
                cornerRadiusPx = 12f
            )
        }
        rv.setImageViewBitmap(R.id.widget_background, bgBitmap)

        // Cap artwork dimensions to reduce the widget IPC payload.
        val artwork = state.artwork
        val artworkBitmap = if (artwork != null) {
            when (type) {
                WidgetType.CAPSULE -> ColorExtractor.toCircleBitmap(
                    artwork,
                    targetSizePx = minOf((38 * density).toInt().coerceAtLeast(1), 96)
                )
                WidgetType.LARGE_PLAYER -> ColorExtractor.toRoundedBitmap(
                    artwork,
                    cornerRadiusPx = 12f,
                    targetSizePx = minOf((250 * density).toInt().coerceAtLeast(1), 192)
                )
                else -> ColorExtractor.toRoundedBitmap(
                    artwork,
                    cornerRadiusPx = 10f,
                    targetSizePx = minOf((140 * density).toInt().coerceAtLeast(1), 128)
                )
            }
        } else {
            getDefaultArtworkBitmap(context, type, density)
        }
        rv.setImageViewBitmap(R.id.widget_artwork, artworkBitmap)

        val defaultTitle = context.getString(R.string.widget_default_title)
        val defaultArtist = context.getString(R.string.widget_default_artist)
        val displayTitle = if (state.hasTrack && state.title.isNotBlank()) state.title else defaultTitle
        val displayArtist = if (state.hasTrack && state.artist.isNotBlank()) state.artist else defaultArtist

        rv.setTextViewText(R.id.widget_title, displayTitle)
        rv.setTextColor(R.id.widget_title, state.dynamicColors.titleTextArgb)

        if (type != WidgetType.CAPSULE) {
            rv.setTextViewText(R.id.widget_artist, displayArtist)
            rv.setTextColor(R.id.widget_artist, state.dynamicColors.bodyTextArgb)
        }

        val playPauseIcon = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        rv.setImageViewResource(R.id.btn_play_pause, playPauseIcon)
        rv.setOnClickPendingIntent(R.id.btn_play_pause, getControlPendingIntent(context, WidgetControlReceiver.ACTION_PLAY_PAUSE, 101))

        rv.setOnClickPendingIntent(R.id.btn_next, getControlPendingIntent(context, WidgetControlReceiver.ACTION_NEXT, 102))

        if (type != WidgetType.CAPSULE) {
            rv.setOnClickPendingIntent(R.id.btn_prev, getControlPendingIntent(context, WidgetControlReceiver.ACTION_PREVIOUS, 103))
        }

        val openAppPendingIntent = getOpenAppPendingIntent(context, null, 100)
        rv.setOnClickPendingIntent(R.id.widget_artwork, openAppPendingIntent)
        rv.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent)
        rv.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
        if (type == WidgetType.CAPSULE) {
            rv.setOnClickPendingIntent(R.id.capsule_inner_container, openAppPendingIntent)
        }

        when (type) {
            WidgetType.SEARCH_AND_PLAY -> {
                val searchIntent = getOpenAppPendingIntent(context, WidgetActions.ACTION_OPEN_SEARCH, 201)
                rv.setOnClickPendingIntent(R.id.search_bar_container, searchIntent)
            }

            WidgetType.RECOMMENDED -> {
                rv.setOnClickPendingIntent(R.id.btn_shortcut_playlists, getOpenAppPendingIntent(context, WidgetActions.ACTION_OPEN_PLAYLISTS, 202))
                rv.setOnClickPendingIntent(R.id.btn_shortcut_explore, getOpenAppPendingIntent(context, WidgetActions.ACTION_OPEN_EXPLORE, 203))
                rv.setOnClickPendingIntent(R.id.btn_shortcut_history, getOpenAppPendingIntent(context, WidgetActions.ACTION_OPEN_HISTORY, 204))
                rv.setOnClickPendingIntent(R.id.btn_shortcut_liked, getOpenAppPendingIntent(context, WidgetActions.ACTION_OPEN_LIKED_SONGS, 205))
            }

            WidgetType.LARGE_PLAYER -> {
                val (repeatIcon, repeatAlpha) = when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> Pair(R.drawable.ic_widget_repeat_one, 255)
                    Player.REPEAT_MODE_ALL -> Pair(R.drawable.ic_widget_repeat, 255)
                    else -> Pair(R.drawable.ic_widget_repeat, 130)
                }
                rv.setImageViewResource(R.id.btn_repeat, repeatIcon)
                rv.setInt(R.id.btn_repeat, "setImageAlpha", repeatAlpha)
                rv.setOnClickPendingIntent(R.id.btn_repeat, getControlPendingIntent(context, WidgetControlReceiver.ACTION_REPEAT, 104))

                val canLike = state.hasTrack && state.canLike
                if (canLike) {
                    val likeIcon = if (state.isLiked) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart_outline
                    rv.setImageViewResource(R.id.btn_like, likeIcon)
                    rv.setInt(R.id.btn_like, "setImageAlpha", 255)
                    rv.setBoolean(R.id.btn_like, "setEnabled", true)
                    rv.setContentDescription(
                        R.id.btn_like,
                        context.getString(if (state.isLiked) R.string.widget_action_unlike else R.string.widget_action_like)
                    )
                    rv.setOnClickPendingIntent(R.id.btn_like, getControlPendingIntent(context, WidgetControlReceiver.ACTION_TOGGLE_LIKE, 105))
                } else {
                    rv.setImageViewResource(R.id.btn_like, R.drawable.ic_widget_heart_outline)
                    rv.setInt(R.id.btn_like, "setImageAlpha", 90)
                    rv.setBoolean(R.id.btn_like, "setEnabled", false)
                    val likeDesc = if (state.hasTrack) {
                        R.string.widget_action_like_unavailable
                    } else {
                        R.string.widget_action_like_no_track
                    }
                    rv.setContentDescription(R.id.btn_like, context.getString(likeDesc))
                    rv.setOnClickPendingIntent(R.id.btn_like, null)
                }
            }

            WidgetType.CAPSULE -> {
            }
        }

        return rv
    }

    private fun getControlPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetControlReceiver::class.java).apply {
            this.action = action
            flags = Intent.FLAG_RECEIVER_FOREGROUND
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getOpenAppPendingIntent(context: Context, action: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            if (action != null) {
                this.action = action
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Draws placeholder artwork when no track image is available. */
    private fun getDefaultArtworkBitmap(context: Context, type: WidgetType, density: Float): Bitmap {
        val (sizePx, cornerRadiusPx) = when (type) {
            WidgetType.CAPSULE -> Pair(minOf((38 * density).toInt().coerceAtLeast(1), 96), 48f)
            WidgetType.LARGE_PLAYER -> Pair(minOf((250 * density).toInt().coerceAtLeast(1), 192), 12f)
            else -> Pair(minOf((52 * density).toInt().coerceAtLeast(1), 128), 10f)
        }

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#201F25")
        }

        val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        if (type == WidgetType.CAPSULE) {
            canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, bgPaint)
        } else {
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, bgPaint)
        }

        val noteDrawable = ContextCompat.getDrawable(context, R.drawable.ic_default_artwork)
        if (noteDrawable != null) {
            val iconSize = (sizePx * 0.48f).toInt()
            val left = (sizePx - iconSize) / 2
            val top = (sizePx - iconSize) / 2
            noteDrawable.setBounds(left, top, left + iconSize, top + iconSize)
            noteDrawable.setTint(android.graphics.Color.parseColor("#7E7D8A"))
            noteDrawable.draw(canvas)
        }

        return bitmap
    }
}
