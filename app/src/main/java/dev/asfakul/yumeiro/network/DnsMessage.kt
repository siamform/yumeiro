/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

/**
 * Minimal RFC 1035 DNS message encoder/decoder (A and AAAA only), used for plain DNS (UDP/TCP 53)
 * and DNS-over-TLS (RFC 7858). DNS-over-HTTPS uses OkHttp's own implementation.
 */
internal object DnsMessage {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    private const val RCODE_NXDOMAIN = 3

    data class Answer(val addresses: List<InetAddress>, val ttlSeconds: Long, val truncated: Boolean)

    fun query(id: Int, host: String, type: Int): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeShort(id and 0xFFFF)
            writeShort(0x0100) // standard query, recursion desired
            writeShort(1); writeShort(0); writeShort(0); writeShort(0)
            host.trimEnd('.').split('.').forEach { label ->
                val bytes = java.net.IDN.toASCII(label).toByteArray(Charsets.US_ASCII)
                require(bytes.size in 1..63) { "Invalid host name" }
                writeByte(bytes.size); write(bytes)
            }
            writeByte(0)
            writeShort(type); writeShort(1) // class IN
        }
        return out.toByteArray()
    }

    /** Parses a response for [host]. Throws [UnknownHostException] for NXDOMAIN, IOException when malformed. */
    fun parse(data: ByteArray, expectedId: Int, host: String): Answer {
        if (data.size < 12) throw IOException("Short DNS response")
        val id = u16(data, 0)
        if (id != (expectedId and 0xFFFF)) throw IOException("DNS response id mismatch")
        val flags = u16(data, 2)
        val truncated = flags and 0x0200 != 0
        val rcode = flags and 0x000F
        if (rcode == RCODE_NXDOMAIN) throw UnknownHostException("$host: no such domain")
        if (rcode != 0) throw IOException("DNS server error (rcode $rcode)")
        val qd = u16(data, 4); val an = u16(data, 6)
        var pos = 12
        repeat(qd) { pos = skipName(data, pos) + 4 }
        val addresses = mutableListOf<InetAddress>()
        var ttl = Long.MAX_VALUE
        repeat(an) {
            pos = skipName(data, pos)
            if (pos + 10 > data.size) throw IOException("Truncated DNS answer")
            val type = u16(data, pos); val rdLength = u16(data, pos + 8)
            val recordTtl = ((u16(data, pos + 4).toLong() shl 16) or u16(data, pos + 6).toLong())
            pos += 10
            if (pos + rdLength > data.size) throw IOException("Truncated DNS record")
            if ((type == TYPE_A && rdLength == 4) || (type == TYPE_AAAA && rdLength == 16)) {
                addresses += InetAddress.getByAddress(host, data.copyOfRange(pos, pos + rdLength))
                ttl = minOf(ttl, recordTtl)
            }
            pos += rdLength
        }
        return Answer(addresses, if (ttl == Long.MAX_VALUE) 0 else ttl, truncated)
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun skipName(b: ByteArray, start: Int): Int {
        var pos = start
        while (true) {
            if (pos >= b.size) throw IOException("Bad DNS name")
            val len = b[pos].toInt() and 0xFF
            when {
                len == 0 -> return pos + 1
                len and 0xC0 == 0xC0 -> return pos + 2 // compression pointer ends the name
                else -> pos += len + 1
            }
        }
    }

    fun newId() = Random.nextInt(0, 0xFFFF)
}

/** Sends DNS queries to one resolver over UDP, TCP or TLS. */
internal class DnsWireClient(
    private val serverIp: String,
    private val transport: DnsTransport,
    private val tlsHost: String?,
    private val timeoutMs: Int = 3_000,
) {
    fun resolve(host: String): DnsMessage.Answer {
        val results = mutableListOf<InetAddress>()
        var ttl = Long.MAX_VALUE
        var lastError: IOException? = null
        for (type in intArrayOf(DnsMessage.TYPE_A, DnsMessage.TYPE_AAAA)) {
            try {
                val a = exchange(host, type)
                results += a.addresses
                if (a.addresses.isNotEmpty()) ttl = minOf(ttl, a.ttlSeconds)
            } catch (e: UnknownHostException) {
                throw e
            } catch (e: IOException) {
                lastError = e
            }
        }
        if (results.isEmpty()) throw lastError ?: UnknownHostException("$host: no addresses")
        return DnsMessage.Answer(results, if (ttl == Long.MAX_VALUE) 60 else ttl, false)
    }

    private fun exchange(host: String, type: Int): DnsMessage.Answer {
        val id = DnsMessage.newId()
        val q = DnsMessage.query(id, host, type)
        return when (transport) {
            DnsTransport.PLAIN -> {
                val udp = udp(q, id, host)
                if (udp.truncated) stream(q, id, host, tls = false) else udp
            }
            DnsTransport.DOT -> stream(q, id, host, tls = true)
            DnsTransport.DOH -> error("DoH is handled by OkHttp")
        }
    }

    private fun udp(q: ByteArray, id: Int, host: String): DnsMessage.Answer = DatagramSocket().use { s ->
        s.soTimeout = timeoutMs
        val address = InetAddress.getByName(serverIp) // IP literal: no lookup
        s.send(DatagramPacket(q, q.size, address, 53))
        val buf = ByteArray(4096)
        val p = DatagramPacket(buf, buf.size)
        s.receive(p)
        DnsMessage.parse(buf.copyOf(p.length), id, host)
    }

    /** TCP framing (2-byte length prefix), optionally wrapped in TLS with certificate host verification. */
    private fun stream(q: ByteArray, id: Int, host: String, tls: Boolean): DnsMessage.Answer {
        val port = if (tls) 853 else 53
        val plain = Socket()
        plain.connect(InetSocketAddress(InetAddress.getByName(serverIp), port), timeoutMs)
        plain.soTimeout = timeoutMs
        val socket: Socket = if (tls) {
            val name = tlsHost ?: throw IOException("Resolver has no TLS name")
            (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(plain, name, port, true).also {
                val ssl = it as SSLSocket
                ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                ssl.startHandshake()
            }
        } else plain
        socket.use { s ->
            val out = DataOutputStream(s.getOutputStream())
            out.writeShort(q.size); out.write(q); out.flush()
            val input = DataInputStream(s.getInputStream())
            val len = input.readUnsignedShort()
            val buf = ByteArray(len)
            input.readFully(buf)
            return DnsMessage.parse(buf, id, host)
        }
    }
}
