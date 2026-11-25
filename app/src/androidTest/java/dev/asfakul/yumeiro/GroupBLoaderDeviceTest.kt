/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.components.LocalReducedMotion
import dev.asfakul.yumeiro.ui.components.YuLoader
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.Palettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group B / B3 on the device: the brand loader (the splash moon mark with an orbiting arc) really
 * moves on screen, in real time, and stands still with reduced motion. No compose test rule here:
 * it cancels infinite animations. Pixels come from real screenshots (tag YuGroupB).
 */
@RunWith(AndroidJUnit4::class)
class GroupBLoaderDeviceTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun centre(b: Bitmap, sizePx: Int): Bitmap {
        val s = sizePx.coerceAtMost(minOf(b.width, b.height))
        return Bitmap.createBitmap(b, (b.width - s) / 2, (b.height - s) / 2, s, s)
    }

    private fun changed(a: Bitmap, b: Bitmap): Int {
        var n = 0
        for (y in 0 until a.height step 2) for (x in 0 until a.width step 2) if (a.getPixel(x, y) != b.getPixel(x, y)) n++
        return n
    }

    private fun run(reduced: Boolean): Int {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            var px = 0
            scenario.onActivity { a ->
                px = (120 * a.resources.displayMetrics.density).toInt()
                a.setContent {
                    MTPlayerTheme(Palettes.Dark) {
                        CompositionLocalProvider(LocalReducedMotion provides reduced) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { YuLoader(size = 96.dp) }
                        }
                    }
                }
            }
            Thread.sleep(1200)
            inst.waitForIdleSync()
            val a = centre(inst.uiAutomation.takeScreenshot(), px)
            Thread.sleep(330)
            val b = centre(inst.uiAutomation.takeScreenshot(), px)
            return changed(a, b)
        } finally { scenario.close() }
    }

    @Test fun a_loaderMovesOnScreen() {
        val scale = android.provider.Settings.Global.getFloat(inst.targetContext.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val n = run(reduced = false)
        Log.i(TAG, "loader pixels changed over 330 ms: $n (animator scale $scale)")
        assertTrue("brand loader animates on screen: $n", n > 50)
        Log.i(TAG, "GROUPB_LOADER_A PASS")
    }

    @Test fun b_loaderStillWithReducedMotion() {
        val n = run(reduced = true)
        Log.i(TAG, "reduced-motion loader pixels changed: $n")
        assertEquals("reduced motion: loader does not move", 0, n)
        Log.i(TAG, "GROUPB_LOADER_B PASS")
    }

    companion object { const val TAG = "YuGroupB" }
}
