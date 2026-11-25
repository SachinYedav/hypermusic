@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.hypermusic.core.player


import android.app.PendingIntent
import android.content.Intent
import com.hypermusic.MainActivity
import com.hypermusic.core.model.isDeviceOrigin
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import androidx.media3.session.DefaultMediaNotificationProvider
import com.hypermusic.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import android.os.Bundle
import android.os.SystemClock

import org.koin.java.KoinJavaComponent.inject
import com.hypermusic.core.data.repository.LocalMediaRepository
import com.hypermusic.core.model.Track
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.hypermusic.core.datastore.PreferencesManager

class HyperMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    
    private val localMediaRepository: LocalMediaRepository by inject(LocalMediaRepository::class.java)
    private val preferencesManager: PreferencesManager by inject(PreferencesManager::class.java)
    private val equalizerManager: com.hypermusic.core.audio.EqualizerManager by inject(com.hypermusic.core.audio.EqualizerManager::class.java)
    private val playbackStateStore: com.hypermusic.core.data.repository.PlaybackStateStore by inject(com.hypermusic.core.data.repository.PlaybackStateStore::class.java)
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private var coordinator: PlaybackCoordinator? = null

    override fun onCreate() {
        super.onCreate()
        
        val dataSourceFactory = HyperDataSourceFactory(this)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        trackSelector = DefaultTrackSelector(this)
        trackSelector?.parameters = trackSelector!!.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector!!)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        if (exoPlayer?.audioSessionId != 0) {
            equalizerManager.attachAudioSession(exoPlayer!!.audioSessionId)
        }

        val koin = org.koin.java.KoinJavaComponent.getKoin()
        coordinator = PlaybackCoordinator(applicationContext, exoPlayer!!, preferencesManager,
            koin.get(), koin.get(), playbackStateStore, koin.get(), koin.get(), koin.get())
        CompleteCopyLookup.loadFromDisk(applicationContext)
        exoPlayer?.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) { equalizerManager.attachAudioSession(audioSessionId) }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val track = mediaItem?.let(TrackMediaItemMapper::fromMediaItem)
                val video = track?.trackType in setOf("video", "podcast", "podcast_episode") && track?.isDeviceOrigin() != true
                trackSelector?.let { it.parameters = it.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !video).build() }
            }
        })

        val videoModeCommand = SessionCommand("SET_VIDEO_MODE", Bundle.EMPTY)

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val availableCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(videoModeCommand)
                    .add(SessionCommand("RETRY_AUTOPLAY", Bundle.EMPTY))
                    .add(SessionCommand("RETRY_PLAYBACK", Bundle.EMPTY))
                    .add(SessionCommand("INSERT_NEXT", Bundle.EMPTY))
                    .add(SessionCommand("APPEND_QUEUE", Bundle.EMPTY))
                    .add(SessionCommand("PLAY_TRACK", Bundle.EMPTY))
                    .add(SessionCommand("REMOVE_ENTRY", Bundle.EMPTY))
                    .add(SessionCommand("MOVE_ENTRY", Bundle.EMPTY))
                    .add(SessionCommand("TOGGLE_SHUFFLE", Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(availableCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (controller.packageName != packageName) return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
                if (customCommand.customAction == "TOGGLE_SHUFFLE") {
                    val current = coordinator?.isShuffleModeEnabled() ?: false
                    val target = args.getBoolean("enabled", !current)
                    coordinator?.setShuffleModeEnabled(target)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction == "RETRY_PLAYBACK") {
                    coordinator?.retryPlayback()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction in setOf("REMOVE_ENTRY", "MOVE_ENTRY")) {
                    val entry = args.getString("entryId")?.takeIf { it.isNotBlank() }
                        ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    if (customCommand.customAction == "REMOVE_ENTRY") coordinator?.removeEntry(entry)
                    else coordinator?.moveEntry(entry, args.getString("beforeEntryId"))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction in setOf("INSERT_NEXT", "APPEND_QUEUE", "PLAY_TRACK")) {
                    val item = args.getBundle("item")?.let { runCatching { MediaItem.fromBundle(it) }.getOrNull() }
                        ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    val track = TrackMediaItemMapper.fromMediaItem(item)
                    if (track.id.isBlank()) return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    if (customCommand.customAction == "PLAY_TRACK") coordinator?.playTrack(track, args.getString("entryId"))
                    else coordinator?.enqueue(track, customCommand.customAction == "INSERT_NEXT")
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction == "RETRY_AUTOPLAY") {
                    coordinator?.retryAutoplay()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction == "SET_VIDEO_MODE") {
                    val isVideo = args.getBoolean("isVideo", false)
                    trackSelector?.let { selector ->
                        selector.parameters = selector.buildUponParameters()
                            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !isVideo)
                            .build()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardingPlayer = object : ForwardingPlayer(exoPlayer!!) {
            override fun pause() { coordinator?.pauseRequested(); super.pause() }
            override fun stop() { coordinator?.invalidateSelection(); super.stop() }
            override fun clearMediaItems() { coordinator?.invalidateSelection(clear = true); super.clearMediaItems() }
            override fun setMediaItems(items: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
                coordinator?.invalidateSelection()
                super.setMediaItems(items, startIndex, startPositionMs)
            }

            override fun play() {
                if (coordinator?.requestPlay() == false) return
                if (playbackState == Player.STATE_IDLE && mediaItemCount > 0) {
                    prepare()
                }
                super.play()
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (playWhenReady && coordinator?.requestPlay() == false) return
                if (!playWhenReady) coordinator?.pauseRequested()
                if (playWhenReady && playbackState == Player.STATE_IDLE && mediaItemCount > 0) {
                    prepare()
                }
                super.setPlayWhenReady(playWhenReady)
            }

            override fun seekToNextMediaItem() {
                super.seekToNextMediaItem()
                if (!playWhenReady) {
                    play()
                }
            }

            override fun seekToPreviousMediaItem() {
                super.seekToPreviousMediaItem()
                if (!playWhenReady) {
                    play()
                }
            }

            override fun seekToNext() {
                super.seekToNext()
                if (!playWhenReady) {
                    play()
                }
            }

            override fun seekToPrevious() {
                super.seekToPrevious()
                if (!playWhenReady) {
                    play()
                }
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(callback)
            .build()

        val notificationProvider = DefaultMediaNotificationProvider(this).apply {
            setSmallIcon(R.drawable.ic_notification)
        }
        setMediaNotificationProvider(notificationProvider)
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        coordinator?.checkpoint()
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        coordinator?.release()
        coordinator = null
        equalizerManager.release()
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        serviceScope.coroutineContext.cancelChildren()
        super.onDestroy()
    }

    companion object {
        private const val QUALIFYING_LISTEN_TIME_MS = 10_000L
        private const val TRACKING_INTERVAL_MS = 1_000L
    }
}
