/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.security

import dev.asfakul.yumeiro.network.NetworkClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Trust boundary for data the app reads from the project's own GitHub repository:
 * the Playlist Market, Extension Market and default-streams catalogs (bot-only branches) and the
 * self-updater (GitHub Releases).
 *
 * Rules: HTTPS only, exact host allowlist, path must stay inside `siamform/yumeiro`, the final
 * URL after redirects is re-checked, and every fetch has a hard size and wall-clock limit.
 * User-added playlists and third-party extension repositories do NOT go through here.
 */
object TrustedSources {
    const val OWNER = "siamform"
    const val REPO = "yumeiro"

    private const val RAW_HOST = "raw.githubusercontent.com"
    /** Bot-only branches the app reads catalogs from. */
    private val CATALOG_BRANCHES = setOf("market", "streams", "extensions", "feed")
    /** Whole-call limit for catalog downloads (connect + redirects + body). */
    private const val CALL_TIMEOUT_S = 45L

    class UntrustedSourceException(message: String) : IOException(message)

    /** True for `https://raw.githubusercontent.com/siamform/yumeiro/{market|streams|extensions|feed}/…`. */
    fun isOfficialCatalogUrl(url: HttpUrl): Boolean {
        if (!url.isHttps || url.host != RAW_HOST || url.port != 443) return false
        val seg = url.pathSegments
        return seg.size >= 4 && seg[0] == OWNER && seg[1] == REPO && seg[2] in CATALOG_BRANCHES &&
            seg.none { it == ".." || it == "." }
    }

    fun isOfficialCatalogUrl(url: String): Boolean = url.toHttpUrlOrNull()?.let(::isOfficialCatalogUrl) == true

    /**
     * Release asset URLs as published by the GitHub API for [owner]/[repo]:
     *  - `https://github.com/{owner}/{repo}/releases/download/{tag}/{file}` (browser URL)
     *  - `https://api.github.com/repos/{owner}/{repo}/releases/assets/{id}` (API URL)
     */
    fun isOfficialReleaseAssetUrl(url: String?, owner: String = OWNER, repo: String = REPO): Boolean {
        val u = url?.toHttpUrlOrNull() ?: return false
        if (!u.isHttps || u.port != 443) return false
        val s = u.pathSegments
        return when (u.host) {
            "github.com" -> s.size >= 6 && s[0].equals(owner, true) && s[1].equals(repo, true) &&
                s[2] == "releases" && s[3] == "download"
            "api.github.com" -> s.size == 6 && s[0] == "repos" && s[1].equals(owner, true) &&
                s[2].equals(repo, true) && s[3] == "releases" && s[4] == "assets" && s[5].all(Char::isDigit)
            else -> false
        }
    }

    /** Hosts GitHub redirects release downloads to (signed, short-lived URLs). */
    fun isGitHubDownloadHost(url: String?): Boolean {
        val u = url?.toHttpUrlOrNull() ?: return false
        if (!u.isHttps) return false
        val h = u.host
        return h == "github.com" || h == "api.github.com" || h.endsWith(".githubusercontent.com")
    }

    private val client: OkHttpClient by lazy {
        NetworkClient.get().newBuilder().callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS).build()
    }

    /**
     * Downloads an official catalog file as UTF-8 text. Throws on a non-official URL, an
     * off-allowlist redirect, a non-2xx status, a body larger than [maxBytes] or a timeout, so
     * callers fall back to their last-known-good cached copy.
     */
    fun fetchCatalogText(url: String, maxBytes: Long): String {
        val parsed = url.toHttpUrlOrNull()
        if (parsed == null || !isOfficialCatalogUrl(parsed)) throw UntrustedSourceException("Blocked untrusted catalog URL")
        val req = Request.Builder().url(parsed)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(req).execute().use { r ->
            if (!isOfficialCatalogUrl(r.request.url)) throw UntrustedSourceException("Catalog redirected to an untrusted host")
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val declared = r.body.contentLength()
            if (declared > maxBytes) throw IOException("Catalog too large")
            val src = r.body.source()
            if (src.request(maxBytes + 1)) throw IOException("Catalog too large")
            return src.buffer.readUtf8()
        }
    }
}
