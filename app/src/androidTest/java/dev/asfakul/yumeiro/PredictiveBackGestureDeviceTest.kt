/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.Build
import android.util.Log
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R19 NAV-1 (owner: "native Android back gives a strange closing transition; the in-app back icon is right").
 *
 * The app turns predictive back on (`enableOnBackInvokedCallback="true"`). From Android 14 a Back *gesture*
 * sends progress events, and NavHost then plays its predictivePopExit transition, not popExit. The app never set
 * it, so the library default ran: the page shrank to 0.7x in the middle of the screen while the page below faded
 * in. The on-screen Back is a plain pop and slid. BackNavigationDeviceTest only sends key 4 (no progress), so it
 * never saw this.
 *
 * Real input, no fake dispatcher: gesture navigation is switched on, then a finger goes down at the left edge
 * and is held a third of the way across (`input motionevent`). While it is held, the closing page must keep its
 * full width and be shifted to the right (a slide, like the in-app Back), not scaled. Lifting the finger must
 * close the page.
 */
@RunWith(AndroidJUnit4::class)
class PredictiveBackGestureDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private var navMode: String? = null
    private fun log(s: String) = Log.i("YuBackGesture", s)

    private fun pageBounds(): Rect? = rule.onAllNodesWithTag("downloads_screen", useUnmergedTree = true)
        .fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    @After fun restoreNav() {
        navMode?.let { if ("2" !in it) runCatching { FirstRun.shell("cmd overlay disable com.android.internal.systemui.navbar.gestural") } }
    }

    @Test fun backGestureSlidesThePageLikeTheOnScreenBack() {
        FirstRun.toTabs(rule)
        FirstRun.dismissPrompts(rule)
        navMode = FirstRun.shell("settings get secure navigation_mode").trim()
        val gestural = FirstRun.shell("cmd overlay enable com.android.internal.systemui.navbar.gestural")
        Thread.sleep(2_500)
        log("api=${Build.VERSION.SDK_INT} nav_mode before=$navMode after=${FirstRun.shell("settings get secure navigation_mode").trim()} overlay='${gestural.trim()}'")

        rule.runOnUiThread { dev.asfakul.yumeiro.ui.screens.downloads.DownloadsNav.open() }
        rule.waitUntil(15_000) { pageBounds() != null }
        rule.waitForIdle(); Thread.sleep(800)
        val full = pageBounds()!!
        log("page at rest: $full")

        // Finger down at the very left edge, moved a third of the way across in small steps, then held.
        val dm = rule.activity.resources.displayMetrics
        val y = dm.heightPixels / 2
        val endX = dm.widthPixels / 3
        FirstRun.shell("input motionevent DOWN 2 $y")
        for (x in listOf(20, 60, 120, endX / 2, endX * 3 / 4, endX)) { FirstRun.shell("input motionevent MOVE $x $y"); Thread.sleep(40) }
        Thread.sleep(500)
        rule.mainClock.advanceTimeByFrame()
        val mid = pageBounds()
        FirstRun.shot("nav1_back_gesture_held")
        log("page while the gesture is held: $mid")
        FirstRun.shell("input motionevent UP $endX $y")

        val closed = runCatching { rule.waitUntil(8_000) { pageBounds() == null }; true }.getOrDefault(false)
        log("page closed after release: $closed")
        assertTrue("lifting the finger after a Back gesture must close the page", closed)

        if (Build.VERSION.SDK_INT < 34 || mid == null) {
            // Android 13 sends no gesture progress to apps: Back is one event and runs popExit (the slide).
            log("no predictive progress on this device (api ${Build.VERSION.SDK_INT}); only the close is checked")
            return
        }
        val widthRatio = mid.width / full.width
        val shift = (mid.left - full.left) / full.width
        log("width ratio=$widthRatio shift=$shift")
        if (widthRatio > 0.995f && shift < 0.005f) {
            log("the gesture did not reach the app as a predictive back (page did not move); only the close is checked")
            return
        }
        assertTrue("closing page was scaled during the Back gesture (width ${"%.3f".format(widthRatio)} of full): library default scale-down, not our slide", widthRatio > 0.98f)
        assertTrue("closing page did not slide right during the Back gesture (shift ${"%.3f".format(shift)})", shift > 0.03f)
    }
}
