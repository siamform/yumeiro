/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.OnlineRuntime
import dev.asfakul.yumeiro.online.RemoteExtension
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R16 Step 3, group 2 (extensions and market) on an emulator, through the real UI and the real
 * market (network):
 * E1 market search finds an extension; E2 install goes through the safety review (cancel keeps it
 * out, install puts it in); E4 the per-extension switch; E6 select all / single selection with bulk
 * turn off, turn on and remove; E7 search of installed extensions (and select all acts on the
 * search result only); E8 a broken link shows an error in the dialog instead of crashing;
 * E3 a missing extension file (as after a restore) is downloaded again.
 */
@RunWith(AndroidJUnit4::class)
class ExtensionsDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun log(s: String) = Log.i("YuExtTest", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 30_000) {
        val ok = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
        assertTrue("no '$tag' after ${ms}ms · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun waitGone(tag: String, ms: Long = 10_000) {
        val ok = runCatching { rule.waitUntil(ms) { !has(tag) }; true }.getOrDefault(false)
        assertTrue("'$tag' still shown after ${ms}ms", ok)
    }
    private fun waitFor(what: String, ms: Long = 60_000, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        assertTrue("$what (after ${ms}ms) · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun tap(tag: String) = FirstRun.tapVisible(rule, tag)
    private fun back() { closeGuide(); FirstRun.shell("input keyevent 4") }
    private fun closeGuide() {
        if (has("reliability_guide")) { log("closing the reliability guide first"); runCatching { tap("guide_close") }; waitGone("reliability_guide", 5_000) }
    }
    private fun text(tag: String): String = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString(" ") { t -> t.text }.orEmpty()

    private fun installed(internal: String) = ExtensionManager.state.value.installed.firstOrNull { it.internalName == internal }
    private fun enabled(internal: String) = installed(internal)?.enabled == true

    private lateinit var second: String

    @Before fun setUp() {
        runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        FirstRun.toTabs(rule)
        runBlocking {
            withTimeout(240_000) {
                ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
                // Start from nothing installed so every step is visible in the UI.
                ExtensionManager.state.value.installed.forEach { ExtensionManager.uninstall(ctx, it.key) }
                val items = ExtensionSources.all(ctx, force = true).items
                // A second extension for the bulk steps: any small, working one the market lists.
                val candidates = items.filter {
                    it.primary && !it.isNsfw && !it.isDown && it.apiVersion <= OnlineRuntime.API_VERSION && it.internalName != IA
                }.take(8)
                var picked: RemoteExtension? = null
                for (c in candidates) {
                    val ok = runCatching { ExtensionManager.install(ctx, c, ExtensionReview.Mode.AUTO) }
                        .onFailure { log("candidate ${c.internalName} failed: ${it.message}") }.isSuccess
                    if (ok) { picked = c; break }
                }
                second = checkNotNull(picked) { "no second extension could be installed from ${candidates.map { it.internalName }}" }.internalName
                log("second extension: $second")
            }
        }
        inst.runOnMainSync {
            NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.MARKET, true)
            NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.EXTENSIONS, true)
        }
    }

