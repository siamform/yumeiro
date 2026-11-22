/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.backup.Restart
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.ui.screens.profiles.ProfileSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * OWN-15: a locked profile, the app swiped away, opened again hours later. The process is new
 * (each class runs in a fresh instrumentation process, like a cold start), so the real path is:
 * MainActivity.onCreate → ProfileSession.decide → "Who's watching?" → PIN → the app.
 *  a The current profile is locked: unlocking it must open the app in place and keep it open
 *    (no crash, no new crash report, the activity stays resumed, the logo goes away).
 *  b Another profile is locked: unlocking it saves it as current and the one-minute pass, and asks
 *    for the restart; the next start (decide again) goes straight in without the gate.
 * Log tag YuOwn15.
 */
@RunWith(AndroidJUnit4::class)
class ProfileColdStartDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val compose = createEmptyComposeRule()
    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val added = mutableListOf<String>()
    private fun log(s: String) = Log.i("YuOwn15", s)
    private fun crashes(): Set<String> = File(ctx.filesDir, "crash").listFiles()?.map { it.name }?.toSet().orEmpty()
    private fun has(tag: String) = runCatching { compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun waitFor(what: String, ms: Long, ok: () -> Boolean) {
        val end = android.os.SystemClock.uptimeMillis() + ms
        while (android.os.SystemClock.uptimeMillis() < end) { if (ok()) return; Thread.sleep(150) }
        if (!ok()) throw AssertionError("timed out after $ms ms: $what")
    }
    private fun typePin(pin: String) {
        waitFor("PIN pad", 15_000) { has("pin_pad") }
        pin.forEach { d -> compose.onAllNodesWithTag("pin_key_$d", true)[0].performClick(); compose.waitForIdle() }
        if (has("pin_key_ok")) compose.onAllNodesWithTag("pin_key_ok", true)[0].performClick()
        compose.waitForIdle()
    }
    private fun pick(id: String) {
        waitFor("Who's watching", 30_000) { has("profile_gate") }
        compose.onAllNodesWithTag("profile_pick_$id", true)[0].performClick()
        compose.waitForIdle()
    }

    @After fun tearDown() {
        Restart.testHook = null
        runCatching { scenario?.close() }
        Profiles.select(ctx, Profiles.DEFAULT)
        Profiles.setPin(ctx, Profiles.DEFAULT, null)
        added.forEach { runCatching { Profiles.remove(ctx, it) } }
    }

    @Test fun a_currentLockedProfileOpensAfterPinAndStays() {
        FirstRun.grant()
        Profiles.select(ctx, Profiles.DEFAULT)
        assertTrue(Profiles.setPin(ctx, Profiles.DEFAULT, "1234"))
        ProfileSession.chosen.value = false
        val before = crashes()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        pick(Profiles.DEFAULT)
        typePin("1234")
        waitFor("the app after the PIN", 30_000) { !has("profile_gate") && !has("pin_pad") }
        waitFor("the logo to go", 15_000) { !has("splash_screen_root") }
        Thread.sleep(8_000) // the start-up work that waited behind the gate runs now
        val state = scenario!!.state
        val after = crashes() - before
        log("OWN15_A state=$state chosen=${ProfileSession.chosen.value} newCrash=$after")
        assertTrue("new crash report after unlock: $after", after.isEmpty())
        assertEquals(Lifecycle.State.RESUMED, state)
        assertTrue("gate still up", !has("profile_gate"))
    }

    @Test fun b_otherLockedProfileRestartsStraightIn() {
        FirstRun.grant()
        Profiles.select(ctx, Profiles.DEFAULT)
        val kid = Profiles.add(ctx, "Own15", dev.asfakul.yumeiro.ui.theme.AccentTone.values().first()).id
        added += kid
        assertTrue(Profiles.setPin(ctx, kid, "4321"))
        ProfileSession.chosen.value = false
        var restarted = false
        Restart.testHook = { restarted = true }
        val before = crashes()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        pick(kid)
        typePin("4321")
        waitFor("the restart request", 15_000) { restarted }
        assertEquals(kid, Profiles.currentId(ctx))
        // The next process: decide() must let it in once, without the gate.
        ProfileSession.chosen.value = false
        ProfileSession.decide(ctx)
        log("OWN15_B restarted=$restarted current=${Profiles.currentId(ctx)} chosen=${ProfileSession.chosen.value}")
        assertTrue("the restart into the unlocked profile showed the gate again", ProfileSession.chosen.value)
        assertTrue("new crash report: ${crashes() - before}", (crashes() - before).isEmpty())
    }
}
