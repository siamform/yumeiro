/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.adchain

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * ADCHAIN-3 (owner item 28, ADR-064): walks a link-shortener chain with plain HTTP first — no browser, no taps —
 * and stops as soon as a step needs a real browser or a person. Adapted from the contributor's AdChainHttpFastPath.
 *
 * Kept from the contributor: token wrappers, WP Safelink forms, AdLinkFly /links/go with its session cookies,
 * meta / JS redirects, a hop limit and loop detection, challenge detection that hands over instead of looping.
 * Changed for Yumeiro: bodies are read only for pages (Content-Type first, capped at [MAX_BODY]) so a video at
 * the end is never downloaded here; AdLinkFly's own wait is honoured before the form is sent (the server checks
 * it); an AdLinkFly answer without a link is a hand-over, not a destination; cookies follow domain / path /
 * secure rules ([Cookie.matches]); the user agent is the WebView's, so cookies stay valid when the browser takes
 * over; every hop is checked by [AdChainDestinations.refusal]. Never solves a captcha, never makes a token.
 */
class AdChainHttp(
    base: OkHttpClient,
    private val userAgent: String,
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val budgetMs: Long = 45_000L,
) {
    sealed interface Outcome {
        /** The chain ended on [url] (a file host, a media file or the site behind the shortener). */
        data class Reached(val url: String, val referer: String?, val cookies: List<Cookie>, val hops: Int) : Outcome
        /** A step needs the browser (a check, a JS-only button); [url] is where it stopped, [cookies] the session. */
        data class NeedsBrowser(val url: String, val reason: String, val check: AdChainPages.Check?, val cookies: List<Cookie>) : Outcome
        /** The first page is not a shortener step: leave the link to the normal flow. */
        data object NotAChain : Outcome
        data class Failed(val url: String, val reason: String) : Outcome
    }

    /** One chain's cookies: domain / path / secure / expiry matching by OkHttp itself. Memory only. */
    class SessionJar : CookieJar {
        private val store = ConcurrentHashMap<String, Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (c in cookies) store["${c.domain}|${c.path}|${c.name}"] = c
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            store.entries.removeAll { it.value.expiresAt < now }
            return store.values.filter { it.matches(url) }
        }
        fun all(): List<Cookie> = store.values.toList()
        fun header(url: String): String? = url.toHttpUrlOrNull()?.let { u -> loadForRequest(u).joinToString("; ") { "${it.name}=${it.value}" } }?.takeIf { it.isNotEmpty() }
    }

    val jar = SessionJar()
    private val client = base.newBuilder().followRedirects(false).followSslRedirects(false).cookieJar(jar).build()

    private class Page(val url: String, val code: Int, val type: String, val body: String?, val location: String?)

    suspend fun walk(start: String): Outcome = withTimeoutOrNull(budgetMs) { run(start) }
        ?: Outcome.Failed(start, "the link took too long")

    private suspend fun run(start: String): Outcome {
        var url = AdChainDestinations.unwrapTracking(start)
        var referer: String? = null
        val seen = HashSet<String>()
        var hops = 0
        while (hops < MAX_HOPS) {
            AdChainDestinations.refusal(url)?.let { return Outcome.Failed(url, it) }
            if (hops > 0 && AdChainDestinations.isMediaOrFileHost(url)) return Outcome.Reached(url, referer, jar.all(), hops)
            if (!seen.add(url)) return Outcome.NeedsBrowser(url, "the link goes round in a circle", null, jar.all())
            val page = try { get(url, referer) } catch (e: IOException) {
                return if (hops == 0) Outcome.NotAChain else Outcome.Failed(url, e.message ?: "network error")
            }
            if (page.location != null) {
                val next = AdChainPages.resolve(page.location, url) ?: return Outcome.Failed(url, "bad redirect")
                referer = url; url = AdChainDestinations.unwrapTracking(next); hops++; continue
            }
            if (page.body == null) {
                // Not a page (a video, an archive): the chain has ended here.
                return if (hops == 0) Outcome.NotAChain else Outcome.Reached(url, referer, jar.all(), hops)
            }
            val html = page.body
            val kind = AdChainPages.classify(url, html)
            if (page.code == 403 || page.code == 503) {
                if (kind.check != null || AdChainPages.check(html) != null) return Outcome.NeedsBrowser(url, "the site asks for a check", kind.check, jar.all())
            }
            val next: String? = when (kind.family) {
                AdChainPages.Family.TOKEN_WRAPPER -> kind.unwrapped
                AdChainPages.Family.MEDIA_OR_FILE_HOST -> return Outcome.Reached(url, referer, jar.all(), hops)
                AdChainPages.Family.HUMAN_CHECK -> return Outcome.NeedsBrowser(url, "the site asks for a check", kind.check, jar.all())
                AdChainPages.Family.WP_SAFELINK -> {
                    if (kind.delaySeconds > 0) wait(kind.delaySeconds.coerceAtMost(MAX_WAIT_S) * 1000L)
                    kind.direct ?: submit(kind, url)?.let { r -> r.location?.let { AdChainPages.resolve(it, kind.formAction ?: url) } ?: r.body?.let { b -> AdChainPages.classify(kind.formAction ?: url, b).let { it.direct ?: it.next ?: it.unwrapped } } }
                }
                AdChainPages.Family.ADLINKFLY -> {
                    if (kind.tooEarly) return Outcome.NeedsBrowser(url, "the site's timer runs in the browser", null, jar.all())
                    // The server refuses /links/go before its own timer ends: wait for it, never shorten it.
                    wait((kind.delaySeconds.coerceAtMost(MAX_WAIT_S) + 1) * 1000L)
                    val r = submit(kind, url, xhr = true) ?: return Outcome.NeedsBrowser(url, "the site didn't answer", null, jar.all())
                    linkFrom(r) ?: return Outcome.NeedsBrowser(url, "the site wants its button tapped", AdChainPages.check(r.body.orEmpty()), jar.all())
                }
                AdChainPages.Family.COUNTDOWN -> kind.next ?: return Outcome.NeedsBrowser(url, "the site's timer runs in the browser", null, jar.all())
                AdChainPages.Family.PLAIN_PAGE -> return when {
                    hops == 0 && !AdChainDestinations.isGate(url) -> Outcome.NotAChain
                    AdChainDestinations.isGate(url) -> Outcome.NeedsBrowser(url, "the site's button needs the browser", null, jar.all())
                    else -> Outcome.Reached(url, referer, jar.all(), hops)
                }
            }
            if (next.isNullOrBlank()) return Outcome.NeedsBrowser(url, "the next step needs the browser", null, jar.all())
            referer = url; url = AdChainDestinations.unwrapTracking(next); hops++
        }
        return Outcome.NeedsBrowser(url, "the link has too many steps", null, jar.all())
    }

    private fun linkFrom(r: Page): String? {
        r.location?.let { return it }
        val b = r.body ?: return null
        val o = runCatching { JSONObject(b.trim()) }.getOrNull() ?: return null
        if (!o.optString("status").equals("success", true)) return null
        return o.optString("url").takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun submit(kind: AdChainPages.Page, pageUrl: String, xhr: Boolean = false): Page? {
        val action = kind.formAction ?: return null
        AdChainDestinations.refusal(action)?.let { return null }
        val b = Request.Builder().header("User-Agent", userAgent).header("Referer", pageUrl)
            .header("Accept", if (xhr) "application/json, text/javascript, */*; q=0.01" else ACCEPT_HTML)
        pageUrl.toHttpUrlOrNull()?.let { b.header("Origin", "${it.scheme}://${it.host}${if (it.port != HttpUrl.defaultPort(it.scheme)) ":${it.port}" else ""}") }
        if (xhr) b.header("X-Requested-With", "XMLHttpRequest")
        val req = if (kind.formPost) {
            val f = FormBody.Builder(); kind.fields.forEach { (k, v) -> f.add(k, v) }
            b.url(action).post(f.build()).build()
        } else {
            val u = action.toHttpUrlOrNull()?.newBuilder() ?: return null
            kind.fields.forEach { (k, v) -> u.addQueryParameter(k, v) }
            b.url(u.build()).get().build()
        }
        return try { client.newCall(req).execute().use { read(action, it) } } catch (_: IOException) { null }
    }

    private fun get(url: String, referer: String?): Page {
        val b = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", ACCEPT_HTML)
        referer?.let { b.header("Referer", it) }
        return client.newCall(b.get().build()).execute().use { read(url, it) }
    }

    private fun read(url: String, r: Response): Page {
        val type = r.header("Content-Type").orEmpty().lowercase()
        val loc = if (r.code in 300..399) r.header("Location") else null
        val textual = type.isEmpty() || "html" in type || "json" in type || "text/" in type || "javascript" in type || "xml" in type
        val body = if (loc == null && textual) {
            val src = r.body.source()
            src.request(MAX_BODY)
            src.buffer.let { buf -> buf.readUtf8(minOf(buf.size, MAX_BODY)) }
        } else null
        return Page(url, r.code, type, body, loc)
    }

    companion object {
        const val MAX_HOPS = 12
        const val MAX_WAIT_S = 20
        const val MAX_BODY = 768L * 1024
        private const val ACCEPT_HTML = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    }
}
