/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * H-1 (owner list 2026-11-25 item 1): a dynamic range compressor for every video player path
 * (anime, movies, series, live TV, YouTube / Yumix, downloads and local files) and the music player.
 * Loud scenes come down and quiet dialogue comes up, so nobody has to ride the volume at night.
 *
 * It is Yumeiro's own Media3 [AudioProcessor] in the audio sink, not the platform DynamicsProcessing
 * effect, because that needs Android 9 and some TVs and phones run Android 7. Settings change live
 * (read once per buffer), so playback never stops and nothing is re-prepared. Off = samples are
 * copied through unchanged. Encoded passthrough (AC-3 / E-AC-3 / DTS sent to a receiver) never
 * reaches a PCM processor; the receiver's own night mode applies there.
 *
 * The maths ([Dsp]) is pure and unit-tested: a feed-forward peak compressor with a soft knee,
 * attack and release smoothing, make-up gain and a final -1 dBFS safety limiter so make-up gain
 * can never clip.
 */
object DynamicRangeCompressor {
    data class Settings(
        val thresholdDb: Float = -24f,
        val ratio: Float = 3f,
        val kneeDb: Float = 8f,
        val attackMs: Float = 5f,
        val releaseMs: Float = 200f,
        val makeupDb: Float = 8f,
    ) {
        fun clamped() = Settings(
            thresholdDb.coerceIn(-60f, 0f), ratio.coerceIn(1f, 20f), kneeDb.coerceIn(0f, 24f),
            attackMs.coerceIn(0.5f, 200f), releaseMs.coerceIn(10f, 2000f), makeupDb.coerceIn(0f, 24f),
        )
    }

    enum class Preset(val label: String, val summary: String, val settings: Settings?) {
        OFF("Off", "Sound as the video was mixed", null),
        LIGHT("Light", "Takes the edge off explosions", Settings(-18f, 2f, 6f, 10f, 150f, 4f)),
        NIGHT("Night", "Quieter loud scenes, clearer voices", Settings(-24f, 3f, 8f, 5f, 200f, 8f)),
        STRONG("Strong", "Everything at about the same loudness", Settings(-32f, 6f, 6f, 3f, 250f, 14f)),
        CUSTOM("Custom", "Your own threshold, ratio and timing", null),
    }

    private const val PREFS = "yumeiro_drc"
    private val _preset = MutableStateFlow(Preset.OFF)
    val preset: StateFlow<Preset> = _preset
    private val _custom = MutableStateFlow(Preset.NIGHT.settings!!)
    val custom: StateFlow<Settings> = _custom
    @Volatile private var loaded = false

    /** What the processor uses now; null = off. */
    @Volatile var active: Settings? = null
        private set

    fun load(context: Context) {
        if (loaded) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _preset.value = runCatching { Preset.valueOf(p.getString("preset", Preset.OFF.name)!!) }.getOrDefault(Preset.OFF)
        val d = Preset.NIGHT.settings!!
        _custom.value = Settings(
            p.getFloat("threshold", d.thresholdDb), p.getFloat("ratio", d.ratio), p.getFloat("knee", d.kneeDb),
            p.getFloat("attack", d.attackMs), p.getFloat("release", d.releaseMs), p.getFloat("makeup", d.makeupDb),
        ).clamped()
        loaded = true
        refresh()
    }

    fun setPreset(context: Context, preset: Preset) {
        load(context)
        _preset.value = preset
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("preset", preset.name).apply()
        refresh()
    }

    fun setCustom(context: Context, settings: Settings) {
        load(context)
        val s = settings.clamped()
        _custom.value = s
        _preset.value = Preset.CUSTOM
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("preset", Preset.CUSTOM.name)
            .putFloat("threshold", s.thresholdDb).putFloat("ratio", s.ratio).putFloat("knee", s.kneeDb)
            .putFloat("attack", s.attackMs).putFloat("release", s.releaseMs).putFloat("makeup", s.makeupDb)
            .apply()
        refresh()
    }

    private fun refresh() {
        active = when (val p = _preset.value) {
            Preset.OFF -> null
            Preset.CUSTOM -> _custom.value
            else -> p.settings
        }
    }

    /** Frames that went through the compressor while it was on (device tests and the info overlay). */
    @Volatile var framesCompressed: Long = 0L
    /** The most gain reduction of the last block, in dB (≤ 0). */
    @Volatile var lastReductionDb: Float = 0f

    /** For tests: set without SharedPreferences. */
    internal fun setActiveForTest(s: Settings?) { active = s }

    fun summary(): String = when (val p = _preset.value) {
        Preset.CUSTOM -> custom.value.let { "Custom · ${it.thresholdDb.roundToInt()} dB, ${fmt(it.ratio)}:1" }
        else -> p.label
    }

