/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.adchain

import org.jsoup.Jsoup
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * ADCHAIN-3 (owner item 28): what kind of link-shortener page a page is, from its forms and scripts (not from
 * a list of domains). Adapted from the contributor's AdProviderRecognizer (wanxtaexe-ai/legendary-octo-fishstick,
 * yumeiro-adchain): same families, regexes hoisted, captcha detection kept, the "Continue" link is never an ad host
 * (Yumeiro's one ad list, [dev.asfakul.yumeiro.online.web.AdFilter]). Pure.
 */
object AdChainPages {
    enum class Family { ADLINKFLY, WP_SAFELINK, TOKEN_WRAPPER, COUNTDOWN, HUMAN_CHECK, MEDIA_OR_FILE_HOST, PLAIN_PAGE }
    enum class Check { TURNSTILE, RECAPTCHA, HCAPTCHA, CLOUDFLARE_PAGE }

    data class Page(
        val family: Family,
        val check: Check? = null,
        val unwrapped: String? = null,
        val delaySeconds: Int = 0,
        val formAction: String? = null,
        val formPost: Boolean = false,
        val fields: Map<String, String> = emptyMap(),
        val direct: String? = null,
        val next: String? = null,
        val tooEarly: Boolean = false,
    )

    private const val TPX_KEY = "7eea12b58ee6bd4833706e6d5a9468a865315209"
    private val WRAPPER_KEYS = setOf("url", "safe", "shortid", "link", "target", "dest", "go")
    private val TPX_PARAM = Regex("[?&]url=([^&#]+)")
    private val META_URL = Regex("url\\s*=\\s*['\"]?([^'\";\\s]+)", RegexOption.IGNORE_CASE)
    private val JS_JUMP = Regex("(?:window|document|top)\\.location(?:\\.href)?\\s*=\\s*['\"](https?://[^'\"]+)['\"]", RegexOption.IGNORE_CASE)
    /** AdLinkFly's own wait: `var counter = 10`, `data-time="8"`, `timer: 5`. */
    private val ALF_WAIT = Regex("(?:counter|timer|countdown|data-time|seconds)\\s*[=:\"']+\\s*(\\d{1,2})\\b", RegexOption.IGNORE_CASE)
    private val WAIT_MARKERS = listOf("please wait", "redirecting", "too early", "your link is almost ready", "almost ready")
    private val CONTINUE = setOf("continue", "proceed", "get link", "go to link", "click here to continue", "skip ad")

    /** A wrapper address that carries its target (`?url=https…`, base64 in `?safe=`, tpxsub main2.php). */
    fun unwrap(url: String): String? {
        tpx(url)?.let { return it }
        val q = url.substringAfter('?', "").takeIf { it.isNotBlank() } ?: return null
        for (pair in q.split('&')) {
            if (pair.substringBefore('=').lowercase() !in WRAPPER_KEYS) continue
            val raw = runCatching { URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8") }.getOrDefault(pair.substringAfter('=', "")).trim()
            if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
            base64Url(raw)?.let { return it }
        }
        return null
    }

    internal fun tpx(url: String): String? {
        if (!url.contains("main2.php", true) && !url.contains("links.tpxsub.com", true)) return null
        val p = TPX_PARAM.find(url)?.groupValues?.get(1) ?: return null
        val enc = runCatching { URLDecoder.decode(p, "UTF-8") }.getOrDefault(p).trim()
        if (enc.length < 16 || enc.length % 2 != 0) return null
        val sb = StringBuilder(enc.length / 2)
        for (i in 0 until enc.length / 2) {
            val n = "${enc[i * 2 + 1]}${enc[i * 2]}".toIntOrNull(36) ?: return null
            val ch = n - TPX_KEY[i % TPX_KEY.length].code
            if (ch !in 32..126) return null
            sb.append(ch.toChar())
        }
        return sb.toString().takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun base64Url(raw: String): String? {
        if (raw.length < 12) return null
        return runCatching {
            val n = raw.replace('-', '+').replace('_', '/')
            String(Base64.getDecoder().decode(n + "=".repeat((4 - n.length % 4) % 4)), StandardCharsets.UTF_8).trim()
        }.getOrNull()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    fun check(html: String): Check? {
        val l = html.lowercase()
        return when {
            "cf-turnstile" in l || "challenges.cloudflare.com/turnstile" in l -> Check.TURNSTILE
            "/cdn-cgi/challenge-platform/" in l || "cf-chl-" in l || "<title>just a moment" in l -> Check.CLOUDFLARE_PAGE
            "g-recaptcha" in l || "recaptcha/api.js" in l || "please click on below captcha box" in l -> Check.RECAPTCHA
            "h-captcha" in l || "hcaptcha.com/1/api.js" in l -> Check.HCAPTCHA
            else -> null
        }
    }

    /** AdLinkFly's wait before /links/go accepts the form (the server checks it), 0..30 s. */
    fun waitSeconds(html: String): Int = ALF_WAIT.find(html)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 30) ?: 0

    fun classify(url: String, html: String): Page {
        unwrap(url)?.takeIf { it != url }?.let { return Page(Family.TOKEN_WRAPPER, unwrapped = it) }
        if (AdChainDestinations.isMediaOrFileHost(url)) return Page(Family.MEDIA_OR_FILE_HOST)
        check(html)?.let { return Page(Family.HUMAN_CHECK, check = it) }
        val lower = html.lowercase()
        val tooEarly = "too early" in lower
        val doc = Jsoup.parse(html, url)
        var safe: Page? = null
        for (form in doc.select("form")) {
            val inputs = LinkedHashMap<String, String>()
            for (inp in form.select("input[name]")) {
                if (inp.attr("type").lowercase() in setOf("submit", "button", "image", "reset", "file")) continue
                inputs[inp.attr("name")] = inp.attr("value")
            }
            val action = resolve(form.attr("action").ifBlank { url }, url) ?: url
            val post = form.attr("method").equals("post", true)
            if (inputs.containsKey("ad_form_data") || form.id() == "go-link" || action.trimEnd('/').endsWith("/links/go")) {
                return Page(Family.ADLINKFLY, formAction = action, formPost = true, fields = inputs, delaySeconds = waitSeconds(html), tooEarly = tooEarly)
            }
            val wp = inputs["newwpsafelink"]
            if (!wp.isNullOrBlank()) {
                val m = jsonMap(wp)
                safe = Page(Family.WP_SAFELINK, delaySeconds = (m?.get("delay") as? Number)?.toInt() ?: 0, formAction = action, formPost = post, fields = inputs,
                    direct = listOf("linkr", "second_safelink_url", "newsafelink").firstNotNullOfOrNull { k -> (m?.get(k) as? String)?.takeIf { it.startsWith("http") } })
            } else if (safe == null && (form.attr("name") == "tp" || inputs.containsKey("go"))) {
                safe = Page(Family.WP_SAFELINK, formAction = action, formPost = post, fields = inputs)
            }
        }
        safe?.let { return it.copy(tooEarly = tooEarly) }
        val meta = doc.selectFirst("meta[http-equiv~=(?i)refresh]")?.attr("content")?.let { META_URL.find(it)?.groupValues?.get(1) }?.let { resolve(it, url) }
        val js = JS_JUMP.find(html)?.groupValues?.get(1)?.replace("\\/", "/")
        val cont = doc.select("#nextPage a, a.get-link, #btn-get-link, #get-link-btn, a[href]").firstOrNull { a ->
            val href = a.attr("href").trim()
            if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript:", true)) return@firstOrNull false
            val idc = "${a.id()} ${a.className()}".lowercase()
            ("get-link" in idc || "tp-snp2" in idc || a.text().trim().lowercase() in CONTINUE) &&
                !dev.asfakul.yumeiro.online.web.AdFilter.isAd(resolve(href, url) ?: href)
        }?.let { resolve(it.attr("href"), url) }
        val waiting = tooEarly || WAIT_MARKERS.any { it in lower } || meta != null || js != null || cont != null
        return Page(if (waiting) Family.COUNTDOWN else Family.PLAIN_PAGE, next = meta ?: js ?: cont, tooEarly = tooEarly)
    }

    private fun jsonMap(b64: String): Map<String, Any?>? = runCatching {
        val n = b64.replace('-', '+').replace('_', '/')
        val o = org.json.JSONObject(String(Base64.getDecoder().decode(n + "=".repeat((4 - n.length % 4) % 4)), StandardCharsets.UTF_8))
        o.keys().asSequence().associateWith { o.opt(it) }
    }.getOrNull()

    fun resolve(raw: String?, base: String): String? {
        val c = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (c.startsWith("javascript:", true) || c.startsWith("mailto:", true) || c.startsWith("#")) return null
        return runCatching { URI(base).resolve(c).toString() }.getOrNull()
    }
}
