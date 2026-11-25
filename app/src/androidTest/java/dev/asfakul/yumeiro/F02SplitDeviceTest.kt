/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-02 split on a device: Telegram and Websites are two pages, each with a nav-bar shortcut, and each
 * page refuses the other kind (a website on Telegram, a channel on Websites) with a message that says where it goes.
 * Lines: F02S_CASE ….
 */
@RunWith(AndroidJUnit4::class)
class F02SplitDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = inst.targetContext
    private fun log(s: String) = Log.i("YuF02Split", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(FirstRun.navTag(rule, tag), true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun hasText(t: String) = runCatching { rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun waitFor(tag: String, ms: Long = 15_000) = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
    private fun back() { FirstRun.dismissForeignDialog(rule); FirstRun.shell("input keyevent 4"); rule.waitForIdle() }
    private fun home() { repeat(4) { if (!has("home_screen_root")) back() }; waitFor("home_screen_root", 10_000) }

    @Before fun setUp() {
        FirstRun.toTabs(rule)
        waitFor("home_screen_root", 20_000)
    }

    @Test fun telegramAndWebsitesAreTwoPagesThatRefuseTheOtherKind() {
        data class Case(val id: String, val page: String, val other: String, val hint: String)
        val cases = listOf(
            Case("telegram", "telegram_screen", "example.com", "Websites go on the Websites page"),
            Case("websites", "websites_screen", "@durov", "Add it on the Telegram page"),
        )
        val failed = ArrayList<String>()
        for (c in cases) {
            val s = NavPrefs.Shortcut.of(c.id)!!
            try {
                inst.runOnMainSync { NavPrefs.setShortcut(ctx, s, true) }
                home()
                val pinned = waitFor("nav_shortcut_${c.id}")
                val opened = pinned && runCatching { FirstRun.tapVisible(rule, "nav_shortcut_${c.id}", 10_000); true }.getOrDefault(false) && waitFor(c.page)
                val otherPage = if (c.page == "telegram_screen") "websites_screen" else "telegram_screen"
                val alone = opened && !has(otherPage)
                var refused = false
                // WEB-ADD-1: with saved sites the Websites add card opens from "+". GROUP F: Telegram adds through its "+" sheet.
                val tg = c.page == "telegram_screen"
                val field = if (tg) "tg_add_field" else "site_add_field"
                if (opened && !tg && !waitFor(field, 2_000) && has("site_add_open")) rule.onNodeWithTag("site_add_open", true).performClick()
                if (opened && tg && has("tg_add_open")) rule.onNodeWithTag("tg_add_open", true).performClick()
                if (opened && waitFor(field, 5_000)) {
                    rule.onNodeWithTag(field, true).performTextInput(c.other)
                    rule.onNodeWithTag(if (tg) "tg_add_add" else "site_add", true).performClick()
                    refused = runCatching { rule.waitUntil(5_000) { hasText(c.hint) }; true }.getOrDefault(false)
                }
                FirstRun.shot("f02s_${c.id}")
                log("F02S_CASE ${c.id} pinned=$pinned opened=$opened alone=$alone refused=$refused")
                if (!(opened && alone && refused)) failed += "${c.id}: opened=$opened alone=$alone refused=$refused"
                back()
            } finally { inst.runOnMainSync { NavPrefs.setShortcut(ctx, s, false) } }
        }
        assertTrue("F-02 split: $failed", failed.isEmpty())
    }
}
