/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import android.util.Log
import android.view.View
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * G12 RTL layout check. The app is switched to Arabic (a right-to-left locale) with the per-app
 * language API before the activity starts, then the real UI is checked:
 *  - the window's layout direction is RTL;
 *  - the tab bar is mirrored (Home, the first tab, is right of Music);
 *  - a sub-page back button sits on the right half and its arrow mirrors (AutoMirrored);
 *  - Home, Videos, Library and Settings open without a crash; screenshots rtl_* for review.
 * API 33+ only (LocaleManager); below that the check is skipped and logged.
 * Log tag YuRtl; RTL_RESULT PASS is the evidence.
 */
@RunWith(AndroidJUnit4::class)
class RtlLayoutDeviceTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule(order = 0) val rtl = object : ExternalResource() {
        override fun before() {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("ar")
            }
        }
        override fun after() {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
            }
        }
    }
    @get:Rule(order = 1) val hang = HangDumpRule()
    @get:Rule(order = 2) val rule = createAndroidComposeRule<MainActivity>()

    private fun log(s: String) = Log.i("YuRtl", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun bounds(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes()[0].boundsInRoot
    private fun wait(what: String, ms: Long = 20_000, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("rtl_missing_${what.replace(' ', '_')}")
        assertTrue("$what (after ${ms}ms) · ${FirstRun.onScreen(rule)}", ok)
    }

    @Test fun theAppMirrorsInARightToLeftLanguage() {
        assumeTrue("per-app language needs API 33 (this is ${Build.VERSION.SDK_INT})", Build.VERSION.SDK_INT >= 33)
        FirstRun.toTabs(rule)
        var dir = -1
        rule.runOnUiThread { dir = rule.activity.window.decorView.layoutDirection }
        log("locale=${rule.activity.resources.configuration.locales} layoutDirection=$dir")
        assertEquals("window layout direction", View.LAYOUT_DIRECTION_RTL, dir)

        // Tab bar mirrored.
        wait("tab bar") { has("nav_tab_home") && has("nav_tab_music") }
        val home = bounds("nav_tab_home"); val music = bounds("nav_tab_music")
        log("tabs home=$home music=$music")
        assertTrue("Home (first tab) is right of Music in RTL: home=$home music=$music", home.left > music.left)
        FirstRun.shot("rtl_home")

        for (tab in listOf("nav_tab_videos", "nav_tab_library")) {
            FirstRun.tapVisible(rule, tab)
            Thread.sleep(2_000)
            FirstRun.shot("rtl_${tab.removePrefix("nav_tab_")}")
        }
        FirstRun.tapVisible(rule, "nav_tab_home")

        // A sub-page: Settings. Its back button is on the right half.
        FirstRun.tapVisible(rule, "topbar_settings_action")
        wait("settings") { has("settings_cat_appearance") }
        val width = rule.activity.window.decorView.width
        val back = if (has("subpage_back")) bounds("subpage_back") else null
        log("settings back=$back width=$width")
        assertTrue("Settings back button on the right in RTL: $back of $width", back != null && back.center.x > width / 2f)
        FirstRun.shot("rtl_settings")
        assertTrue("app still running", FirstRun.shell("pidof ${ctx.packageName}").isNotBlank())
        log("RTL_RESULT PASS")
    }
}
