/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.cast

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.net.Socket

/** R17 PL-19: the local server the TV fetches converted subtitles from. Real sockets on loopback. */
@RunWith(RobolectricTestRunner::class)
class CastSubtitleServerTest {
    private val lo = InetAddress.getByName("127.0.0.1")
    @After fun tearDown() = CastSubtitleServer.stop()

    private fun raw(url: String, method: String = "GET", path: String? = null): String {
        val u = java.net.URI(url)
        Socket(u.host, u.port).use { s ->
            s.getOutputStream().write("$method ${path ?: u.path} HTTP/1.1\r\nHost: x\r\nOrigin: https://www.gstatic.com\r\n\r\n".toByteArray())
            return s.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }

    @Test fun servesVttWithCors() {
        val url = CastSubtitleServer.register("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nこんにちは\n", lo)!!
        assertTrue(url, Regex("""http://127\.0\.0\.1:\d+/[0-9a-f]{32}\.vtt""").matches(url))
        val r = raw(url)
        assertTrue(r, r.startsWith("HTTP/1.1 200"))
        assertTrue(r.contains("Access-Control-Allow-Origin: *"))
        assertTrue(r.contains("Content-Type: text/vtt; charset=utf-8"))
        assertTrue(r.endsWith("こんにちは\n"))
        assertTrue(raw(url, "OPTIONS").startsWith("HTTP/1.1 204"))
    }

    @Test fun unknownPathsAndMethodsAre404() {
        val url = CastSubtitleServer.register("WEBVTT\n\n", lo)!!
        assertTrue(raw(url, path = "/" + "0".repeat(32) + ".vtt").startsWith("HTTP/1.1 404"))
        assertTrue(raw(url, "POST").startsWith("HTTP/1.1 404"))
        assertTrue(raw(url, path = "/../../etc/passwd").startsWith("HTTP/1.1 404"))
    }

    @Test fun keepsAtMostEightTracksAndRefusesHugeOnes() {
        val urls = (1..9).map { CastSubtitleServer.register("WEBVTT\n\n$it", lo)!! }
        assertTrue(raw(urls[0]).startsWith("HTTP/1.1 404"))
        assertTrue(raw(urls[8]).startsWith("HTTP/1.1 200"))
        assertNull(CastSubtitleServer.register("x".repeat(CastSubtitleServer.MAX_BYTES + 1), lo))
    }

    @Test fun stopClosesTheServer() {
        CastSubtitleServer.register("WEBVTT\n\n", lo)
        assertTrue(CastSubtitleServer.running())
        CastSubtitleServer.stop()
        assertEquals(false, CastSubtitleServer.running())
    }
}
