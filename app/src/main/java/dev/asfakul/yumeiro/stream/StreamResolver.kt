/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.network.NetworkSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.URI

/** Outcome of inspecting a link before (or while) playing it. */
sealed interface Resolution {
    /** Ready for the player. [detected] describes what was found (shown to the user). */
    data class Playable(val request: StreamRequest, val detected: Sniff?) : Resolution

    /** The link is a channel list (M3U / M3U8 / PLS). */
    data class Playlist(val name: String?, val channels: List<StreamRequest>, val source: StreamRequest) : Resolution

    /** A web page whose player builds the stream at runtime: open it in the page browser. */
    data class NeedsBrowser(val pageUrl: String, val reason: String, val base: StreamRequest) : Resolution

    /** Only the platform's own app may play it (YouTube, DRM subscription services). */
    data class Handoff(val url: String, val platform: String, val reason: String) : Resolution

    /** Can't be played; [message] says why and what the user can do. */
    data class Failed(val title: String, val message: String, val suggestDns: Boolean = false) : Resolution
}

/**
 * Turns any link into something playable: direct media, HLS/DASH/Smooth manifests, IPTV playlists,
 * redirecting / token links and web pages that publish their player source. Every request uses the
 * stream's own headers, cookies and the configured DNS, exactly like playback will.
 *
 * Authentication, DRM and regional licensing are respected: nothing here bypasses them. When a page
 * only hands its stream to signed-in or licensed players, the result explains that instead.
 */
@OptIn(UnstableApi::class)
object StreamResolver {
    private const val MAX_PAGE_CANDIDATES = 6
    private const val MAX_IFRAME_DEPTH = 1

    fun defaultUserAgent(context: Context): String = Util.getUserAgent(context, "Yumeiro")

    /** True when the URL alone tells us the format, so playback can start without probing. */
    fun isConfident(request: StreamRequest): Boolean {
        if (request.format != StreamFormat.AUTO) return true
        val hint = StreamSniffer.fromUrl(request.url) ?: return false
        return hint.kind != MediaKind.IPTV_PLAYLIST
    }

    suspend fun resolve(context: Context, request: StreamRequest, thorough: Boolean = true): Resolution = withContext(Dispatchers.IO) {
        NetworkSettings.init(context.applicationContext)
        // YouTube / YouTube Music links play in-app through the login-free extractor
        // (dev.asfakul.yumeiro.youtube); the YouTube-app hand-off stays as the fallback.
        if (request.group != "YouTube" && PlatformRules.isYouTube(request.url)) {
            val id = dev.asfakul.yumeiro.youtube.YouTube.idFrom(request.url)
            val req = id?.let { runCatching { dev.asfakul.yumeiro.youtube.YouTubeStreams.videoRequest(context, it) }.getOrNull() }
            if (req != null) return@withContext Resolution.Playable(req, Sniff(MediaKind.VIDEO, "YouTube", "extractor"))
        }
        resolveBlocking(request, defaultUserAgent(context), StreamInterceptors.clientFor(request.url, NetworkClient.get(context)), thorough)
    }

    /**
     * @param thorough also probe URLs whose extension looks certain (`.m3u8` can be an IPTV list).
     * Player-side resolution uses `false` so confident links start instantly.
     */
    fun resolveBlocking(request: StreamRequest, userAgent: String, client: OkHttpClient, thorough: Boolean = true): Resolution {
        val url = request.url.trim()
        val scheme = url.substringBefore("://", "").lowercase()
        when (scheme) {
            "rtsp", "rtsps" -> return Resolution.Playable(request.copy(url = url, format = StreamFormat.RTSP), Sniff(MediaKind.VIDEO, "RTSP", "url"))
            "http", "https" -> Unit
            "rtmp", "rtmps", "udp", "rtp", "mms", "srt" -> return Resolution.Failed(
                "Unsupported protocol",
                "${scheme.uppercase()} links aren't supported by the player. Use the HLS (.m3u8) or DASH (.mpd) address of the same stream if the provider offers one.",
            )
            else -> return Resolution.Failed("Invalid link", "Enter an http(s):// or rtsp:// address.")
        }

        when (val rule = PlatformRules.ruleFor(url)) {
            is PlatformRules.Handoff -> return Resolution.Handoff(url, rule.platform, rule.reason)
            else -> Unit
        }
        if (request.format != StreamFormat.AUTO) return Resolution.Playable(request, null)
        if (!thorough && isConfident(request)) return Resolution.Playable(request, StreamSniffer.fromUrl(url))

        var req = request
        var probe = StreamProbe.probe(request, userAgent, client)
        // Refused (401/403/…): try the header variants a real browser / VLC / the host's own player
        // would send before giving up. Region locks (451) and expired tokens can't be fixed this way.
        if (probe.failure == null && probe.status in dev.asfakul.yumeiro.online.AccessLadder.ACCESS_CODES && probe.status != 451) {
            var unlocked: ProbeResult? = null
            val attempt = dev.asfakul.yumeiro.online.AccessLadder.firstWorking(request, request.pageUrl) { v ->
                val p = StreamProbe.probe(v, userAgent, client)
                (p.failure == null && p.status in 200..299).also { if (it) unlocked = p }
            }
            val ok = unlocked
            if (attempt != null && ok != null) {
                android.util.Log.i("StreamResolver", "HTTP ${probe.status} unlocked by ${attempt.step}")
                dev.asfakul.yumeiro.online.AccessLadder.learn(request.url, attempt.step)
                req = attempt.request
                probe = ok
            }
        }
        failureFor(probe, req)?.let { return it }

        return when (probe.sniff.kind) {
            MediaKind.HLS, MediaKind.DASH, MediaKind.SMOOTH, MediaKind.VIDEO, MediaKind.AUDIO ->
                Resolution.Playable(req.copy(format = probe.sniff.format), probe.sniff)
            MediaKind.IPTV_PLAYLIST -> playlistFrom(probe, req, userAgent, client)
            MediaKind.WEB_PAGE -> resolvePage(probe, req, userAgent, client, depth = 0)
            // Unknown binary: let the player's extractors sniff it (they recognise many more containers).
            MediaKind.UNKNOWN -> if (probe.text != null && probe.text.isNotBlank() && !probe.text.trimStart().startsWith("#"))
                Resolution.Failed("Not a media stream", "The link returned text (${probe.contentType ?: "unknown type"}) that isn't a playlist or a media manifest. Check that it's the stream address, not a web page or an API response.")
            else Resolution.Playable(req.copy(format = StreamFormat.PROGRESSIVE), probe.sniff)
        }
    }

