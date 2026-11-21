/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.WindowInsets
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.OnlineRuntime
import dev.asfakul.yumeiro.ui.screens.online.filterExtensionNames
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NBUG-10 device check ("Show videos from" search lists only 2). Fix G15-1 (4c6345f8, ADR-031) opens
 * the filter sheet at full height on every device so the matches are not under the keyboard.
 *
 * On a phone, with several real extensions on, the test opens Yumix → filter, types a query into the
 * sheet's search box through the real keyboard path, waits for the soft keyboard, and counts the match
 * rows that lie fully above the keyboard (window insets, not a guess). At least min(matches, 3) must be
 * readable; the bug showed about 2. TV opens the keyboard only on OK (FINAL-39), so this is a phone check.
 */
@RunWith(AndroidJUnit4::class)
class Nbug10FilterSearchDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("NBUG10", s)

    @Before fun setUp() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        runCatching { ui.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        FirstRun.toTabs(rule)
        runBlocking {
            withTimeout(300_000) {
                ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
                var n = ExtensionManager.state.value.installed.count { it.enabled }
                if (n < WANT) {
                    val items = ExtensionSources.all(ctx, force = true).items.filter {
                        it.primary && !it.isNsfw && !it.isDown && it.apiVersion <= OnlineRuntime.API_VERSION
                    }
                    for (c in items) {
                        if (n >= WANT) break
                        if (ExtensionManager.state.value.installed.any { it.internalName == c.internalName }) continue
                        runCatching { ExtensionManager.install(ctx, c, ExtensionReview.Mode.AUTO) }
                            .onSuccess { n++; log("installed ${c.internalName}") }
                            .onFailure { log("install ${c.internalName} failed: ${it.message}") }
                    }
                }
            }
        }
    }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 30_000) {
        val ok = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
        assertTrue("no '$tag' after ${ms}ms · ${FirstRun.onScreen(rule)}", ok)
    }

    private val notRows = setOf("online_filter_all", "online_filter_sheet", "online_filter_clear", "online_filter_empty",
        "online_filter_search", "online_filter_search_clear", "online_filter_active", "online_filter_active_clear")

    /** Extension rows in the sheet: name → bounds (top, bottom) in root px. */
    private fun rows(): List<Triple<String, Float, Float>> = rule.onAllNodes(SemanticsMatcher("filter row") { n ->
        val t = n.config.getOrElseNullable(SemanticsProperties.TestTag) { null } ?: return@SemanticsMatcher false
        t.startsWith("online_filter_") && !t.startsWith("online_filter_pin_") && t !in notRows
    }, true).fetchSemanticsNodes().map {
        Triple(it.config[SemanticsProperties.TestTag].removePrefix("online_filter_"), it.boundsInRoot.top, it.boundsInRoot.bottom)
    }.sortedBy { it.second }

    /** Top of the keyboard in the activity window, px (window height when it is down). */
    private fun keyboardTop(): Int {
        var top = 0
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            val ime = if (Build.VERSION.SDK_INT >= 30) v.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0 else 0
            top = v.height - ime
        }
        return top
    }

    private fun imeShown(): Boolean = FirstRun.shell("dumpsys input_method").lineSequence().any { "mInputShown=true" in it }

    @Test fun searchMatchesStayAboveTheKeyboard() {
        assumeTrue("phone check: insets API 30+", Build.VERSION.SDK_INT >= 30)
        FirstRun.tapVisible(rule, "nav_tab_home"); FirstRun.tapVisible(rule, "quick_card_hues")
        waitTag("online_filter", 120_000)
        assumeTrue("TV layout: the keyboard opens only on OK (FINAL-39)", !has("tv_nav_rail"))
        FirstRun.tapVisible(rule, "online_filter"); waitTag("online_filter_sheet")
        val all = rows().map { it.first }
        log("sheet rows: $all")
        assertTrue("need 3+ extensions in the sheet: $all", all.size >= 3)
        // The query that matches the most rows (one letter, as a user narrowing a long list types).
        val query = "aeoinrt".map { it.toString() }.maxByOrNull { filterExtensionNames(all, it).size }!!
        val expectedNames = filterExtensionNames(all, query)
        val want = minOf(expectedNames.size, 3)
        log("query '$query' matches ${expectedNames.size}: $expectedNames")
        assertTrue("query '$query' matches fewer than 3 of $all", expectedNames.size >= 3)

        rule.onAllNodesWithTag("online_filter_search", true)[0].performClick()
        rule.onAllNodesWithTag("online_filter_search", true)[0].performTextInput(query)
        val kb = runCatching { rule.waitUntil(10_000) { imeShown() && keyboardTop() < rule.activity.window.decorView.height }; true }.getOrDefault(false)
        assertTrue("the soft keyboard did not open on the search box", kb)
        rule.waitForIdle(); Thread.sleep(800); rule.waitForIdle() // sheet settles above the keyboard
        val top = keyboardTop()
        val shown = rows()
        val readable = shown.filter { it.second >= 0f && it.third <= top + 1f }
        FirstRun.shot("nbug10_search_keyboard")
        log("NBUG10_RESULT keyboardTop=$top rows=${shown.map { "${it.first}@${it.second.toInt()}-${it.third.toInt()}" }} readable=${readable.size} want=$want")
        assertTrue("only ${readable.size} of ${expectedNames.size} matches are above the keyboard (want $want): $shown, keyboard top $top", readable.size >= want)
        assertTrue("rows shown are not the matches: ${shown.map { it.first }} vs $expectedNames", shown.map { it.first }.all { it in expectedNames })
    }

    private companion object { const val WANT = 6 }
}
