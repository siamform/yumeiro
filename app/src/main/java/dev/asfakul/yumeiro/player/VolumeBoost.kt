/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * F-06 volume boost: a [LoudnessEnhancer] on the video player's audio session, 0 to +10 dB (about twice as loud,
 * shown as 100–200 %). Same rule as the equalizer (ST-7): the effect is created, changed and released only on its
 * own thread, never on main, because binding audio effects blocked main for seconds on some devices.
 * The boost is set back to 0 for every new video and when the player closes (hearing and speaker safety).
 */
object VolumeBoost {
    private const val TAG = "VolumeBoost"
    const val MAX_GAIN_MB = 1000

    fun gainMb(boost: Float): Int = (boost.coerceIn(0f, 1f) * MAX_GAIN_MB).roundToInt()

    /** Number on the volume bar: system volume as percent, plus the boost (100 + up to 100). */
    fun percent(steps: Int, maxSteps: Int, boost: Float): Int {
        val base = if (maxSteps > 0) (steps.coerceIn(0, maxSteps) * 100f / maxSteps).roundToInt() else 0
        return if (maxSteps > 0 && steps >= maxSteps) 100 + (boost.coerceIn(0f, 1f) * 100).roundToInt() else base
    }

    private val fx = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "yu-boost-fx").apply { isDaemon = true } }
    private fun onFx(block: () -> Unit) { runCatching { fx.execute { runCatching(block).onFailure { Log.w(TAG, "boost", it) } } } }

    private var enhancer: LoudnessEnhancer? = null // fx thread only
    private var session = 0 // fx thread only

    private val _supported = MutableStateFlow(true)
    /** False once the effect failed to bind on this device (some TVs / vendors); the UI then stops at 100 %. */
    val supported: StateFlow<Boolean> = _supported

    private val _boost = MutableStateFlow(0f)
    val boost: StateFlow<Float> = _boost

    /** Sets the boost (0..1) for [audioSessionId]; creates the effect on first use. */
    fun set(audioSessionId: Int, value: Float) {
        val v = value.coerceIn(0f, 1f)
        _boost.value = v
        onFx {
            if (audioSessionId <= 0) return@onFx
            if (session != audioSessionId) { releaseNow(); session = audioSessionId }
            val gain = gainMb(v)
            if (gain <= 0 && enhancer == null) return@onFx
            val e = enhancer ?: runCatching { LoudnessEnhancer(audioSessionId) }
                .onFailure { _supported.value = false; Log.w(TAG, "LoudnessEnhancer unavailable", it) }
                .getOrNull()?.also { enhancer = it } ?: return@onFx
            e.setTargetGain(gain)
            e.enabled = gain > 0
        }
    }

    /** New video or player closed: boost to 0 and free the effect. */
    fun reset() {
        _boost.value = 0f
        onFx { releaseNow(); session = 0 }
    }

    private fun releaseNow() {
        runCatching { enhancer?.enabled = false; enhancer?.release() }
        enhancer = null
    }
}
