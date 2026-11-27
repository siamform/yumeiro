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
        // Group Y (B8): more starting points. Values chosen from common broadcast / playback practice, own numbers.
        DIALOGUE("Voices", "Lifts speech over music and effects", Settings(-28f, 4f, 10f, 2f, 160f, 10f)),
        MOVIE("Movie", "Keeps the punch, tames sudden peaks", Settings(-14f, 4f, 4f, 1f, 120f, 3f)),
        MUSIC("Music", "Gentle: songs keep their dynamics", Settings(-12f, 1.5f, 12f, 20f, 400f, 2f)),
        LOUD_PLACE("Noisy place", "Commute or kitchen: quiet parts come right up", Settings(-40f, 8f, 6f, 2f, 220f, 18f)),
        /** Group Y (B8): "Manual" in the UI (the saved name stays CUSTOM so earlier choices keep working). */
        CUSTOM("Manual", "Every setting yours: threshold, ratio, knee, attack, release and gain", null),
    }

    /** Group Y (B8): a set of values the person saved under their own name. */
    data class UserPreset(val name: String, val settings: Settings)

    /** At most this many saved presets. */
    const val MAX_SAVED = 8
    const val MAX_NAME = 24

    private const val PREFS = "yumeiro_drc"
    private val _preset = MutableStateFlow(Preset.OFF)
    val preset: StateFlow<Preset> = _preset
    private val _custom = MutableStateFlow(Preset.NIGHT.settings!!)
    val custom: StateFlow<Settings> = _custom
    @Volatile private var loaded = false

    /** What the processor uses now; null = off. */
    @Volatile var active: Settings? = null
        private set

    private val _saved = MutableStateFlow<List<UserPreset>>(emptyList())
    /** Group Y (B8): the person's saved presets, oldest first. */
    val saved: StateFlow<List<UserPreset>> = _saved

    fun load(context: Context) {
        if (loaded) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _saved.value = decodeSaved(p.getString("saved", null))
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

    /**
     * F15: own values. Takes effect on the next audio buffer (live). [persist] false while a slider is dragged (no
     * disk write per frame); the drag's end saves. Values equal to a preset select that preset, as a person expects.
     */
    fun setCustom(context: Context, settings: Settings, persist: Boolean = true) {
        load(context)
        val s = settings.clamped()
        _custom.value = s
        val named = Preset.entries.firstOrNull { it.settings == s }
        _preset.value = named ?: Preset.CUSTOM
        refresh()
        if (!persist) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("preset", _preset.value.name)
            .putFloat("threshold", s.thresholdDb).putFloat("ratio", s.ratio).putFloat("knee", s.kneeDb)
            .putFloat("attack", s.attackMs).putFloat("release", s.releaseMs).putFloat("makeup", s.makeupDb)
            .apply()
    }

    /** F15: what the compressor uses now (the preset's values, or the person's own); null when Off. */
    fun current(): Settings? = when (val p = _preset.value) {
        Preset.OFF -> null
        Preset.CUSTOM -> _custom.value
        else -> p.settings
    }

    /** Group Y (B8): Manual: the person's own values (the last ones they set), saved as the choice. */
    fun selectManual(context: Context) {
        load(context)
        _preset.value = Preset.CUSTOM
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("preset", Preset.CUSTOM.name).apply()
        refresh()
    }

    sealed interface SaveResult {
        data object Saved : SaveResult
        data object Replaced : SaveResult
        data object NoName : SaveResult
        data object Full : SaveResult
        data object Off : SaveResult
    }

    /**
     * Group Y (B8): saves the values in use now under [name] (trimmed, at most [MAX_NAME] characters). The same name
     * (any case) is replaced; at most [MAX_SAVED] presets.
     */
    fun savePreset(context: Context, name: String): SaveResult {
        load(context)
        val s = current() ?: return SaveResult.Off
        val n = cleanName(name) ?: return SaveResult.NoName
        val list = _saved.value
        val i = list.indexOfFirst { it.name.equals(n, ignoreCase = true) }
        val result = if (i >= 0) SaveResult.Replaced else if (list.size >= MAX_SAVED) return SaveResult.Full else SaveResult.Saved
        _saved.value = if (i >= 0) list.toMutableList().also { it[i] = UserPreset(n, s) } else list + UserPreset(n, s)
        writeSaved(context)
        return result
    }

    fun deletePreset(context: Context, name: String) {
        load(context)
        _saved.value = _saved.value.filterNot { it.name == name }
        writeSaved(context)
    }

    /** Group Y (B8): use a saved preset (its values become the Manual ones, saved). */
    fun applySaved(context: Context, p: UserPreset) {
        setCustom(context, p.settings)
        // Its values may equal a built-in preset; the person picked their own, so Manual stays the choice.
        if (_preset.value != Preset.CUSTOM) selectManual(context)
    }

    /** The saved preset whose values are in use now (Manual only), for the selected chip. */
    fun activeSaved(preset: Preset, custom: Settings, saved: List<UserPreset>): UserPreset? =
        if (preset == Preset.CUSTOM) saved.firstOrNull { it.settings == custom } else null

    internal fun cleanName(name: String): String? =
        name.split(' ', '\t', '\n', '\r').filter { it.isNotEmpty() }.joinToString(" ").take(MAX_NAME).trim().takeIf { it.isNotEmpty() }

    private fun writeSaved(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("saved", encodeSaved(_saved.value)).apply()
    }

    internal fun encodeSaved(list: List<UserPreset>): String = org.json.JSONArray().apply {
        list.forEach { u ->
            val s = u.settings
            put(org.json.JSONObject().put("name", u.name).put("threshold", s.thresholdDb.toDouble()).put("ratio", s.ratio.toDouble())
                .put("knee", s.kneeDb.toDouble()).put("attack", s.attackMs.toDouble()).put("release", s.releaseMs.toDouble()).put("makeup", s.makeupDb.toDouble()))
        }
    }.toString()

    /** Never throws: a damaged value gives no saved presets rather than a crash. */
    internal fun decodeSaved(raw: String?): List<UserPreset> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        val a = org.json.JSONArray(raw)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val n = cleanName(o.optString("name")) ?: return@mapNotNull null
            val d = DEFAULT
            UserPreset(n, Settings(
                o.optDouble("threshold", d.thresholdDb.toDouble()).toFloat(), o.optDouble("ratio", d.ratio.toDouble()).toFloat(),
                o.optDouble("knee", d.kneeDb.toDouble()).toFloat(), o.optDouble("attack", d.attackMs.toDouble()).toFloat(),
                o.optDouble("release", d.releaseMs.toDouble()).toFloat(), o.optDouble("makeup", d.makeupDb.toDouble()).toFloat(),
            ).clamped())
        }.distinctBy { it.name.lowercase() }.take(MAX_SAVED)
    }.getOrDefault(emptyList())

    /** F15: back to the recommended sound (Night) and its values, saved. */
    fun reset(context: Context) = setCustom(context, DEFAULT)

    /** The recommended values Reset returns to. */
    val DEFAULT: Settings get() = Preset.NIGHT.settings!!

    /** For tests: read the saved choice again, as after an app restart. */
    internal fun reloadForTest(context: Context) { loaded = false; load(context) }

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
        Preset.CUSTOM -> custom.value.let { "Manual · ${it.thresholdDb.roundToInt()} dB, ${fmt(it.ratio)}:1, +${it.makeupDb.roundToInt()} dB" }
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
