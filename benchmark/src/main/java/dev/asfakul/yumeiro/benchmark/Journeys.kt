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
    /** Waits until the app's window has content and the UI is idle. */
    fun firstFrameSettles(device: UiDevice) {
        device.wait(Until.hasObject(By.pkg(TARGET).depth(0)), 10_000)
        device.waitForIdle(3_000)
    }

    /** Start, past the welcome guide if it shows, then scroll Home and visit the other tabs. */
    fun browseTabs(device: UiDevice) {
        firstFrameSettles(device)
        device.findObject(By.text("Skip"))?.click()
        device.waitForIdle(2_000)
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

    /** ST-7 frame timing: past the welcome guide, fling Home up and down, then swipe the tab pager. */
    fun scrollHomeAndSwipeTabs(device: UiDevice) {
        firstFrameSettles(device)
        device.findObject(By.text("Skip"))?.click()
        device.waitForIdle(2_000)
        val w = device.displayWidth; val h = device.displayHeight
        repeat(4) { device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 8); device.waitForIdle(500) }
        repeat(4) { device.swipe(w / 2, h / 4, w / 2, h * 3 / 4, 8); device.waitForIdle(500) }
        // The Home pager sits under the top bar: swipe it sideways a few pages and back.
        repeat(3) { device.swipe(w * 7 / 8, h / 2, w / 8, h / 2, 10); device.waitForIdle(800) }
        repeat(3) { device.swipe(w / 8, h / 2, w * 7 / 8, h / 2, 10); device.waitForIdle(800) }
    }
}