    @After fun tearDown() {
        inst.runOnMainSync {
            NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.MARKET, false)
            NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.EXTENSIONS, false)
        }
    }

    private fun openExtensions() {
        waitTag("nav_shortcut_extensions", 15_000); tap("nav_shortcut_extensions")
        waitTag("extensions_screen"); waitTag("extensions_list", 60_000)
    }

    @Test fun e1_e2_marketSearchThenReviewCancelThenInstall() {
        waitTag("nav_shortcut_market", 15_000); tap("nav_shortcut_market")
        waitTag("market_screen"); waitTag("market_list", 120_000)
        tap("market_search_toggle"); waitTag("market_search")
        rule.onNodeWithTag("market_search").performTextInput("Internet Archive")
        waitTag("market_install_$IA", 30_000)
        log("market search found $IA")

        // Cancel in the safety review: nothing installed, no error.
        tap("market_install_$IA"); waitTag("extension_review", 120_000)
        log("review: ${text("extension_review")}")
        tap("extension_review_cancel"); waitGone("extension_review")
        rule.waitForIdle()
        assertTrue("cancel must not install", installed(IA) == null)

        // Install for real.
        tap("market_install_$IA"); waitTag("extension_review", 120_000)
        if (has("extension_review_install")) tap("extension_review_install") else tap("extension_review_install_anyway")
        waitFor("Internet Archive installed and loaded", 120_000) { ExtensionManager.provider("Internet Archive") != null }
        log("E2_RESULT PASS installed $IA via review")
    }

    @Test fun e4_e6_e7_selectionSearchAndBulkActions() {
        runBlocking { withTimeout(180_000) {
            if (installed(IA) == null) ExtensionManager.install(ctx, ExtensionSources.all(ctx).items.first { it.internalName == IA }, ExtensionReview.Mode.AUTO)
        } }
        openExtensions()
        waitTag("extension_$IA"); waitTag("extension_$second")

        // E6: select all → both; bulk turn off.
        tap("extensions_select"); waitTag("extensions_selection_bar")
        tap("extensions_select_all")
        waitFor("2 selected") { text("extensions_selected_count").startsWith("2 selected") }
        tap("extensions_bulk_off")
        waitFor("both off") { !enabled(IA) && !enabled(second) }
        waitGone("extensions_selection_bar")
        log("bulk off: both off")

        // E7: search narrows the list; select all acts only on what is shown.
        tap("extensions_search_toggle"); waitTag("extensions_search")
        rule.onNodeWithTag("extensions_search").performTextInput("Internet")
        waitGone("extension_$second"); waitTag("extension_$IA")
        tap("extensions_select"); waitTag("extensions_selection_bar")
        tap("extensions_select_all")
        waitFor("1 selected") { text("extensions_selected_count").startsWith("1 selected") }
        tap("extensions_bulk_on")
        waitFor("IA on, the other still off") { enabled(IA) && !enabled(second) }
        tap("extensions_search_toggle") // closes and clears the search
        waitTag("extension_$second")
        log("search + select all on shown only: pass")

        // E4: the single switch.
        tap("extension_toggle_$second")
        waitFor("second on by its switch") { enabled(second) }

        // Back leaves selection mode, not the screen.
        tap("extensions_select"); waitTag("extensions_selection_bar")
        back(); waitGone("extensions_selection_bar"); assertTrue(has("extensions_screen"))

        // E6: long-press selects one; bulk remove asks first, then removes only that one.
        // Press the title row: the card's centre is its chip row (Auto-update, Check for update), whose chips take the press.
        rule.onNodeWithTag("extension_$second", true).performTouchInput { longClick(percentOffset(0.4f, 0.12f)) }
        waitTag("extensions_selection_bar")
        waitFor("1 selected by long-press") { text("extensions_selected_count").startsWith("1 selected") }
        tap("extensions_bulk_remove"); waitTag("extensions_bulk_confirm_remove")
        tap("extensions_bulk_confirm_remove")
        waitFor("second removed, IA kept") { installed(second) == null && installed(IA) != null }
        waitGone("extension_$second")
        log("E6_RESULT PASS select all, search scope, bulk off/on/remove, long-press, back")
    }

    @Test fun e8_brokenLinkShowsAnErrorInTheDialog() {
        openExtensions()
        tap("extensions_add"); waitTag("extensions_add_link"); tap("extensions_add_link")
        waitTag("input_dialog_field")
        rule.onNodeWithTag("input_dialog_field").performTextInput("https://yumeiro-test.invalid/Broken.cs3")
        tap("input_dialog_confirm")
        waitTag("input_dialog_error", 60_000)
        val err = text("input_dialog_error")
        log("E8 error text: $err")
        assertTrue("the error must be readable", err.isNotBlank())
        assertFalse("no stack trace in the message", err.contains("Exception") || err.contains("at dev."))
        // The first Back only hides the keyboard (standard Android); Cancel closes the dialog.
        tap("input_dialog_cancel"); waitGone("input_dialog_field")
        assertTrue(has("extensions_screen"))
        log("E8_RESULT PASS")
    }

    @Test fun e3_missingFileIsDownloadedAgain() = runBlocking<Unit> {
        withTimeout(240_000) {
            if (installed(IA) == null) ExtensionManager.install(ctx, ExtensionSources.all(ctx).items.first { it.internalName == IA }, ExtensionReview.Mode.AUTO)
            val ext = checkNotNull(installed(IA))
            val f = ExtensionManager.pluginFile(ctx, ext)
            f.setWritable(true); assertTrue("delete the file like a restore without files", f.delete())
            assertEquals(listOf(IA), ExtensionManager.missingFiles(ctx).map { it.internalName }.filter { it == IA })
            val (ok, failed) = ExtensionManager.repairMissing(ctx)
            log("repair: ok=$ok failed=$failed")
            assertEquals(0, failed)
            assertTrue("file is back", ExtensionManager.pluginFile(ctx, checkNotNull(installed(IA))).exists())
            log("E3_RESULT PASS")
        }
    }

    /** E11: Source status loads the installed sources (even when opened right after start) and checks them off the main thread. */
    @Test fun e11_sourceStatusChecksInstalledSources() {
        inst.runOnMainSync { NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.HEALTH, true) }
        try {
            waitTag("nav_shortcut_health", 15_000); tap("nav_shortcut_health")
            waitTag("source_health_run")
            waitFor("the installed source is listed") { rule.onAllNodesWithText("Not checked", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            assertFalse("no false empty state", has("source_status_empty"))
            tap("source_health_run")
            waitFor("the check finished", 90_000) { rule.onAllNodesWithText(" working", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            log("E11_RESULT PASS")
            back(); waitGone("source_health_run")
        } finally { inst.runOnMainSync { NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.HEALTH, false) } }
    }

    /** E12: a website-address override is normalised, applied at once, kept, and cleared back to the shipped address. */
    @Test fun e12_websiteAddressOverride() {
        val api = checkNotNull(ExtensionManager.providers().firstOrNull()) { "no provider loaded" }
        val shipped = api.mainUrl
        try {
            dev.asfakul.yumeiro.online.ProviderOverrides.setMainUrl(api, " mirror.yumeiro-test.invalid/ ")
            assertEquals("https://mirror.yumeiro-test.invalid", api.mainUrl)
            assertEquals("https://mirror.yumeiro-test.invalid", dev.asfakul.yumeiro.online.ProviderOverrides.mainUrl(api.name))
            assertEquals(shipped, dev.asfakul.yumeiro.online.ProviderOverrides.defaultUrl(api))
        } finally { dev.asfakul.yumeiro.online.ProviderOverrides.setMainUrl(api, null) }
        assertEquals(shipped, api.mainUrl)
        assertEquals(null, dev.asfakul.yumeiro.online.ProviderOverrides.mainUrl(api.name))
        log("E12_RESULT PASS")
    }

    companion object { const val IA = "InternetArchiveProvider" }
}
