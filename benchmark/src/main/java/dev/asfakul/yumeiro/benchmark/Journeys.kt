/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** User paths shared by the benchmark and the profile generator (no test tags in release builds). */
internal object Journeys {
    /**
     * X2-2 (run 43993294720 traces): on API 33+ the notification permission prompt opened over the
     * app, and every swipe of the journeys went to GrantPermissionsActivity (138 input events there,
     * 19 app frames). Grant the runtime permissions the app asks for before measuring; called from
     * each setup block. Failures are ignored (a permission the API level does not have).
     */
    fun grantPermissions(device: UiDevice) {
        listOf(
            "android.permission.POST_NOTIFICATIONS", "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_EXTERNAL_STORAGE",
        ).forEach { runCatching { device.executeShellCommand("pm grant $TARGET $it") } }
    }

    /** Waits until the app's window has content and the UI is idle; dismisses a leftover permission prompt. */
    fun firstFrameSettles(device: UiDevice) {
        device.wait(Until.hasObject(By.pkg(TARGET).depth(0)), 10_000)
        device.waitForIdle(3_000)
        repeat(3) {
            val prompt = device.findObject(By.pkg("com.google.android.permissioncontroller"))
                ?: device.findObject(By.pkg("com.android.permissioncontroller")) ?: return@repeat
            (device.findObject(By.textContains("Allow")) ?: device.findObject(By.textContains("Don"))
                ?: prompt).click()
            device.waitForIdle(1_000)
        }
    }

    /**
     * X2-2 (run 43999288738 screenshots): the welcome button reads "Skip for now"; the old exact
     * By.text("Skip") never matched, so every journey (frame timing, startup, baseline profile)
     * stayed on the welcome screen. Match the prefix and wait until the guide is gone.
     */
    fun passWelcome(device: UiDevice) {
        repeat(3) {
            val skip = device.findObject(By.textStartsWith("Skip")) ?: return
            skip.click()
            device.wait(Until.gone(By.textStartsWith("Skip for now")), 5_000)
            device.waitForIdle(2_000)
        }
    }

    /** Start, past the welcome guide if it shows, then scroll Home and visit the other tabs. */
    fun browseTabs(device: UiDevice) {
        firstFrameSettles(device)
        passWelcome(device)
        val w = device.displayWidth; val h = device.displayHeight
        repeat(3) { device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 20); device.waitForIdle(1_000) }
        // The bottom bar: four tabs evenly across the width near the bottom.
        for (i in 0 until 4) {
            device.click(w * (2 * i + 1) / 8, h - h / 16)
            device.waitForIdle(2_000)
            device.findObject(By.scrollable(true))?.let { runCatching { it.scroll(Direction.DOWN, 0.8f) } }
            device.waitForIdle(1_000)
        }
    }

    /**
     * G12 X2-1 (after G1 H8, 44094439021): the path a user takes back into the app after Android killed
     * it in the background: pick another tab, Home, `am kill` (task and saved state kept), relaunch
     * from the launcher intent without CLEAR_TASK. The restored tab's classes then join the profile.
     */
    fun restoreOpenTab(device: UiDevice) {
        val w = device.displayWidth; val h = device.displayHeight
        device.click(w * 5 / 8, h - h / 16) // third tab
        device.waitForIdle(2_000)
        device.pressHome()
        device.wait(Until.gone(By.pkg(TARGET).depth(0)), 5_000)
        device.waitForIdle(1_000)
        device.executeShellCommand("am kill $TARGET")
        repeat(20) { if (device.executeShellCommand("pidof $TARGET").isNotBlank()) Thread.sleep(250) }
        val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(TARGET)!!.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        firstFrameSettles(device)
        device.findObject(By.scrollable(true))?.let { runCatching { it.scroll(Direction.DOWN, 0.8f) } }
        device.waitForIdle(1_000)
    }

    /** ST-7 frame timing: past the welcome guide, fling Home up and down, then swipe the tab pager. */
    fun scrollHomeAndSwipeTabs(device: UiDevice) {
        firstFrameSettles(device)
        passWelcome(device)
        snapshot(device, "before_scroll")
        val w = device.displayWidth; val h = device.displayHeight
        repeat(4) { device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 8); device.waitForIdle(500) }
        repeat(4) { device.swipe(w / 2, h / 4, w / 2, h * 3 / 4, 8); device.waitForIdle(500) }
        // The Home pager sits under the top bar: swipe it sideways a few pages and back.
        repeat(3) { device.swipe(w * 7 / 8, h / 2, w / 8, h / 2, 10); device.waitForIdle(800) }
        repeat(3) { device.swipe(w / 8, h / 2, w * 7 / 8, h / 2, 10); device.waitForIdle(800) }
        snapshot(device, "after_scroll")
    }

    /** Screenshot + window dump into the additional output dir the workflow uploads (what the journey saw). */
    fun snapshot(device: UiDevice, name: String) = runCatching {
        val dir = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { java.io.File(it) } ?: return@runCatching
        dir.mkdirs()
        device.takeScreenshot(java.io.File(dir, "journey_$name.png"))
        java.io.File(dir, "journey_$name.xml").outputStream().use { device.dumpWindowHierarchy(it) }
    }
}
