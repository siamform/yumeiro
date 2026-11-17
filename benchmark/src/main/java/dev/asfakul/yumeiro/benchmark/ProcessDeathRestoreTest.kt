/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.benchmark

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G1 H8 (re-audit): the open tab survives a real process death, the way Android kills a
 * background app and brings it back from Recents.
 *
 * The app-side `h8_openTabSurvivesRecreate` only recreates the activity in a live process, because
 * the app's own instrumentation dies with the process. This test runs in the benchmark module's
 * own process (self-instrumenting), so it can kill the app and watch it come back:
 * open the app (R8 preview build, what users run) → pick another bottom-bar tab → Home key (the
 * activity saves its state) → `am kill` (process gone, task and saved state kept) → relaunch from
 * the launcher intent (Android restores the task) → the same tab must be selected in a new process.
 */
@RunWith(AndroidJUnit4::class)
class ProcessDeathRestoreTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun log(s: String) = Log.i("YuRestoreTest", s)
    private fun pid(): String = device.executeShellCommand("pidof $TARGET").trim()

    /** Text or description of the selected item in the bottom bar (lowest selected node on screen). */
    private fun selectedTab(): String? {
        val h = device.displayHeight
        return device.findObjects(By.pkg(TARGET).selected(true))
            .filter { runCatching { it.visibleBounds.centerY() > h * 3 / 4 }.getOrDefault(false) }
            .maxByOrNull { it.visibleBounds.centerY() }
            ?.let { o -> o.text ?: o.contentDescription ?: o.children.firstNotNullOfOrNull { c -> c.text ?: c.contentDescription } }
    }

    private fun launch() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val intent = ctx.packageManager.getLaunchIntentForPackage(TARGET)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        Journeys.firstFrameSettles(device)
    }

    @Test fun openTabSurvivesProcessDeath() {
        Journeys.grantPermissions(device)
        device.executeShellCommand("am force-stop $TARGET")
        launch()
        Journeys.passWelcome(device)
        device.waitForIdle(2_000)
        val home = selectedTab()
        log("RESTORE_START pid=${pid()} selected=$home")
        // The bottom bar: tabs evenly across the width near the bottom (as Journeys.browseTabs).
        val w = device.displayWidth; val h = device.displayHeight
        var picked: String? = null
        for (i in listOf(2, 1, 3)) {
            device.click(w * (2 * i + 1) / 8, h - h / 16)
            device.waitForIdle(2_000)
            picked = selectedTab()
            if (picked != null && picked != home) break
        }
        Journeys.snapshot(device, "h8_before_kill")
        log("RESTORE_PICKED selected=$picked (home=$home)")
        assertTrue("could not select another tab (home=$home, now=$picked)", picked != null && picked != home)

        device.pressHome()
        device.wait(Until.gone(By.pkg(TARGET).depth(0)), 5_000)
        device.waitForIdle(2_000)
        val before = pid()
        device.executeShellCommand("am kill $TARGET")
        var gone = false
        repeat(20) { if (!gone) { gone = pid().isEmpty(); if (!gone) Thread.sleep(250) } }
        log("RESTORE_KILL pid before=$before after=${pid()} gone=$gone")
        assertTrue("am kill did not end the background process ($before)", gone)

        launch()
        device.waitForIdle(3_000)
        val after = pid()
        val restored = selectedTab()
        Journeys.snapshot(device, "h8_after_restore")
        log("RESTORE_RESULT pid=$after (was $before) selected=$restored (expected $picked)")
        assertNotEquals("the app did not start a new process", before, after)
        assertTrue("open tab lost across a process death: expected $picked, got $restored", restored == picked)
        log("RESTORE_RESULT PASS")
    }
}
