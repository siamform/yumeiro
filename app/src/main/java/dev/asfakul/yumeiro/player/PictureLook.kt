/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * A4K-3 (owner 2026-11-25, "Anime4K Enhancement v2"): picture presets, the Anime4K filter section and picture controls.
 *
 * Order on the GPU, for every player path (VideoPlayerModal and Shorts):
 *   decoded frame → engine level scale (Presentation) → Anime4K pass × [Tuning.iterations] → [PictureGrade]
 *   (sharpness → white balance → brightness → contrast → black point / lift → gamma → saturation / vibrance → hue →
 *   video filter → grain) → rotation → screen. Subtitles are drawn by the subtitle view above the video surface, so
 *   nothing here touches them. HDR frames pass through untouched (see [PictureGrade]).
 *
 * The grade's values are read by the shader every frame from [live], so a slider move shows on the next frame without
 * rebuilding the effect graph. Only adding or removing a pass (iterations, turning the grade on) rebuilds it.
 */
object PictureLook {
    enum class VideoFilter(val label: String) { NONE("None"), MONO("Black and white"), SEPIA("Sepia"), VINTAGE("Vintage") }

    /** Everything the grade shader needs; neutral = the source picture. */
    data class Grade(
        val brightness: Float = 0f, val contrast: Float = 1f, val saturation: Float = 1f, val gamma: Float = 1f,
        val hue: Float = 0f, val sharpness: Float = 0f, val temperature: Float = 0f, val tint: Float = 0f,
        val lift: Float = 0f, val black: Float = 0f, val vibrance: Float = 0f, val filter: VideoFilter = VideoFilter.NONE,
        val grain: Float = 0f,
    ) {
        val neutral: Boolean get() = this == NEUTRAL
    }

    val NEUTRAL = Grade()

    enum class Look(val label: String, val detail: String, val base: Grade) {
        ORIGINAL("Original", "The source picture, unchanged", NEUTRAL),
        VIVID("Vivid", "Richer colour and a little more contrast", Grade(contrast = 1.08f, saturation = 1.22f, vibrance = 0.3f)),
        WARM("Warm Tone", "Warmer whites and skin tones", Grade(temperature = 0.45f, saturation = 1.04f)),
        COOL("Cool Tone", "Cooler, crisper whites", Grade(temperature = -0.45f, contrast = 1.03f)),
        PASTEL("Soft Pastel", "Softer contrast, lifted shadows, gentle colour", Grade(brightness = 0.03f, contrast = 0.88f, saturation = 0.82f, lift = 0.05f)),
        CINEMATIC("Cinematic", "Deeper contrast, calmer colour, warm highlights", Grade(contrast = 1.12f, saturation = 0.9f, temperature = 0.12f, tint = -0.05f, black = 0.03f)),
        NIGHT("Night Mode", "Dimmer and warmer for dark rooms", Grade(brightness = -0.08f, contrast = 0.95f, temperature = 0.6f, saturation = 0.95f)),
        GHIBLI("Ghibli Style", "Bright mid-tones, warm and lush colour", Grade(contrast = 0.96f, saturation = 1.12f, gamma = 0.94f, temperature = 0.18f, lift = 0.03f, vibrance = 0.2f)),
        NEON("Neon Pop", "Bold colour and strong contrast", Grade(contrast = 1.15f, saturation = 1.42f, vibrance = 0.4f, tint = 0.05f)),
        DEEP_BLACK("Deep Black", "Inky blacks, best on OLED screens", Grade(contrast = 1.1f, gamma = 1.05f, black = 0.06f)),
        CUSTOM("Custom", "Your own picture controls", NEUTRAL),
    }

    /** The user's picture controls, applied on top of the preset. Defaults = no change. */
    data class Controls(
        val brightness: Float = 0f, val saturation: Float = 1f, val contrast: Float = 1f, val gamma: Float = 1f,
        val hue: Float = 0f, val sharpness: Float = 0f, val filter: VideoFilter = VideoFilter.NONE,
    ) {
        fun clamped() = copy(
            brightness = brightness.coerceIn(-0.5f, 0.5f), saturation = saturation.coerceIn(0f, 2f), contrast = contrast.coerceIn(0.5f, 1.5f),
            gamma = gamma.coerceIn(0.5f, 2f), hue = hue.coerceIn(-180f, 180f), sharpness = sharpness.coerceIn(0f, 1f),
        )
        val neutral: Boolean get() = this == Controls()
    }

