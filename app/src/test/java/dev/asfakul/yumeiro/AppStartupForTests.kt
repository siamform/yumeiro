/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

/**
 * Robolectric starts [MTApplication] for every test, and its "yu-startup" thread loads stores in the background
 * (taste import, the B23 7-day cleanup, …). A test that resets those stores waits for it first, or the startup work
 * can land in the middle of the test (CI 46134105195: TasteSignalsTest saw "imported" right after a reset).
 */
object AppStartupForTests {
    fun await() {
        Thread.getAllStackTraces().keys.filter { it.name == "yu-startup" && it.isAlive }.forEach { it.join(30_000) }
    }
}
