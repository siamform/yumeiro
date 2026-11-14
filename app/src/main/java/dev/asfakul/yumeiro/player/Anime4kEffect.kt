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
 * Round 13: Settings → Quality and audio → Anime4K enhancement.
 *
 * An Anime4K-style pass that runs on the GPU for every frame: it sharpens line art, darkens and
 * thins outlines and clamps the result to the local colour range so no halos appear. It is made
 * for clean 1080p anime; on noisy or low-bitrate sources the edge gate keeps flat areas untouched.
 * The GPU tier only changes how strong the pass is, so even the High-end tier is one draw call.
 */
@OptIn(UnstableApi::class)
class Anime4kEffect(private val tier: VideoPrefs.GpuTier) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = Program(tier, useHdr)

    /** Strength of the detail boost and of the line darkening for [tier]. Pure → unit-tested. */
    data class Weights(val sharpen: Float, val darken: Float, val diagonals: Boolean)

    companion object {
        fun weights(tier: VideoPrefs.GpuTier): Weights = when (tier) {
            VideoPrefs.GpuTier.LOW -> Weights(0.35f, 0.25f, false)
            VideoPrefs.GpuTier.MID -> Weights(0.6f, 0.5f, false)
            VideoPrefs.GpuTier.HIGH -> Weights(0.85f, 0.8f, true)
        }

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
uniform float uSharpen;
uniform float uDarken;
uniform float uDiagonals;
varying vec2 vTexSamplingCoord;
float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
void main() {
  vec2 p = vTexSamplingCoord;
  vec2 t = uTexel;
  vec4 c = texture2D(uTexSampler, p);
  vec3 n = texture2D(uTexSampler, p + vec2(0.0, -t.y)).rgb;
  vec3 s = texture2D(uTexSampler, p + vec2(0.0, t.y)).rgb;
  vec3 e = texture2D(uTexSampler, p + vec2(t.x, 0.0)).rgb;
  vec3 w = texture2D(uTexSampler, p + vec2(-t.x, 0.0)).rgb;
  vec3 blur = (n + s + e + w) * 0.25;
  vec3 mn = min(min(min(n, s), min(e, w)), c.rgb);
  vec3 mx = max(max(max(n, s), max(e, w)), c.rgb);
  if (uDiagonals > 0.5) {
    vec3 a = texture2D(uTexSampler, p + vec2(t.x, t.y)).rgb;
    vec3 b = texture2D(uTexSampler, p + vec2(-t.x, t.y)).rgb;
    vec3 d = texture2D(uTexSampler, p + vec2(t.x, -t.y)).rgb;
    vec3 f = texture2D(uTexSampler, p + vec2(-t.x, -t.y)).rgb;
    blur = blur * 0.6 + (a + b + d + f) * 0.1;
    mn = min(mn, min(min(a, b), min(d, f)));
    mx = max(mx, max(max(a, b), max(d, f)));
  }
  float edge = abs(luma(n) - luma(s)) + abs(luma(e) - luma(w));
  float gate = smoothstep(0.02, 0.16, edge);
  vec3 o = c.rgb + (c.rgb - blur) * (uSharpen * 2.0 * gate);
  float line = max(0.0, luma(blur) - luma(c.rgb));
  o -= vec3(line * uDarken * gate);
  o = clamp(o, mn - 0.015, mx + 0.015);
  gl_FragColor = vec4(clamp(o, 0.0, 1.0), c.a);
}
"""
    }

    private class Program(tier: VideoPrefs.GpuTier, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
        private val w = weights(tier)
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
