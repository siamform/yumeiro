/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.net.Uri
import dev.asfakul.yumeiro.stream.StreamRequest
import java.util.concurrent.ConcurrentHashMap

/**
 * Recovers online links that the host refuses with HTTP 401 / 403 / 405 / 407 / 410 / 428 / 451,
 * without dropping the extension.
 *
 * Why links get refused (found while auditing the playback path, see docs/HANDOVER.md → 403):
 *  1. hot-link protection checks `Origin` + `Sec-Fetch-*`, which browsers (hls.js) always send
 *     but the extension never declared;
 *  2. the CDN wants the *site* (provider main URL) or its *own* origin as Referer, not the embed;
 *  3. the CDN rejects browser UAs on media requests (it expects the native player / okhttp);
 *  4. the signed token expired or was bound to the extraction session (handled by a fresh
 *     `loadLinks`, see [OnlineSession]);
 *  5. the link only works inside the embed page (handled by [dev.asfakul.yumeiro.online.web.HeadlessCapture]).
 *
 * This ladder handles 1–3: for a refused URL it hands out the next header variant to try, each
 * once, before the session fails over to the next source. Pure + keyed by URL → unit-testable.
 */
object AccessLadder {
    /** HTTP codes worth retrying with different headers. */
    val ACCESS_CODES = setOf(401, 403, 405, 407, 410, 428, 451)

    enum class Step(val label: String) {
        BROWSER("Retrying like a browser"),
        SITE_REFERER("Retrying with the site as referer"),
        SELF_REFERER("Retrying with the host as referer"),
        NO_REFERER("Retrying without referer"),
        PLAYER_UA("Retrying as a media player"),
        VLC_UA("Retrying as VLC"),
        SELF_BROWSER("Retrying like the host's own web player"),
    }

    data class Attempt(val step: Step, val request: StreamRequest)

    /** Provider main URLs for links, so SITE_REFERER knows the site (set by [OnlineSession]). */
    private val siteFor = ConcurrentHashMap<String, String>()
    private val tried = ConcurrentHashMap<String, MutableSet<Step>>()

    // ---- Learned profiles: the step that unlocked a host is tried first next time. ----------------
    private val learnedHosts = ConcurrentHashMap<String, Step>()
    @Volatile private var store: android.content.SharedPreferences? = null

    /** Loads the per-host profiles learned in earlier sessions (called from Application.onCreate). */
    fun attach(context: android.content.Context) {
        val prefs = context.applicationContext.getSharedPreferences("access_ladder", android.content.Context.MODE_PRIVATE)
        store = prefs
        prefs.all.forEach { (host, v) -> runCatching { Step.valueOf(v.toString()) }.getOrNull()?.let { learnedHosts[host] = it } }
    }

    /** Remembers that [step] unlocked [url]'s host. */
    fun learn(url: String, step: Step) {
        val host = hostOf(url) ?: return
        if (learnedHosts.put(host, step) != step) store?.edit()?.putString(host, step.name)?.apply()
    }

    /** The step that unlocked [url]'s host before, if any. */
    fun learned(url: String): Step? = hostOf(url)?.let { learnedHosts[it] }

    /** Forgets every learned profile (Settings → clear). */
    fun forgetLearned() { learnedHosts.clear(); store?.edit()?.clear()?.apply() }

    internal fun hostOf(url: String?): String? =
        url?.substringAfter("://", "")?.substringBefore('/')?.substringBefore('?')?.substringAfterLast('@')?.lowercase()?.takeIf { it.isNotBlank() }

    fun hint(url: String, siteUrl: String?) { if (!siteUrl.isNullOrBlank()) siteFor[url] = siteUrl }

    fun reset(url: String) { tried.remove(url) }

    /**
     * The next variant of [original] to try after a refusal, or null when every variant was tried.
     * [original] is the request exactly as the extension produced it.
     */
    fun next(original: StreamRequest): Attempt? {
        val done = tried.getOrPut(original.url) { mutableSetOf() }
        synchronized(done) {
            for (step in ordered(original.url)) {
                if (step in done) continue
                done += step
                val variant = variant(original, step, siteFor[original.url]) ?: continue
                if (variant == original) continue
                return Attempt(step, variant)
            }
        }
        return null
    }

