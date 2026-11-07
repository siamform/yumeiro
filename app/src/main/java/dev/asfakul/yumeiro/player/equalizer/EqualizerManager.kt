/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player.equalizer

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class EqualizerManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("mt_player_eq_prefs", Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var currentAudioSessionId: Int = 0

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    companion object {
        private const val TAG = "EqualizerManager"

        private const val KEY_ENABLED = "eq_enabled"
        private const val KEY_PRESET = "eq_preset"
        private const val KEY_BASS_BOOST = "eq_bass_boost"
        private const val KEY_VIRTUALIZER = "eq_virtualizer"
        private const val KEY_BAND_PREFIX = "eq_band_"

        val PRESETS_LIST = listOf("Normal", "Rock", "Pop", "Jazz", "Classical", "Dance", "Bass Boost", "Custom")

        // Relative gain offsets (-1500 to +1500 millibels) across 5 normalized frequency points
        private val PRESET_GAINS = mapOf(
            "Normal" to listOf(0, 0, 0, 0, 0),
            "Rock" to listOf(400, 200, -100, 300, 500),
            "Pop" to listOf(-100, 200, 500, 200, -100),
            "Jazz" to listOf(300, 200, 100, 200, 400),
            "Classical" to listOf(400, 300, -100, 200, 300),
            "Dance" to listOf(600, 400, 200, 400, 500),
            "Bass Boost" to listOf(700, 500, 100, 0, 0)
        )

        @Volatile
        private var instance: EqualizerManager? = null

        fun getInstance(context: Context): EqualizerManager {
            return instance ?: synchronized(this) {
                instance ?: EqualizerManager(context).also { instance = it }
            }
        }
    }

    init {
        loadSavedState()
    }

    private fun loadSavedState() {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val preset = prefs.getString(KEY_PRESET, "Normal") ?: "Normal"
        val bass = prefs.getInt(KEY_BASS_BOOST, 0)
        val virt = prefs.getInt(KEY_VIRTUALIZER, 0)

        // Default initial fallback bands if audio session is not yet attached
        val defaultBands = listOf(
            EqualizerBand(0, 60, 0),
            EqualizerBand(1, 230, 0),
            EqualizerBand(2, 910, 0),
            EqualizerBand(3, 3600, 0),
            EqualizerBand(4, 14000, 0)
        ).map { band ->
            val savedLevel = prefs.getInt("$KEY_BAND_PREFIX${band.bandIndex}", band.levelMb.toInt()).toShort()
            band.copy(levelMb = savedLevel)
        }

        _equalizerState.value = EqualizerState(
            isEnabled = enabled,
            currentPreset = preset,
            presets = PRESETS_LIST,
            bands = defaultBands,
            bassBoostStrength = bass,
            virtualizerStrength = virt,
            isBassBoostSupported = true,
            isVirtualizerSupported = true,
            isSupportedOnDevice = true
        )
    }

    @Synchronized
    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentAudioSessionId) return
        currentAudioSessionId = audioSessionId

        releaseAudioEffects()

        try {
            // 1. Equalizer Setup
            val eq = Equalizer(0, audioSessionId)
            val numBands = eq.numberOfBands
            val range = eq.bandLevelRange
            val minRange = if (range != null && range.size >= 2) range[0] else (-1500).toShort()
            val maxRange = if (range != null && range.size >= 2) range[1] else (1500).toShort()

            val detectedBands = mutableListOf<EqualizerBand>()
            for (i in 0 until numBands) {
                val bandIndex = i.toShort()
                val centerFreqHz = eq.getCenterFreq(bandIndex) / 1000
                val savedLevel = prefs.getInt("$KEY_BAND_PREFIX$bandIndex", 0).toShort()
                val clampedLevel = savedLevel.coerceIn(minRange, maxRange)

                try {
                    eq.setBandLevel(bandIndex, clampedLevel)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set band level $bandIndex: ${e.message}")
                }

                detectedBands.add(
                    EqualizerBand(
                        bandIndex = bandIndex,
                        centerFreqHz = centerFreqHz,
                        levelMb = clampedLevel,
                        minLevelMb = minRange,
                        maxLevelMb = maxRange
                    )
                )
            }

            eq.enabled = _equalizerState.value.isEnabled
            equalizer = eq

            // 2. Bass Boost Setup
            var bassSupported = false
            try {
                val bb = BassBoost(0, audioSessionId)
                bassSupported = bb.strengthSupported
                if (bassSupported) {
                    val strength = _equalizerState.value.bassBoostStrength.toShort()
                    bb.setStrength(strength)
                    bb.enabled = _equalizerState.value.isEnabled && strength > 0
                }
                bassBoost = bb
            } catch (e: Exception) {
                Log.w(TAG, "BassBoost not supported: ${e.message}")
            }

            // 3. Virtualizer Setup
            var virtSupported = false
            try {
                val virt = Virtualizer(0, audioSessionId)
                virtSupported = virt.strengthSupported
                if (virtSupported) {
                    val strength = _equalizerState.value.virtualizerStrength.toShort()
                    virt.setStrength(strength)
                    virt.enabled = _equalizerState.value.isEnabled && strength > 0
                }
                virtualizer = virt
            } catch (e: Exception) {
                Log.w(TAG, "Virtualizer not supported: ${e.message}")
            }

            _equalizerState.update {
                it.copy(
                    bands = if (detectedBands.isNotEmpty()) detectedBands else it.bands,
                    isBassBoostSupported = bassSupported,
                    isVirtualizerSupported = virtSupported,
                    isSupportedOnDevice = true
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Audio effects initialization error: ${e.message}")
            _equalizerState.update { it.copy(isSupportedOnDevice = false) }
        }
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _equalizerState.update { it.copy(isEnabled = enabled) }

        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled && (_equalizerState.value.bassBoostStrength > 0)
            virtualizer?.enabled = enabled && (_equalizerState.value.virtualizerStrength > 0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle equalizer effects enabled: ${e.message}")
        }
    }

    fun setBandLevel(bandIndex: Short, levelMb: Short) {
        val currentBands = _equalizerState.value.bands.toMutableList()
        val index = currentBands.indexOfFirst { it.bandIndex == bandIndex }
        if (index >= 0) {
            val band = currentBands[index]
            val clamped = levelMb.coerceIn(band.minLevelMb, band.maxLevelMb)
            currentBands[index] = band.copy(levelMb = clamped)

            try {
                equalizer?.setBandLevel(bandIndex, clamped)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set band level $bandIndex: ${e.message}")
            }

            prefs.edit().putInt("$KEY_BAND_PREFIX$bandIndex", clamped.toInt()).apply()
            prefs.edit().putString(KEY_PRESET, "Custom").apply()

            _equalizerState.update {
                it.copy(
                    bands = currentBands,
                    currentPreset = "Custom"
                )
            }
        }
    }

    fun applyPreset(presetName: String) {
        if (!PRESETS_LIST.contains(presetName) || presetName == "Custom") {
            _equalizerState.update { it.copy(currentPreset = presetName) }
            prefs.edit().putString(KEY_PRESET, presetName).apply()
            return
        }

        val gains = PRESET_GAINS[presetName] ?: return
        val currentBands = _equalizerState.value.bands.toMutableList()

        currentBands.forEachIndexed { i, band ->
            val targetGain = if (i < gains.size) gains[i].toShort() else 0.toShort()
            val clamped = targetGain.coerceIn(band.minLevelMb, band.maxLevelMb)
            currentBands[i] = band.copy(levelMb = clamped)

            try {
                equalizer?.setBandLevel(band.bandIndex, clamped)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set band level for preset: ${e.message}")
            }
            prefs.edit().putInt("$KEY_BAND_PREFIX${band.bandIndex}", clamped.toInt()).apply()
        }

        prefs.edit().putString(KEY_PRESET, presetName).apply()

        _equalizerState.update {
            it.copy(
                bands = currentBands,
                currentPreset = presetName
            )
        }
    }

    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        prefs.edit().putInt(KEY_BASS_BOOST, clamped).apply()
        _equalizerState.update { it.copy(bassBoostStrength = clamped) }

        try {
            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength(clamped.toShort())
                    bb.enabled = _equalizerState.value.isEnabled && clamped > 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set bass boost strength: ${e.message}")
        }
    }

    fun setVirtualizerStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        prefs.edit().putInt(KEY_VIRTUALIZER, clamped).apply()
        _equalizerState.update { it.copy(virtualizerStrength = clamped) }

        try {
            virtualizer?.let { virt ->
                if (virt.strengthSupported) {
                    virt.setStrength(clamped.toShort())
                    virt.enabled = _equalizerState.value.isEnabled && clamped > 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set virtualizer strength: ${e.message}")
        }
    }

    fun resetToDefault() {
        applyPreset("Normal")
        setBassBoostStrength(0)
        setVirtualizerStrength(0)
    }

    @Synchronized
    fun releaseAudioEffects() {
        try {
            equalizer?.release()
        } catch (e: Exception) {}
        equalizer = null

        try {
            bassBoost?.release()
        } catch (e: Exception) {}
        bassBoost = null

        try {
            virtualizer?.release()
        } catch (e: Exception) {}
        virtualizer = null
    }
}
