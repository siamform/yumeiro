/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
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
    /** G10 ER-13: whether the Software decoder is already chosen (then the tip is not offered). */
    private val softwareDecoder: () -> Boolean = { false },
    /** H-3: the Auto decoder: switches this video to software decoders once; true if it did (then retry). */
    private val autoSoftware: () -> Boolean = { false },
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
            else RecoveryAction.Fail(tr(R.string.live_stream_keeps_falling_behind, "Live stream keeps falling behind"), tr(R.string.the_connection_is_too_slow_to, "The connection is too slow to keep up with this live stream. Try a lower quality or a faster network."))
        }
        f.httpStatus?.let { code -> return decideHttp(code, f, canRefresh) }

        if (f.dnsFailure) {
            val n = next("dns")
            if (n <= 2) return RecoveryAction.Retry(1_500L * n, n, 2, tr(R.string.looking_up_the_server_again, "Looking up the server again"))
            return if (alternativeDns()) RecoveryAction.Fail(tr(R.string.cant_find_the_server, "Can't find the server"), "The stream's host couldn't be resolved by the system DNS or ${dnsLabel()}. Check the link and your internet connection.")
            else RecoveryAction.Fail(tr(R.string.cant_find_the_server, "Can't find the server"), tr(R.string.the_streams_host_couldnt_be_resolved, "The stream's host couldn't be resolved by the system DNS. Turn on an alternative DNS (Cloudflare 1.1.1.1, Quad9 or Google) in Settings → Network — it's used only when the system DNS fails — then retry."), offerDnsSettings = true)
        }
        if (f.isNetwork && f.errorCode != PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND &&
            f.errorCode != PlaybackException.ERROR_CODE_IO_NO_PERMISSION && f.errorCode != PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED &&
            f.errorCode != PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE) {
            val n = next(if (f.isTimeout) "timeout" else "network")
            return if (n <= 3) RecoveryAction.Retry(backoff(n), n, 3, if (f.isTimeout) tr(R.string.connection_timed_out_reconnecting, "Connection timed out — reconnecting") else tr(R.string.connection_lost_reconnecting, "Connection lost — reconnecting"))
            else RecoveryAction.Fail(
                if (f.isTimeout) tr(R.string.connection_timed_out, "Connection timed out") else tr(R.string.connection_problem, "Connection problem"),
                tr(R.string.the_stream_couldnt_be_reached_after, "The stream couldn't be reached after 3 attempts. Check your connection; if the stream works elsewhere, the server may be blocking this network."),
            )
        }
        if (f.isParsing && autoFormat && !reprobed) { reprobed = true; return RecoveryAction.Reprobe }
        if (f.isDrm) return RecoveryAction.Fail(
            tr(R.string.protected_stream_drm, "Protected stream (DRM)"),
            tr(R.string.the_licence_server_refused_playback_or, "The licence server refused playback or this device can't decrypt the stream. DRM streams play only with a licence URL or ClearKey keys you're authorised to use; Yumeiro doesn't bypass DRM."),
        )
        if (f.isParsing) return RecoveryAction.Fail(
            tr(R.string.unsupported_stream_format, "Unsupported stream format"),
            "The data from this link isn't a media format the player understands${f.message?.let { " ($it)" }.orEmpty()}. If it's a web page, open it from the Stream tab so its player stream can be found.",
        )
        // G10 ER-25: only decoder errors (4xxx) are video format problems. Audio output, the player's own
        // errors and the four excluded I/O errors used to fall through to "hardware decoders could not
        // play this file", and switching the video decoder can't fix any of them (NewPipe v0.28.0
        // Player.onPlayerError and the media3 1.8.0 demo name decoders only for decoder failures).
        val detail = "\n\n${f.message ?: "Playback error"}"
        if (f.errorCode in 5000..5999) {
            val n = next("audio")
            return if (n <= 1) RecoveryAction.Retry(1_000L, n, 1, tr(R.string.audio_output_stopped_restarting, "Audio output stopped — restarting"))
            else RecoveryAction.Fail(
                tr(R.string.audio_output_failed, "Audio output failed"),
                tr(R.string.the_audio_couldnt_play_on_this, "The audio couldn't play on this device's speaker or headset. Reconnect the headset or switch the audio output, then retry.%1\$s", detail),
            )
        }
        when (f.errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> return RecoveryAction.Fail(
                tr(R.string.file_or_stream_not_found, "File or stream not found"),
                tr(R.string.the_file_was_moved_or_deleted_2, "The file was moved or deleted, or the stream's link no longer exists. Rescan your files or get a fresh link.%1\$s", detail),
            )
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> return RecoveryAction.Fail(
                tr(R.string.cant_open_this_file, "Can't open this file"),
                tr(R.string.yumeiro_isnt_allowed_to_read_this, "Yumeiro isn't allowed to read this file. Pick it again from the file picker, or allow access to the folder.%1\$s", detail),
            )
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> return RecoveryAction.Fail(
                tr(R.string.insecure_link_blocked, "Insecure link blocked"),
                tr(R.string.this_stream_uses_plain_http_which, "This stream uses plain http, which this device doesn't allow. Use the https link if the provider has one.%1\$s", detail),
            )
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> return RecoveryAction.Fail(
                tr(R.string.couldnt_seek_in_this_stream, "Couldn't seek in this stream"),
                tr(R.string.the_server_cant_jump_to_that, "The server can't jump to that position. Retry from the start or try another source.%1\$s", detail),
            )
        }
        if (!f.isDecoder) return RecoveryAction.Fail(
            tr(R.string.playback_error, "Playback error"),
            tr(R.string.the_player_stopped_unexpectedly_retry_if, "The player stopped unexpectedly. Retry; if it keeps happening, try another quality or source.%1\$s", detail),
        )
        if (autoSoftware()) return RecoveryAction.Retry(300L, 1, 1, AUTO_SOFTWARE)
        return RecoveryAction.Fail(
            tr(R.string.unsupported_video_format, "Unsupported video format"),
            tr(R.string.this_devices_hardware_decoders_could_not, "This device's hardware decoders could not play this file, or the media stream is corrupted. ") +
                (if (softwareDecoder()) tr(R.string.try_another_quality_or_source, "Try another quality or source.") else DECODER_TIP) + "\n\n${f.message ?: "Playback error"}",
        )
    }

    private fun decideHttp(code: Int, f: ErrorFacts, canRefresh: Boolean): RecoveryAction = when (code) {
        401 -> RecoveryAction.Fail(tr(R.string.sign_in_required_http_401, "Sign-in required (HTTP 401)"), tr(R.string.the_server_asked_for_authorization_add, "The server asked for authorization. Add the Cookie or Authorization header the provider gave you under Stream → Headers & DRM."))
        403, 404, 410 -> if (canRefresh && now() - lastRefreshAt > 120_000L) {
            lastRefreshAt = now()
            RecoveryAction.Refresh(if (code == 403) tr(R.string.access_refused_fetching_a_fresh_link, "Access refused — fetching a fresh link") else tr(R.string.stream_link_expired_refreshing, "Stream link expired — refreshing"))
        } else if (code == 403) RecoveryAction.Fail(
            tr(R.string.access_denied_http_403, "Access denied (HTTP 403)"),
            tr(R.string.the_server_refused_the_stream_the, "The server refused the stream. The link's token may have expired, the host may require its own Referer / Origin / User-Agent / Cookie headers, or the stream is limited to some countries. Get a fresh link or add the headers under Stream → Headers & DRM. Yumeiro doesn't bypass geo-blocking or access controls."),
        ) else RecoveryAction.Fail(tr(R.string.stream_not_found_http, "Stream not found (HTTP %1\$s)", code), tr(R.string.the_stream_is_offline_or_its, "The stream is offline or its link has expired. Get a fresh link or refresh the playlist."))
        429 -> {
            val n = next("429")
            val wait = ((f.retryAfterSeconds ?: (10L * n)) * 1000L).coerceIn(1_000L, 60_000L)
            if (n <= 2) RecoveryAction.Retry(wait, n, 2, "Server is busy — waiting ${wait / 1000}s as it asked")
            else RecoveryAction.Fail(tr(R.string.too_many_requests_http_429, "Too many requests (HTTP 429)"), tr(R.string.the_server_keeps_rate_limiting_this, "The server keeps rate-limiting this stream. Try again in a few minutes."))
        }
        451 -> RecoveryAction.Fail(tr(R.string.unavailable_in_your_region_http_451, "Unavailable in your region (HTTP 451)"), tr(R.string.the_provider_blocks_this_content_here, "The provider blocks this content here for legal or licensing reasons. Yumeiro doesn't bypass regional restrictions."))
        in 500..599 -> {
            val n = next("5xx")
            if (n <= 3) RecoveryAction.Retry(backoff(n), n, 3, tr(R.string.server_error_retrying, "Server error %1\$s — retrying", code))
            else RecoveryAction.Fail(tr(R.string.server_error_http, "Server error (HTTP %1\$s)", code), tr(R.string.the_stream_server_keeps_failing_try, "The stream server keeps failing. Try again later."))
        }
        else -> RecoveryAction.Fail("HTTP $code", tr(R.string.the_server_answered_with_http_and, "The server answered with HTTP %1\$s and the stream can't be loaded.", code))
    }

    companion object {
        const val STABLE_RESET_MS = 30_000L
        /** H-3: shown while Auto moves a video that failed to decode onto the software decoder. */
        val AUTO_SOFTWARE: String get() = tr(R.string.switching_to_the_software_decoder, "Switching to the software decoder")
        /** The setting that helps: Settings › Audio quality & sound › Quality and audio › Video decoder. */
        val DECODER_TIP: String get() = tr(R.string.set_video_decoder_to_software_in, "Set Video decoder to Software in Settings › Video quality & playback, or try another quality or source.")
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
