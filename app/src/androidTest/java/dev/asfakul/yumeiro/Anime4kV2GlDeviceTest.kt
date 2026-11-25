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
import dev.asfakul.yumeiro.player.Anime4kV2
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A4K-2 (item 26): every V2 preset × style on the emulator's real GL driver, plus timing.
 * Picture: 64×64, left half 77 with ±12 grain (checkerboard), right half 179 (a line-art edge).
 * Each profile must compile and draw, never halo past the local range, keep the edge at least as steep;
 * Denoise must reduce the grain; a timed loop reports ms per frame at 1280×720. Log tag YuAnime4k2;
 * ANIME4K2_GL_RESULT PASS is the evidence.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class Anime4kV2GlDeviceTest {
    private val size = 64
    private val dark = 77
    private val light = 179
    private val grain = 12

    private fun log(s: String) = Log.w("YuAnime4k2", s)

    private fun input(x: Int, y: Int): Int = if (x < size / 2) dark + (if ((x + y) % 2 == 0) grain else -grain) else light

    private fun <T> withGl(block: () -> T): T {
        val display = GlUtil.getDefaultEglDisplay()
        val context = GlUtil.createEglContext(display)
        GlUtil.createFocusedPlaceholderEglSurface(context, display)
        try { return block() } finally { GlUtil.destroyEglContext(display, context) }
    }

    private fun render(p: Anime4kV2.Params): Array<IntArray> = withGl {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) for (x in 0 until size) { val v = input(x, y); bmp.setPixel(x, y, Color.argb(255, v, v, v)) }
        val inTex = GlUtil.createTexture(bmp)
        val outTex = GlUtil.createTexture(size, size, false)
        val fbo = GlUtil.createFboForTexture(outTex)
        GlUtil.focusFramebufferUsingCurrentContext(fbo, size, size)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val program = Anime4kV2.Effect(p).toGlShaderProgram(ctx, false) as BaseGlShaderProgram
        program.configure(size, size)
        program.drawFrame(inTex, 0)
        GLES20.glFinish(); GlUtil.checkGlError()
        val buf = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, size, size, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        program.release(); GlUtil.deleteFbo(fbo); GlUtil.deleteTexture(inTex); GlUtil.deleteTexture(outTex)
        // glReadPixels rows are bottom-up; the picture is symmetric in y apart from the grain phase, so map back.
        Array(size) { r -> IntArray(size) { x -> buf.get(((size - 1 - r) * size + x) * 4).toInt() and 0xff } }
    }

    /** Mean absolute deviation from the flat dark level inside the grainy half (away from the edge). */
    private fun grainOf(img: Array<IntArray>): Double {
        var s = 0.0; var n = 0
        for (y in 4 until size - 4) for (x in 4 until size / 2 - 6) { s += kotlin.math.abs(img[y][x] - dark); n++ }
        return s / n
    }

    private fun msPerFrame(p: Anime4kV2.Params): Double = withGl {
        val w = 1280; val h = 720
        val inTex = GlUtil.createTexture(w, h, false)
        val outTex = GlUtil.createTexture(w, h, false)
        val fbo = GlUtil.createFboForTexture(outTex)
        GlUtil.focusFramebufferUsingCurrentContext(fbo, w, h)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val program = Anime4kV2.Effect(p).toGlShaderProgram(ctx, false) as BaseGlShaderProgram
        program.configure(w, h)
        repeat(5) { program.drawFrame(inTex, 0) }; GLES20.glFinish()
        val frames = 60
        val t0 = System.nanoTime()
        repeat(frames) { program.drawFrame(inTex, it.toLong()) }
        GLES20.glFinish()
        val ms = (System.nanoTime() - t0) / 1e6 / frames
        program.release(); GlUtil.deleteFbo(fbo); GlUtil.deleteTexture(inTex); GlUtil.deleteTexture(outTex)
        ms
    }

    @Test fun everyPresetAndStyleRendersCleanly() {
        val inputGrain = grain.toDouble()
        val report = StringBuilder()
        val profiles = buildList {
            for (pr in Anime4kV2.Preset.entries) {
                if (pr == Anime4kV2.Preset.CUSTOM) add("CUSTOM" to Anime4kV2.Params(0.6f, 0.5f, 0.5f, wide = true, diagonals = true))
                else for (st in Anime4kV2.Style.entries) add("${pr.name}/${st.name}" to Anime4kV2.params(pr, st))
            }
        }
        for ((name, p) in profiles) {
            val img = render(p)
            val row = img[size / 2]
            for (y in 0 until size) for (x in 0 until size) {
                val v = img[y][x]
                assertTrue("$name halo at $x,$y: $v", v in (dark - grain - 6)..(light + 6))
            }
            // Flat light half stays flat.
            for (x in listOf(43, 53, 61)) assertTrue("$name flat light x=$x is ${row[x]}", kotlin.math.abs(row[x] - light) <= 3)
            val g = grainOf(img)
            val edge = row[size / 2] - row[size / 2 - 1]
            if (p.denoise >= 0.3f) assertTrue("$name denoise reduces grain ($g < $inputGrain)", g < inputGrain * 0.85)
            val ms = msPerFrame(p)
            report.append("$name taps=${Anime4kV2.taps(p)} grain=${"%.1f".format(g)} edge=$edge ms720p=${"%.2f".format(ms)}; ")
            log("$name $p grain=$g edge=$edge ms720p=$ms")
        }
        log("ANIME4K2_GL_RESULT PASS $report")
    }
}
