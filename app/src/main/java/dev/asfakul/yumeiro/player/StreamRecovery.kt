/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import dev.asfakul.yumeiro.stream.RetryAfter
import java.io.InterruptedIOException
import java.net.UnknownHostException

/** The facts about a playback error that recovery decisions depend on. */
data class ErrorFacts(
    val errorCode: Int,
    val httpStatus: Int? = null,
    val retryAfterSeconds: Long? = null,
    val dnsFailure: Boolean = false,
    val message: String? = null,
) {
    val isNetwork: Boolean get() = errorCode in 2000..2999
    val isTimeout: Boolean get() = errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
    val isParsing: Boolean get() = errorCode in 3000..3999
    val isDecoder: Boolean get() = errorCode in 4000..4999
    val isDrm: Boolean get() = errorCode in 6000..6999

    companion object {
        @OptIn(UnstableApi::class)
        fun from(error: PlaybackException): ErrorFacts {
            var http: HttpDataSource.InvalidResponseCodeException? = null
            var dns = false
            var t: Throwable? = error
            while (t != null) {
                if (t is HttpDataSource.InvalidResponseCodeException && http == null) http = t
                if (t is UnknownHostException) dns = true
                t = t.cause
            }
            return ErrorFacts(
                errorCode = error.errorCode,
                httpStatus = http?.responseCode,
                retryAfterSeconds = http?.headerFields?.let { RetryAfter.fromHeaders(it) },
                dnsFailure = dns,
                message = error.cause?.message ?: error.message,
            )
        }
    }
}

/** What the player should do about an error. */
sealed interface RecoveryAction {
    /** Re-prepare after [delayMs]. */
    data class Retry(val delayMs: Long, val attempt: Int, val maxAttempts: Int, val reason: String) : RecoveryAction
    /** Fetch a fresh manifest / token: re-open the original link or re-resolve its web page. */
    data class Refresh(val reason: String) : RecoveryAction
    /** Live stream fell behind the live window: jump back to the live edge. */
    data object JumpToLive : RecoveryAction
    /** The format guess was wrong: inspect the response and retry with the detected format. */
    data object Reprobe : RecoveryAction
    data class Fail(val title: String, val message: String, val offerDnsSettings: Boolean = false) : RecoveryAction
}

/**
 * Error-specific, bounded recovery for one playback session. Every category has its own retry
 * budget, budgets reset after [STABLE_RESET_MS] of healthy playback, and nothing retries forever.
 */
