package com.hypermusic.core.audio

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import com.hypermusic.core.utils.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.hypermusic.core.datastore.EqualizerPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class Band(
    val index: Short,
    val centerFrequency: Int, // in milliHertz
    val minLevel: Short,
    val maxLevel: Short,
    val currentLevel: Short
)

class EqualizerManager(
    private val context: Context,
    private val eqPrefs: EqualizerPreferencesManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = 0
    private var restoreJob: Job? = null

    private val _isSupported = MutableStateFlow(false)
    val isSupported = _isSupported.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled = _isEnabled.asStateFlow()

    private val _bands = MutableStateFlow<List<Band>>(emptyList())
    val bands = _bands.asStateFlow()

    private val _presets = MutableStateFlow<List<String>>(emptyList())
    val presets = _presets.asStateFlow()

    private val _currentPreset = MutableStateFlow<Short>(-1)
    val currentPreset = _currentPreset.asStateFlow()

    private var bassBoost: android.media.audiofx.BassBoost? = null
    private val _bassBoostSupported = MutableStateFlow(false)
    val bassBoostSupported = _bassBoostSupported.asStateFlow()
    private val _bassBoostStrength = MutableStateFlow<Short>(0)
    val bassBoostStrength = _bassBoostStrength.asStateFlow()

    // Legacy Virtualizer audio effect.
    @Suppress("DEPRECATION")
    private var virtualizer: android.media.audiofx.Virtualizer? = null
    private val _virtualizerSupported = MutableStateFlow(false)
    val virtualizerSupported = _virtualizerSupported.asStateFlow()
    private val _virtualizerStrength = MutableStateFlow<Short>(0)
    val virtualizerStrength = _virtualizerStrength.asStateFlow()

    // Spatial Audio (Android 13+ / API 33+)
    val isSpatialAudioSupported: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.spatializer?.isAvailable == true
            } else {
                false
            }
        }

    val isSpatialAudioEnabled: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.spatializer?.isEnabled == true
            } else {
                false
            }
        }

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val _loudnessSupported = MutableStateFlow(false)
    val loudnessSupported = _loudnessSupported.asStateFlow()
    private val _loudnessEnabled = MutableStateFlow(false)
    val loudnessEnabled = _loudnessEnabled.asStateFlow()
    private val _loudnessGain = MutableStateFlow(0)
    val loudnessGain = _loudnessGain.asStateFlow()

    init {
        scope.launch {
            eqPrefs.settingsFlow.collect { settings ->
                if (_isEnabled.value != settings.isEnabled) {
                    _isEnabled.value = settings.isEnabled
                    applyEnabledState()
                }
                if (_loudnessEnabled.value != settings.loudnessEnabled) {
                    _loudnessEnabled.value = settings.loudnessEnabled
                    try {
                        loudnessEnhancer?.enabled = settings.loudnessEnabled
                    } catch (e: Exception) {
                        Logger.e("EqualizerManager", "Error setting loudnessEnhancer.enabled: ${e.message}")
                    }
                }
                val targetGain = if (settings.loudnessTargetGain > 0) settings.loudnessTargetGain else 250
                if (_loudnessGain.value != targetGain) {
                    _loudnessGain.value = targetGain
                    try {
                        loudnessEnhancer?.setTargetGain(targetGain)
                    } catch (e: Exception) {
                        Logger.e("EqualizerManager", "Error setting loudness target gain: ${e.message}")
                    }
                }
            }
        }
    }

    fun attachAudioSession(sessionId: Int) {
        if (sessionId <= 0) return
        if (currentSessionId == sessionId && equalizer != null) return

        release()
        currentSessionId = sessionId

        try {
            equalizer = Equalizer(0, sessionId)
            _isSupported.value = true
            equalizer?.enabled = _isEnabled.value
            refreshPresets()
        } catch (e: Exception) {
            Logger.e("EqualizerManager", "Failed to initialize Equalizer: ${e.message}")
            _isSupported.value = false
        }

        try {
            bassBoost = android.media.audiofx.BassBoost(0, sessionId)
            _bassBoostSupported.value = bassBoost?.strengthSupported == true
            bassBoost?.enabled = _isEnabled.value
        } catch (e: Exception) {
            Logger.e("EqualizerManager", "Failed to initialize BassBoost: ${e.message}")
            _bassBoostSupported.value = false
        }

        try {
            @Suppress("DEPRECATION")
            val v = android.media.audiofx.Virtualizer(0, sessionId)
            @Suppress("DEPRECATION")
            _virtualizerSupported.value = v.strengthSupported
            virtualizer = v
            virtualizer?.enabled = _isEnabled.value
        } catch (e: Exception) {
            Logger.e("EqualizerManager", "Failed to initialize Virtualizer: ${e.message}")
            _virtualizerSupported.value = false
        }

        try {
            loudnessEnhancer = LoudnessEnhancer(sessionId)
            _loudnessSupported.value = true
        } catch (e: Exception) {
            Logger.e("EqualizerManager", "Failed to initialize LoudnessEnhancer: ${e.message}")
            _loudnessSupported.value = false
        }

        restoreJob?.cancel()
        restoreJob = scope.launch { restoreAllSettings() }
    }

    /** Restores every saved EQ setting from the dedicated DataStore. */
    private suspend fun restoreAllSettings() {
        val settings = eqPrefs.settingsFlow.first()
        _isEnabled.value = settings.isEnabled
        applyEnabledState()

        val eq = equalizer
        if (eq != null) {
            val savedLevels = settings.bandLevels
            if (savedLevels.isNotEmpty()) {
                // Custom levels take priority
                val levels = savedLevels.split(",").mapNotNull { it.toShortOrNull() }
                try {
                    val numBands = eq.numberOfBands.toInt()
                    if (levels.size == numBands) {
                        levels.forEachIndexed { i, level ->
                            eq.setBandLevel(i.toShort(), level)
                        }
                        _currentPreset.value = -1
                    }
                } catch (e: Exception) {
                    Logger.e("EqualizerManager", "Error restoring band levels: ${e.message}")
                }
            } else if (settings.presetIndex >= 0) {
                try {
                    eq.usePreset(settings.presetIndex)
                    _currentPreset.value = settings.presetIndex
                } catch (e: Exception) {
                    Logger.e("EqualizerManager", "Error restoring preset: ${e.message}")
                }
            }
            refreshBands()
        }

        if (_bassBoostSupported.value) {
            try {
                bassBoost?.setStrength(settings.bassBoostStrength)
                _bassBoostStrength.value = settings.bassBoostStrength
            } catch (e: Exception) {
                Logger.e("EqualizerManager", "Error restoring bass boost: ${e.message}")
            }
        }

        if (_virtualizerSupported.value) {
            try {
                @Suppress("DEPRECATION")
                virtualizer?.setStrength(settings.virtualizerStrength)
                _virtualizerStrength.value = settings.virtualizerStrength
            } catch (e: Exception) {
                Logger.e("EqualizerManager", "Error restoring virtualizer: ${e.message}")
            }
        }

        if (_loudnessSupported.value) {
            try {
                _loudnessEnabled.value = settings.loudnessEnabled
                val gain = if (settings.loudnessTargetGain > 0) settings.loudnessTargetGain else 250
                _loudnessGain.value = gain
                loudnessEnhancer?.setTargetGain(gain)
                loudnessEnhancer?.enabled = settings.loudnessEnabled
            } catch (e: Exception) {
                Logger.e("EqualizerManager", "Error restoring loudness: ${e.message}")
            }
        }
    }

    private fun refreshBands() {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands
            val bandLevelRange = eq.bandLevelRange
            val minLevel = bandLevelRange[0]
            val maxLevel = bandLevelRange[1]

            val newBands = mutableListOf<Band>()
            for (i in 0 until numBands) {
                val idx = i.toShort()
                newBands.add(
                    Band(
                        index = idx,
                        centerFrequency = eq.getCenterFreq(idx),
                        minLevel = minLevel,
                        maxLevel = maxLevel,
                        currentLevel = eq.getBandLevel(idx)
                    )
                )
            }
            _bands.value = newBands
        } catch (e: Exception) {
            Logger.e("EqualizerManager", "Error refreshing bands: ${e.message}")
        }
    }

    private fun refreshPresets() {
        val eq = equalizer ?: return
        try {
            val numPresets = eq.numberOfPresets
            val presetList = mutableListOf<String>()
            for (i in 0 until numPresets) {
                presetList.add(eq.getPresetName(i.toShort()))
            }
            _presets.value = presetList
            _currentPreset.value = eq.currentPreset
        } catch (e: Exception) {
            Logger.e("EqualizerManager", "Error refreshing presets: ${e.message}")
        }
    }

    private fun applyEnabledState() {
        val enabled = _isEnabled.value
        try { equalizer?.enabled = enabled } catch (e: Exception) { Logger.e("EqualizerManager", "Error setting equalizer.enabled: ${e.message}") }
        try { bassBoost?.enabled = enabled } catch (e: Exception) { Logger.e("EqualizerManager", "Error setting bassBoost.enabled: ${e.message}") }
        try { virtualizer?.enabled = enabled } catch (e: Exception) { Logger.e("EqualizerManager", "Error setting virtualizer.enabled: ${e.message}") }
    }


    fun setEnabled(enabled: Boolean) {
        try {
            _isEnabled.value = enabled
            applyEnabledState()
            scope.launch { eqPrefs.setEnabled(enabled) }
        } catch (e: Exception) {
            Logger.e("EqualizerManager", "Error setting EQ enabled: ${e.message}")
        }
    }

    fun setBandLevel(bandIndex: Short, level: Short) {
        try {
            equalizer?.setBandLevel(bandIndex, level)
            _bands.value = _bands.value.map {
                if (it.index == bandIndex) it.copy(currentLevel = level) else it
            }
            _currentPreset.value = -1

            val allLevels = _bands.value.map { it.currentLevel }
            scope.launch {
                eqPrefs.saveBandLevels(allLevels)
                eqPrefs.setPresetIndex(-1) // clear preset since user customized
            }
        } catch (e: Exception) {
            Logger.e("EqualizerManager", "Error setting band level: ${e.message}")
        }
    }

    fun usePreset(presetIndex: Short) {
        try {
            equalizer?.usePreset(presetIndex)
            _currentPreset.value = presetIndex
            refreshBands()
            scope.launch {
                eqPrefs.setPresetIndex(presetIndex)
                eqPrefs.saveBandLevels(emptyList())
            }
        } catch (e: Exception) {
            Logger.e("EqualizerManager", "Error using preset: ${e.message}")
        }
    }

    fun setBassBoostStrength(strength: Short) {
        try {
            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(strength)
                _bassBoostStrength.value = strength
                scope.launch { eqPrefs.setBassBoostStrength(strength) }
            }
        } catch (e: Exception) {
            Logger.e("EqualizerManager", "Error setting bass boost: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    fun setVirtualizerStrength(strength: Short) {
        try {
            if (virtualizer?.strengthSupported == true) {
                virtualizer?.setStrength(strength)
                _virtualizerStrength.value = strength
                scope.launch { eqPrefs.setVirtualizerStrength(strength) }
            }
        } catch (e: Exception) {
            Logger.e("EqualizerManager", "Error setting virtualizer: ${e.message}")
        }
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        try {
            _loudnessEnabled.value = enabled
            loudnessEnhancer?.enabled = enabled
            if (enabled && _loudnessGain.value == 0) {
                setLoudnessGain(250) // Default gain when loudness enhancement is enabled.
            }
            scope.launch { eqPrefs.setLoudnessEnabled(enabled) }
        } catch (e: Exception) {
            Logger.e("EqualizerManager", "Error toggling loudness: ${e.message}")
        }
    }

    fun setLoudnessGain(gainMb: Int) {
        try {
            loudnessEnhancer?.setTargetGain(gainMb)
            _loudnessGain.value = gainMb
            scope.launch { eqPrefs.setLoudnessTargetGain(gainMb) }
        } catch (e: Exception) {
            Logger.e("EqualizerManager", "Error setting loudness gain: ${e.message}")
        }
    }

    fun release() {
        try {
            restoreJob?.cancel()
            equalizer?.release(); equalizer = null
            bassBoost?.release(); bassBoost = null
            virtualizer?.release(); virtualizer = null
            loudnessEnhancer?.release(); loudnessEnhancer = null
        } catch (e: Exception) {
            Logger.e("EqualizerManager", "Error releasing audio effects: ${e.message}")
        }
    }
}
