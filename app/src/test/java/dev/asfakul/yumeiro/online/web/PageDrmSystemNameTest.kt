/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** X7-4: what a page script passes to MTFinder.onDrm is bounded before it is stored or shown. */
class PageDrmSystemNameTest {
    @Test fun realKeySystemsPassUnchanged() {
        assertEquals("com.widevine.alpha", pageDrmSystemName("com.widevine.alpha"))
        assertEquals("com.microsoft.playready", pageDrmSystemName("com.microsoft.playready"))
        assertEquals("org.w3.clearkey", pageDrmSystemName("org.w3.clearkey"))
    }

    @Test fun aHugeStringIsCut() {
        val out = pageDrmSystemName("a".repeat(5_000_000))
        assertEquals(40, out.length)
    }

    @Test fun markupAndControlCharactersAreDropped() {
        val out = pageDrmSystemName("<img src=x onerror=alert(1)>\n\u0000\u202E")
        assertTrue(out, out.none { it in "<>=()\n\u0000\u202E" })
    }
}
