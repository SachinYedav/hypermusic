package com.hypermusic.feature.widget.pinning

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.hypermusic.feature.widget.receiver.WidgetPinningReceiver

object WidgetPinningManager {

    fun isPinningSupported(context: Context): Boolean {
        val appWidgetManager = context.getSystemService(AppWidgetManager::class.java) ?: return false
        return appWidgetManager.isRequestPinAppWidgetSupported
    }

    fun pinWidget(context: Context, receiverClass: Class<*>, widgetName: String): Boolean {
        val appWidgetManager = context.getSystemService(AppWidgetManager::class.java) ?: return false
        if (!appWidgetManager.isRequestPinAppWidgetSupported) return false

        val myProvider = ComponentName(context, receiverClass)
        val callbackIntent = Intent(context, WidgetPinningReceiver::class.java).apply {
            action = WidgetPinningReceiver.ACTION_WIDGET_PINNED
            putExtra(WidgetPinningReceiver.EXTRA_WIDGET_NAME, widgetName)
            putExtra(WidgetPinningReceiver.EXTRA_PROVIDER_CLASS, receiverClass.name)
        }

        val requestCode = receiverClass.name.hashCode()
        val successCallback = PendingIntent.getBroadcast(
            context,
            requestCode,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        return try {
            appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
        } catch (e: Exception) {
            false
        }
    }
}
