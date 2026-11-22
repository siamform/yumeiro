/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream.local

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bytes that are still arriving (a Telegram file TDLib downloads, a torrent's pieces). [read] blocks until bytes at
 * [position] exist, returns -1 at the end and throws [IOException] when they won't come (timeout, closed, removed).
 */
interface ByteSource {
    /** Total size in bytes; must be known (Telegram and torrents both know it up front). */
    val length: Long
    val mimeType: String
    fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int
    /** The player left this position (seek, close): stop fetching for it. Optional. */
    fun release(position: Long) {}
}

/**
 * F-02 / ADR-052 (and B-31 later): a tiny HTTP/1.1 server on 127.0.0.1 that serves a [ByteSource] with Range
 * support, so Yumeiro's player, external players (F-07) and downloads all read a Telegram video like any
 * web file. Each source gets an unguessable path (`/s/<128-bit token>`), so other apps on the phone can't list or
 * guess what is playing. At most [maxConnections] requests run at once (players open 1-3); others get 503.
 */
class LocalStreamServer(private val maxConnections: Int = 6) {
    private val sources = ConcurrentHashMap<String, ByteSource>()
    private val random = SecureRandom()
    private val permits = Semaphore(maxConnections)
    @Volatile private var socket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool(object : ThreadFactory {
        private val n = AtomicInteger()
        override fun newThread(r: Runnable) = Thread(r, "yumeiro-local-http-" + n.incrementAndGet()).apply { isDaemon = true }
    })

    val port: Int get() = socket?.localPort ?: 0

    /** Starts listening (once). Call from a background thread. */
    @Synchronized
    fun start(): Int {
        socket?.let { if (!it.isClosed) return it.localPort }
        val s = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        socket = s
        pool.execute { acceptLoop(s) }
        return s.localPort
    }

    /** Publishes [source] and returns its URL. */
    fun publish(source: ByteSource): String {
        val port = start()
        val token = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        sources[token] = source
        return "http://127.0.0.1:$port/s/$token"
    }

    fun unpublish(url: String) { sources.remove(url.substringAfter("/s/", "").substringBefore('/')) }

    @Synchronized
    fun stop() {
        sources.clear()
        runCatching { socket?.close() }
        socket = null
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val c = try { s.accept() } catch (e: IOException) { break }
            if (!permits.tryAcquire()) {
                runCatching { c.getOutputStream().write("HTTP/1.1 503 Busy\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); c.close() }
                continue
            }
            pool.execute { try { handle(c) } finally { permits.release(); runCatching { c.close() } } }
        }
    }

    private fun handle(c: Socket) {
        c.soTimeout = 30_000
        c.tcpNoDelay = true
        val input = BufferedInputStream(c.getInputStream())
        val out = BufferedOutputStream(c.getOutputStream(), 64 * 1024)
        val head = readHead(input) ?: return
        val req = parseRequest(head) ?: return respond(out, 400, "Bad Request")
        if (req.method != "GET" && req.method != "HEAD") return respond(out, 405, "Method Not Allowed")
        // `/s/<token>` or `/s/<token>/<file name>` (the name only tells downloaders the file type).
        val token = req.path.substringAfter("/s/", "").substringBefore('?').substringBefore('/')
        val src = sources[token] ?: return respond(out, 404, "Not Found")
        val total = src.length
        val range = parseRange(req.range, total)
        if (range == INVALID) {
            out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$total\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush(); return
        }
        val (start, end) = range ?: (0L to total - 1)
        val len = end - start + 1
        val status = if (range != null) "206 Partial Content" else "200 OK"
        val sb = StringBuilder("HTTP/1.1 ").append(status).append("\r\n")
            .append("Content-Type: ").append(src.mimeType).append("\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("Content-Length: ").append(len).append("\r\n")
        if (range != null) sb.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(total).append("\r\n")
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray())
        if (req.method == "HEAD") { out.flush(); return }
        val buf = ByteArray(64 * 1024)
        var pos = start
        try {
            while (pos <= end) {
                val want = minOf(buf.size.toLong(), end - pos + 1).toInt()
                val n = src.read(pos, buf, 0, want)
                if (n < 0) break
                out.write(buf, 0, n)
                pos += n
            }
            out.flush()
        } catch (e: SocketException) {
            // The player closed the connection (seek, stop): normal.
        } catch (e: IOException) {
            // The source gave up (timeout, removed); the player sees a short body and retries or shows its error.
        } finally {
            src.release(pos)
        }
    }

    private fun respond(out: BufferedOutputStream, code: Int, text: String) {
        out.write("HTTP/1.1 $code $text\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()
    }

    internal class Req(val method: String, val path: String, val range: String?)

    companion object {
        /** B-31 owner add: one loopback server for the app's life, shared by Telegram and torrents. */
        val shared: LocalStreamServer by lazy { LocalStreamServer(maxConnections = 8) }
        internal val INVALID = -1L to -1L

        /** Reads the request head (up to the blank line), at most 16 KB. */
        internal fun readHead(input: InputStream): String? {
            val sb = StringBuilder()
            var last4 = 0
            while (sb.length < 16 * 1024) {
                val b = input.read()
                if (b < 0) return null
                sb.append(b.toChar())
                last4 = (last4 shl 8) or b
                if (last4 == 0x0d0a0d0a) return sb.toString()
            }
            return null
        }

        internal fun parseRequest(head: String): Req? {
            val lines = head.split("\r\n")
            val first = lines.firstOrNull()?.split(' ') ?: return null
            if (first.size < 3) return null
            val range = lines.drop(1).firstOrNull { it.startsWith("range:", ignoreCase = true) }?.substringAfter(':')?.trim()
            return Req(first[0], first[1], range)
        }

        /**
         * `bytes=a-b`, `bytes=a-`, `bytes=-n` (one range; multi-range answers are not needed by players). Null = no
         * Range header (whole file); [INVALID] = unsatisfiable (416).
         */
        internal fun parseRange(header: String?, total: Long): Pair<Long, Long>? {
            if (header.isNullOrBlank()) return null
            if (!header.startsWith("bytes=", ignoreCase = true)) return null
            val spec = header.substring(6).substringBefore(',').trim()
            val dash = spec.indexOf('-')
            if (dash < 0) return INVALID
            val a = spec.substring(0, dash).trim()
            val b = spec.substring(dash + 1).trim()
            if (total <= 0) return INVALID
            return when {
                a.isEmpty() -> {
                    val n = b.toLongOrNull() ?: return INVALID
                    if (n <= 0) INVALID else (maxOf(0L, total - n) to total - 1)
                }
                else -> {
                    val s = a.toLongOrNull() ?: return INVALID
                    val e = if (b.isEmpty()) total - 1 else (b.toLongOrNull() ?: return INVALID).coerceAtMost(total - 1)
                    if (s >= total || s > e) INVALID else (s to e)
                }
            }
        }
    }
}
