package com.hypermusic.feature.equalizer

import androidx.lifecycle.ViewModel
import com.hypermusic.core.audio.EqualizerManager

class EqualizerViewModel(
    private val equalizerManager: EqualizerManager
) : ViewModel() {

    val isSupported = equalizerManager.isSupported
    val isEnabled = equalizerManager.isEnabled
    val bands = equalizerManager.bands
    val presets = equalizerManager.presets
    val currentPreset = equalizerManager.currentPreset

    val bassBoostSupported = equalizerManager.bassBoostSupported
    val bassBoostStrength = equalizerManager.bassBoostStrength

    val virtualizerSupported = equalizerManager.virtualizerSupported
    val virtualizerStrength = equalizerManager.virtualizerStrength

    fun setEnabled(enabled: Boolean) = equalizerManager.setEnabled(enabled)

    fun setBandLevel(bandIndex: Short, level: Short) =
        equalizerManager.setBandLevel(bandIndex, level)

    fun usePreset(presetIndex: Short) = equalizerManager.usePreset(presetIndex)

    fun setBassBoostStrength(strength: Short) =
        equalizerManager.setBassBoostStrength(strength)

    fun setVirtualizerStrength(strength: Short) =
        equalizerManager.setVirtualizerStrength(strength)
}
