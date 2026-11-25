/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A4K-2 (owner item 26, Anime4K Enhancement V2). Beside the existing pass ([Anime4kEffect], "Classic"), which stays the
 * default and is untouched: presets chosen by their real GPU cost, three styles (Zangetsu's Sharpen / De-blur / Denoise
 * split, studied in lib/features/player/shader_presets.dart), a Custom profile whose every control changes the
 * shader's uniforms, and a player control that switches live (VideoPlayerModal → applyEngineLevel).
 *
 * Zangetsu feeds bloc97's multi-pass CNN .glsl files to mpv (`glsl-shaders`). Yumeiro plays through Media3, whose
 * effect graph takes GL ES programs, not mpv hook files; so V2 is one Media3 pass built from the same ideas
 * (restore, soft restore, denoise, clamp highlights) with its cost bounded by [taps].
 */
object Anime4kV2 {
    enum class Engine(val label: String, val detail: String) {
        CLASSIC("Classic", "The original Yumeiro pass with its GPU tier"),
        V2("V2 presets", "Presets, styles and a custom profile; switch them in the player"),
    }

    enum class Preset(val label: String, val detail: String) {
        PERFORMANCE("Performance", "Lightest: 5 samples per pixel, for older phones and TV boxes"),
        BALANCED("Balanced", "Clear lines at low cost; good on most phones"),
        QUALITY("Quality", "Stronger restore with diagonal samples"),
        HIGH_QUALITY("High quality", "Strongest restore and line darkening; needs a recent GPU"),
        CUSTOM("Custom", "Your own strength, lines, denoise and reach"),
    }

    enum class Style(val label: String, val detail: String) {
        SHARPEN("Sharpen", "Restore detail. Best for clean sources"),
        DEBLUR("De-blur", "Softer, wider restore for blurry or upscaled sources"),
        DENOISE("Denoise", "Smooth grain and blocks while keeping lines. For compressed sources"),
    }

    /** Every value here reaches the shader as a uniform. */
    data class Params(val sharpen: Float, val darken: Float, val denoise: Float, val wide: Boolean, val diagonals: Boolean) {
        fun clamped() = copy(sharpen = sharpen.coerceIn(0f, 1f), darken = darken.coerceIn(0f, 1f), denoise = denoise.coerceIn(0f, 1f))
    }

    val DEFAULT_CUSTOM = Params(0.6f, 0.5f, 0f, wide = false, diagonals = false)

    /** Pure → unit-tested. Presets rise in strength and cost; the style decides what the strength is spent on. */
    fun params(preset: Preset, style: Style, custom: Params = DEFAULT_CUSTOM): Params {
        if (preset == Preset.CUSTOM) return custom.clamped()
        val (s, d, diag) = when (preset) {
            Preset.PERFORMANCE -> Triple(0.35f, 0.25f, false)
            Preset.BALANCED -> Triple(0.55f, 0.45f, false)
            Preset.QUALITY -> Triple(0.72f, 0.62f, true)
            else -> Triple(0.88f, 0.8f, true)
        }
        return when (style) {
            Style.SHARPEN -> Params(s, d, 0f, wide = false, diagonals = diag)
            Style.DEBLUR -> Params(s * 0.85f, d * 0.5f, 0f, wide = preset != Preset.PERFORMANCE, diagonals = diag)
            Style.DENOISE -> Params(s * 0.35f, d * 0.3f, (0.35f + (s - 0.35f) * 1.2f).coerceIn(0f, 1f), wide = false, diagonals = diag)
        }
    }

    /** Texture reads per output pixel (the pass's cost). */
    fun taps(p: Params): Int = 1 + 4 + (if (p.diagonals) 4 else 0)

    /** What the player shows for the active profile. */
    fun label(engine: Engine, on: Boolean, preset: Preset, style: Style, tier: VideoPrefs.GpuTier): String = when {
        !on -> "Off"
        engine == Engine.CLASSIC -> "Classic · ${tier.label}"
        preset == Preset.CUSTOM -> "Custom"
        else -> "${preset.label} · ${style.label}"
    }

