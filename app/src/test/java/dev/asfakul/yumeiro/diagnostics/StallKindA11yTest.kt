/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

/** GATE-1-T11: a main thread answering an accessibility query is told apart from the app's own work. */
class StallKindA11yTest {
    private fun f(c: String, m: String = "run") = StackTraceElement(c, m, "X.java", 1)

    @Test fun accessibilityQueryIsA11y() {
        val t = arrayOf(f("android.view.AccessibilityInteractionController\$AccessibilityNodePrefetcher", "prefetch"),
            f("android.view.AccessibilityInteractionController", "findAccessibilityNodeInfoByAccessibilityIdUiThread"), f("android.os.Handler", "dispatchMessage"))
        assertEquals("a11y", MainThreadWatchdog.kindOf(t))
    }

    @Test fun composeWorkStaysApp() {
        val t = arrayOf(f("androidx.compose.runtime.GapComposer", "startGroup"), f("android.view.Choreographer", "doFrame"))
        assertEquals("app", MainThreadWatchdog.kindOf(t))
    }

    @Test fun renderStaysRender() {
        val t = arrayOf(f("android.graphics.HardwareRenderer", "nSyncAndDrawFrame"), f("android.view.ThreadedRenderer", "draw"))
        assertEquals("render", MainThreadWatchdog.kindOf(t))
    }
}
