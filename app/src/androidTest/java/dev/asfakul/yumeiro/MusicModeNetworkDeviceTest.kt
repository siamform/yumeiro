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
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.network.Connectivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G6-MO1 on the emulator with the real network turned off and on (`svc wifi/data`). The user
 * picks Online on the Music tab; with no connection the tab must show On device; when the
 * connection is back it must return to Online, the mode the user picked. Then the user picks On
 * device; a drop and a return must leave it on On device. Log tag YuMusicMode: MM_… lines.
 */
@RunWith(AndroidJUnit4::class)
class MusicModeNetworkDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun log(s: String) = Log.i("YuMusicMode", s)
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
    private fun isOnlineMode() = mode().let { "Online" in it && "On device" !in it }
    private fun isDeviceMode() = "On device" in mode()
    private fun net(on: Boolean) { val v = if (on) "enable" else "disable"; FirstRun.shell("svc wifi $v"); FirstRun.shell("svc data $v") }
    private fun pick(tag: String) {
        tap("music_source_switch")
        assertTrue("no $tag in the source menu", waitFor(5_000) { has(tag) })
        tap(tag); SystemClock.sleep(800)
    }

    @After fun restore() = net(true)

    @Test fun pickedModeSurvivesAConnectionDrop() {
        FirstRun.toTabs(rule)
        assumeTrue("emulator has no connection to start with", waitFor(20_000) { Connectivity.online.value })
        tap("nav_tab_music")
        assertTrue("no music source switch", waitFor(15_000) { has("music_source_switch") })

        pick("music_source_online")
        assertTrue("Online not picked: [${mode()}]", waitFor(5_000) { isOnlineMode() })
        net(false)
        val off = waitFor(30_000) { !Connectivity.online.value }
        val toDevice = waitFor(10_000) { isDeviceMode() }
        FirstRun.shot("mm_offline")
        log("MM_OFFLINE signalOff=$off mode=[${mode()}]")
        net(true)
        val back = waitFor(60_000) { Connectivity.online.value }
        val toOnline = waitFor(10_000) { isOnlineMode() }
        FirstRun.shot("mm_back")
        log("MM_BACK signalBack=$back mode=[${mode()}]")
        assumeTrue("network toggle not seen by the app (off=$off back=$back)", off && back)
        assertTrue("offline must leave Online", toDevice)
        assertTrue("connection back: the picked Online must return", toOnline)

        pick("music_source_device")
        net(false); waitFor(30_000) { !Connectivity.online.value }
        net(true); waitFor(60_000) { Connectivity.online.value }
        SystemClock.sleep(2_000)
        val stays = isDeviceMode()
        log("MM_DEVICE_PICK stays=$stays mode=[${mode()}]")
        assertTrue("a picked On device must stay On device after a drop and return", stays)
        log("MM_RESULT PASS")
    }
}