    // ── Saved choice (same preferences file as VideoPrefs; Classic stays the default) ──────────────────────────
    private const val PREFS = "yu_video_prefs"
    private val _engine = MutableStateFlow(Engine.CLASSIC); val engine: StateFlow<Engine> = _engine.asStateFlow()
    private val _preset = MutableStateFlow(Preset.BALANCED); val preset: StateFlow<Preset> = _preset.asStateFlow()
    private val _style = MutableStateFlow(Style.SHARPEN); val style: StateFlow<Style> = _style.asStateFlow()
    private val _custom = MutableStateFlow(DEFAULT_CUSTOM); val custom: StateFlow<Params> = _custom.asStateFlow()
    @Volatile private var loaded = false

    fun init(c: Context) {
        if (loaded) return
        val p = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _engine.value = Engine.entries.firstOrNull { it.name == p.getString("a4k2_engine", null) } ?: Engine.CLASSIC
        _preset.value = Preset.entries.firstOrNull { it.name == p.getString("a4k2_preset", null) } ?: Preset.BALANCED
        _style.value = Style.entries.firstOrNull { it.name == p.getString("a4k2_style", null) } ?: Style.SHARPEN
        _custom.value = Params(
            p.getFloat("a4k2_sharpen", DEFAULT_CUSTOM.sharpen), p.getFloat("a4k2_darken", DEFAULT_CUSTOM.darken),
            p.getFloat("a4k2_denoise", DEFAULT_CUSTOM.denoise), p.getBoolean("a4k2_wide", false), p.getBoolean("a4k2_diag", false),
        ).clamped()
        loaded = true
    }

    private fun edit(c: Context, b: android.content.SharedPreferences.Editor.() -> Unit) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply(b).apply()

