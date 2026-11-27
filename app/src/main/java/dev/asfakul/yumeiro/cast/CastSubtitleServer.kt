/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.cast

import android.util.Log
import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * R17 (G3 proposal "SRT → WebVTT for Cast"): serves converted subtitles to the TV.
 *
 * A Cast receiver fetches a text track itself, over HTTP, and needs CORS headers on the answer.
 * An SRT converted on the phone has no URL, so this tiny server gives it one on the local network.
 *
 * Limits, on purpose:
 *  - It listens on the phone's private (site-local) IPv4 address only, never on a public one.
 *  - Each subtitle sits under an unguessable path (128-bit random token).
 *  - Only GET, HEAD and the CORS preflight (OPTIONS) are answered, for registered tracks only.
 *  - At most [MAX_TRACKS] tracks of at most [MAX_BYTES] each; the oldest track is dropped first.
 *  - Two worker threads, 10 s socket timeout, request head capped at 8 KB.
 *  - [stop] closes it. CastSupport stops it when the cast session ends.
 */
object CastSubtitleServer {
    private const val TAG = "YuCastSubs"
    const val MAX_TRACKS = 8
    const val MAX_BYTES = 2 * 1024 * 1024

    private val tracks = ConcurrentHashMap<String, ByteArray>()
    private val order = ArrayDeque<String>()
    private val random = SecureRandom()
    @Volatile private var server: ServerSocket? = null
    @Volatile private var host: String? = null
    private var pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "yu-cast-subs").apply { isDaemon = true } }

    /** Registers [vtt] and returns its URL, or null when the phone has no local network address. */
    @Synchronized
    fun register(vtt: String, address: InetAddress? = localAddress()): String? {
        val bytes = vtt.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) return null
        val s = ensureStarted(address ?: return null) ?: return null
        val token = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        tracks[token] = bytes
        order.addLast(token)
        while (order.size > MAX_TRACKS) tracks.remove(order.removeFirst())
        return "http://${host}:${s.localPort}/$token.vtt"
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null; host = null
        tracks.clear(); order.clear()
        pool.shutdownNow()
        pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "yu-cast-subs").apply { isDaemon = true } }
    }

    internal fun running(): Boolean = server?.isClosed == false

    private fun ensureStarted(address: InetAddress): ServerSocket? {
        server?.takeIf { !it.isClosed && host == address.hostAddress }?.let { return it }
        runCatching { server?.close() }
        return runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(address, 0), 8)
            }
        }.onFailure { Log.w(TAG, "bind", it) }.getOrNull()?.also { s ->
            server = s; host = address.hostAddress
            Thread({ acceptLoop(s) }, "yu-cast-subs-accept").apply { isDaemon = true }.start()
        }
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val c = runCatching { s.accept() }.getOrNull() ?: break
            runCatching { pool.execute { serve(c) } }.onFailure { runCatching { c.close() } }
        }
    }

    private fun serve(c: Socket) = c.use { sock ->
        runCatching {
            sock.soTimeout = 10_000
            val input = BufferedInputStream(sock.getInputStream())
            val head = readHead(input) ?: return@runCatching
            val requestLine = head.lineSequence().firstOrNull().orEmpty().split(' ')
            val method = requestLine.getOrNull(0).orEmpty()
            val token = requestLine.getOrNull(1).orEmpty().substringBefore('?').trim('/').removeSuffix(".vtt")
            val body = tracks[token]
            val out = sock.getOutputStream()
            val cors = "Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, HEAD, OPTIONS\r\nAccess-Control-Allow-Headers: *\r\n"
            when {
                method == "OPTIONS" && body != null ->
                    out.write("HTTP/1.1 204 No Content\r\n${cors}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                (method == "GET" || method == "HEAD") && body != null -> {
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/vtt; charset=utf-8\r\n$cors" +
                        "Cache-Control: no-store\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
                    if (method == "GET") out.write(body)
                }
                else -> out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            }
            out.flush()
        }
    }

    /** Request line and headers, up to the blank line; null when longer than 8 KB. */
    private fun readHead(input: BufferedInputStream): String? {
        val buf = StringBuilder()
        while (buf.length < 8192) {
            val b = input.read()
            if (b < 0) break
            buf.append(b.toChar())
            if (buf.endsWith("\r\n\r\n") || buf.endsWith("\n\n")) return buf.toString()
        }
        return if (buf.isNotEmpty() && buf.length < 8192) buf.toString() else null
    }

    /** The phone's private IPv4 address on Wi-Fi or Ethernet (where the Cast device is). */
    fun localAddress(): InetAddress? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { n -> if (n.name.startsWith("wlan") || n.name.startsWith("eth")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
    }.getOrNull()
}
