@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.hypermusic.core.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import com.hypermusic.core.data.repository.*
import com.hypermusic.core.datastore.PreferencesManager
import com.hypermusic.core.domain.MusicService
import com.hypermusic.core.model.Track
import com.hypermusic.core.model.isDeviceOrigin
import com.hypermusic.core.utils.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.util.UUID

/** Application-owned projection shared by the service runtime and controller client. */
class PlaybackRuntimeState {
    val ready = MutableStateFlow(false)
    val autoplay = MutableStateFlow<AutoplayStatus>(AutoplayStatus.Idle)
    val error = MutableStateFlow<String?>(null)
}

/** Owns queue side effects and persistence on the service's player looper, for every controller. */
class PlaybackCoordinator(
    private val context: Context,
    private val player: ExoPlayer,
    private val preferences: PreferencesManager,
    private val music: MusicService,
    private val downloads: DownloadAvailability,
    private val snapshots: PlaybackStateStore,
    private val history: PlaybackHistoryWriter,
    private val timer: SleepTimerManager,
    private val state: PlaybackRuntimeState
) : Player.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var revision = 0L
    private var released = false
    private var restoring = true
    private var pendingPlay = false
    private var autoplay = false
    private var persistent = true
    private var quality = "normal"
    private var saver = false
    private val effectiveQuality: String get() = if (saver) "data_saver" else quality
    private var radio: Job? = null
    private var radioEntries: List<String>? = null
    private var failedSeed: String? = null
    private var prefetch: Job? = null
    private var prefetchKey: String? = null
    private var track: Track? = null
    private var session = UUID.randomUUID().toString()
    private var activeMs = 0L
    private var lastTick = SystemClock.elapsedRealtime()
    private var wasPlaying = false
    private var historyEpoch = history.generation
    private var lastCheckpoint = 0L
    private var retriedEntry: String? = null
    private val failures = ConsecutiveFailurePolicy()
    private var recovery: Job? = null
    private var recoveryRevision = 0L
    private var skippedToEntry: String? = null
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { scope.launch { schedulePrefetch() } }
        override fun onLost(network: Network) { scope.launch { cancelPrefetch() } }
    }

    init {
        state.ready.value = false
        player.addListener(this)
        timer.setPauseCallback { invalidateRadio(); player.pause() }
        timer.setVolumeChangeCallback { player.volume = it }
        timer.setVolumeProvider { player.volume }
        timer.setIsPlayingProvider { player.isPlaying }
        connectivity.registerDefaultNetworkCallback(networkCallback)
        scope.launch {
            try {
                val settings = preferences.userSettingsFlow.first()
                persistent = settings.isPersistentQueueEnabled
                val capturedRevision = revision
                val saved = if (persistent) withContext(Dispatchers.IO) { snapshots.loadState() } else null
                if (revision == capturedRevision && player.mediaItemCount == 0 && saved != null) {
                    val index = saved.currentIndex.coerceIn(saved.queueTracks.indices)
                    val tracks = saved.queueTracks.map(::mediaItem)
                    player.setMediaItems(tracks, index, saved.playbackPositionMs.coerceAtLeast(0))
                    val order = saved.traversalOrder
                    if (saved.shuffleModeEnabled) {
                        val isOrderValid = order.size == saved.queueTracks.size &&
                                           order.toSet().size == order.size &&
                                           order.all { it in saved.queueTracks.indices }
                        if (isOrderValid) {
                            player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
                            player.shuffleModeEnabled = true
                        } else {
                            generateAndApplyShuffleOrder()
                            player.shuffleModeEnabled = true
                        }
                    } else {
                        player.shuffleModeEnabled = false
                    }
                    player.repeatMode = saved.repeatMode.takeIf { it in 0..2 } ?: Player.REPEAT_MODE_OFF
                }
                if (!persistent) snapshots.clearState()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                state.error.value = "Unable to restore playback"
            }
            finally {
                restoring = false
                state.ready.value = !released
                if (pendingPlay && !released && player.mediaItemCount > 0) {
                    player.prepare()
                    player.play()
                }
                pendingPlay = false
            }
        }
        scope.launch {
            preferences.userSettingsFlow.collect { settings ->
                val changed = quality != settings.streamQuality || saver != settings.dataSaverEnabled
                quality = settings.streamQuality
                saver = settings.dataSaverEnabled
                if (autoplay && !settings.isAutoplayEnabled) {
                    invalidateRadio()
                    (player.mediaItemCount - 1 downTo 0).filter {
                        it != player.currentMediaItemIndex && TrackMediaItemMapper.fromMediaItem(player.getMediaItemAt(it)).isImplicit
                    }.forEach(player::removeMediaItem)
                }
                autoplay = settings.isAutoplayEnabled
                persistent = settings.isPersistentQueueEnabled
                player.skipSilenceEnabled = settings.isSkipSilenceEnabled
                if (!persistent) snapshots.clearState() else checkpoint()
                if (changed) cancelPrefetch()
                reconcileQueue()
            }
        }
        scope.launch {
            while (isActive) {
                delay(1_000)
                accrue()
                if (player.isPlaying && SystemClock.elapsedRealtime() - lastCheckpoint >= 10_000) {
                    checkpoint()
                    flushHistory()
                }
            }
        }
    }

    fun mediaItem(track: Track): MediaItem = TrackMediaItemMapper.toMediaItem(track,
        if (track.isDeviceOrigin()) track.id else "hyper://${track.id}/${if (isVideo(track)) "video" else "audio"}")

    private fun isVideo(track: Track) = track.trackType in setOf("video", "podcast", "podcast_episode") && !track.isDeviceOrigin()
    private fun entries() = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaMetadata.extras?.getString("queueEntryId") ?: player.getMediaItemAt(it).mediaId }

    /** Called synchronously by the session adapter, before its mutation reaches event callbacks. */
    fun invalidateSelection(clear: Boolean = false) {
        cancelRecovery()
        failures.reset()
        revision++
        pendingPlay = false
        invalidateRadio()
        cancelPrefetch()
        if (clear) { timer.cancel(); snapshots.clearState() }
    }

    fun requestPlay(): Boolean {
        if (restoring) {
            pendingPlay = true
            return false
        }
        failures.reset()
        return true
    }

    private fun cancelRecovery() { recoveryRevision++; recovery?.cancel(); recovery = null }
    fun pauseRequested() { pendingPlay = false; cancelRecovery() }
    fun retryAutoplay() { failedSeed = null; reconcileQueue() }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val nextTrack = mediaItem?.let(TrackMediaItemMapper::fromMediaItem)
        val nextEntry = nextTrack?.queueEntryId ?: nextTrack?.id
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && nextEntry != skippedToEntry) {
            cancelRecovery()
            failures.reset()
        }
        skippedToEntry = null
        val sameEntry = (nextTrack?.queueEntryId ?: nextTrack?.id) == (track?.queueEntryId ?: track?.id)
        if (sameEntry && reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            track = nextTrack
            return
        }
        accrue()
        flushHistory()
        track = nextTrack
        session = UUID.randomUUID().toString()
        activeMs = 0
        lastTick = SystemClock.elapsedRealtime()
        historyEpoch = history.generation
        retriedEntry = null
        state.error.value = null
        timer.onTrackTransition(reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        if (track?.isImplicit == true && player.repeatMode != Player.REPEAT_MODE_ALL) {
            val entry = track?.queueEntryId
            val generation = revision
            scope.launch {
                yield()
                if (generation != revision || entry == null) return@launch
                val index = (0 until player.mediaItemCount).firstOrNull {
                    player.getMediaItemAt(it).mediaMetadata.extras?.getString("queueEntryId") == entry
                } ?: return@launch
                player.replaceMediaItem(index, TrackMediaItemMapper.promote(player.getMediaItemAt(index)))
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        timer.onPlayingChanged(isPlaying)
        accrue()
        wasPlaying = isPlaying
        if (!isPlaying) flushHistory()
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        if (shuffleModeEnabled) {
            val physical = entries()
            val implicitIds = (0 until player.mediaItemCount).filter {
                TrackMediaItemMapper.fromMediaItem(player.getMediaItemAt(it)).isImplicit
            }.map { physical[it] }.toSet()
            if (implicitIds.isNotEmpty()) {
                val currentTrav = traversalEntries()
                val currentEntryId = player.currentMediaItem?.mediaMetadata?.extras?.getString("queueEntryId")
                    ?: player.currentMediaItem?.mediaId
                val currentIdx = if (currentEntryId != null) currentTrav.indexOf(currentEntryId) else -1
                val pendingStartIndex = if (currentIdx >= 0) currentIdx + 1 else 0
                if (pendingStartIndex in currentTrav.indices) {
                    val pendingTrav = currentTrav.subList(pendingStartIndex, currentTrav.size)
                    val firstImplicit = pendingTrav.indexOfFirst { it in implicitIds }
                    val lastExplicit = pendingTrav.indexOfLast { it !in implicitIds }
                    if (firstImplicit in 0..lastExplicit) {
                        generateAndApplyShuffleOrder()
                    }
                }
            }
        }
        checkpoint()
        schedulePrefetch()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) timer.onTrackTransition(true)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (released) return
        if (events.contains(Player.EVENT_TIMELINE_CHANGED) && radio?.isActive == true && radioEntries != entries()) invalidateRadio()
        if (events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
            events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
            events.contains(Player.EVENT_REPEAT_MODE_CHANGED) || events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
            reconcileQueue()
        }
        if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) checkpoint()
    }

    private fun traversal(): List<Int> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val indices = mutableListOf<Int>()
        var index = timeline.getFirstWindowIndex(player.shuffleModeEnabled)
        while (index != C.INDEX_UNSET && indices.size < timeline.windowCount) {
            indices += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        }
        return indices
    }

    private fun traversalEntries(): List<String> =
        traversal().map { player.getMediaItemAt(it).mediaMetadata.extras?.getString("queueEntryId") ?: player.getMediaItemAt(it).mediaId }

    fun generateAndApplyShuffleOrder() {
        if (player.mediaItemCount == 0) return
        val physical = entries()
        val idToIdx = physical.withIndex().associate { it.value to it.index }
        val implicitIds = (0 until player.mediaItemCount).filter {
            TrackMediaItemMapper.fromMediaItem(player.getMediaItemAt(it)).isImplicit
        }.map { physical[it] }.toSet()
        val currentEntryId = player.currentMediaItem?.mediaMetadata?.extras?.getString("queueEntryId")
            ?: player.currentMediaItem?.mediaId
        val currentOrder = traversalEntries()
        val newOrder = QueueOrder.buildShuffledTraversal(currentOrder, currentEntryId, implicitIds)
        val indices = newOrder.mapNotNull { idToIdx[it] }.toIntArray()
        if (indices.size == player.mediaItemCount) {
            player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(indices, System.nanoTime()))
        }
    }

    fun isShuffleModeEnabled(): Boolean = player.shuffleModeEnabled

    fun setShuffleModeEnabled(enabled: Boolean) {
        if (player.shuffleModeEnabled == enabled) return
        if (enabled) {
            generateAndApplyShuffleOrder()
            player.shuffleModeEnabled = true
        } else {
            player.shuffleModeEnabled = false
        }
        checkpoint()
        schedulePrefetch()
    }

    private fun reconcileQueue() {
        if (released || restoring) return
        schedulePrefetch()
        if (player.mediaItemCount == 0) { invalidateRadio(); timer.cancel(); return }
        if (!autoplay || player.repeatMode != Player.REPEAT_MODE_OFF) { invalidateRadio(); return }
        val order = traversal()
        val remaining = order.dropWhile { it != player.currentMediaItemIndex }
        if (remaining.size > 3 || radio?.isActive == true) return
        val seed = remaining.asReversed().map { TrackMediaItemMapper.fromMediaItem(player.getMediaItemAt(it)) }
            .firstOrNull { !it.isDeviceOrigin() } ?: run { state.autoplay.value = AutoplayStatus.LocalOnly; return }
        if (seed.id == failedSeed) return
        val generation = revision
        val captured = entries()
        radioEntries = captured
        state.autoplay.value = AutoplayStatus.Loading
        radio = scope.launch {
            try {
                val result = music.getRadioQueue(seed.id)
                ensureActive()
                if (released || !QueueOrder.acceptsResult(generation, revision, captured, entries(), autoplay,
                    player.repeatMode == Player.REPEAT_MODE_OFF)) return@launch
                result.onSuccess { tracks ->
                    val existing = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                    val rawAdditions = tracks.distinctBy { it.id }.filter { it.id !in existing }
                    failedSeed = seed.id
                    if (rawAdditions.isEmpty()) state.autoplay.value = AutoplayStatus.Error("No more similar tracks found")
                    else {
                        val finalAdditions = rawAdditions.map { it.copy(isImplicit = true, queueEntryId = UUID.randomUUID().toString()) }
                        val oldTraversal = if (player.shuffleModeEnabled) traversalEntries() else null
                        player.addMediaItems(finalAdditions.map(::mediaItem))
                        if (player.shuffleModeEnabled && oldTraversal != null) {
                            val newPhysical = entries()
                            val idToIdx = newPhysical.withIndex().associate { it.value to it.index }
                            val newTraversal = oldTraversal + finalAdditions.map { it.queueEntryId!! }
                            val indices = newTraversal.mapNotNull { idToIdx[it] }.toIntArray()
                            if (indices.size == player.mediaItemCount) {
                                player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(indices, System.nanoTime()))
                            }
                        }
                        state.autoplay.value = AutoplayStatus.Idle
                        if (player.playbackState == Player.STATE_ENDED && player.playWhenReady) {
                            player.seekToNextMediaItem(); player.prepare()
                        }
                    }
                }.onFailure { failedSeed = seed.id; state.autoplay.value = AutoplayStatus.Error("Unable to load similar tracks") }
            } catch (e: CancellationException) { throw e }
            finally { if (generation == revision && state.autoplay.value is AutoplayStatus.Loading) state.autoplay.value = AutoplayStatus.Idle }
        }
    }

    private fun invalidateRadio() {
        radio?.cancel(); radio = null; failedSeed = null
        radioEntries = null
        state.autoplay.value = AutoplayStatus.Idle
    }

    private fun cancelPrefetch() { prefetch?.cancel(); prefetch = null; prefetchKey = null; BytePreloader.cancel() }

    private fun schedulePrefetch() {
        if (released || !player.playWhenReady || saver || !NetworkUtils.isValidatedConnection(context) || player.currentTimeline.isEmpty) { cancelPrefetch(); return }
        val next = player.currentTimeline.getNextWindowIndex(player.currentMediaItemIndex, player.repeatMode, player.shuffleModeEnabled)
        if (next == C.INDEX_UNSET || next == player.currentMediaItemIndex) { cancelPrefetch(); return }
        val target = TrackMediaItemMapper.fromMediaItem(player.getMediaItemAt(next))
        if (target.isDeviceOrigin()) { cancelPrefetch(); return }
        val key = "${target.queueEntryId}|${effectiveQuality}|${revision}"
        if (key == prefetchKey) return
        cancelPrefetch()
        prefetchKey = key
        val targetQuality = effectiveQuality
        val cacheEpoch = StreamUrlCache.generation
        prefetch = scope.launch {
            var success = false
            try {
                if (withContext(Dispatchers.IO) { downloads.find(target.id).file != null }) {
                    success = true
                    return@launch
                }
                val type = if (isVideo(target)) "video" else "audio"
                val result = StreamResolutionCoordinator.resolveStreamUrl(target.id, targetQuality, type, music, prefetch = true)
                ensureActive()
                if (prefetchKey != key || cacheEpoch != StreamUrlCache.generation || !NetworkUtils.isValidatedConnection(context) || saver) return@launch
                val url = result.getOrNull()
                if (url != null) {
                    BytePreloader.preload(context, url)
                    success = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                if (!success && prefetchKey == key) {
                    prefetchKey = null
                }
            }
        }
    }

    private fun accrue() {
        val now = SystemClock.elapsedRealtime()
        if (historyEpoch != history.generation) {
            activeMs = if (wasPlaying) (now - maxOf(lastTick, history.clearedAtMs)).coerceAtLeast(0) else 0
            session = UUID.randomUUID().toString(); historyEpoch = history.generation
        } else if (wasPlaying) activeMs += (now - lastTick).coerceAtLeast(0)
        failures.onPlayingTime(activeMs)
        lastTick = now
    }

    private fun flushHistory() { track?.let { history.checkpoint(it, session, activeMs, historyEpoch) } }

    fun checkpoint() {
        if (restoring || !persistent || released) return
        lastCheckpoint = SystemClock.elapsedRealtime()
        if (player.mediaItemCount == 0) { snapshots.clearState(); return }
        snapshots.saveState(PlaybackStateSnapshot(
            (0 until player.mediaItemCount).map { TrackMediaItemMapper.fromMediaItem(player.getMediaItemAt(it)) },
            player.currentMediaItemIndex.coerceAtLeast(0), player.currentPosition.coerceAtLeast(0),
            player.duration.coerceAtLeast(0), player.shuffleModeEnabled, player.repeatMode,
            track?.let(::isVideo) ?: false, traversal()))
    }

    override fun onPlayerError(error: PlaybackException) {
        cancelRecovery()
        val entry = track?.queueEntryId ?: track?.id
        val failure = PlaybackFailure.classify(error, isDeviceTrack = track?.isDeviceOrigin() == true)
        val item = player.currentMediaItem
        val uri = item?.localConfiguration?.uri
        val sourceAttempt = uri?.let { SourceAttempts.find(it) }
        val isCacheFailure = failure == PlaybackFailure.CACHE || (sourceAttempt?.isCacheOnly == true)

        if ((failure == PlaybackFailure.EXPIRED_URL || isCacheFailure) &&
            entry != null && retriedEntry != entry && track?.isDeviceOrigin() != true && uri != null) {
            retriedEntry = entry
            val generation = revision
            val intentRevision = recoveryRevision
            val cacheKey = sourceAttempt?.cacheKey
            recovery = scope.launch {
                try {
                    if (isCacheFailure && cacheKey != null) withContext(Dispatchers.IO) {
                        HyperCache.getInstance(context).removeResource(cacheKey)
                    }
                    val currentTrack = track
                    if (currentTrack != null) {
                        CompleteCopyLookup.invalidate(currentTrack.id, context = context)
                    }
                    ensureActive()
                    val current = player.currentMediaItem?.let(TrackMediaItemMapper::fromMediaItem)
                    if (released || generation != revision || intentRevision != recoveryRevision || (current?.queueEntryId ?: current?.id) != entry) {
                        return@launch
                    }
                    if (!NetworkUtils.isConnected(context)) {
                        player.pause()
                        state.error.value = "Playback failed offline. Reconnect to stream this track."
                        return@launch
                    }
                    restartAttempt(play = player.playWhenReady)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    player.pause()
                    state.error.value = failure.userMessage
                }
            }
        } else if (failure in setOf(PlaybackFailure.CONTENT_UNAVAILABLE, PlaybackFailure.UNSUPPORTED) && entry != null) {
            val next = player.nextMediaItemIndex
            val canSkip = failures.shouldSkip(entry, player.playWhenReady, next != C.INDEX_UNSET && next != player.currentMediaItemIndex,
                player.repeatMode == Player.REPEAT_MODE_ONE)
            if (!canSkip) {
                player.pause()
                state.error.value = if (failures.count >= 3) "Three consecutive tracks failed. Playback stopped. Retry or select another track." else failure.userMessage
                return
            }
            val target = entries()[next]
            val generation = revision
            val intentRevision = recoveryRevision
            recovery = scope.launch {
                yield()
                val current = player.currentMediaItem?.let(TrackMediaItemMapper::fromMediaItem)
                if (released || generation != revision || intentRevision != recoveryRevision || !player.playWhenReady ||
                    (current?.queueEntryId ?: current?.id) != entry) return@launch
                val targetIndex = entries().indexOf(target)
                if (targetIndex < 0) return@launch
                skippedToEntry = target
                player.seekToDefaultPosition(targetIndex)
                player.prepare()
                state.error.value = "Skipped an unavailable track."
            }
        } else {
            player.pause()
            state.error.value = failure.userMessage
        }
    }

    private fun restartAttempt(play: Boolean) {
        val item = player.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        StreamUrlCache.remove(item.mediaId)
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.filter { it != "attempt" }.forEach { name ->
            uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
        }
        builder.appendQueryParameter("attempt", UUID.randomUUID().toString())
        player.replaceMediaItem(index, item.buildUpon().setUri(if (uri.scheme == "hyper") builder.build() else uri).build())
        player.seekTo(index, position)
        player.prepare()
        player.playWhenReady = play
    }

    fun retryPlayback() {
        cancelRecovery()
        failures.reset()
        retriedEntry = null
        state.error.value = null
        restartAttempt(play = true)
    }

    fun playTrack(track: Track, entryId: String?) {
        val index = if (entryId != null) entries().indexOf(entryId) else
            (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == track.id } ?: -1
        // A row removed since the controller rendered it must not become a new selection.
        if (entryId != null && index < 0) return
        invalidateSelection()
        if (index >= 0) {
            val item = player.getMediaItemAt(index)
            if (TrackMediaItemMapper.fromMediaItem(item).isImplicit) player.replaceMediaItem(index, TrackMediaItemMapper.promote(item))
            player.seekToDefaultPosition(index)
        } else {
            player.setMediaItem(mediaItem(track.copy(isImplicit = false, queueEntryId = UUID.randomUUID().toString())))
        }
        player.prepare()
        player.play()
    }

    fun removeEntry(entryId: String) {
        val index = entries().indexOf(entryId)
        if (index < 0) return
        if (player.mediaItemCount == 1) {
            invalidateSelection(clear = true)
            player.stop()
            player.clearMediaItems()
        } else player.removeMediaItem(index)
    }

    fun moveEntry(entryId: String, beforeEntryId: String?) {
        val currentEntryId = player.currentMediaItem?.mediaMetadata?.extras?.getString("queueEntryId")
            ?: player.currentMediaItem?.mediaId
        val physicalEntries = entries()
        val implicitIds = (0 until player.mediaItemCount).filter {
            TrackMediaItemMapper.fromMediaItem(player.getMediaItemAt(it)).isImplicit
        }.map { physicalEntries[it] }.toSet()

        if (player.shuffleModeEnabled) {
            val currentTraversal = traversalEntries()
            val newTraversal = QueueOrder.moveInTraversal(currentTraversal, currentEntryId, entryId, beforeEntryId, implicitIds) ?: return
            val idToIdx = physicalEntries.withIndex().associate { it.value to it.index }
            val indices = newTraversal.mapNotNull { idToIdx[it] }.toIntArray()
            if (indices.size == player.mediaItemCount) {
                player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(indices, System.nanoTime()))
                checkpoint()
                schedulePrefetch()
            }
        } else {
            val newPhysical = QueueOrder.moveInTraversal(physicalEntries, currentEntryId, entryId, beforeEntryId, implicitIds) ?: return
            val from = physicalEntries.indexOf(entryId)
            val to = newPhysical.indexOf(entryId)
            if (from >= 0 && to >= 0 && from != to) {
                player.moveMediaItem(from, to)
                checkpoint()
                schedulePrefetch()
            }
        }
    }

    fun enqueue(track: Track, next: Boolean) {
        val entry = track.copy(isImplicit = false, queueEntryId = UUID.randomUUID().toString())
        val currentEntryId = player.currentMediaItem?.mediaMetadata?.extras?.getString("queueEntryId")
            ?: player.currentMediaItem?.mediaId
        val currentPhysicalIndex = player.currentMediaItemIndex

        val oldTraversal = traversalEntries()

        // Play Next inserts immediately after the current item in physical order.
        // Normal enqueue inserts before the first pending implicit item, or at the end.
        val physicalInsertion = if (next) {
            (currentPhysicalIndex + 1).coerceIn(0, player.mediaItemCount)
        } else {
            val firstPendingImplicitPhysical = (currentPhysicalIndex + 1 until player.mediaItemCount).firstOrNull {
                TrackMediaItemMapper.fromMediaItem(player.getMediaItemAt(it)).isImplicit
            }
            firstPendingImplicitPhysical ?: player.mediaItemCount
        }

        player.addMediaItem(physicalInsertion, mediaItem(entry))

        if (player.shuffleModeEnabled) {
            val newPhysical = entries()
            val idToIdx = newPhysical.withIndex().associate { it.value to it.index }
            val implicitIds = (0 until player.mediaItemCount).filter {
                TrackMediaItemMapper.fromMediaItem(player.getMediaItemAt(it)).isImplicit
            }.map { newPhysical[it] }.toSet()

            val desiredTraversal = oldTraversal.toMutableList()
            val currentPos = if (currentEntryId != null) desiredTraversal.indexOf(currentEntryId) else -1

            val insertPos = if (next) {
                if (currentPos >= 0) currentPos + 1 else desiredTraversal.size
            } else {
                val firstPendingImplicitPos = if (currentPos >= 0 && currentPos + 1 < desiredTraversal.size) {
                    val sub = desiredTraversal.subList(currentPos + 1, desiredTraversal.size)
                    val relIdx = sub.indexOfFirst { it in implicitIds }
                    if (relIdx >= 0) currentPos + 1 + relIdx else desiredTraversal.size
                } else {
                    val firstImplicit = desiredTraversal.indexOfFirst { it in implicitIds }
                    if (firstImplicit >= 0) firstImplicit else desiredTraversal.size
                }
                firstPendingImplicitPos
            }

            desiredTraversal.add(insertPos.coerceIn(0, desiredTraversal.size), entry.queueEntryId!!)
            val indices = desiredTraversal.mapNotNull { idToIdx[it] }.toIntArray()
            if (indices.size == player.mediaItemCount) {
                player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(indices, System.nanoTime()))
            }
        }
        schedulePrefetch()
    }

    fun release() {
        cancelRecovery()
        accrue(); flushHistory(); checkpoint()
        released = true
        state.ready.value = false
        player.removeListener(this)
        invalidateRadio(); cancelPrefetch()
        timer.releaseCallbacks()
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        scope.cancel()
    }
}
