/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.network.Connectivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G6-PG2 guard on the emulator, the whole user path: Music → Online Home, scroll past the
 * continuation pages into the endless "Similar to …" shelves, lose the connection (Music shows
 * On device, G6-MO1), get it back (Online again), scroll on: new "Similar to" shelves must still
 * come. Since MO1 the offline calls themselves can't be reached from the screen (offline leaves
 * Online), so this is a guard, not a fail-before; the offline/cap rules are unit tests
 * (YmEndlessOfflineTest, YmEndlessCapTest). From CI addresses YouTube refuses the related call
 * (bot wall), so the test skips there; it is the owner's OMT-29 on a home connection. Log tag YuEndless: EN_… lines, EN_RESULT PASS.
 */
@RunWith(AndroidJUnit4::class)
class MusicEndlessNetworkDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun log(s: String) = Log.i("YuEndless", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun waitFor(ms: Long, ok: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) { if (ok()) return true; SystemClock.sleep(300) }
        return ok()
    }
    // FINAL-34 (run 44660329194, tv-api24): the first node with a nav tag can be the phone bar's twin laid
    // out off screen on TV ("Failed to inject touch input"). Tap the one on screen, or the rail's twin.
    private fun tap(tag: String) = FirstRun.tapVisible(rule, tag)
    private fun mode(): String = runCatching {
        rule.onAllNodesWithTag("music_source_switch", false).fetchSemanticsNodes().first().config.let { c ->
            (c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: "") + " " + (c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: "")
        }
    }.getOrDefault("")
    private fun net(on: Boolean) { val v = if (on) "enable" else "disable"; FirstRun.shell("svc wifi $v"); FirstRun.shell("svc data $v") }
    private val seen = linkedSetOf<String>()
    private fun collect() = runCatching {
        rule.onAllNodesWithText("Similar to ", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } }
            .filter { it.startsWith("Similar to ") }.forEach { seen += it }
    }
    /** A few texts on screen, for the log when the endless shelves never show. */
    private fun texts(): List<String> = runCatching {
        rule.onAllNodesWithTag("youtube_music_list", true)[0].fetchSemanticsNode().let { root ->
            fun walk(n: androidx.compose.ui.semantics.SemanticsNode): List<String> =
                (n.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + n.children.flatMap { walk(it) }
            walk(root).filter { it.length > 2 }.distinct()
        }
    }.getOrDefault(emptyList())
    /** Swipes to the end of the Home list until [ok] or [ms] pass. */
    private fun scrollUntil(ms: Long, ok: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + ms
        var round = 0
        while (SystemClock.uptimeMillis() < end) {
            if (++round % 10 == 0) log("EN_ROUND $round list=${has("youtube_music_list")} similar=${seen.size} texts=${texts().take(6)}")
            if (!has("youtube_music_list")) { SystemClock.sleep(500); continue }
            repeat(3) { runCatching { rule.onAllNodesWithTag("youtube_music_list", true)[0].performTouchInput { swipeUp(durationMillis = 250) } }; collect() }
            rule.waitForIdle(); SystemClock.sleep(1_200); collect()
            if (ok()) return true
        }
        return ok()
    }

    @After fun restore() = net(true)

    @Test fun endlessHomeCarriesOnAfterAConnectionDrop() {
        FirstRun.toTabs(rule)
        assumeTrue("emulator has no connection to start with", waitFor(20_000) { Connectivity.online.value })
        tap("nav_tab_music")
        assertTrue("no music source switch", waitFor(15_000) { has("music_source_switch") })
        tap("music_source_switch"); assertTrue(waitFor(5_000) { has("music_source_online") }); tap("music_source_online")
        val home = waitFor(45_000) { has("youtube_music_list") }
        assumeTrue("YouTube Music Home did not load from the CI network", home)

        val reached = scrollUntil(240_000) { seen.isNotEmpty() }
        log("EN_REACHED endless=$reached similar=${seen.size} first=${seen.firstOrNull()} texts=${texts().take(10)}")
        if (!reached) FirstRun.shot("en_not_reached")
        // On CI every "Similar to" call meets YouTube's bot wall (log YouTube: SignInConfirmNotBotException,
        // runs 44242208629 / 44243818628), so this can only run on a home connection: OMT-29.
        assumeTrue("never reached the endless shelves: on CI YouTube's watch page asks to sign in (OMT-29)", reached)
        val before = seen.size
        FirstRun.shot("en_before_drop")

        net(false)
        val off = waitFor(30_000) { !Connectivity.online.value }
        val device = waitFor(10_000) { "On device" in mode() }
        log("EN_OFFLINE signalOff=$off mode=[${mode()}]")
        SystemClock.sleep(8_000)
        net(true)
        val back = waitFor(60_000) { Connectivity.online.value }
        val online = waitFor(15_000) { mode().let { "Online" in it && "On device" !in it } && has("youtube_music_list") }
        log("EN_BACK signalBack=$back online=$online mode=[${mode()}]")
        assumeTrue("network toggle not seen by the app (off=$off back=$back)", off && back)
        assertTrue("offline must show On device (MO1)", device)
        assertTrue("connection back: Online Home must return", online)

        val grew = scrollUntil(180_000) { seen.size > before }
        FirstRun.shot("en_after")
        log("EN_AFTER grew=$grew similar=${seen.size} (before $before) last=${seen.lastOrNull()}")
        assertTrue("no new 'Similar to' shelf after the connection came back (had $before)", grew)
        log("EN_RESULT PASS")
    }
}
