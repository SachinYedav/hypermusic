package com.hypermusic.core.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hypermusic.core.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadActionReceiver : BroadcastReceiver(), KoinComponent {

    private val downloadRepository: DownloadRepository by inject()

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val trackId = intent.getStringExtra("track_id")

        if (action == "ACTION_CANCEL_TRACK" && trackId != null) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    downloadRepository.cancelDownload(trackId)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
