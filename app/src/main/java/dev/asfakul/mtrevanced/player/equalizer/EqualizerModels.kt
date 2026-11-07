/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.player.equalizer

data class EqualizerBand(
    val bandIndex: Short,
    val centerFreqHz: Int,
    val levelMb: Short, // millibels (-1500 to +1500)
    val minLevelMb: Short = -1500,
    val maxLevelMb: Short = 1500
) {
    val displayFrequency: String
        get() = if (centerFreqHz >= 1000) {
            val khz = centerFreqHz / 1000f
            if (khz % 1f == 0f) "${khz.toInt()}kHz" else "${String.format(java.util.Locale.US, "%.1f", khz)}kHz"
        } else {
            "${centerFreqHz}Hz"
        }

    val displayLevel: String
        get() {
            val db = levelMb / 100
            return if (db > 0) "+${db}dB" else "${db}dB"
        }
}

data class EqualizerState(
    val isEnabled: Boolean = false,
    val currentPreset: String = "Normal",
    val presets: List<String> = listOf("Normal", "Rock", "Pop", "Jazz", "Classical", "Dance", "Bass Boost", "Custom"),
    val bands: List<EqualizerBand> = emptyList(),
    val isBassBoostSupported: Boolean = false,
    val bassBoostStrength: Int = 0, // 0 to 1000
    val isVirtualizerSupported: Boolean = false,
    val virtualizerStrength: Int = 0, // 0 to 1000
    val isSupportedOnDevice: Boolean = true
)
