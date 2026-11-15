/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** G12 X11-4, X11-5: plain DNS / DNS-over-TLS client. Pure JVM sockets on 127.0.0.1. */
class DnsWireClientTest {
    private val loopback = InetAddress.getByName("127.0.0.1")

    /** A valid one-answer A response for [query] (same id, question echoed, 93.184.216.34). */
    private fun answerFor(query: ByteArray): ByteArray {
        val q = query.copyOf()
        val head = byteArrayOf(q[0], q[1], 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0)
        val question = q.copyOfRange(12, q.size)
        val rr = byteArrayOf(0xC0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 93, 184.toByte(), 216.toByte(), 34)
        return head + question + rr
    }

    /** X11-5: an answer from a different socket than the resolver's must be ignored. */
    @Test fun udpIgnoresAnswersFromAnotherSender() {
        DatagramSocket(0, loopback).use { resolver ->
            DatagramSocket(0, loopback).use { spoofer ->
                resolver.soTimeout = 3_000
                val t = thread {
                    val buf = ByteArray(512)
                    repeat(2) { // A and AAAA queries
                        val p = DatagramPacket(buf, buf.size)
                        runCatching { resolver.receive(p) }.onFailure { return@thread }
                        val reply = answerFor(buf.copyOf(p.length))
                        spoofer.send(DatagramPacket(reply, reply.size, p.address, p.port))
                    }
                }
                val client = DnsWireClient("127.0.0.1", DnsTransport.PLAIN, null, timeoutMs = 600, udpPort = resolver.localPort)
                try {
                    client.resolve("site.example")
                    fail("a spoofed answer was accepted")
                } catch (e: IOException) {
                    assertTrue("expected a timeout, got $e", e is java.net.SocketTimeoutException)
                }
                t.join(3_000)
            }
        }
    }

    /** The real resolver's answer is still accepted. */
    @Test fun udpAcceptsTheResolver() {
        DatagramSocket(0, loopback).use { resolver ->
            resolver.soTimeout = 3_000
            val t = thread {
                val buf = ByteArray(512)
                repeat(2) {
                    val p = DatagramPacket(buf, buf.size)
                    runCatching { resolver.receive(p) }.onFailure { return@thread }
                    val reply = answerFor(buf.copyOf(p.length))
                    resolver.send(DatagramPacket(reply, reply.size, p.address, p.port))
                }
            }
            val a = DnsWireClient("127.0.0.1", DnsTransport.PLAIN, null, timeoutMs = 1_500, udpPort = resolver.localPort).resolve("site.example")
            assertTrue(a.addresses.isNotEmpty())
            t.join(3_000)
        }
    }

    /** X11-4: a TLS handshake that fails must close the TCP socket it opened. */
    @Test fun failedTlsHandshakeClosesTheSocket() {
        ServerSocket(0, 1, loopback).use { server ->
            val t = thread { runCatching { server.accept().use { it.getOutputStream().write("not tls\r\n".toByteArray()) } } }
            val made = mutableListOf<Socket>()
            val client = DnsWireClient("127.0.0.1", DnsTransport.DOT, "dns.example", timeoutMs = 1_500,
                streamPort = server.localPort, newSocket = { Socket().also { made += it } })
            try { client.resolve("site.example"); fail("handshake should fail") } catch (_: IOException) {}
            t.join(3_000)
            assertTrue("socket made", made.isNotEmpty())
            assertTrue("every socket closed: ${made.map { it.isClosed }}", made.all { it.isClosed })
        }
    }

    /** X11-4: a refused connection must close the socket too. */
    @Test fun refusedConnectClosesTheSocket() {
        val port = ServerSocket(0, 1, loopback).use { it.localPort } // now closed: connect is refused
        val made = mutableListOf<Socket>()
        val client = DnsWireClient("127.0.0.1", DnsTransport.DOT, "dns.example", timeoutMs = 1_000,
            streamPort = port, newSocket = { Socket().also { made += it } })
        try { client.resolve("site.example"); fail() } catch (_: IOException) {}
        assertTrue(made.all { it.isClosed })
    }
}
