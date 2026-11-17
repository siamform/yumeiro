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
import androidx.annotation.OptIn
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.Anime4kEffect
import dev.asfakul.yumeiro.player.VideoPrefs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * G3 P16 (re-audit): the Anime4K pass's real output on the device GPU, for every tier.
 * `UpscaleFreezeDeviceTest` proves playback keeps going with the effect on; this checks what the
 * shader does to pixels. A 64×64 grey frame with a 2-pixel dark vertical line (line art) goes
 * through the same `GlShaderProgram` the player uses, offscreen, and is read back:
 *  - flat areas are unchanged (edge gate);
 *  - the line gets darker and its neighbours not darker (sharper line art);
 *  - nothing leaves the local colour range by more than the clamp (no halos).
 * Log tag YuA4kTest, A4K_CASE / A4K_RESULT.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class Anime4kShaderDeviceTest {
    private fun log(s: String) = Log.i("YuA4kTest", s)
    private val size = 64
    private val bg = 153
    private val ink = 60
    private val lineX = 31..32

    private fun input(): Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until size) for (x in 0 until size) {
            val v = if (x in lineX) ink else bg
            setPixel(x, y, Color.rgb(v, v, v))
        }
    }

    /** Runs the effect for [tier] on [input] and returns the red channel of the middle row. */
    private fun run(tier: VideoPrefs.GpuTier): IntArray {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val display = GlUtil.getDefaultEglDisplay()
        val egl = GlUtil.createEglContext(display)
        try {
            GlUtil.createFocusedPlaceholderEglSurface(egl, display)
            val inTex = GlUtil.createTexture(input())
            val outTex = GlUtil.createTexture(size, size, false)
            val fbo = GlUtil.createFboForTexture(outTex)
            GlUtil.focusFramebufferUsingCurrentContext(fbo, size, size)
            val program = Anime4kEffect(tier).toGlShaderProgram(ctx, false) as BaseGlShaderProgram
            program.configure(size, size)
            program.drawFrame(inTex, 0)
            GLES20.glFinish()
            val buf = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, size, size, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            GlUtil.checkGlError()
            val y = size / 2
            val row = IntArray(size) { x -> buf.get((y * size + x) * 4).toInt() and 0xFF }
            runCatching { program.release() }
            GlUtil.deleteFbo(fbo); GlUtil.deleteTexture(outTex); GlUtil.deleteTexture(inTex)
            return row
        } finally {
            runCatching { GlUtil.destroyEglContext(display, egl) }
        }
    }

    @Test fun anime4kSharpensLineArtAndLeavesFlatAreasAlone() {
        val clamp = (0.015f * 255).toInt() + 1 // the shader's halo clamp, plus rounding
        val lineOut = HashMap<VideoPrefs.GpuTier, Int>()
        for (tier in VideoPrefs.GpuTier.entries) {
            val row = run(tier)
            val inRow = IntArray(size) { if (it in lineX) ink else bg }
            log("A4K_CASE tier=$tier row[26..37]=${row.slice(26..37)} in=${inRow.slice(26..37)}")
            val flat = (4..20).maxOf { abs(row[it] - inRow[it]) }
            assertTrue("$tier: flat area changed by $flat", flat <= 2)
            val line = row[lineX.first]
            assertTrue("$tier: the line is not darker ($line vs $ink)", line < ink)
            val left = row[lineX.first - 1]; val right = row[lineX.last + 1]
            assertTrue("$tier: a neighbour got darker ($left, $right vs $bg)", left >= bg - 1 && right >= bg - 1)
            val worst = (0 until size).maxOf { abs(row[it] - inRow[it]) }
            assertTrue("$tier: a pixel moved $worst, more than the halo clamp $clamp", worst <= clamp)
            lineOut[tier] = line
        }
        log("A4K_CASE line by tier=$lineOut")
        assertTrue("a stronger tier made the line lighter: $lineOut",
            lineOut.getValue(VideoPrefs.GpuTier.HIGH) <= lineOut.getValue(VideoPrefs.GpuTier.MID) + 1 &&
                lineOut.getValue(VideoPrefs.GpuTier.MID) <= lineOut.getValue(VideoPrefs.GpuTier.LOW) + 1)
        log("A4K_RESULT PASS")
    }
}