class PlaybackRecovery(
    private val now: () -> Long = System::currentTimeMillis,
    /** Whether an alternative DNS resolver is configured (DNS errors then already went through it). */
    private val alternativeDns: () -> Boolean = { false },
    private val dnsLabel: () -> String = { "the alternative DNS" },
) {
    private val attempts = mutableMapOf<String, Int>()
    private var lastRefreshAt = Long.MIN_VALUE / 2
    private var reprobed = false
    private var liveJumps = mutableListOf<Long>()

    fun onStablePlayback() { attempts.clear() }

    private fun next(category: String): Int = (attempts[category] ?: 0).plus(1).also { attempts[category] = it }

    private fun backoff(attempt: Int, baseMs: Long = 2_000L, capMs: Long = 16_000L): Long =
        (baseMs shl (attempt - 1).coerceIn(0, 4)).coerceAtMost(capMs)

    /**
     * @param canRefresh the stream is a network stream that can be re-opened from its original link/page.
     * @param autoFormat the format was auto-detected (a wrong guess can be corrected by probing).
     */
    fun decide(f: ErrorFacts, canRefresh: Boolean, autoFormat: Boolean): RecoveryAction {
        if (f.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            val t = now(); liveJumps.removeAll { t - it > 60_000L }; liveJumps += t
            return if (liveJumps.size <= 3) RecoveryAction.JumpToLive
            else RecoveryAction.Fail("Live stream keeps falling behind", "The connection is too slow to keep up with this live stream. Try a lower quality or a faster network.")
        }
        f.httpStatus?.let { code -> return decideHttp(code, f, canRefresh) }

        if (f.dnsFailure) {
            val n = next("dns")
            if (n <= 2) return RecoveryAction.Retry(1_500L * n, n, 2, "Looking up the server again")
            return if (alternativeDns()) RecoveryAction.Fail("Can't find the server", "The stream's host couldn't be resolved by the system DNS or ${dnsLabel()}. Check the link and your internet connection.")
            else RecoveryAction.Fail("Can't find the server", "The stream's host couldn't be resolved by the system DNS. Turn on an alternative DNS (Cloudflare 1.1.1.1, Quad9 or Google) in Settings → Network — it's used only when the system DNS fails — then retry.", offerDnsSettings = true)
        }
        if (f.isNetwork && f.errorCode != PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND &&
            f.errorCode != PlaybackException.ERROR_CODE_IO_NO_PERMISSION && f.errorCode != PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED &&
            f.errorCode != PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE) {
            val n = next(if (f.isTimeout) "timeout" else "network")
            return if (n <= 3) RecoveryAction.Retry(backoff(n), n, 3, if (f.isTimeout) "Connection timed out — reconnecting" else "Connection lost — reconnecting")
            else RecoveryAction.Fail(
                if (f.isTimeout) "Connection timed out" else "Connection problem",
                "The stream couldn't be reached after 3 attempts. Check your connection; if the stream works elsewhere, the server may be blocking this network.",
            )
        }
        if (f.isParsing && autoFormat && !reprobed) { reprobed = true; return RecoveryAction.Reprobe }
        if (f.isDrm) return RecoveryAction.Fail(
            "Protected stream (DRM)",
            "The licence server refused playback or this device can't decrypt the stream. DRM streams play only with a licence URL or ClearKey keys you're authorised to use; Yumeiro doesn't bypass DRM.",
        )
        if (f.isParsing) return RecoveryAction.Fail(
            "Unsupported stream format",
            "The data from this link isn't a media format the player understands${f.message?.let { " ($it)" }.orEmpty()}. If it's a web page, open it from the Stream tab so its player stream can be found.",
        )
        return RecoveryAction.Fail(
            "Unsupported video format",
            "This device's hardware decoders could not play this file, or the media stream is corrupted.\n\n${f.message ?: "Playback error"}",
        )
    }

    private fun decideHttp(code: Int, f: ErrorFacts, canRefresh: Boolean): RecoveryAction = when (code) {
        401 -> RecoveryAction.Fail("Sign-in required (HTTP 401)", "The server asked for authorization. Add the Cookie or Authorization header the provider gave you under Stream → Headers & DRM.")
        403, 404, 410 -> if (canRefresh && now() - lastRefreshAt > 120_000L) {
            lastRefreshAt = now()
            RecoveryAction.Refresh(if (code == 403) "Access refused — fetching a fresh link" else "Stream link expired — refreshing")
        } else if (code == 403) RecoveryAction.Fail(
            "Access denied (HTTP 403)",
            "The server refused the stream. The link's token may have expired, the host may require its own Referer / Origin / User-Agent / Cookie headers, or the stream is limited to some countries. Get a fresh link or add the headers under Stream → Headers & DRM. Yumeiro doesn't bypass geo-blocking or access controls.",
        ) else RecoveryAction.Fail("Stream not found (HTTP $code)", "The stream is offline or its link has expired. Get a fresh link or refresh the playlist.")
        429 -> {
            val n = next("429")
            val wait = ((f.retryAfterSeconds ?: (10L * n)) * 1000L).coerceIn(1_000L, 60_000L)
            if (n <= 2) RecoveryAction.Retry(wait, n, 2, "Server is busy — waiting ${wait / 1000}s as it asked")
            else RecoveryAction.Fail("Too many requests (HTTP 429)", "The server keeps rate-limiting this stream. Try again in a few minutes.")
        }
        451 -> RecoveryAction.Fail("Unavailable in your region (HTTP 451)", "The provider blocks this content here for legal or licensing reasons. Yumeiro doesn't bypass regional restrictions.")
        in 500..599 -> {
            val n = next("5xx")
            if (n <= 3) RecoveryAction.Retry(backoff(n), n, 3, "Server error $code — retrying")
            else RecoveryAction.Fail("Server error (HTTP $code)", "The stream server keeps failing. Try again later.")
        }
        else -> RecoveryAction.Fail("HTTP $code", "The server answered with HTTP $code and the stream can't be loaded.")
    }

    companion object {
        const val STABLE_RESET_MS = 30_000L
    }
}

/**
 * Per-chunk / per-manifest retry policy used inside the player (before an error becomes fatal):
 *  - 429: waits as long as `Retry-After` asks (max 30 s), at most 3 times.
 *  - 5xx / 408: exponential backoff 1 s → 8 s, at most 4 times.
 *  - timeouts / dropped connections: exponential backoff, at most 5 times.
 *  - DNS failures: two quick retries (the DNS fallback has already been tried by then).
 *  - 401 / 403 / 404 / 410: not retried here; the default policy excludes the failing HLS/DASH
 *    variant (so playback continues on another quality) and the player-level recovery refreshes.
 */
@OptIn(UnstableApi::class)
class StreamLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount = */ 5) {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val e = info.exception
        val n = info.errorCount
        var http: HttpDataSource.InvalidResponseCodeException? = null
        var dns = false
        var timeout = false
        var t: Throwable? = e
        while (t != null) {
            if (t is HttpDataSource.InvalidResponseCodeException && http == null) http = t
            if (t is UnknownHostException) dns = true
            if (t is InterruptedIOException) timeout = true
            t = t.cause
        }
        if (http != null) return when (val code = http.responseCode) {
            429 -> if (n <= 3) ((RetryAfter.fromHeaders(http.headerFields) ?: (2L * n)) * 1000L).coerceIn(500L, 30_000L) else C.TIME_UNSET
            408, in 500..599 -> if (n <= 4 && code != 501) backoff(n) else C.TIME_UNSET
            else -> C.TIME_UNSET
        }
        if (dns) return if (n <= 2) 1_000L * n else C.TIME_UNSET
        if (timeout || e is HttpDataSource.HttpDataSourceException) return if (n <= 5) backoff(n) else C.TIME_UNSET
        return super.getRetryDelayMsFor(info)
    }

    private fun backoff(n: Int): Long = (1_000L shl (n - 1).coerceIn(0, 3)).coerceAtMost(8_000L) + (0..250).random()
}
