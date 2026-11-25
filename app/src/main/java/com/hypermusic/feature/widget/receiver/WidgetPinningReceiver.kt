package com.hypermusic.feature.widget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.hypermusic.feature.widget.manager.WidgetUpdateManager
import org.koin.core.context.GlobalContext

class WidgetPinningReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WIDGET_PINNED) return

        val widgetName = intent.getStringExtra(EXTRA_WIDGET_NAME) ?: "Widget"

        try {
            val updateManager = GlobalContext.getOrNull()?.get<WidgetUpdateManager>()
            updateManager?.requestImmediateUpdate()
        } catch (e: Exception) {
        }

        try {
            Toast.makeText(
                context.applicationContext,
                "$widgetName added to home screen",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
        }
    }

    companion object {
        const val ACTION_WIDGET_PINNED = "com.hypermusic.widget.ACTION_WIDGET_PINNED"
        const val EXTRA_WIDGET_NAME = "extra_widget_name"
        const val EXTRA_PROVIDER_CLASS = "extra_provider_class"
    }
}
