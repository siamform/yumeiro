/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForeignViewGuardTest {
    @Test fun `an extension's 1 px view is quieted, real views and our root are not`() {
        assertTrue(ForeignViewGuard.shouldQuiet(isOwnRoot = false, widthPx = 1, heightPx = 1))
        assertTrue(ForeignViewGuard.shouldQuiet(isOwnRoot = false, widthPx = 0, heightPx = 0))
        assertFalse(ForeignViewGuard.shouldQuiet(isOwnRoot = true, widthPx = 1, heightPx = 1))
        assertFalse(ForeignViewGuard.shouldQuiet(isOwnRoot = false, widthPx = 1080, heightPx = 600))
        assertFalse(ForeignViewGuard.shouldQuiet(isOwnRoot = false, widthPx = -1, heightPx = -1)) // MATCH_PARENT
        assertFalse(ForeignViewGuard.shouldQuiet(isOwnRoot = false, widthPx = 1, heightPx = 800))
    }

    @Test fun `our own UI stack is never treated as an extension's view`() {
        assertTrue(ForeignViewGuard.isOwnClass("androidx.activity.EdgeToEdge\$enableEdgeToEdge\$1\$2"))
        assertTrue(ForeignViewGuard.isOwnClass("androidx.compose.ui.platform.ComposeView"))
        assertTrue(ForeignViewGuard.isOwnClass("com.android.internal.policy.DecorView"))
        assertFalse(ForeignViewGuard.isOwnClass("com.phisher98.animepahe.cf.CfWebFetch\$obtainHost\$container\$1"))
        assertFalse(ForeignViewGuard.isOwnClass("android.widget.FrameLayout"))
        assertFalse(ForeignViewGuard.isOwnClass("android.webkit.WebView"))
    }
}
