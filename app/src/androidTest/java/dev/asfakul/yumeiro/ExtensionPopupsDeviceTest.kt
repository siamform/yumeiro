/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.AlertDialog
import android.util.Log
import android.view.View
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionPopups
import dev.asfakul.yumeiro.online.ExtensionGuard
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import dev.asfakul.yumeiro.ui.components.AppMessages
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.yumeirotest.fakeext.FakeExtension

/**
 * ADR-014 on a device: windows opened by extension code (the [FakeExtension] test extension) go
 * through the guarded window manager. Counts are of the process's real root windows
 * (WindowManagerGlobal), focus is read from `dumpsys window`, Back is a real key event.
 */
@RunWith(AndroidJUnit4::class)
class ExtensionPopupsDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private fun log(s: String) = Log.i("YuPopupTest", s)

    /** Root windows of this process a user can see (concealed extension windows are invisible). */
    @Suppress("UNCHECKED_CAST")
    private fun allRoots(): List<View> =
        if (android.os.Build.VERSION.SDK_INT >= 29) android.view.inspector.WindowInspector.getGlobalWindowViews()
        else Class.forName("android.view.WindowManagerGlobal").let { cls ->
            val g = cls.getMethod("getInstance").invoke(null)
            ArrayList(cls.getDeclaredField("mViews").apply { isAccessible = true }.get(g) as List<View>)
        }

    private fun roots(): Int = ui {
        allRoots().count { v ->
            val lp = v.layoutParams as? android.view.WindowManager.LayoutParams
            v.isAttachedToWindow && v.visibility == View.VISIBLE && (lp == null || lp.alpha > 0f)
        }
    }

    /** Every visible root window by title, type and alpha, so a count mismatch names the window. */
    private fun rootNames(): String = ui {
        allRoots().filter { it.isAttachedToWindow && it.visibility == View.VISIBLE }.joinToString { v ->
            val lp = v.layoutParams as? android.view.WindowManager.LayoutParams
            "${lp?.title}/t${lp?.type}/a${lp?.alpha}/${v.javaClass.simpleName}"
        }
    }

    private fun <T> ui(block: () -> T): T { var r: Result<T>? = null; inst.runOnMainSync { r = runCatching(block) }; inst.waitForIdleSync(); return r!!.getOrThrow() }
    private fun settle() { inst.waitForIdleSync(); Thread.sleep(600); inst.waitForIdleSync() }
    private fun focus(): String = FirstRun.focusedWindow()
    private fun focusIsDialog(): Boolean = FirstRun.isDialogType(FirstRun.windowType(focus()))
    private fun back() { FirstRun.shell("input keyevent 4"); settle() }
    /** The card of the test fake ("An extension" = no installed extension owns its class), not a real extension's. */
    private fun fakeCard() = AppMessages.current.value?.takeIf { it.text.startsWith("An extension ") }
    private fun fakeHeld() = ExtensionPopups.held.value.filter { it.extName == "An extension" }
    private fun resetPrefs() = ctx.getSharedPreferences("extension_popups", 0).edit().clear().commit()

    @Before fun setUp() {
        FirstRun.toTabs(rule)
        ExtensionPopups.testExtensionPrefix = "org.yumeirotest."
        ExtensionPopups.testPlayerOnScreen = null
        resetPrefs()
        ExtensionPopups.setMode(ctx, ExtensionPopups.Mode.QUIET)
        ExtensionPopups.held.value.forEach { ExtensionPopups.discard(it.id) }
        // Tests share one process: a card left by an earlier test must not count for this one.
        AppMessages.current.value?.let { AppMessages.dismiss(it.id) }
        log("setUp roots=${roots()} focus=${focus()}")
    }

    @After fun tearDown() {
        ExtensionPopups.held.value.forEach { ExtensionPopups.discard(it.id) }
        ExtensionPopups.setMode(ctx, ExtensionPopups.Mode.QUIET)
        ExtensionPopups.testPlayerOnScreen = null
        ExtensionPopups.testExtensionPrefix = null
        resetPrefs()
    }

    /** Quiet (default): a donation prompt opens no window, keeps Back for the app, shows one card; "Show" opens it framed and Back closes only it. */
    @Test fun p1_quietHoldsDonationAndBackStaysWithTheApp() {
        val before = roots()
        val dialog = ui { FakeExtension.donation(rule.activity) }
        settle()
        log("p1 after donation roots=${roots()} focus=${focus()} card=${AppMessages.current.value?.text}")
        assertEquals("a promotional window was added", before, roots())
        assertFalse("focus moved to a dialog: ${focus()}", focusIsDialog())
        // Only the fake's own window counts: extensions installed by an earlier class (ExtensionCompat)
        // load in this process and may hold their own start-up prompt meanwhile (run 44509508921).
        fun mine() = ExtensionPopups.held.value.filter { it.view === dialog.window?.decorView }
        assertEquals(1, mine().size)
        assertTrue("no quiet card: ${AppMessages.current.value}", AppMessages.current.value?.text?.contains("has a message") == true)
        val drawn = runCatching { rule.waitUntil(4_000) { rule.onAllNodesWithTag("app_message", true).fetchSemanticsNodes().isNotEmpty() }; true }.getOrDefault(false)
        log("p1 card on screen=$drawn · ${FirstRun.onScreen(rule).take(300)}")
        assertTrue("quiet card not on screen", drawn)
        FirstRun.shot("adr014_quiet_card")
        // Diagnostics: where the card is, and what Compose itself draws for it (uiAutomation shots
        // of the first run showed no card although the node existed).
        runCatching {
            val n = rule.onAllNodesWithTag("app_message", true).fetchSemanticsNodes().first()
            log("p1 card bounds=${n.boundsInWindow} root=${rule.activity.window.decorView.width}x${rule.activity.window.decorView.height} alpha=${rule.activity.window.attributes.alpha}")
            val img = rule.onAllNodesWithTag("app_message", true)[0].captureToImage().asAndroidBitmap()
            val dir = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "shots").apply { mkdirs() }
            java.io.File(dir, "adr014_quiet_card_node.png").outputStream().use { img.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
        }.onFailure { log("p1 card capture failed: $it") }
        Thread.sleep(700); rule.waitForIdle()
        FirstRun.shot("adr014_quiet_card_late")
        // The extension still believes its dialog is open, and closing it must not crash.
        assertTrue(dialog.isShowing)

        val held = mine().single(); val id = held.id
        // The card on screen is the fake's (another extension's card waits behind it, ER-29).
        val card = AppMessages.current.value
        assertTrue("card on screen is not the fake's: ${card?.text}", card?.text?.contains(held.extName) == true)
        // The user's path: tap "Show" on the card.
        rule.onAllNodesWithTag("app_message_action", true)[0].performClick()
        settle()
        assertTrue("held entry kept after Show", ExtensionPopups.held.value.none { it.id == id })
        // Run 44512112484: AniKoto's own start-up card (queued behind ours) came next; ours must be gone.
        assertTrue("card stayed after Show: ${AppMessages.current.value}", AppMessages.current.value?.id != card!!.id)
        log("p1 revealed roots=${roots()} focus=${focus()}")
        assertEquals("revealed window not added", before + 1, roots())
        assertTrue("revealed dialog not focused: ${focus()}", focusIsDialog())
        FirstRun.shot("adr014_revealed_donation")
        back()
        log("p1 after Back roots=${roots()} focus=${focus()}")
        assertFalse("Back did not close the revealed dialog", dialog.isShowing)
        assertEquals(before, roots())
        assertFalse("activity finished by Back", rule.activity.isFinishing)
    }

    /** At most one card per extension per week: the second prompt is dropped without a window or card. */
    @Test fun p2_secondPromptInAWeekIsDropped() {
        val before = roots()
        val first = ui { FakeExtension.donation(rule.activity) }; settle()
        ui { AppMessages.current.value?.let { AppMessages.dismiss(it.id) } }
        val second = ui { FakeExtension.donation(rule.activity) }; settle()
        log("p2 roots=${roots()} held=${fakeHeld().size} card=${AppMessages.current.value?.text}")
        assertEquals(before, roots())
        assertEquals("second prompt replaced or added a held message", 1, fakeHeld().size)
        assertNull("second card shown", fakeCard())
        ui { first.dismiss(); second.dismiss() }; settle() // removing windows that were never added: no crash
        assertEquals(0, fakeHeld().size)
    }

    /** A login form is functional: shown at once (framed), focused, and Back closes it and nothing else. */
    @Test fun p3_loginShownAndBackClosesOnlyIt() {
        val before = roots()
        val dialog: AlertDialog = ui { FakeExtension.login(rule.activity) }
        settle()
        log("p3 login roots=${roots()} focus=${focus()}")
        assertEquals("login window not shown", before + 1, roots())
        assertTrue("login not focused: ${focus()}", focusIsDialog())
        FirstRun.shot("adr014_login_framed")
        FirstRun.shell("input keyevent 4") // the first Back may only hide the keyboard
        settle()
        if (dialog.isShowing) back()
        log("p3 after Back roots=${roots()} focus=${focus()} showing=${dialog.isShowing}")
        assertFalse("Back did not close the login dialog", dialog.isShowing)
        assertEquals(before, roots())
        assertFalse("Back closed the app screen too", rule.activity.isFinishing)
        assertTrue("app screen gone after Back: ${FirstRun.onScreen(rule)}", FirstRun.hasNav(rule, "nav_home") || FirstRun.onScreen(rule).isNotBlank())
    }

    @Test fun p4_showModeShowsEverything() {
        ExtensionPopups.setMode(ctx, ExtensionPopups.Mode.SHOW)
        val before = roots()
        val d = ui { FakeExtension.donation(rule.activity) }; settle()
        assertEquals(before + 1, roots())
        assertTrue(focusIsDialog())
        ui { d.dismiss() }; settle()
        assertEquals(before, roots())
    }

    @Test fun p5_blockModeDropsPromotionalKeepsLogin() {
        ExtensionPopups.setMode(ctx, ExtensionPopups.Mode.BLOCK_PROMOTIONAL)
        val before = roots()
        val d = ui { FakeExtension.donation(rule.activity) }; settle()
        assertEquals(before, roots())
        assertEquals(0, fakeHeld().size)
        assertNull(fakeCard())
        val l = ui { FakeExtension.login(rule.activity) }; settle()
        assertEquals("login must still show when promotional is blocked", before + 1, roots())
        ui { d.dismiss(); l.dismiss() }; settle()
        assertEquals(before, roots())
    }

    /** Never over the player: the card waits until the video closes. */
    @Test fun p6_cardWaitsForThePlayer() {
        ExtensionPopups.testPlayerOnScreen = true
        AppMessages.current.value?.let { AppMessages.dismiss(it.id) }
        val before = roots()
        val dialog = ui { FakeExtension.donation(rule.activity) }; settle()
        assertEquals(before, roots())
        assertNull("card shown during playback", fakeCard())
        ExtensionPopups.testPlayerOnScreen = null
        settle()
        assertTrue("card not shown after playback", AppMessages.current.value?.text?.contains("has a message") == true)
        ui { runCatching { dialog.dismiss() } }
    }

    /**
     * Main-thread guard: an extension that shows a window after its screen is gone (BadToken, thrown
     * inside ViewRootImpl.setView) and an extension callback that throws on the main thread are
     * swallowed by ExtensionGuard; the app keeps running. Before ADR-014 the first one crashed:
     * ViewRootImpl on the stack counted as "inside a frame".
     */
    @Test fun p7_deadTokenAndThrowingCallbackDoNotCrash() {
        val before = ExtensionGuard.swallowedCount
        FakeExtension.postDeadTokenWindow(rule.activity)
        settle()
        assertTrue("dead-token window was not swallowed", ExtensionGuard.swallowedCount > before)
        val mid = ExtensionGuard.swallowedCount
        FakeExtension.postThrowingCallback()
        settle()
        assertTrue("throwing extension callback was not swallowed", ExtensionGuard.swallowedCount > mid)
        assertFalse(rule.activity.isFinishing)
        log("p7 swallowed=${ExtensionGuard.swallowedCount} last=${ExtensionGuard.lastSwallowed}")
    }

    /**
     * The Extensions screen setting (look changed: new card). A held message is listed with Show and
     * Dismiss; the mode chips switch and are saved; Dismiss removes the entry and its card.
     */
    @Test fun p8_extensionsScreenListsHeldMessagesAndSwitchesMode() {
        val before = roots()
        val dialog = ui { FakeExtension.donation(rule.activity) }; settle()
        assertEquals(1, fakeHeld().size)
        inst.runOnMainSync { dev.asfakul.yumeiro.ui.navigation.NavPrefs.setShortcut(ctx, dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.EXTENSIONS, true) }
        try {
            // The TV rail tags its items "tv_" + the bar tag (TvNavRail); run 43865392890 waited for
            // the phone tag on the TV emulator (test error).
            val tags = listOf("nav_shortcut_extensions", "tv_nav_shortcut_extensions")
            rule.waitUntil(15_000) { tags.any { rule.onAllNodesWithTag(it, true).fetchSemanticsNodes().isNotEmpty() } }
            val tag = tags.first { rule.onAllNodesWithTag(it, true).fetchSemanticsNodes().isNotEmpty() }
            log("p8 shortcut tag=$tag")
            rule.onAllNodesWithTag(tag, true)[0].performClick()
            rule.waitUntil(15_000) { rule.onAllNodesWithTag("extensions_screen", true).fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(60_000) { rule.onAllNodesWithTag("extensions_list", true).fetchSemanticsNodes().isNotEmpty() }
            rule.onAllNodesWithTag("extensions_list", true)[0].performScrollToNode(hasTestTag("extensions_popups"))
            rule.waitForIdle()
            assertTrue("held row missing", rule.onAllNodesWithTag("extensions_popup_held", true).fetchSemanticsNodes().isNotEmpty())
            // The list replaces the quiet card; before the fix the card covered Dismiss and a tap
            // on Dismiss opened the donation dialog (43823291873, both phones).
            rule.waitUntil(5_000) { AppMessages.current.value == null }
            FirstRun.shot("adr014_extensions_setting")
            rule.onAllNodesWithTag("extensions_popups_block_promotional", true)[0].performClick(); rule.waitForIdle()
            assertEquals(ExtensionPopups.Mode.BLOCK_PROMOTIONAL, ExtensionPopups.mode.value)
            rule.onAllNodesWithTag("extensions_popups_quiet", true)[0].performClick(); rule.waitForIdle()
            assertEquals(ExtensionPopups.Mode.QUIET, ExtensionPopups.mode.value)
            // Baseline right before Dismiss: the Extensions screen may own windows of its own that
            // were not there when `before` was taken on Home (run 43812110468 failed on that).
            val atDismiss = roots(); val namesAtDismiss = rootNames()
            log("p8 before=$before atDismiss=$atDismiss roots=[$namesAtDismiss]")
            // Only the fake's row stays, so [0] is it (a real extension's start-up prompt may be listed too).
            ui { ExtensionPopups.held.value.filter { it.extName != "An extension" }.forEach { ExtensionPopups.discard(it.id) } }; rule.waitForIdle()
            rule.onAllNodesWithTag("extensions_popup_discard", true)[0].performClick(); rule.waitForIdle()
            assertTrue("held entry kept after Dismiss", fakeHeld().isEmpty())
            assertNull("card stayed after Dismiss", fakeCard())
            val after = roots(); val namesAfter = rootNames()
            log("p8 afterDismiss=$after roots=[$namesAfter]")
            assertEquals("Dismiss added a window: before=[$namesAtDismiss] after=[$namesAfter]", atDismiss, after)
            ui { runCatching { dialog.dismiss() } }
            assertFalse(rule.activity.isFinishing)
            log("p8 ok focus=${focus()}")
        } finally {
            inst.runOnMainSync { dev.asfakul.yumeiro.ui.navigation.NavPrefs.setShortcut(ctx, dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.EXTENSIONS, false) }
        }
    }
}
