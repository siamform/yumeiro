/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FINAL-7: Chromium 53 (Android 7 / tv-api24) removes "Referer" from the header map handed to
 * `WebView.loadUrl(url, headers)`. A read-only map threw UnsupportedOperationException, so the
 * TeraBox / DiskWala re-capture never opened the share page (device-check 44600322143).
 */
class WebViewHeadersTest {
    /** What AwContents.loadUrl does with the map on that WebView build. */
    private fun chromium53(headers: MutableMap<String, String>): String? = headers.remove("Referer")

    @Test fun theHeadersWebViewGetsCanBeEditedByIt() {
        for (given in listOf(mapOf("Referer" to "https://a.example/"), emptyMap(), buildMap { put("Referer", "r"); put("Cookie", "c") })) {
            val handed = SafeWebView.headersFor(given)
            assertEquals(given["Referer"], chromium53(handed))
            assertTrue(handed.keys.none { it == "Referer" })
        }
    }

    @Test fun theCallersMapIsNotChanged() {
        val given = mapOf("Referer" to "https://a.example/")
        chromium53(SafeWebView.headersFor(given))
        assertEquals("https://a.example/", given["Referer"])
    }

    /** Guard: every loadUrl with headers goes through SafeWebView.loadUrl. */
    @Test fun noDirectLoadUrlWithHeadersInAppSources() {
        val twoArgs = Regex("""(^|[^.\w])(\w+\.)?loadUrl\([^()]*,""")
        val hits = java.io.File("src/main/java").walkTopDown().filter { it.extension == "kt" && it.name != "SafeWebView.kt" }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (twoArgs.containsMatchIn(l) && !l.contains("SafeWebView.loadUrl(") && !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*")) "${f.path}:${i + 1}" else null } }
            .toList()
        assertEquals("WebView.loadUrl(url, headers) must go through SafeWebView.loadUrl: $hits", emptyList<String>(), hits)
    }
}
