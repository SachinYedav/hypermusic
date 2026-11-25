package com.hypermusic.feature.widget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hypermusic.MainActivity
import com.hypermusic.core.data.repository.LocalMediaRepository
import com.hypermusic.core.model.isDeviceOrigin
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.feature.widget.manager.WidgetUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.getKoin

class WidgetControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                val playerRepo = getKoin().getOrNull<PlayerRepository>()
                val localMediaRepo = getKoin().getOrNull<LocalMediaRepository>()
                val widgetUpdateManager = getKoin().getOrNull<WidgetUpdateManager>()

                when (action) {
                    ACTION_PLAY_PAUSE -> {
                        val executed = playerRepo?.executeWidgetActionWhenReady {
                            playerRepo.togglePlayPause()
                        } ?: false
                        if (executed) {
                            widgetUpdateManager?.requestImmediateUpdate()
                        } else {
                            launchApp(context)
                        }
                    }

                    ACTION_PREVIOUS -> {
                        val executed = playerRepo?.executeWidgetActionWhenReady {
                            playerRepo.skipPrevious()
                        } ?: false
                        if (executed) {
                            widgetUpdateManager?.requestImmediateUpdate()
                        } else {
                            launchApp(context)
                        }
                    }

                    ACTION_NEXT -> {
                        val executed = playerRepo?.executeWidgetActionWhenReady {
                            playerRepo.skipNext()
                        } ?: false
                        if (executed) {
                            widgetUpdateManager?.requestImmediateUpdate()
                        } else {
                            launchApp(context)
                        }
                    }

                    ACTION_REPEAT -> {
                        val executed = playerRepo?.executeWidgetActionWhenReady {
                            playerRepo.toggleRepeat()
                        } ?: false
                        if (executed) {
                            widgetUpdateManager?.requestImmediateUpdate()
                        }
                    }

                    ACTION_TOGGLE_LIKE -> {
                        val ready = playerRepo?.executeWidgetActionWhenReady { } ?: false
                        if (ready) {
                            val currentTrack = playerRepo?.currentTrack?.value
                            if (currentTrack != null && !currentTrack.isDeviceOrigin() && localMediaRepo != null) {
                                withContext(Dispatchers.IO) {
                                    val isLiked = localMediaRepo.isTrackLiked(currentTrack.id).first()
                                    localMediaRepo.setLiked(currentTrack, !isLiked)
                                }
                                widgetUpdateManager?.requestImmediateUpdate()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun launchApp(context: Context) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(launchIntent)
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.hypermusic.widget.ACTION_PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.hypermusic.widget.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.hypermusic.widget.ACTION_NEXT"
        const val ACTION_REPEAT = "com.hypermusic.widget.ACTION_REPEAT"
        const val ACTION_TOGGLE_LIKE = "com.hypermusic.widget.ACTION_TOGGLE_LIKE"
    }
}
