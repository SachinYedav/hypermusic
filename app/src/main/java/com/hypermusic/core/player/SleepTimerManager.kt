package com.hypermusic.core.player

import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SleepTimerManager(private val clock: () -> Long = SystemClock::elapsedRealtime) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _remainingMs = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()
    private val _activeOption = MutableStateFlow("off")
    val activeOption: StateFlow<String> = _activeOption.asStateFlow()
    private var timerJob: Job? = null
    private var onPause: (() -> Unit)? = null
    private var onVolumeChange: ((Float) -> Unit)? = null
    private var volumeProvider: (() -> Float)? = null
    private var isPlayingProvider: (() -> Boolean)? = null
    private var savedVolume: Float? = null
    private var lastTick = 0L
    private var wasPlaying = false

    fun setPauseCallback(callback: () -> Unit) { onPause = callback }
    fun setVolumeChangeCallback(callback: (Float) -> Unit) { onVolumeChange = callback }
    fun setVolumeProvider(provider: () -> Float) { volumeProvider = provider }
    fun setIsPlayingProvider(provider: () -> Boolean) { isPlayingProvider = provider }
    fun onPlayingChanged(playing: Boolean) { tick(); wasPlaying = playing }
    fun onTrackTransition(isNaturalTransition: Boolean) {
        if (_activeOption.value == "end_of_track" && isNaturalTransition) finish()
    }

    fun set(option: String) {
        cancel()
        if (option == "end_of_track") { _activeOption.value = option; return }
        val minutes = option.toLongOrNull()?.takeIf { it in 1..1440 } ?: return
        _activeOption.value = option
        _remainingMs.value = minutes * 60_000
        lastTick = clock()
        wasPlaying = isPlayingProvider?.invoke() == true
        timerJob = scope.launch { while (isActive) { delay(250); tick() } }
    }
    private fun tick() {
        val remaining = _remainingMs.value ?: return
        val now = clock()
        val next = (remaining - if (wasPlaying) (now - lastTick).coerceAtLeast(0) else 0).coerceAtLeast(0)
        lastTick = now
        _remainingMs.value = next
        if (next == 0L) { finish(); return }
        if (next <= 8_000 && wasPlaying) {
            if (savedVolume == null) savedVolume = volumeProvider?.invoke() ?: 1f
            onVolumeChange?.invoke(savedVolume!! * (next / 8_000f))
        }
    }
    private fun finish() {
        val pause = onPause
        cancel()
        pause?.invoke()
    }
    fun cancel() {
        timerJob?.cancel(); timerJob = null
        _remainingMs.value = null
        _activeOption.value = "off"
        savedVolume?.let { onVolumeChange?.invoke(it) }
        savedVolume = null
    }
    fun releaseCallbacks() {
        cancel()
        onPause = null; onVolumeChange = null; volumeProvider = null; isPlayingProvider = null
    }
}
