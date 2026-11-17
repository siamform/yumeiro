/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.KeyguardManager
import android.content.Context
import android.util.Log
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.security.Privacy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G13 N6 (app lock) on a device, through the real MainActivity and the system prompt.
 *  a (N6-1) The lock is on but the phone has no screen lock (PIN removed after turning it on):
 *    the gate must not stay up for good. Expected: the lock turns itself off, the app opens, the
 *    saved setting is off. Old code: the gate stays (every prompt fails at once).
 *  b The lock is on and the phone has a PIN: the gate shows, the PIN typed into the system prompt
 *    opens the app, and the lock stays on.
 * The emulator's PIN is always cleared afterwards. Log tag YuLockTest, LOCK_CASE / LOCK_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class AppLockDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val compose = createEmptyComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val prefs get() = ctx.getSharedPreferences("privacy", Context.MODE_PRIVATE)
    private var saved: Map<String, *> = emptyMap<String, Any?>()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var pinSet = false
    private fun log(s: String) = Log.i("YuLockTest", s)
    private fun gate() = runCatching { compose.onAllNodesWithTag("app_lock", true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    /** Plain polling: works while another activity (the system PIN screen) hides the app's Compose tree. */
    private fun waitFor(ms: Long, ok: () -> Boolean): Boolean {
        val end = android.os.SystemClock.uptimeMillis() + ms
        while (android.os.SystemClock.uptimeMillis() < end) { if (ok()) return true; Thread.sleep(100) }
        return ok()
    }
    private fun secure() = ctx.getSystemService(KeyguardManager::class.java).isDeviceSecure

    @Before fun setUp() {
        FirstRun.grant()
        saved = HashMap(prefs.all)
        prefs.edit().putBoolean("lock", true).putInt("lock_after", 0).putBoolean("hide_recents", false).commit()
        // Privacy is a process object: make it read the file again, as after a fresh start.
        Privacy::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Privacy, false)
        Privacy::class.java.getDeclaredField("backgroundAt").apply { isAccessible = true }.setLong(Privacy, 0L)
    }

    @After fun tearDown() {
        runCatching { scenario?.close() }
        if (pinSet || secure()) runCatching { FirstRun.shell("locksettings clear --old $PIN") }
        prefs.edit().clear().apply {
            saved.forEach { (k, v) -> when (v) { is Boolean -> putBoolean(k, v); is Int -> putInt(k, v); is String -> putString(k, v); is Long -> putLong(k, v) } }
        }.commit()
        runCatching { Privacy::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(Privacy, false) }
        runCatching { Privacy.load(ctx) }
    }

    @Test fun a_lockWithoutScreenLockDoesNotTrapTheUser() {
        assumeTrue("emulator already has a screen lock", !secure())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // Give the prompt time to fail (old code) or the release to happen (new code); tap Unlock once.
        runCatching { compose.waitUntil(8_000) { !gate() } }
        if (gate()) runCatching { compose.onNodeWithText("Unlock").performClick() }
        runCatching { compose.waitUntil(5_000) { !gate() } }
        val stillLocked = gate()
        log("LOCK_CASE a gate=$stillLocked lockEnabled=${Privacy.lockEnabled.value} saved=${prefs.getBoolean("lock", true)}")
        assertFalse("a: app stayed locked with no screen lock on the phone", stillLocked)
        assertFalse("a: lock setting still on in memory", Privacy.lockEnabled.value)
        assertFalse("a: lock setting still saved as on", prefs.getBoolean("lock", true))
        log("LOCK_RESULT a PASS")
    }

    @Test fun b_pinOpensTheAppAndTheLockStaysOn() {
        assumeTrue("emulator already has a screen lock", !secure())
        val out = FirstRun.shell("locksettings set-pin $PIN")
        pinSet = secure()
        log("LOCK_CASE b set-pin secure=$pinSet out=${out.trim().take(80)}")
        assumeTrue("could not set a PIN on this emulator", pinSet)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // Run 44067357672 (api28): Android 9's fallback PIN screen (N6-3) is its own activity; while it is on
        // top the app's Compose tree is not visible to the test, so the gate is read from Privacy.locked
        // (what the gate shows) and "opened" means the lock really let go, not "no gate node found".
        waitFor(10_000) { Privacy.locked.value || gate() }
        assertTrue("b: the app opened without asking for the PIN", Privacy.locked.value)
        // The system prompt (BiometricPrompt with device credential) shows a PIN field.
        var opened = false
        for (attempt in 1..3) {
            Thread.sleep(2_500)
            FirstRun.shell("input text $PIN")
            FirstRun.shell("input keyevent 66")
            opened = waitFor(6_000) { !Privacy.locked.value }
            log("LOCK_CASE b attempt=$attempt opened=$opened window=${FirstRun.focusedWindow().take(120)}")
            if (opened) break
            // Prompt not up yet or dismissed: ask again the way the user would (Unlock button = Enter).
            FirstRun.shell("input keyevent 66")
        }
        assertTrue("b: the PIN did not open the app", opened)
        runCatching { compose.waitUntil(5_000) { !gate() } }
        assertFalse("b: gate still on screen after the PIN", gate())
        assertTrue("b: lock turned off after a good PIN", Privacy.lockEnabled.value)
        assertEquals("b: saved lock setting", true, prefs.getBoolean("lock", false))
        log("LOCK_RESULT b PASS")
    }

    private companion object { const val PIN = "1357" }
}
