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

/**
 * A4K-3: one GPU pass for the picture preset, the picture controls, the video filter and grain (see [PictureLook] for
 * the order). One texture read per pixel, five when Sharpness is above 0. Values come from [source] every frame, so
 * moving a slider never rebuilds the graph. HDR frames are copied through untouched: the controls are written for
 * SDR (0..1) values and would clip HDR highlights.
 */
@OptIn(UnstableApi::class)
class PictureGrade(private val source: () -> PictureLook.Grade = { PictureLook.live.grade }) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Program(source, useHdr)

    private class Program(private val source: () -> PictureLook.Grade, private val hdr: Boolean) : BaseGlShaderProgram(hdr, 1) {
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
            val g = source()
            try {
                gl.use()
                gl.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                gl.setFloatsUniform("uTexel", texel)
                gl.setFloatUniform("uHdr", if (hdr) 1f else 0f)
                gl.setFloatUniform("uBright", g.brightness)
                gl.setFloatUniform("uContrast", g.contrast)
                gl.setFloatUniform("uSat", g.saturation)
                gl.setFloatUniform("uGamma", g.gamma)
                gl.setFloatUniform("uHue", Math.toRadians(g.hue.toDouble()).toFloat())
                gl.setFloatUniform("uSharp", g.sharpness)
                gl.setFloatUniform("uTemp", g.temperature)
                gl.setFloatUniform("uTint", g.tint)
                gl.setFloatUniform("uLift", g.lift)
                gl.setFloatUniform("uBlack", g.black)
                gl.setFloatUniform("uVib", g.vibrance)
                gl.setFloatUniform("uFilter", g.filter.ordinal.toFloat())
                gl.setFloatUniform("uGrain", g.grain)
                // Grain changes every frame (seeded by the frame time) so it moves like film, not a fixed screen pattern.
                gl.setFloatUniform("uSeed", ((presentationTimeUs / 1000L) % 9973L).toFloat() / 9973f)
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

    companion object {
        private const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""

        private const val FRAGMENT = """
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform float uHdr;
uniform float uBright;
uniform float uContrast;
uniform float uSat;
uniform float uGamma;
uniform float uHue;
uniform float uSharp;
uniform float uTemp;
uniform float uTint;
uniform float uLift;
uniform float uBlack;
uniform float uVib;
uniform float uFilter;
uniform float uGrain;
uniform float uSeed;
varying vec2 vTexSamplingCoord;
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void main() {
  vec4 c4 = texture2D(uTexSampler, vTexSamplingCoord);
  if (uHdr > 0.5) { gl_FragColor = c4; return; }
  vec3 c = c4.rgb;
  if (uSharp > 0.001) {
    vec3 n = texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, -uTexel.y)).rgb;
    vec3 s = texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, uTexel.y)).rgb;
    vec3 e = texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexel.x, 0.0)).rgb;
    vec3 w = texture2D(uTexSampler, vTexSamplingCoord + vec2(-uTexel.x, 0.0)).rgb;
    vec3 mn = min(min(min(n, s), min(e, w)), c);
    vec3 mx = max(max(max(n, s), max(e, w)), c);
    c = clamp(c + (c - (n + s + e + w) * 0.25) * (uSharp * 1.5), mn, mx);
  }
  // White balance: warm pushes red up and blue down; tint moves green against magenta.
  c *= vec3(1.0 + 0.08 * uTemp, 1.0 + 0.06 * uTint, 1.0 - 0.08 * uTemp);
  c += vec3(uBright);
  c = (c - 0.5) * uContrast + 0.5;
  // Black point (crush) then shadow lift.
  if (uBlack > 0.0001) c = max(c - vec3(uBlack), 0.0) / (1.0 - uBlack);
  if (uLift > 0.0001) c = c * (1.0 - uLift) + vec3(uLift);
  if (abs(uGamma - 1.0) > 0.001) c = pow(max(c, 0.0), vec3(1.0 / max(uGamma, 0.01)));
  float y = luma(c);
  vec3 grey = vec3(y);
  float chroma = max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b);
  float sat = uSat * (1.0 + uVib * (1.0 - clamp(chroma * 2.0, 0.0, 1.0)));
  c = grey + (c - grey) * sat;
  if (abs(uHue) > 0.0001) {
    // Rotate around the grey axis (Rodrigues), which keeps luma close to constant.
    float cs = cos(uHue); float sn = sin(uHue);
    vec3 k = vec3(0.57735);
    c = c * cs + cross(k, c) * sn + k * dot(k, c) * (1.0 - cs);
  }
  if (uFilter > 0.5 && uFilter < 1.5) {
    c = vec3(luma(c));
  } else if (uFilter > 1.5 && uFilter < 2.5) {
    float l = luma(c);
    c = vec3(l * 1.07, l * 0.87, l * 0.66) + vec3(0.03, 0.02, 0.0);
  } else if (uFilter > 2.5) {
    vec3 fade = c * 0.85 + vec3(0.06, 0.05, 0.02);
    c = mix(fade, vec3(luma(fade)), 0.25) * vec3(1.04, 1.0, 0.9);
  }
  if (uGrain > 0.001) {
    float n = hash(vTexSamplingCoord * 1000.0 + uSeed * 37.0) - 0.5;
    // Strongest in mid-tones, where film grain shows; nearly none in pure black or white.
    float m = 1.0 - abs(luma(c) - 0.5) * 1.6;
    c += vec3(n * uGrain * 0.12 * max(m, 0.15));
  }
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), c4.a);
}
"""
    }
}

/**
 * A4K-3: the enhancement part of every player's effect list, in the defined order (Anime4K × iterations, then the
 * grade). Also publishes the live values the shaders read. [previewing] keeps the grade pass in while the sheet is
 * open, so the first slider move shows at once instead of waiting for a graph rebuild.
 */
@OptIn(UnstableApi::class)
object PictureChain {
    /** Whether the last built list had the grade pass (so the sheet knows when a rebuild is needed). */
    @Volatile var lastHadGrade = false
    fun effects(anime4k: Boolean, hdr: Boolean, previewing: Boolean = false, withAnime4k: Boolean = true): List<androidx.media3.common.Effect> {
        val heat = HeatGuard.level.value
        val p = PictureLook.profileFor(PictureLook.title.value)
        PictureLook.live.tuning = p.tuning.clamped()
        PictureLook.live.grade = PictureLook.resolve(p, heat)
        // HDR: nothing here is applied, so HDR colour and highlights stay exactly as mastered.
        if (hdr) { lastHadGrade = false; return emptyList() }
        return buildList {
            if (withAnime4k && anime4k && HeatGuard.allowGpuUpscale(heat)) {
                val n = PictureLook.allowedIterations(p.tuning.iterations, VideoPrefs.gpuTier.value, heat)
                repeat(n) { add(Anime4kV2.currentEffect()) }
            }
            val grade = !PictureLook.live.grade.neutral || previewing
            lastHadGrade = grade
            if (grade) add(PictureGrade())
        }
    }

    /** True when the player's current video track is HDR (PQ or HLG). */
    fun isHdr(player: androidx.media3.exoplayer.ExoPlayer): Boolean =
        player.videoFormat?.colorInfo?.let { androidx.media3.common.ColorInfo.isTransferHdr(it) } == true
}
