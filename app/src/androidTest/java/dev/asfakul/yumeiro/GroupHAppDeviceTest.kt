/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.OnlineRuntime
import dev.asfakul.yumeiro.online.YumixDefaults
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import dev.asfakul.yumeiro.ui.screens.online.yumixMixNow
import dev.asfakul.yumeiro.ui.screens.online.yumixReadyNames
import org.junit.After
import org.junit.Assert.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GROUP H on the device, in the real app (phones and TV). Log tag YuGroupH; GROUPH_<item>_RESULT lines are evidence.
 *  h5 (owner item 5): Yumix → "Show videos from" → Default extensions → pick one → Save. The mixed feed then uses
 *     only that extension, and the choice is kept (YumixDefaults). Fail-before: the sheet had no defaults button.
 *  h12 (owner item 12): Settings → Navigation bar offers "Network stream"; pinned, its button opens Stream's link
 *     pane. Fail-before: there was no such shortcut.
 */
@RunWith(AndroidJUnit4::class)
class GroupHAppDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private fun log(s: String) = Log.i("YuGroupH", s)
    private fun n(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun has(tag: String) = n(tag) > 0
    private fun waitTag(tag: String, ms: Long = 30_000) {
        val ok = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
        assertTrue("no '$tag' after ${ms}ms · ${FirstRun.onScreen(rule)}", ok)
    }

    @Before fun setUp() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        runCatching { ui.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        FirstRun.toTabs(rule)
        runBlocking {
            withTimeout(300_000) {
                ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
                var have = ExtensionManager.state.value.installed.count { it.enabled }
                if (have < WANT) {
                    val items = ExtensionSources.all(ctx, force = true).items.filter {
                        it.primary && !it.isNsfw && !it.isDown && it.apiVersion <= OnlineRuntime.API_VERSION
                    }
                    for (c in items) {
                        if (have >= WANT) break
                        if (ExtensionManager.state.value.installed.any { it.internalName == c.internalName }) continue
                        runCatching { ExtensionManager.install(ctx, c, ExtensionReview.Mode.AUTO) }
                            .onSuccess { have++; log("installed ${c.internalName}") }
                            .onFailure { log("install ${c.internalName} failed: ${it.message}") }
                    }
                }
            }
        }
    }

    @After fun tearDown() {
        runCatching { inst.runOnMainSync { YumixDefaults.set(ctx, emptySet()) } }
        NavPrefs.Shortcut.of("network_stream")?.let { s -> runCatching { inst.runOnMainSync { NavPrefs.setShortcut(ctx, s, false) } } }
    }

    @Test fun h5_yumixDefaultExtensions() {
        inst.runOnMainSync { YumixDefaults.set(ctx, emptySet()) }
        FirstRun.tapVisible(rule, "nav_tab_home"); FirstRun.tapVisible(rule, "quick_card_hues")
        waitTag("online_filter", 120_000)
        val ready = runCatching { rule.waitUntil(180_000) { yumixReadyNames().isNotEmpty() }; true }.getOrDefault(false)
        val all0 = yumixMixNow
        log("h5 start mix=$all0 ready=${yumixReadyNames()}")
        assertTrue("an extension loaded titles", ready)
        assertTrue("need two or more extensions in the mix: $all0", all0.size >= 2)
        val pick = yumixReadyNames().first()
        FirstRun.tapVisible(rule, "online_filter"); waitTag("online_filter_sheet")
        assertTrue("H-5: the sheet has no Default extensions button", has("online_filter-defaults"))
        FirstRun.tapVisible(rule, "online_filter-defaults"); waitTag("online_filter_defaults")
        rule.onAllNodesWithTag("online_filter_default_$pick", true)[0].performClick(); rule.waitForIdle()
        FirstRun.shot("grouph_h5_defaults_editor")
        FirstRun.tapVisible(rule, "online_filter_defaults_save")
        val narrowed = runCatching { rule.waitUntil(30_000) { yumixMixNow == listOf(pick) }; true }.getOrDefault(false)
        log("h5 after save defaults=${YumixDefaults.names.value} mix=$yumixMixNow")
        assertTrue("the feed uses only the default ($pick): $yumixMixNow", narrowed)
        assertEquals(setOf(pick), YumixDefaults.names.value)
        val saved = ctx.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(ctx, "yumix_defaults"), Context.MODE_PRIVATE).getString("defaults", null)
        assertEquals("kept on disk", pick, saved)
        rule.waitUntil(60_000) { yumixReadyNames().contains(pick) }
        FirstRun.shot("grouph_h5_feed")
        // Back to every extension.
        FirstRun.tapVisible(rule, "online_filter"); waitTag("online_filter_sheet")
        FirstRun.tapVisible(rule, "online_filter-defaults"); waitTag("online_filter_defaults")
        FirstRun.tapVisible(rule, "online_filter_defaults_all")
        val back = runCatching { rule.waitUntil(30_000) { yumixMixNow.size == all0.size }; true }.getOrDefault(false)
        log("h5 reset mix=$yumixMixNow")
        assertTrue("Use every extension restores the mix", back)
        log("GROUPH_H5_RESULT PASS pick=$pick of ${all0.size}")
    }

    @Test fun h12_networkStreamInTheNavBar() {
        val s = NavPrefs.Shortcut.of("network_stream")
        log("h12 known=${s != null} offered=${NavPrefs.Shortcut.offered().map { it.id }}")
        assertNotNull("H-12: Navigation bar cannot pin Network stream", s)
        assertTrue("offered in Settings", s in NavPrefs.Shortcut.offered())
        var pinned = false
        inst.runOnMainSync { pinned = NavPrefs.setShortcut(ctx, s!!, true) }
        assertTrue("pinned", pinned)
        val tag = "nav_shortcut_network_stream"
        val shown = runCatching { rule.waitUntil(15_000) { rule.onAllNodesWithTag(FirstRun.navTag(rule, tag), true).fetchSemanticsNodes().isNotEmpty() }; true }.getOrDefault(false)
        assertTrue("the bar shows the Network stream button · ${FirstRun.onScreen(rule)}", shown)
        FirstRun.tapVisible(rule, tag)
        waitTag("stream_link_page", 20_000)
        waitTag("stream_url", 10_000)
        FirstRun.shot("grouph_h12_stream")
        log("GROUPH_H12_RESULT PASS")
    }

    companion object { const val WANT = 3 }
}
