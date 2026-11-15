/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.HeatGuard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G12 X4: HeatGuard follows the real thermal status (`cmd thermalservice override-status`), and each
 * level changes what it should: loop interval, parallel extension calls, GPU upscaling.
 * `LocalPlayerDeviceTest#d` already covers battery saver → WARM and SEVERE → HOT; this adds
 * MODERATE → WARM, CRITICAL → HOT, the way back to COOL and the effect of every level.
 */
@RunWith(AndroidJUnit4::class)
class HeatGuardDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuHeat", s)
    private fun sh(c: String) = FirstRun.shell(c).trim().also { log("$ $c -> ${it.take(120)}") }

    private fun waitLevel(want: HeatGuard.Level, ms: Long = 10_000): Long {
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < ms) {
            if (HeatGuard.level.value == want) return System.currentTimeMillis() - t0
            Thread.sleep(100)
        }
        throw AssertionError("HeatGuard stayed ${HeatGuard.level.value}, wanted $want after ${ms}ms")
    }

    @After fun restore() {
        runCatching { sh("cmd thermalservice reset") }
    }

    @Test fun thermalStatusDrivesTheGuard() {
        assumeTrue("thermal status needs Android 10+", Build.VERSION.SDK_INT >= 29)
        HeatGuard.install(ctx) // already done by MTApplication; a second call is a no-op
        sh("cmd power set-mode 0") // battery saver off, or the level never reaches COOL
        sh("cmd thermalservice override-status 0")
        waitLevel(HeatGuard.Level.COOL)
        assertEquals(1_000L, HeatGuard.interval(1_000L)); assertEquals(6, HeatGuard.parallel()); assertTrue(HeatGuard.allowGpuUpscale())

        sh("cmd thermalservice override-status 2") // MODERATE
        val warm = waitLevel(HeatGuard.Level.WARM)
        assertEquals(2_000L, HeatGuard.interval(1_000L)); assertEquals(4, HeatGuard.parallel()); assertTrue(HeatGuard.allowGpuUpscale())

        sh("cmd thermalservice override-status 4") // CRITICAL
        val hot = waitLevel(HeatGuard.Level.HOT)
        assertEquals(4_000L, HeatGuard.interval(1_000L)); assertEquals(2, HeatGuard.parallel()); assertTrue(!HeatGuard.allowGpuUpscale())

        sh("cmd thermalservice override-status 0")
        val cool = waitLevel(HeatGuard.Level.COOL)
        log("HEAT_THERMAL_RESULT PASS warm=${warm}ms hot=${hot}ms cool=${cool}ms")
    }
}
