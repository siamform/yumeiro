/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Small built-in ad / tracker blocker for the WebViews that open a source's own page (stream
 * finder, headless capture, Cloudflare check). Blocking pre-roll / VAST requests means the page's
 * player goes straight to the real video, and the capture never mistakes an ad clip for the
 * stream (the "black screen like an ad" bug). Host matching walks up parent domains, like
 * uBlock / AdAway hosts lists, so `pubads.g.doubleclick.net` matches `doubleclick.net`.
 */
object AdFilter {
    /** Ad servers, ad exchanges and pop-under networks common on free streaming pages. */
    private val HOSTS = hashSetOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com", "imasdk.googleapis.com",
        "adservice.google.com", "pagead2.googlesyndication.com", "google-analytics.com", "googletagmanager.com",
        "adnxs.com", "adsrvr.org", "advertising.com", "amazon-adsystem.com", "criteo.com", "criteo.net", "pubmatic.com",
        "rubiconproject.com", "openx.net", "taboola.com", "outbrain.com", "revcontent.com", "mgid.com", "smartadserver.com",
        "exoclick.com", "exosrv.com", "exdynsrv.com", "juicyads.com", "trafficjunky.net", "trafficstars.com", "tsyndicate.com",
        "popads.net", "popcash.net", "propellerads.com", "propellerclick.com", "onclickads.net", "adsterra.com", "adsterratools.com",
        "hilltopads.net", "clickadu.com", "admaven.com", "ad-maven.com", "a-ads.com", "adcash.com", "bidvertiser.com",
        "monetag.com", "galaksion.com", "richpush.co", "pushame.com", "realsrv.com", "magsrv.com", "syndication.realsrv.com",
        "vidoomy.com", "spotxchange.com", "spotx.tv", "springserve.com", "lkqd.net", "teads.tv", "yandexadexchange.net",
        "scorecardresearch.com", "quantserve.com", "hotjar.com", "mc.yandex.ru", "adskeeper.com", "zeydoo.com", "ptdrv.com",
    )

    /** Path / query patterns of video-ad requests on otherwise legitimate hosts. */
    private val PATTERNS = Regex(
        """[/?&._-](vast|vpaid|vmap|preroll|pre-roll|midroll|adtag|ad_tag|adserver|ads?/(video|vast|tag)|prebid|popunder|pop-under)([/?&=._-]|$)""",
        RegexOption.IGNORE_CASE,
    )

    fun isAd(url: String): Boolean {
        val u = url.trim()
        if (!u.startsWith("http", ignoreCase = true)) return false
        val host = hostOf(u) ?: return false
        if (isAdHost(host)) return true
        return PATTERNS.containsMatchIn(u.substringAfter(host))
    }

    internal fun isAdHost(host: String): Boolean {
        var h = host.lowercase().trimEnd('.')
        while (true) {
            if (h in HOSTS) return true
            val dot = h.indexOf('.')
            if (dot < 0 || dot == h.lastIndexOf('.')) return false
            h = h.substring(dot + 1)
        }
    }

    private fun hostOf(url: String): String? = url.substringAfter("://", "").substringBefore('/').substringBefore('?')
        .substringAfterLast('@').substringBefore(':').takeIf { it.isNotBlank() }

    /** Empty 204-style answer for a blocked request. */
    fun blocked(): WebResourceResponse = WebResourceResponse("text/plain", "utf-8", 204, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
