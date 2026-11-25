/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * A11Y-EXT-1c: does what AnimePahe's extension does (field probes 45530059278, 45533712008): a 1 × 1 FrameLayout
 * holding a full-size WebView, added straight to the window's decor view, whose WebView then asks for focus.
 * Yumeiro must keep it out of screen readers and give the focus back. Also the same view added to the content
 * view. A normal-size view an extension adds is left alone. Log tag YuForeignTest.
 */
@RunWith(AndroidJUnit4::class)
class ForeignViewGuardDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun hidden(parent: () -> ViewGroup, w: Int, h: Int): Pair<FrameLayout, WebView> {
        lateinit var frame: FrameLayout; lateinit var web: WebView
        inst.runOnMainSync {
            val a = rule.activity
            web = WebView(a).apply { isFocusable = true; isFocusableInTouchMode = true }
            frame = FrameLayout(a).apply { addView(web, FrameLayout.LayoutParams(945, 1680)) }
            parent().addView(frame, ViewGroup.LayoutParams(w, h))
        }
        inst.waitForIdleSync(); Thread.sleep(500)
        inst.runOnMainSync { web.requestFocus() }
        inst.waitForIdleSync(); Thread.sleep(500)
        return frame to web
    }

    @Test fun tinyViewInDecorIsQuietedAndLosesFocus() {
        rule.waitForIdle()
        val (frame, web) = hidden({ rule.activity.window.decorView as ViewGroup }, 1, 1)
        var imp = -1; var focused = true; var webImp = -1
        inst.runOnMainSync { imp = frame.importantForAccessibility; focused = web.isFocused || web.hasFocus(); webImp = web.importantForAccessibility }
        Log.w("YuForeignTest", "CASE decor 1x1 importantForAccessibility=$imp webFocused=$focused")
        assertEquals("decor 1x1 view hidden from screen readers", View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, imp)
        assertFalse("decor 1x1 WebView kept the focus", focused)
        inst.runOnMainSync { (frame.parent as? ViewGroup)?.removeView(frame) }
    }

    @Test fun tinyViewInContentIsQuietedAndLosesFocus() {
        rule.waitForIdle()
        val (frame, web) = hidden({ rule.activity.findViewById(android.R.id.content) }, 1, 1)
        var imp = -1; var focused = true
        inst.runOnMainSync { imp = frame.importantForAccessibility; focused = web.isFocused || web.hasFocus() }
        Log.w("YuForeignTest", "CASE content 1x1 importantForAccessibility=$imp webFocused=$focused")
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, imp)
        assertFalse("content 1x1 WebView kept the focus", focused)
        inst.runOnMainSync { (frame.parent as? ViewGroup)?.removeView(frame) }
    }

    @Test fun normalSizeViewIsLeftAlone() {
        rule.waitForIdle()
        val (frame, web) = hidden({ rule.activity.window.decorView as ViewGroup }, 600, 400)
        var imp = -1; var focused = false
        inst.runOnMainSync { imp = frame.importantForAccessibility; focused = web.isFocused }
        Log.w("YuForeignTest", "CASE decor 600x400 importantForAccessibility=$imp webFocused=$focused")
        assertEquals("a real-size view keeps its accessibility", View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, imp)
        inst.runOnMainSync { (frame.parent as? ViewGroup)?.removeView(frame) }
        Log.w("YuForeignTest", "FOREIGN_RESULT PASS")
    }
}