    /** Pure: the request for [step]. Null when the step doesn't apply. */
    fun variant(r: StreamRequest, step: Step, siteUrl: String?): StreamRequest? {
        val referer = r.referer?.takeIf { it.isNotBlank() }
        return when (step) {
            Step.BROWSER -> {
                val origin = r.origin?.takeIf { it.isNotBlank() } ?: originOf(referer) ?: originOf(siteUrl)
                r.copy(
                    userAgent = r.userAgent ?: BROWSER_UA,
                    origin = origin,
                    headers = r.headers + BROWSER_FETCH_HEADERS + (origin?.let { mapOf("Sec-Fetch-Site" to siteRelation(it, r.url)) } ?: emptyMap()),
                )
            }
            Step.SITE_REFERER -> {
                val site = siteUrl?.let { originOf(it) } ?: return null
                if (originOf(referer) == site) return null
                r.copy(referer = "$site/", origin = site, headers = r.headers + BROWSER_FETCH_HEADERS)
            }
            Step.SELF_REFERER -> {
                val self = originOf(r.url) ?: return null
                if (originOf(referer) == self) return null
                r.copy(referer = "$self/", origin = self, headers = r.headers + BROWSER_FETCH_HEADERS + mapOf("Sec-Fetch-Site" to "same-origin"))
            }
            Step.NO_REFERER -> {
                if (referer == null && r.origin == null) return null
                r.copy(referer = null, origin = null, headers = r.headers.filterKeys { !it.equals("Referer", true) && !it.equals("Origin", true) })
            }
            Step.PLAYER_UA -> r.copy(userAgent = PLAYER_UA, headers = r.headers.filterKeys { !it.startsWith("Sec-Fetch", true) })
            // Many IPTV panels whitelist VLC (the player their playlists are made for).
            Step.VLC_UA -> r.copy(userAgent = VLC_UA, headers = r.headers.filterKeys { !it.startsWith("Sec-Fetch", true) })
            // Browser identity + the host's own origin as Referer/Origin: what its web player sends.
            Step.SELF_BROWSER -> {
                val self = originOf(r.url) ?: return null
                r.copy(
                    userAgent = BROWSER_UA, referer = "$self/", origin = self,
                    headers = r.headers + BROWSER_FETCH_HEADERS + mapOf("Sec-Fetch-Site" to "same-origin"),
                )
            }
        }
    }

    /**
     * Probes every distinct header variant of [original] (in ladder order) with [works] and returns
     * the first accepted one, or null. Used by the Stream resolver before a link is reported as
     * refused, so most 401/403 links start playing without the user touching Headers & DRM.
     */
    fun firstWorking(original: StreamRequest, siteUrl: String?, maxProbes: Int = 7, works: (StreamRequest) -> Boolean): Attempt? {
        val seen = mutableSetOf(original)
        var probes = 0
        for (step in ordered(original.url)) {
            if (probes >= maxProbes) break
            val v = variant(original, step, siteUrl) ?: continue
            if (!seen.add(v)) continue
            probes++
            if (runCatching { works(v) }.getOrDefault(false)) return Attempt(step, v)
        }
        return null
    }

    /** Ladder order, with the step learned for this host (if any) moved to the front. */
    fun ordered(url: String): List<Step> {
        val first = learned(url) ?: return Step.entries
        return listOf(first) + Step.entries.filter { it != first }
    }

    internal fun originOf(url: String?): String? {
        val u = runCatching { Uri.parse(url ?: return null) }.getOrNull() ?: return null
        val scheme = u.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
        val host = u.host?.takeIf { it.isNotBlank() } ?: return null
        val port = if (u.port > 0) ":${u.port}" else ""
        return "$scheme://$host$port"
    }

    private fun siteRelation(origin: String, url: String): String {
        val a = originOf(origin)?.substringAfter("://")?.substringBefore(':') ?: return "cross-site"
        val b = originOf(url)?.substringAfter("://")?.substringBefore(':') ?: return "cross-site"
        fun base(h: String) = h.split('.').takeLast(2).joinToString(".")
        return when {
            a == b -> "same-origin"
            base(a) == base(b) -> "same-site"
            else -> "cross-site"
        }
    }

    const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    const val PLAYER_UA = "ExoPlayer/1.5 (Linux; Android 14) okhttp/4.12.0"
    const val VLC_UA = "VLC/3.0.21 LibVLC/3.0.21"

    /** What Chrome's media / hls.js XHR requests carry. */
    private val BROWSER_FETCH_HEADERS = mapOf(
        "Accept" to "*/*",
        "Accept-Language" to "en-US,en;q=0.9",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
    )
}