    private fun fmt(v: Float) = if (v % 1f == 0f) v.roundToInt().toString() else "%.1f".format(v)

    /** Pure compressor state for one stream (all channels linked so the stereo image stays put). */
    class Dsp(private val sampleRate: Int) {
        private var envDb = -120f
        private var gainDb = 0f

        fun reset() { envDb = -120f; gainDb = 0f }

        /** Static curve: gain reduction (≤ 0 dB) for an input level, soft knee around the threshold. */
        fun reductionDb(levelDb: Float, s: Settings): Float {
            val over = levelDb - s.thresholdDb
            val slope = 1f / s.ratio - 1f
            return when {
                s.kneeDb > 0f && abs(over) <= s.kneeDb / 2f -> slope * (over + s.kneeDb / 2f).pow(2) / (2f * s.kneeDb)
                over > 0f -> slope * over
                else -> 0f
            }
        }

        /**
         * Processes [frames] interleaved frames of [channels] in place in [buf] (float, -1..1).
         * Returns the largest gain reduction in dB this block (for tests and the debug log).
         */
        fun process(buf: FloatArray, frames: Int, channels: Int, s: Settings): Float {
            val att = coef(s.attackMs)
            val rel = coef(s.releaseMs)
            val makeup = s.makeupDb
            val ceiling = 0.891f // -1 dBFS
            var most = 0f
            var i = 0
            for (f in 0 until frames) {
                var peak = 0f
                for (c in 0 until channels) { val a = abs(buf[i + c]); if (a > peak) peak = a }
                val lvl = if (peak > 1e-6f) 20f * log10(peak) else -120f
                // Peak envelope: fast up (attack), slow down (release).
                envDb = if (lvl > envDb) att * envDb + (1 - att) * lvl else rel * envDb + (1 - rel) * lvl
                val target = reductionDb(envDb, s)
                gainDb = if (target < gainDb) att * gainDb + (1 - att) * target else rel * gainDb + (1 - rel) * target
                if (gainDb < most) most = gainDb
                val g = dbToLin(gainDb + makeup)
                for (c in 0 until channels) {
                    var v = buf[i + c] * g
                    // Safety limiter: make-up gain never clips (soft above the ceiling, hard at 1).
                    val av = abs(v)
                    if (av > ceiling) v = (if (v > 0) 1f else -1f) * (ceiling + (1f - ceiling) * kotlin.math.tanh((av - ceiling) / (1f - ceiling)))
                    buf[i + c] = v
                }
                i += channels
            }
            return most
        }

        private fun coef(ms: Float): Float = exp(-1f / (ms / 1000f * sampleRate)).toFloat()
        private fun dbToLin(db: Float): Float = 10f.pow(db / 20f)
    }

    /** The music player's renderers: Media3's defaults with the compressor in the audio sink. */
    @OptIn(UnstableApi::class)
    fun musicRenderers(context: Context): androidx.media3.exoplayer.RenderersFactory {
        load(context)
        return object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean) =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf<AudioProcessor>(Processor()))
                    .build()
        }
    }

    /** The Media3 processor: 16-bit or float PCM in, the same encoding out. */
    @OptIn(UnstableApi::class)
    class Processor : BaseAudioProcessor() {
        private var dsp: Dsp? = null
        private var scratch = FloatArray(0)

        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            val enc = inputAudioFormat.encoding
            if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
            dsp = Dsp(inputAudioFormat.sampleRate)
            return inputAudioFormat
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val size = inputBuffer.remaining()
            if (size == 0) return
            val out = replaceOutputBuffer(size)
            val s = active
            val d = dsp
            if (s == null || d == null) { out.put(inputBuffer); out.flip(); return }
            val ch = inputAudioFormat.channelCount.coerceAtLeast(1)
            val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
            val n = size / (if (float) 4 else 2)
            if (scratch.size < n) scratch = FloatArray(n)
            val src = inputBuffer.order(ByteOrder.nativeOrder())
            if (float) for (k in 0 until n) scratch[k] = src.getFloat()
            else for (k in 0 until n) scratch[k] = src.getShort() / 32768f
            lastReductionDb = d.process(scratch, n / ch, ch, s)
            framesCompressed += n / ch
            if (float) for (k in 0 until n) out.putFloat(scratch[k])
            else for (k in 0 until n) out.putShort((scratch[k] * 32767f).roundToInt().coerceIn(-32768, 32767).toShort())
            out.flip()
        }

        override fun onFlush() { dsp?.reset() }
        override fun onReset() { dsp = null; scratch = FloatArray(0) }
    }
}
