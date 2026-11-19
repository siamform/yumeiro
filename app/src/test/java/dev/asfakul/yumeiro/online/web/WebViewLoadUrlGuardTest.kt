/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import org.junit.Assert.assertEquals
import org.junit.Test

/** FINAL-7 source guard: no call site hands WebView a read-only header map (see WebViewHeadersTest). */
class WebViewLoadUrlGuardTest {
    /** Guard: every loadUrl with headers goes through SafeWebView.loadUrl. */
    @Test fun noDirectLoadUrlWithHeadersInAppSources() {
        val twoArgs = Regex("""(^|[^.\w])(\w+\.)?loadUrl\([^()]*,""")
        val hits = java.io.File("src/main/java").walkTopDown().filter { it.extension == "kt" && it.name != "SafeWebView.kt" }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (twoArgs.containsMatchIn(l) && !l.contains("SafeWebView.loadUrl(") && !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*")) "${f.path}:${i + 1}" else null } }
            .toList()
        assertEquals("WebView.loadUrl(url, headers) must go through SafeWebView.loadUrl: $hits", emptyList<String>(), hits)
    }
}
