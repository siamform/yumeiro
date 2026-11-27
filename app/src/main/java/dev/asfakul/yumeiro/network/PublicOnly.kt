/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.network

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress

/**
 * SRC-2: a network interceptor for requests that scripts make (Mangayomi sources through JsBridge).
 * It runs on every hop after DNS, so it sees the real address for names, IP literals, redirects and
 * DNS rebinding, and refuses the user's own network: loopback, private and link-local ranges, CGNAT,
 * unique-local IPv6 and multicast ([SmartDns.isBogon]). The TCP connection may open, but no request
 * bytes are written. User-added home servers (Jellyfin, SMB, repo indexes) do not pass through here.
 */
object PublicOnly : Interceptor {
    val MESSAGE: String get() = tr(R.string.blocked_private_address, "Blocked: private address")

    fun isPrivate(a: InetAddress): Boolean = a.isMulticastAddress || SmartDns.isBogon(a)

    override fun intercept(chain: Interceptor.Chain): Response {
        val address = chain.connection()?.route()?.socketAddress?.address
        // Through a proxy the socket address is the proxy's; the proxy is the user's own choice.
        val viaProxy = chain.connection()?.route()?.proxy?.type() != java.net.Proxy.Type.DIRECT
        if (address != null && !viaProxy && isPrivate(address)) throw IOException(MESSAGE)
        return chain.proceed(chain.request())
    }
}