    /** Anime4K filter section. Defaults reproduce the V2 pass exactly as it was before (edge gate 0.02, clamp 0.015). */
    data class Tuning(val iterations: Int = 1, val threshold: Float = 0.2f, val range: Float = 0.3f, val grain: Float = 0f) {
        fun clamped() = copy(iterations = iterations.coerceIn(1, MAX_ITERATIONS), threshold = threshold.coerceIn(0f, 1f), range = range.coerceIn(0f, 1f), grain = grain.coerceIn(0f, 1f))
        /** Edge level below which nothing is sharpened. */
        val gateLow: Float get() = 0.1f * threshold
        /** How far past the local min/max a restored pixel may go. */
        val clampMargin: Float get() = 0.05f * range
    }

    const val MAX_ITERATIONS = 3

    data class Profile(val look: Look = Look.ORIGINAL, val controls: Controls = Controls(), val tuning: Tuning = Tuning()) {
        val isDefault: Boolean get() = this == Profile()
    }

    /** Pure → unit-tested. Preset first, then the user's controls; grain comes from the filter section. */
    fun resolve(p: Profile, heat: HeatGuard.Level = HeatGuard.Level.COOL): Grade {
        val b = p.look.base; val c = p.controls.clamped()
        return b.copy(
            brightness = (b.brightness + c.brightness).coerceIn(-0.5f, 0.5f),
            contrast = (b.contrast * c.contrast).coerceIn(0.4f, 1.8f),
            saturation = (b.saturation * c.saturation).coerceIn(0f, 2.5f),
            gamma = (b.gamma * c.gamma).coerceIn(0.4f, 2.5f),
            hue = wrapHue(b.hue + c.hue),
            // A hot phone keeps its colour but drops the extra samples.
            sharpness = if (heat == HeatGuard.Level.HOT) 0f else maxOf(b.sharpness, c.sharpness),
            filter = if (c.filter != VideoFilter.NONE) c.filter else b.filter,
            grain = p.tuning.clamped().grain,
        )
    }

    private fun wrapHue(h: Float): Float { var x = h; while (x > 180f) x -= 360f; while (x < -180f) x += 360f; return x }

    /** How many Anime4K passes this device may run now (0 = Anime4K paused). */
    fun allowedIterations(wanted: Int, tier: VideoPrefs.GpuTier, heat: HeatGuard.Level): Int {
        val cap = when (heat) {
            HeatGuard.Level.HOT -> 0
            HeatGuard.Level.WARM -> 1
            HeatGuard.Level.COOL -> when (tier) { VideoPrefs.GpuTier.LOW -> 1; VideoPrefs.GpuTier.MID -> 2; VideoPrefs.GpuTier.HIGH -> MAX_ITERATIONS }
        }
        return wanted.coerceIn(1, MAX_ITERATIONS).coerceAtMost(cap)
    }

    /** What the player shows. A changed control on a named preset reads "Custom (based on …)". */
    fun label(p: Profile): String = when {
        p.look == Look.CUSTOM -> "Custom"
        p.controls.neutral -> p.look.label
        else -> "Custom (based on ${p.look.label})"
    }

    /** Per-title key: online and download titles are "Show · S1E2"; the part before " · " is the show. */
    fun titleKey(title: String): String = title.substringBefore(" · ").trim().lowercase()

    // ── Live values (read by the shaders every frame) ────────────────────────────────────────────────────────────
    class Live(@Volatile var grade: Grade = NEUTRAL, @Volatile var tuning: Tuning = Tuning())
    val live = Live()

    // ── Saved profiles: one global, optional per-title overrides ────────────────────────────────────────────────
    private const val PREFS = "yu_picture"
    private val _global = MutableStateFlow(Profile()); val global: StateFlow<Profile> = _global.asStateFlow()
    private val _overrides = MutableStateFlow<Map<String, Profile>>(emptyMap()); val overrides: StateFlow<Map<String, Profile>> = _overrides.asStateFlow()
    /** The title playing now (key), or null. */
    private val _title = MutableStateFlow<String?>(null); val title: StateFlow<String?> = _title.asStateFlow()
    @Volatile private var loaded = false