    private fun playlistFrom(probe: ProbeResult, request: StreamRequest, userAgent: String, client: OkHttpClient): Resolution {
        // Large lists (> 4 MB) are fetched completely before parsing.
        val text = if (probe.complete) probe.text else runCatching { fetchFull(probe.finalUrl, request, userAgent, client) }.getOrNull() ?: probe.text
        val channels = when {
            text == null -> emptyList()
            probe.sniff.container == "PLS" -> PlsParser.parse(text)
            else -> M3uParser.parse(text, request)
        }
        if (channels.isEmpty()) return Resolution.Failed("Empty playlist", "The playlist was downloaded but contains no playable channels.")
        // A one-entry list is just a wrapper around a single stream: play it directly.
        if (channels.size == 1) {
            val only = channels.first()
            return Resolution.Playable(only.copy(title = only.title.ifBlank { request.title }), null)
        }
        val name = runCatching { URI(probe.finalUrl).path?.substringAfterLast('/')?.ifBlank { null } }.getOrNull() ?: PlatformRules.hostOf(probe.finalUrl)
        return Resolution.Playlist(name, channels, request)
    }

    private fun fetchFull(url: String, request: StreamRequest, userAgent: String, client: OkHttpClient): String {
        val b = okhttp3.Request.Builder().url(url).header("User-Agent", request.userAgent ?: userAgent)
        request.requestHeaders().forEach { (k, v) -> runCatching { b.header(k, v) } }
        client.newCall(b.build()).execute().use { r ->
            require(r.isSuccessful) { "HTTP ${r.code}" }
            val src = r.body.source()
            require(!src.request(30L * 1024 * 1024)) { "Playlist too large" }
            return src.buffer.readUtf8()
        }
    }

    private fun resolvePage(probe: ProbeResult, request: StreamRequest, userAgent: String, client: OkHttpClient, depth: Int): Resolution {
        val pageUrl = probe.finalUrl
        val html = probe.text.orEmpty()
        val page = WebPageExtractor.analyze(html, pageUrl)
        val origin = runCatching { URI(pageUrl).let { "${it.scheme}://${it.authority}" } }.getOrNull()
        val sessionRule = PlatformRules.ruleFor(pageUrl) as? PlatformRules.WebSession

        page.refreshUrl?.takeIf { depth == 0 && it != pageUrl }?.let { next ->
            val p = StreamProbe.probe(request.copy(url = next, referer = request.referer ?: pageUrl), userAgent, client)
            if (p.ok && p.sniff.kind == MediaKind.WEB_PAGE) return resolvePage(p, request, userAgent, client, depth + 1)
            if (p.ok && p.sniff.isPlayableMedia) return Resolution.Playable(request.copy(url = next, format = p.sniff.format, pageUrl = pageUrl), p.sniff)
        }

        var tried = 0
        for (c in page.candidates) {
            if (tried >= MAX_PAGE_CANDIDATES) break
            if (PlatformRules.isYouTube(c.url)) {
                return Resolution.Handoff(c.url, "YouTube", "This page plays a YouTube video. YouTube videos can only be played through YouTube's own player — open it in the YouTube app.")
            }
            if (c.origin == "iframe") {
                if (depth >= MAX_IFRAME_DEPTH) continue
                tried++
                val p = StreamProbe.probe(request.copy(url = c.url, referer = pageUrl, origin = null), userAgent, client)
                if (p.ok && p.sniff.kind == MediaKind.WEB_PAGE) {
                    val inner = resolvePage(p, request.copy(referer = pageUrl), userAgent, client, depth + 1)
                    if (inner is Resolution.Playable || inner is Resolution.Handoff) return inner
                }
                continue
            }
            tried++
            // The site's player sends its page as Referer/Origin; many CDNs check them.
            val candidate = request.copy(
                url = c.url,
                title = request.title.ifBlank { page.title.orEmpty() },
                referer = request.referer ?: pageUrl,
                origin = request.origin ?: origin,
                pageUrl = request.pageUrl ?: pageUrl,
            )
            val p = StreamProbe.probe(candidate, userAgent, client)
            if (p.ok && p.sniff.isPlayableMedia) return Resolution.Playable(candidate.copy(format = p.sniff.format), p.sniff)
            if (p.ok && p.sniff.kind == MediaKind.UNKNOWN && p.text == null) return Resolution.Playable(candidate.copy(format = StreamFormat.PROGRESSIVE), p.sniff)
        }

        val reason = buildString {
            when {
                sessionRule != null -> append(sessionRule.note)
                page.hasLoginForm -> append("This page asks you to sign in before it shows its player. Open it in the page browser, sign in, and start playback there; the authorised stream will be picked up.")
                else -> append("This link is a web page, not a media file, and its HTML doesn't publish a stream address — the site's player loads it at runtime. Open it in the page browser and start playback; the stream the page plays is picked up with the headers it needs.")
            }
            if (page.mentionsDrm && sessionRule == null) append("\n\nThe page uses DRM (encrypted media). DRM streams play only when the site's licence server accepts this device.")
        }
        return Resolution.NeedsBrowser(pageUrl, reason, request.copy(title = request.title.ifBlank { page.title.orEmpty() }))
    }

