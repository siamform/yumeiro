/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groupr

import dev.asfakul.yumeiro.online.vault.VaultEntry
import dev.asfakul.yumeiro.online.vault.VaultHtml
import dev.asfakul.yumeiro.online.vault.VaultLinks
import dev.asfakul.yumeiro.online.vault.VaultSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F1 Vault: file format, safety and link rules (pure). */
class GroupRVaultTest {
    private val page = "https://site.example/movie/yu-movie"
    private fun entry(title: String = "Yu Movie", sources: List<VaultSource> = listOf(
        VaultSource("Server 1", "https://player.example/embed/abc", VaultSource.Kind.EMBED, referer = page),
        VaultSource("1080p", "https://cdn.example/v/master.m3u8?t=1&x=2", VaultSource.Kind.STREAM, referer = page, userAgent = "UA/1"),
    )) = VaultEntry("abcd1234ef56", title, page, sources, 1_700_000_000_000L)

    @Test fun f1_roundTripThroughTheHtmlFile() {
        val e = entry()
        val html = VaultHtml.render(e, "Saved today")
        val back = VaultHtml.parse(html)
        assertEquals(e, back)
        assertTrue(html.contains("<a href=\"https://cdn.example/v/master.m3u8?t=1&amp;x=2\""))
        assertTrue(html.contains("default-src 'none'"))
    }

    @Test fun f1_hostileTextCannotBreakOutOrRunScript() {
        val evil = "</script><script>alert(1)</script><img src=x onerror=alert(2)>\"'"
        val e = entry(title = evil, sources = listOf(VaultSource(evil, "https://a.example/x?q=</script>", VaultSource.Kind.LINK)))
        val html = VaultHtml.render(e, "Saved")
        // Exactly one script element: the data block (no live script anywhere else).
        assertEquals(1, Regex("<script", RegexOption.IGNORE_CASE).findAll(html).count())
        assertFalse(html.contains("<img"))
        assertFalse(html.contains("onerror=alert(2)>"))
        val back = VaultHtml.parse(html)
        assertNotNull(back)
        assertEquals(evil.take(VaultHtml.MAX_TITLE), back!!.title)
        assertEquals("https://a.example/x?q=</script>", back.sources.single().url)
    }

    @Test fun f1_onlyWebAndMagnetLinksAreKept() {
        assertTrue(VaultLinks.isSafeUrl("https://a.example/x"))
        assertTrue(VaultLinks.isSafeUrl("http://a.example/x"))
        assertTrue(VaultLinks.isSafeUrl("magnet:?xt=urn:btih:abc&dn=Yu"))
        for (bad in listOf("javascript:alert(1)", "JAVASCRIPT:alert(1)", "data:text/html,hi", "file:///sdcard/x", "content://x/y", "intent://x", "https://", "https://a.example/\nx", ""))
            assertFalse(bad, VaultLinks.isSafeUrl(bad))
        // A tampered file's javascript: links are dropped on read.
        val html = VaultHtml.render(entry(), "Saved").replace("https://player.example/embed/abc", "javascript:alert(1)")
        val back = VaultHtml.parse(html)!!
        assertEquals(1, back.sources.size)
        assertTrue(back.sources.none { it.url.startsWith("javascript") })
    }

    @Test fun f1_notAVaultFile() {
        assertNull(VaultHtml.parse("<html><body>hello</body></html>"))
        assertNull(VaultHtml.parse("<script type=\"application/json\" id=\"yumeiro-vault\">{broken</script>"))
        assertNull(VaultHtml.parse("<script type=\"application/json\" id=\"yumeiro-vault\">{\"id\":\"../../x\",\"title\":\"a\"}</script>"))
    }

    @Test fun f1_classifyKeepsPlayersAndFilesDropsNoise() {
        val found = listOf(
            VaultLinks.Found("https://site.example/", "Home", "link"),
            VaultLinks.Found("https://site.example/genre/action", "Action", "link"),
            VaultLinks.Found("https://www.facebook.com/site", "Facebook", "link"),
            VaultLinks.Found("https://site.example/static/app.css", "", "link"),
            VaultLinks.Found("https://player.example/embed/abc", "Server 1", "iframe"),
            VaultLinks.Found("https://other.example/e/xyz", "Server 2", "server"),
            VaultLinks.Found("https://files.example/dl/yu-movie.mkv", "Download", "link"),
            VaultLinks.Found("https://ads.example/click", "Ad", "iframe"),
            VaultLinks.Found("javascript:void(0)", "x", "link"),
            VaultLinks.Found("https://player.example/embed/abc#t=1", "Server 1 again", "link"),
            VaultLinks.Found("https://site.example/movie/yu-movie/watch", "Same site nav", "link"),
            VaultLinks.Found("magnet:?xt=urn:btih:abc&dn=Yu%20Movie", "", "link"),
        )
        val out = VaultLinks.classify(page, listOf("https://cdn.example/master.m3u8" to "HLS 1080p"), found,
            isAd = { it.contains("ads.example") }, isMedia = { it.endsWith(".m3u8") })
        val urls = out.map { it.url }
        assertEquals(VaultSource.Kind.STREAM, out.first().kind)
        assertEquals("HLS 1080p", out.first().name)
        assertTrue("https://player.example/embed/abc" in urls)
        assertTrue("https://other.example/e/xyz" in urls)
        assertTrue("https://files.example/dl/yu-movie.mkv" in urls)
        assertTrue(urls.any { it.startsWith("magnet:") })
        assertEquals("Yu Movie", out.first { it.url.startsWith("magnet:") }.name)
        assertEquals(1, urls.count { it.startsWith("https://player.example/embed/abc") })
        for (gone in listOf("facebook", "genre", "app.css", "ads.example", "javascript", "/watch")) assertTrue(gone, urls.none { it.contains(gone) })
        assertTrue(out.zipWithNext().all { (a, b) -> a.kind.ordinal <= b.kind.ordinal })
    }

    @Test fun f1_fileNamesAreSafeAndUnique() {
        assertEquals("yu-movie-2026-abcd1234ef56.html", VaultLinks.fileName("Yu Movie (2026)", "abcd1234ef56"))
        assertEquals("title-abcd.html", VaultLinks.fileName("../../..", "abcd"))
        val n = VaultLinks.fileName("a/b\\c:d*e?f\"g<h>i|j", "id01")
        assertFalse(n.contains('/') || n.contains('\\') || n.contains(':') || n.contains('*'))
        assertTrue(VaultLinks.fileName("x".repeat(300), "id01").length <= 48 + "-id01.html".length)
    }

    @Test fun f1_collectorAnswerParses() {
        val raw = "\"[{\\\"u\\\":\\\"https://a.example/e/1\\\",\\\"t\\\":\\\"S1\\\",\\\"f\\\":\\\"iframe\\\"}]\""
        val f = VaultLinks.parseFound(raw)
        assertEquals(listOf(VaultLinks.Found("https://a.example/e/1", "S1", "iframe")), f)
        assertEquals(emptyList<VaultLinks.Found>(), VaultLinks.parseFound(null))
        assertEquals(emptyList<VaultLinks.Found>(), VaultLinks.parseFound("garbage"))
    }
}