    fun init(c: Context) {
        if (loaded) return
        val sp = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _global.value = runCatching { decode(JSONObject(sp.getString("global", "{}")!!)) }.getOrDefault(Profile())
        _overrides.value = runCatching {
            val o = JSONObject(sp.getString("titles", "{}")!!)
            o.keys().asSequence().associateWith { decode(o.getJSONObject(it)) }
        }.getOrDefault(emptyMap())
        loaded = true
    }

    fun setTitle(key: String?) { _title.value = key }

    /** The profile in force for [key] (its override, else the global one). */
    fun profileFor(key: String?): Profile = key?.let { _overrides.value[it] } ?: _global.value
    fun hasOverride(key: String?): Boolean = key != null && key in _overrides.value

    /** Saves [p] globally, or for [key] when [perTitle]. */
    fun save(c: Context, p: Profile, perTitle: Boolean, key: String?) {
        val x = p.copy(controls = p.controls.clamped(), tuning = p.tuning.clamped())
        if (perTitle && key != null) _overrides.value = _overrides.value + (key to x) else _global.value = x
        persist(c)
    }

    /** Stops using a per-title override; the title follows the global profile again. */
    fun clearOverride(c: Context, key: String?) { if (key == null) return; _overrides.value = _overrides.value - key; persist(c) }

    /** Reset all: global profile back to defaults and every per-title override removed. */
    fun resetAll(c: Context) { _global.value = Profile(); _overrides.value = emptyMap(); persist(c) }

    private fun persist(c: Context) {
        val titles = JSONObject().apply { _overrides.value.forEach { (k, v) -> put(k, encode(v)) } }
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("global", encode(_global.value).toString()).putString("titles", titles.toString()).apply()
    }

    internal fun encode(p: Profile) = JSONObject().apply {
        put("look", p.look.name)
        val c = p.controls
        put("b", c.brightness.toDouble()); put("s", c.saturation.toDouble()); put("c", c.contrast.toDouble()); put("g", c.gamma.toDouble())
        put("h", c.hue.toDouble()); put("sh", c.sharpness.toDouble()); put("f", c.filter.name)
        val t = p.tuning
        put("it", t.iterations); put("th", t.threshold.toDouble()); put("ra", t.range.toDouble()); put("gr", t.grain.toDouble())
    }

    internal fun decode(o: JSONObject): Profile {
        val d = Controls(); val t = Tuning()
        return Profile(
            look = Look.entries.firstOrNull { it.name == o.optString("look") } ?: Look.ORIGINAL,
            controls = Controls(
                o.optDouble("b", d.brightness.toDouble()).toFloat(), o.optDouble("s", d.saturation.toDouble()).toFloat(),
                o.optDouble("c", d.contrast.toDouble()).toFloat(), o.optDouble("g", d.gamma.toDouble()).toFloat(),
                o.optDouble("h", d.hue.toDouble()).toFloat(), o.optDouble("sh", d.sharpness.toDouble()).toFloat(),
                VideoFilter.entries.firstOrNull { it.name == o.optString("f") } ?: VideoFilter.NONE,
            ).clamped(),
            tuning = Tuning(o.optInt("it", t.iterations), o.optDouble("th", t.threshold.toDouble()).toFloat(), o.optDouble("ra", t.range.toDouble()).toFloat(), o.optDouble("gr", t.grain.toDouble()).toFloat()).clamped(),
        )
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTests() { loaded = false; _global.value = Profile(); _overrides.value = emptyMap(); _title.value = null; live.grade = NEUTRAL; live.tuning = Tuning() }

    /** Slider text: "+12", "0", "1.20×", "−30°". */
    fun show(v: Float, kind: Kind): String = when (kind) {
        Kind.SIGNED -> (v * 100).roundToInt().let { if (it > 0) "+$it" else "$it" }
        Kind.FACTOR -> "%.2f×".format(v)
        Kind.DEGREES -> "${v.roundToInt()}°"
        Kind.PERCENT -> "${(v * 100).roundToInt()}%"
    }

    enum class Kind { SIGNED, FACTOR, DEGREES, PERCENT }
}
