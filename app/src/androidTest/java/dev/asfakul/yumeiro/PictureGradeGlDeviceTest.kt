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
import androidx.media3.effect.GlEffect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.Anime4kEffect
import dev.asfakul.yumeiro.player.Anime4kV2
import dev.asfakul.yumeiro.player.PictureGrade
import dev.asfakul.yumeiro.player.PictureLook
import dev.asfakul.yumeiro.player.PictureLook.Look
import dev.asfakul.yumeiro.player.VideoPrefs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * A4K-3 on the emulator's real GL driver: every picture preset, the picture controls, the HDR passthrough of the grade
 * and of both Anime4K engines, the Anime4K filter section (threshold / range) and ms per frame at 720p and 1080p.
 * Picture: 64×64 colour ramp (red across, green down, blue fixed 0.5) with a hard light/dark edge in the middle.
 * Log tag YuPicture; PICTURE_GL_RESULT PASS is the evidence.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PictureGradeGlDeviceTest {
    private val size = 64
    private fun log(s: String) = Log.w("YuPicture", s)

    @After fun reset() = PictureLook.resetForTests()

    private fun input(x: Int, y: Int): Int {
        val r = 40 + x * 160 / size; val g = 40 + y * 160 / size
        val edge = if (x in 28..35) 0 else if (x > 35) 30 else -30
        return Color.argb(255, (r + edge).coerceIn(0, 255), (g + edge).coerceIn(0, 255), 128)
    }

    private fun <T> withGl(block: () -> T): T {
        val display = GlUtil.getDefaultEglDisplay()
        val context = GlUtil.createEglContext(display)
        GlUtil.createFocusedPlaceholderEglSurface(context, display)
        try { return block() } finally { GlUtil.destroyEglContext(display, context) }
    }

    /** Returns [y][x] = RGB ints, top-down, as the picture was built. */
    private fun render(effect: GlEffect, hdr: Boolean = false): Array<Array<IntArray>> = withGl {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) for (x in 0 until size) bmp.setPixel(x, y, input(x, y))
        val inTex = GlUtil.createTexture(bmp)
        val outTex = GlUtil.createTexture(size, size, false)
        val fbo = GlUtil.createFboForTexture(outTex)
        GlUtil.focusFramebufferUsingCurrentContext(fbo, size, size)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val program = effect.toGlShaderProgram(ctx, hdr) as BaseGlShaderProgram
        program.configure(size, size)
        program.drawFrame(inTex, 0)
        GLES20.glFinish(); GlUtil.checkGlError()
        val buf = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, size, size, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        program.release(); GlUtil.deleteFbo(fbo); GlUtil.deleteTexture(inTex); GlUtil.deleteTexture(outTex)
        // GlUtil.createTexture(bitmap) uploads row 0 first and glReadPixels reads row 0 first, so rows line up.
        Array(size) { y -> Array(size) { x -> IntArray(3) { c -> buf.get((y * size + x) * 4 + c).toInt() and 0xff } } }
    }

    private fun src(x: Int, y: Int) = input(x, y).let { intArrayOf(Color.red(it), Color.green(it), Color.blue(it)) }

    private fun maxDiffFromInput(img: Array<Array<IntArray>>): Int {
        var m = 0
        for (y in 0 until size) for (x in 0 until size) { val s = src(x, y); for (c in 0..2) m = maxOf(m, abs(img[y][x][c] - s[c])) }
        return m
    }

    private fun mean(img: Array<Array<IntArray>>, c: Int): Double = img.sumOf { row -> row.sumOf { it[c].toDouble() } } / (size * size)
    private fun meanSrc(c: Int): Double { var s = 0.0; for (y in 0 until size) for (x in 0 until size) s += src(x, y)[c]; return s / (size * size) }

    private fun grade(g: PictureLook.Grade) = PictureGrade { g }
    private fun look(l: Look) = grade(PictureLook.resolve(PictureLook.Profile(l)))

    @Test fun originalIsTheSourcePicture() {
        val d = maxDiffFromInput(render(look(Look.ORIGINAL)))
        log("ORIGINAL maxDiff=$d")
        assertTrue("Original must not change the picture (max diff $d)", d <= 1)
    }

    @Test fun everyPresetRendersAndMovesThePictureTheRightWay() {
        val report = StringBuilder()
        val named = Look.entries.filter { it != Look.ORIGINAL && it != Look.CUSTOM }
        val r0 = meanSrc(0); val b0 = meanSrc(2)
        for (l in named) {
            val img = render(look(l))
            val d = maxDiffFromInput(img)
            val r = mean(img, 0); val g = mean(img, 1); val b = mean(img, 2)
            report.append("${l.name} diff=$d rgb=${"%.0f/%.0f/%.0f".format(r, g, b)}; ")
            assertTrue("${l.name} must change the picture (max diff $d)", d >= 4)
            // Never a broken (black or NaN) frame.
            assertTrue("${l.name} not black", r + g + b > 60)
            when (l) {
                Look.WARM -> assertTrue("Warm: red up / blue down", r - b > r0 - b0 + 4)
                Look.COOL -> assertTrue("Cool: blue up / red down", r - b < r0 - b0 - 4)
                Look.NIGHT -> assertTrue("Night: dimmer", (r + g + b) < (r0 + meanSrc(1) + b0) - 6)
                Look.DEEP_BLACK -> assertTrue("Deep Black: darkest pixel darker", img[8][2][1] < src(2, 8)[1])
                else -> Unit
            }
        }
        log("PRESETS $report")
    }

    @Test fun pictureControlsDoWhatTheySay() {
        val base = mean(render(look(Look.ORIGINAL)), 1)
        assertTrue("brightness +", mean(render(grade(PictureLook.Grade(brightness = 0.2f))), 1) > base + 20)
        assertTrue("brightness -", mean(render(grade(PictureLook.Grade(brightness = -0.2f))), 1) < base - 20)
        val mono = render(grade(PictureLook.Grade(filter = PictureLook.VideoFilter.MONO)))
        for (y in 0 until size step 7) for (x in 0 until size step 7) {
            val p = mono[y][x]; assertTrue("mono grey at $x,$y ${p.toList()}", abs(p[0] - p[1]) <= 1 && abs(p[1] - p[2]) <= 1)
        }
        val desat = render(grade(PictureLook.Grade(saturation = 0f)))
        assertTrue("saturation 0 is grey", abs(desat[40][40][0] - desat[40][40][2]) <= 2)
        val hue = render(grade(PictureLook.Grade(hue = 120f)))
        assertTrue("hue 120 moves colour", maxDiffFromInput(hue) > 20)
        val sharp = render(grade(PictureLook.Grade(sharpness = 1f)))
        // Sharpening stays inside the neighbourhood range (no halo) and steepens the edge.
        val row = 32
        // The clamp uses the 4 direct neighbours (up / down too), so the bound is the 3×3 neighbourhood.
        for (y in 1 until size - 1) for (x in 1 until size - 1) for (c in 0..2) {
            var lo = 255; var hi = 0
            for (dy in -1..1) for (dx in -1..1) { val v = src(x + dx, y + dy)[c]; lo = minOf(lo, v); hi = maxOf(hi, v) }
            assertTrue("sharpen halo at $x,$y c$c: ${sharp[y][x][c]} not in $lo..$hi", sharp[y][x][c] in (lo - 1)..(hi + 1))
        }
        assertTrue("sharpen steeper edge", (sharp[row][36][1] - sharp[row][35][1]) >= (src(36, row)[1] - src(35, row)[1]))
        val grain = render(grade(PictureLook.Grade(grain = 1f)))
        assertTrue("grain adds texture", maxDiffFromInput(grain) >= 3)
        log("CONTROLS ok")
    }

    @Test fun hdrIsPassedThroughByTheGradeAndBothAnime4kEngines() {
        val loud = grade(PictureLook.resolve(PictureLook.Profile(Look.NEON, PictureLook.Controls(sharpness = 1f, filter = PictureLook.VideoFilter.SEPIA))))
        val dG = maxDiffFromInput(render(loud, hdr = true))
        val dV2 = maxDiffFromInput(render(Anime4kV2.Effect(Anime4kV2.params(Anime4kV2.Preset.HIGH_QUALITY, Anime4kV2.Style.SHARPEN)), hdr = true))
        val dC = maxDiffFromInput(render(Anime4kEffect(VideoPrefs.GpuTier.HIGH), hdr = true))
        log("HDR grade=$dG v2=$dV2 classic=$dC")
        assertTrue("grade must not touch HDR ($dG)", dG <= 1)
        assertTrue("Anime4K V2 must not touch HDR ($dV2)", dV2 <= 1)
        assertTrue("Anime4K Classic must not touch HDR ($dC)", dC <= 1)
    }

    @Test fun anime4kThresholdAndRangeChangeTheResult() {
        val p = Anime4kV2.params(Anime4kV2.Preset.HIGH_QUALITY, Anime4kV2.Style.SHARPEN)
        fun at(t: PictureLook.Tuning): Int { PictureLook.live.tuning = t; return maxDiffFromInput(render(Anime4kV2.Effect(p))) }
        val def = at(PictureLook.Tuning())
        val gated = at(PictureLook.Tuning(threshold = 1f, range = 0f))
        val loose = at(PictureLook.Tuning(threshold = 0f, range = 1f))
        log("TUNING default=$def gated=$gated loose=$loose")
        assertTrue("strict threshold + no range changes less than default ($gated <= $def)", gated <= def)
        assertTrue("loose threshold + wide range changes at least as much ($loose >= $def)", loose >= def)
        assertTrue("the two ends differ ($gated vs $loose)", loose > gated)
    }

    private fun msPerFrame(effect: GlEffect, w: Int, h: Int): Double = withGl {
        val inTex = GlUtil.createTexture(w, h, false)
        val outTex = GlUtil.createTexture(w, h, false)
        val fbo = GlUtil.createFboForTexture(outTex)
        GlUtil.focusFramebufferUsingCurrentContext(fbo, w, h)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val program = effect.toGlShaderProgram(ctx, false) as BaseGlShaderProgram
        program.configure(w, h)
        repeat(5) { program.drawFrame(inTex, 0) }; GLES20.glFinish()
        val frames = 60
        val t0 = System.nanoTime()
        repeat(frames) { program.drawFrame(inTex, it * 16_666L) }
        GLES20.glFinish()
        val ms = (System.nanoTime() - t0) / 1e6 / frames
        program.release(); GlUtil.deleteFbo(fbo); GlUtil.deleteTexture(inTex); GlUtil.deleteTexture(outTex)
        ms
    }

    @Test fun costPerFrameIsReported() {
        val full = grade(PictureLook.resolve(PictureLook.Profile(Look.CINEMATIC, PictureLook.Controls(sharpness = 0.5f, hue = 10f), PictureLook.Tuning(grain = 0.3f))))
        val colour = look(Look.VIVID)
        val a720 = msPerFrame(colour, 1280, 720); val f720 = msPerFrame(full, 1280, 720)
        val a1080 = msPerFrame(colour, 1920, 1080); val f1080 = msPerFrame(full, 1920, 1080)
        // Reference on the same GPU: the Anime4K V2 Performance pass that already ships (5 taps).
        val ref720 = msPerFrame(Anime4kV2.Effect(Anime4kV2.params(Anime4kV2.Preset.PERFORMANCE, Anime4kV2.Style.SHARPEN)), 1280, 720)
        log("PICTURE_GL_COST colour720=%.2f full720=%.2f colour1080=%.2f full1080=%.2f anime4kPerf720=%.2f".format(a720, f720, a1080, f1080, ref720))
        // CI emulators use software GL (tens of ms per 720p frame for any pass), so the budget is relative: colour-only
        // must cost no more than the shipped Anime4K pass, everything on (sharpen + grain) at most twice it.
        assertTrue("colour grade $a720 ms vs Anime4K $ref720 ms", a720 <= ref720 * 1.1)
        assertTrue("full grade $f720 ms vs Anime4K $ref720 ms", f720 <= ref720 * 2.0)
        log("PICTURE_GL_RESULT PASS")
    }
}
