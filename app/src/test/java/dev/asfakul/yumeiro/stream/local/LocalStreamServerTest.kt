/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream.local

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.Socket
import java.net.URI

/** F-02 / ADR-052 (and B-31): the loopback Range server players read Telegram videos from. */
class LocalStreamServerTest {
    private val data = ByteArray(200_000) { (it % 253).toByte() }
    private val released = mutableListOf<Long>()
    private val src = object : ByteSource {
        override val length = data.size.toLong()
        override val mimeType = "video/x-matroska"
        override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int {
            if (position >= length) return -1
            val n = minOf(count.toLong(), length - position, 10_000L).toInt()
            System.arraycopy(data, position.toInt(), buffer, offset, n)
            return n
        }
        override fun release(position: Long) { synchronized(released) { released += position } }
    }
    private val server = LocalStreamServer()

    @After fun tearDown() = server.stop()

    @Test fun parsesRanges() {
        assertNull(LocalStreamServer.parseRange(null, 100))
        assertNull(LocalStreamServer.parseRange("items=0-1", 100))
        assertEquals(0L to 99L, LocalStreamServer.parseRange("bytes=0-", 100))
        assertEquals(10L to 19L, LocalStreamServer.parseRange("bytes=10-19", 100))
        assertEquals(10L to 99L, LocalStreamServer.parseRange("bytes=10-500", 100))
        assertEquals(90L to 99L, LocalStreamServer.parseRange("bytes=-10", 100))
        assertEquals(0L to 99L, LocalStreamServer.parseRange("bytes=-500", 100))
        assertEquals(0L to 4L, LocalStreamServer.parseRange("bytes=0-4, 10-20", 100))
        assertEquals(LocalStreamServer.INVALID, LocalStreamServer.parseRange("bytes=100-", 100))
        assertEquals(LocalStreamServer.INVALID, LocalStreamServer.parseRange("bytes=20-10", 100))
        assertEquals(LocalStreamServer.INVALID, LocalStreamServer.parseRange("bytes=-0", 100))
        assertEquals(LocalStreamServer.INVALID, LocalStreamServer.parseRange("bytes=abc", 100))
        assertEquals(LocalStreamServer.INVALID, LocalStreamServer.parseRange("bytes=x-1", 100))
    }

    @Test fun readsHeadAndRequest() {
        val head = LocalStreamServer.readHead(ByteArrayInputStream("GET /s/ab HTTP/1.1\r\nRange: bytes=1-2\r\n\r\nBODY".toByteArray()))!!
        val r = LocalStreamServer.parseRequest(head)!!
        assertEquals("GET", r.method); assertEquals("/s/ab", r.path); assertEquals("bytes=1-2", r.range)
        assertNull(LocalStreamServer.readHead(ByteArrayInputStream("GET / HTTP/1.1\r\n".toByteArray())))
        assertNull(LocalStreamServer.parseRequest("garbage\r\n\r\n"))
    }

    private class Resp(val status: Int, val headers: Map<String, String>, val body: ByteArray)

    private fun request(url: String, method: String = "GET", range: String? = null): Resp {
        val u = URI(url)
        Socket(u.host, u.port).use { s ->
            s.soTimeout = 10_000
            val req = "$method ${u.rawPath} HTTP/1.1\r\nHost: x\r\n" + (range?.let { "Range: $it\r\n" } ?: "") + "\r\n"
            s.getOutputStream().write(req.toByteArray()); s.getOutputStream().flush()
            val all = s.getInputStream().readBytes()
            val split = (0 until all.size - 3).first { all[it] == '\r'.code.toByte() && all[it + 1] == '\n'.code.toByte() && all[it + 2] == '\r'.code.toByte() && all[it + 3] == '\n'.code.toByte() }
            val lines = String(all, 0, split).split("\r\n")
            val headers = lines.drop(1).associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
            return Resp(lines[0].split(' ')[1].toInt(), headers, all.copyOfRange(split + 4, all.size))
        }
    }

    @Test fun servesWholeFile() {
        val url = server.publish(src)
        assertTrue(url.startsWith("http://127.0.0.1:"))
        assertEquals(32, url.substringAfterLast("/s/").length)
        val r = request(url)
        assertEquals(200, r.status)
        assertEquals("bytes", r.headers["accept-ranges"])
        assertEquals("video/x-matroska", r.headers["content-type"])
        assertEquals(data.size.toString(), r.headers["content-length"])
        assertArrayEquals(data, r.body)
    }

    @Test fun servesRanges() {
        val url = server.publish(src)
        val r = request(url, range = "bytes=150000-")
        assertEquals(206, r.status)
        assertEquals("bytes 150000-199999/200000", r.headers["content-range"])
        assertArrayEquals(data.copyOfRange(150_000, 200_000), r.body)
        val tail = request(url, range = "bytes=-5")
        assertArrayEquals(data.copyOfRange(199_995, 200_000), tail.body)
    }

    @Test fun headHasNoBody() {
        val r = request(server.publish(src), method = "HEAD")
        assertEquals(200, r.status)
        assertEquals(data.size.toString(), r.headers["content-length"])
        assertEquals(0, r.body.size)
    }

    @Test fun unsatisfiableRangeIs416() {
        val r = request(server.publish(src), range = "bytes=999999-")
        assertEquals(416, r.status)
        assertEquals("bytes */200000", r.headers["content-range"])
    }

    @Test fun unknownOrRemovedTokenIs404() {
        val url = server.publish(src)
        assertEquals(404, request(url.substringBeforeLast("/s/") + "/s/00000000000000000000000000000000").status)
        server.unpublish(url)
        assertEquals(404, request(url).status)
    }

    @Test fun fileNameAfterTokenIsIgnored() {
        val url = server.publish(src)
        val r = request("$url/Show%20-%2001.mkv", range = "bytes=0-9")
        assertEquals(206, r.status)
        assertArrayEquals(data.copyOfRange(0, 10), r.body)
        server.unpublish("$url/Show.mkv")
        assertEquals(404, request(url).status)
    }

    @Test fun otherMethodsAre405() {
        assertEquals(405, request(server.publish(src), method = "POST").status)
    }
}