    /** Maps network / HTTP problems to actionable messages. */
    internal fun failureFor(p: ProbeResult, request: StreamRequest): Resolution.Failed? {
        val host = PlatformRules.hostOf(p.requestedUrl) ?: "the server"
        p.failure?.let { f ->
            val dns = NetworkSettings.dns.value
            return when (f) {
                NetworkFailure.DNS -> Resolution.Failed(
                    "Can't find the server",
                    if (dns.usesAlternative) "$host couldn't be resolved by the system DNS or ${dns.provider.label}. Check the address and your connection."
                    else "$host couldn't be resolved by the system DNS. Check the address, or turn on an alternative DNS (Cloudflare 1.1.1.1, Quad9 or Google) in Settings → Network, which is used only when the system DNS fails.",
                    suggestDns = !dns.usesAlternative,
                )
                NetworkFailure.TIMEOUT -> Resolution.Failed("Connection timed out", "$host didn't answer in time. The server may be overloaded or blocked on this network; try again later or on another network.")
                NetworkFailure.CONNECTION -> Resolution.Failed("Can't connect", "The connection to $host was refused or unreachable. The stream may be offline, or the port may be blocked on this network.")
                NetworkFailure.TLS -> Resolution.Failed("Secure connection failed", "The HTTPS certificate of $host couldn't be verified (${p.failureMessage}). Yumeiro only trusts valid system certificates.")
                NetworkFailure.OTHER -> Resolution.Failed("Network error", p.failureMessage ?: "The link couldn't be opened.")
            }
        }
        return when (p.status) {
            in 200..299 -> null
            401 -> Resolution.Failed("Sign-in required (HTTP 401)", "$host requires authorization${p.wwwAuthenticate?.let { " (${it.substringBefore(' ')})" }.orEmpty()}. Add the Cookie or Authorization header the provider gave you under Headers & DRM.")
            403 -> Resolution.Failed("Access denied (HTTP 403)", forbiddenAdvice(request))
            404, 410 -> Resolution.Failed("Stream not found (HTTP ${p.status})", "The stream is offline or the link has expired. Get a fresh link from the provider or refresh the playlist.")
            429 -> Resolution.Failed("Too many requests (HTTP 429)", "$host is rate-limiting requests${p.retryAfterSeconds?.let { "; it asks to wait ${it}s" }.orEmpty()}. Try again later.")
            451 -> Resolution.Failed("Unavailable here (HTTP 451)", "The provider blocks this content in your region for legal or licensing reasons. Yumeiro doesn't bypass regional restrictions.")
            in 500..599 -> Resolution.Failed("Server error (HTTP ${p.status})", "$host had an internal problem. Try again in a moment.")
            else -> Resolution.Failed("HTTP ${p.status}", "$host answered with HTTP ${p.status}.")
        }
    }

    fun forbiddenAdvice(request: StreamRequest): String = buildString {
        append("The server refused this link even after Yumeiro automatically retried it like a browser, like the host's own web player, as VLC and as a media player. What usually helps:\n")
        append("• The link contains a token that has expired — get a fresh link or refresh the playlist.\n")
        if (request.referer == null && request.origin == null) append("• The host only accepts its own web player — add its Referer / Origin under Headers & DRM.\n")
        if (request.userAgent == null) append("• The host blocks unknown players — pick a browser User-Agent.\n")
        if (request.cookie == null) append("• A session cookie is required — add the Cookie header.\n")
        append("• The stream is limited to some countries. Yumeiro doesn't bypass geo-blocking or access controls.")
    }
}
