/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.cloud

import android.content.Context
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.network.NetworkClient
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Shared bits for talking to Yumeiro Cloud. The server is optional by design: nothing in the app
 * waits on it. Calls have short timeouts, and after a failure (server down, Cloudflare's free
 * daily quota used up, no network) [CloudGate] keeps the app from retrying for a while, so a
 * broken server costs users nothing but the online-only extras.
 */
internal object CloudApi {
    val enabled: Boolean get() = BuildConfig.CLOUD_HOST.isNotBlank()
    fun url(path: String) = "https://${BuildConfig.CLOUD_HOST}$path"
    fun ws(path: String) = "wss://${BuildConfig.CLOUD_HOST}$path"

    fun client(c: Context): OkHttpClient = NetworkClient.get(c).newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .build()

    fun error(code: Int, body: String?): IOException {
        val e = runCatching { JSONObject(body ?: "").optString("error") }.getOrNull().orEmpty()
        return IOException(when {
            code == 429 && e == "slow_down" -> "Too many rooms created from this network. Try again in a minute."
            code == 429 || code >= 500 -> CloudGate.BUSY
            else -> "Yumeiro Cloud answered HTTP $code"
        })
    }
}

/** Circuit breaker for the optional server. Pure and clock-injected, unit-tested in CloudGateTest. */
object CloudGate {
    const val BUSY = "Online rooms are unavailable right now. Rooms on the same Wi-Fi still work."
    private const val FIRST_PAUSE_MS = 2 * 60_000L
    private const val MAX_PAUSE_MS = 60 * 60_000L

    @Volatile private var failures = 0
    @Volatile private var pausedUntil = 0L

    /** False while paused after recent failures. */
    fun open(now: Long = System.currentTimeMillis()): Boolean = now >= pausedUntil

    /** Remaining pause in ms (0 when open). */
    fun wait(now: Long = System.currentTimeMillis()): Long = (pausedUntil - now).coerceAtLeast(0)

    fun failed(now: Long = System.currentTimeMillis()) {
        failures = (failures + 1).coerceAtMost(10)
        pausedUntil = now + (FIRST_PAUSE_MS shl (failures - 1).coerceAtMost(5)).coerceAtMost(MAX_PAUSE_MS)
    }

    fun ok() { failures = 0; pausedUntil = 0 }

    internal fun reset() = ok()
}
