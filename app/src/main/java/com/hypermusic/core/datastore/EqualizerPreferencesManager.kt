package com.hypermusic.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Dedicated DataStore for Equalizer — isolated from app-wide settings to avoid
 *  unnecessary observer emissions on frequent slider-driven writes. */
private val Context.eqDataStore by preferencesDataStore(name = "equalizer_prefs")

data class EqualizerSettings(
    val isEnabled: Boolean = false,
    val presetIndex: Short = -1,
    /** Comma-separated band levels e.g. "0,200,-150,0,100". Empty = use preset/default. */
    val bandLevels: String = "",
    val bassBoostStrength: Short = 0,
    val virtualizerStrength: Short = 0,
    val loudnessEnabled: Boolean = false,
    val loudnessTargetGain: Int = 0 // Millibels; the manager uses 250 when the stored value is non-positive.
)

class EqualizerPreferencesManager(private val context: Context) {

    companion object {
        val ENABLED = booleanPreferencesKey("eq_enabled")
        val PRESET_INDEX = intPreferencesKey("eq_preset_index")
        val BAND_LEVELS = stringPreferencesKey("eq_band_levels")
        val BASS_BOOST_STRENGTH = intPreferencesKey("eq_bass_boost_strength")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("eq_virtualizer_strength")
        val LOUDNESS_ENABLED = booleanPreferencesKey("eq_loudness_enabled")
        val LOUDNESS_TARGET_GAIN = intPreferencesKey("eq_loudness_target_gain")
    }

    val settingsFlow: Flow<EqualizerSettings> = context.eqDataStore.data.map { prefs ->
        EqualizerSettings(
            isEnabled = prefs[ENABLED] ?: false,
            presetIndex = (prefs[PRESET_INDEX] ?: -1).toShort(),
            bandLevels = prefs[BAND_LEVELS] ?: "",
            bassBoostStrength = (prefs[BASS_BOOST_STRENGTH] ?: 0).toShort(),
            virtualizerStrength = (prefs[VIRTUALIZER_STRENGTH] ?: 0).toShort(),
            loudnessEnabled = prefs[LOUDNESS_ENABLED] ?: false,
            loudnessTargetGain = prefs[LOUDNESS_TARGET_GAIN] ?: 0
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.eqDataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setPresetIndex(index: Short) {
        context.eqDataStore.edit { it[PRESET_INDEX] = index.toInt() }
    }

    suspend fun saveBandLevels(levels: List<Short>) {
        context.eqDataStore.edit { it[BAND_LEVELS] = levels.joinToString(",") }
    }

    suspend fun setBassBoostStrength(strength: Short) {
        context.eqDataStore.edit { it[BASS_BOOST_STRENGTH] = strength.toInt() }
    }

    suspend fun setVirtualizerStrength(strength: Short) {
        context.eqDataStore.edit { it[VIRTUALIZER_STRENGTH] = strength.toInt() }
    }

    suspend fun setLoudnessEnabled(enabled: Boolean) {
        context.eqDataStore.edit { it[LOUDNESS_ENABLED] = enabled }
    }

    suspend fun setLoudnessTargetGain(gainMb: Int) {
        context.eqDataStore.edit { it[LOUDNESS_TARGET_GAIN] = gainMb }
    }
}