    fun setEngine(c: Context, v: Engine) { _engine.value = v; edit(c) { putString("a4k2_engine", v.name) } }
    fun setPreset(c: Context, v: Preset) { _preset.value = v; edit(c) { putString("a4k2_preset", v.name) } }
    fun setStyle(c: Context, v: Style) { _style.value = v; edit(c) { putString("a4k2_style", v.name) } }
    fun setCustom(c: Context, v: Params) {
        val x = v.clamped(); _custom.value = x
        edit(c) { putFloat("a4k2_sharpen", x.sharpen); putFloat("a4k2_darken", x.darken); putFloat("a4k2_denoise", x.denoise); putBoolean("a4k2_wide", x.wide); putBoolean("a4k2_diag", x.diagonals) }
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTests() { loaded = false; _engine.value = Engine.CLASSIC; _preset.value = Preset.BALANCED; _style.value = Style.SHARPEN; _custom.value = DEFAULT_CUSTOM }

    /** The enhancement pass for the current choice: V2 when chosen, otherwise the Classic pass exactly as before. */
    @OptIn(UnstableApi::class)
    fun currentEffect(): GlEffect =
        if (_engine.value == Engine.V2) Effect(params(_preset.value, _style.value, _custom.value)) else Anime4kEffect(VideoPrefs.gpuTier.value)

    @OptIn(UnstableApi::class)
    class Effect(val params: Params) : GlEffect {
        override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Program(params, useHdr)
    }

    private const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""

    /*
     * One pass: (1) optional edge-preserving denoise — neighbours are averaged in with a weight that falls with their
     * luma difference, so grain is smoothed and lines are not; (2) restore — unsharp mask at 1 or 2 texels ("wide",
     * for soft sources), gated to edges so flat areas stay untouched; (3) line darkening; (4) clamp to the local range
     * (Anime4K's Clamp_Highlights idea) so no halos or ringing appear.
     */
    private const val FRAGMENT = """
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform float uSharpen;
uniform float uDarken;
uniform float uDenoise;
uniform float uReach;
uniform float uDiagonals;
varying vec2 vTexSamplingCoord;
float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
float near(vec3 a, vec3 c, float sigma) { float d = luma(a) - luma(c); return exp(-(d * d) / (2.0 * sigma * sigma)); }
void main() {
  vec2 p = vTexSamplingCoord;
  vec2 t = uTexel * uReach;
  vec4 c4 = texture2D(uTexSampler, p);
  vec3 c = c4.rgb;
  vec3 n = texture2D(uTexSampler, p + vec2(0.0, -t.y)).rgb;
  vec3 s = texture2D(uTexSampler, p + vec2(0.0, t.y)).rgb;
  vec3 e = texture2D(uTexSampler, p + vec2(t.x, 0.0)).rgb;
  vec3 w = texture2D(uTexSampler, p + vec2(-t.x, 0.0)).rgb;
  vec3 mn = min(min(min(n, s), min(e, w)), c);
  vec3 mx = max(max(max(n, s), max(e, w)), c);
  vec3 blur = (n + s + e + w) * 0.25;
  vec3 a = c; vec3 b = c; vec3 d = c; vec3 f = c;
  if (uDiagonals > 0.5) {
    a = texture2D(uTexSampler, p + vec2(t.x, t.y)).rgb;
    b = texture2D(uTexSampler, p + vec2(-t.x, t.y)).rgb;
    d = texture2D(uTexSampler, p + vec2(t.x, -t.y)).rgb;
    f = texture2D(uTexSampler, p + vec2(-t.x, -t.y)).rgb;
    blur = blur * 0.6 + (a + b + d + f) * 0.1;
    mn = min(mn, min(min(a, b), min(d, f)));
    mx = max(mx, max(max(a, b), max(d, f)));
  }
  vec3 base = c;
  if (uDenoise > 0.001) {
    float sigma = 0.02 + 0.08 * uDenoise;
    float wn = near(n, c, sigma); float ws = near(s, c, sigma); float we = near(e, c, sigma); float ww = near(w, c, sigma);
    vec3 acc = c + n * wn + s * ws + e * we + w * ww;
    float tot = 1.0 + wn + ws + we + ww;
    if (uDiagonals > 0.5) {
      float wa = near(a, c, sigma); float wb = near(b, c, sigma); float wd = near(d, c, sigma); float wf = near(f, c, sigma);
      acc += a * wa + b * wb + d * wd + f * wf; tot += wa + wb + wd + wf;
    }
    base = mix(c, acc / tot, uDenoise);
  }
  float edge = abs(luma(n) - luma(s)) + abs(luma(e) - luma(w));
  float gate = smoothstep(0.02, 0.16, edge);
  vec3 o = base + (base - blur) * (uSharpen * 2.0 * gate);
  float line = max(0.0, luma(blur) - luma(base));
  o -= vec3(line * uDarken * gate);
  o = clamp(o, mn - 0.015, mx + 0.015);
  gl_FragColor = vec4(clamp(o, 0.0, 1.0), c4.a);
}
"""

    @OptIn(UnstableApi::class)
    private class Program(private val w: Params, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
        private val gl: GlProgram = try {
            GlProgram(VERTEX, FRAGMENT).apply {
                setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
        private var texel = floatArrayOf(0f, 0f)

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            texel = floatArrayOf(1f / inputWidth.coerceAtLeast(1), 1f / inputHeight.coerceAtLeast(1))
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                gl.use()
                gl.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                gl.setFloatsUniform("uTexel", texel)
                gl.setFloatUniform("uSharpen", w.sharpen)
                gl.setFloatUniform("uDarken", w.darken)
                gl.setFloatUniform("uDenoise", w.denoise)
                gl.setFloatUniform("uReach", if (w.wide) 2f else 1f)
                gl.setFloatUniform("uDiagonals", if (w.diagonals) 1f else 0f)
                gl.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GlUtil.checkGlError()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try { gl.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
        }
    }
}
