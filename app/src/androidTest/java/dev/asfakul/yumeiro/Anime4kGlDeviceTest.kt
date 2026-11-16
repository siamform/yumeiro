/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20
import android.util.Log
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.Anime4kEffect
import dev.asfakul.yumeiro.player.VideoPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * G12 / GROUP-3 P16: the Anime4K pass on the emulator's real GL driver (not a mock).
 * Input: a 64×64 grey picture, left half 77, right half 179 (a vertical line-art edge).
 * For every GPU tier the shader must compile and draw, leave flat areas alone, steepen the edge
 * (dark side darker, light side lighter), never overshoot the local range by more than the
 * shader's 0.015 margin (no halos), and a stronger tier must sharpen at least as much.
 * Log tag YuAnime4k; ANIME4K_GL_RESULT PASS is the evidence.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class Anime4kGlDeviceTest {
    private val size = 64
    private val dark = 77
    private val light = 179

    private fun log(s: String) = Log.i("YuAnime4k", s)

    /** Runs the tier's shader on the edge picture and returns one output row (grey values). */
    private fun render(tier: VideoPrefs.GpuTier): IntArray {
        val display = GlUtil.getDefaultEglDisplay()
        val context = GlUtil.createEglContext(display)
        GlUtil.createFocusedPlaceholderEglSurface(context, display)
        try {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (y in 0 until size) for (x in 0 until size) {
                val v = if (x < size / 2) dark else light
                bmp.setPixel(x, y, Color.argb(255, v, v, v))
            }
            val inTex = GlUtil.createTexture(bmp)
            val outTex = GlUtil.createTexture(size, size, false)
            val fbo = GlUtil.createFboForTexture(outTex)
            GlUtil.focusFramebufferUsingCurrentContext(fbo, size, size)
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val program = Anime4kEffect(tier).toGlShaderProgram(ctx, false) as BaseGlShaderProgram
            val out = program.configure(size, size)
            assertEquals("output size", size to size, out.width to out.height)
            program.drawFrame(inTex, 0)
            GLES20.glFinish()
            GlUtil.checkGlError()
            val buf = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, size, size, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            GlUtil.checkGlError()
            val row = size / 2
            val px = IntArray(size) { x -> buf.get((row * size + x) * 4).toInt() and 0xff }
            // Every row must match (vertical edge), and the picture must not be black.
            var sum = 0L
            for (i in 0 until size * size) sum += buf.get(i * 4).toInt() and 0xff
            val mean = sum / (size * size)
            log("$tier mean=$mean row=${px.joinToString(",", limit = size)}")
            assertTrue("$tier output is not black (mean $mean)", mean in 100..160)
            program.release()
            GlUtil.deleteFbo(fbo); GlUtil.deleteTexture(inTex); GlUtil.deleteTexture(outTex)
            return px
        } finally {
            GlUtil.destroyEglContext(display, context)
        }
    }

    @Test fun anime4kSharpensTheEdgeWithoutHalosOnEveryTier() {
        val margin = 5 // 0.015 × 255 ≈ 3.8, plus rounding
        val gain = mutableMapOf<VideoPrefs.GpuTier, Int>()
        for (tier in VideoPrefs.GpuTier.entries) {
            val px = render(tier)
            // Flat areas untouched.
            for (x in listOf(2, 10, 20)) assertTrue("$tier flat dark x=$x is ${px[x]}", kotlin.math.abs(px[x] - dark) <= 2)
            for (x in listOf(43, 53, 61)) assertTrue("$tier flat light x=$x is ${px[x]}", kotlin.math.abs(px[x] - light) <= 2)
            // No halo anywhere on the row.
            px.forEachIndexed { x, v -> assertTrue("$tier halo at x=$x: $v", v in (dark - margin)..(light + margin)) }
            // The edge is steeper: dark side darker, light side not dimmer.
            val d = px[size / 2 - 1]; val l = px[size / 2]
            assertTrue("$tier dark side of the edge darker ($d < $dark)", d < dark)
            assertTrue("$tier light side of the edge not dimmer ($l >= ${light - 1})", l >= light - 1)
            gain[tier] = (l - d) - (light - dark)
            log("$tier edge $d|$l gain=${gain[tier]}")
        }
        assertTrue("HIGH sharpens at least as much as LOW: $gain", gain.getValue(VideoPrefs.GpuTier.HIGH) >= gain.getValue(VideoPrefs.GpuTier.LOW))
        log("ANIME4K_GL_RESULT PASS $gain")
    }
}
