package com.hypermusic.feature.widget.receiver

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.hypermusic.feature.widget.manager.WidgetUpdateManager
import com.hypermusic.feature.widget.renderer.WidgetRenderer
import com.hypermusic.feature.widget.renderer.WidgetType
import org.koin.java.KoinJavaComponent.getKoin

class LargePlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val updateManager = getKoin().getOrNull<WidgetUpdateManager>()
        val state = updateManager?.playbackState?.value ?: com.hypermusic.feature.widget.data.WidgetPlaybackState()
        for (widgetId in appWidgetIds) {
            val rv = WidgetRenderer.createRemoteViews(context, WidgetType.LARGE_PLAYER, state)
            appWidgetManager.updateAppWidget(widgetId, rv)
        }
    }
}
