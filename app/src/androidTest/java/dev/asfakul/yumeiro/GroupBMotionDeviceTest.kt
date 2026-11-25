/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.ui.components.Entrance
import dev.asfakul.yumeiro.ui.components.LocalReducedMotion
import dev.asfakul.yumeiro.ui.components.YuLoader
import dev.asfakul.yumeiro.ui.components.yuEntrance
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.Palettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group B / B2 + B3 on the device, from real rendered pixels (tag YuGroupB):
 * - B3: the brand loader moves (two frames 300 ms apart differ) and stands still with reduced motion.
 * - B2: a burst of cards rises in staggered (first card further along than the eighth), all end
 *   fully drawn, and showing them again does not replay; on a low-RAM device the cards appear at once.
 */
@RunWith(AndroidJUnit4::class)
class GroupBMotionDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(tag: String): ImageBitmap = rule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage()

    private fun diff(a: ImageBitmap, b: ImageBitmap): Int {
        val pa = a.toPixelMap(); val pb = b.toPixelMap(); var n = 0
        for (y in 0 until minOf(a.height, b.height)) for (x in 0 until minOf(a.width, b.width)) if (pa[x, y] != pb[x, y]) n++
        return n
    }

    private fun loaderMoves(reduced: Boolean): Int {
        rule.setContent {
            MTPlayerTheme(Palettes.Dark) { CompositionLocalProvider(LocalReducedMotion provides reduced) { YuLoader() } }
        }
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(100); rule.waitForIdle()
        val a = shot("yu_loader")
        rule.mainClock.advanceTimeBy(300); rule.waitForIdle()
        val b = shot("yu_loader")
        rule.mainClock.autoAdvance = true
        return diff(a, b)
    }

    @Test fun a_loaderOrbitsAndStandsStillWithReducedMotion() {
        val moving = loaderMoves(false)
        Log.i(TAG, "loader pixels changed over 300 ms: $moving")
        assertTrue("brand loader animates: $moving", moving > 50)
        Log.i(TAG, "GROUPB_LOADER_A PASS")
    }

    @Test fun b_loaderStillWithReducedMotion() {
        val still = loaderMoves(true)
        Log.i(TAG, "reduced-motion loader pixels changed: $still")
        assertEquals("reduced motion: loader does not move", 0, still)
        Log.i(TAG, "GROUPB_LOADER_B PASS")
    }

    private fun centreLum(img: ImageBitmap, i: Int, cards: Int): Float {
        val pm = img.toPixelMap()
        val cw = img.width / cards
        val c = pm[cw * i + cw / 2, img.height / 2]
        return (c.red + c.green + c.blue) / 3f
    }

    @Test fun c_cardsRiseInStaggeredOnce() {
        val ctx = rule.activity
        val lowRam = (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
        Entrance.resetForTest()
        val cards = 8
        var visible by mutableStateOf(true)
        val run = System.nanoTime()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MTPlayerTheme(Palettes.Dark) {
                Row(Modifier.background(Color.Black).testTag("cards"), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    if (visible) repeat(cards) { i -> Box(Modifier.size(40.dp).yuEntrance("card_${run}_$i").background(Color.White)) }
                }
            }
        }
        rule.mainClock.advanceTimeBy(140); rule.waitForIdle()
        val mid = shot("cards")
        val lum = (0 until cards).map { centreLum(mid, it, cards) }
        Log.i(TAG, "lowRam=$lowRam card brightness at 140 ms: ${lum.map { "%.2f".format(it) }}")
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        val done = (0 until cards).map { centreLum(shot("cards"), it, cards) }
        assertTrue("every card ends fully drawn: $done", done.all { it > 0.97f })
        if (lowRam) {
            assertTrue("low-RAM: cards appear at once: $lum", lum.all { it > 0.97f })
        } else {
            assertTrue("first card is further along than the last (stagger): $lum", lum.first() > lum.last() + 0.15f)
            assertTrue("the last card is still rising at 140 ms: $lum", lum.last() < 0.9f)
        }
        // Show them again (as when a tab comes back): no replay.
        visible = false
        rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        visible = true
        rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        val again = (0 until cards).map { centreLum(shot("cards"), it, cards) }
        Log.i(TAG, "shown again, first frame: ${again.map { "%.2f".format(it) }}")
        assertTrue("cards seen before do not animate again: $again", again.all { it > 0.97f })
        rule.mainClock.autoAdvance = true
        Log.i(TAG, "GROUPB_ENTRANCE_C PASS")
    }

    companion object { const val TAG = "YuGroupB" }
}
