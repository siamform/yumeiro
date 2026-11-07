/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update

import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.security.TrustedSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the GitHub Releases REST API.
 *
 * Strategy (works before AND after the repository is made private):
 *  1. If a token is bundled, call the API authenticated (required for private repositories).
 *  2. If there is no token, or the authenticated call fails (revoked/expired token, rate limit,
 *     network hiccup), fall back to the unauthenticated public endpoint.
 */
internal class GitHubReleaseClient(
    private val token: String = UpdateConfig.token,
    private val endpoint: String = UpdateConfig.latestReleaseApi,
) {

    class UpdateCheckException(message: String, cause: Throwable? = null) : IOException(message, cause)

    suspend fun fetchLatestRelease(): ReleaseInfo = withContext(Dispatchers.IO) {
        var authError: Throwable? = null
        if (token.isNotBlank()) {
            try {
                return@withContext withTrustedChecksum(parseRelease(get(endpoint, token), UpdateSource.AUTHENTICATED))
            } catch (t: Throwable) {
                authError = t
            }
        }
        try {
            withTrustedChecksum(parseRelease(get(endpoint, null), UpdateSource.PUBLIC))
        } catch (t: Throwable) {
            val reason = when {
                t is HttpStatusException && t.code == 404 ->
                    "No published release was found (the repository may be private)."
                t is HttpStatusException && (t.code == 403 || t.code == 429) ->
                    "GitHub rate limit reached. Please try again later."
                t is UpdateCheckException -> t.message ?: "Update check failed."
                else -> "Couldn't reach GitHub. Check your internet connection."
            }
            throw UpdateCheckException(reason, authError ?: t)
        }
    }

    /**
     * Trust gate for a release: the APK must live in the official repository and its SHA-256 must
     * be known before anything is downloaded. The hash comes from GitHub's `digest` field and/or the
     * CI-generated `SHA256SUMS.txt`; when both exist they must agree.
     */
    private fun withTrustedChecksum(release: ReleaseInfo): ReleaseInfo {
        val a = release.asset
        val owner = UpdateConfig.owner
        val repo = UpdateConfig.repo
        if (!TrustedSources.isOfficialReleaseAssetUrl(a.browserUrl, owner, repo) ||
            (a.apiUrl.isNotBlank() && !TrustedSources.isOfficialReleaseAssetUrl(a.apiUrl, owner, repo))
        ) {
            throw UpdateCheckException("Update rejected: the file is not hosted on the official repository.")
        }
        val sums = a.checksumsUrl
            ?.takeIf { TrustedSources.isOfficialReleaseAssetUrl(it, owner, repo) }
            ?.let { url -> runCatching { getSmall(url) }.getOrNull() }
        val sha = reconcileChecksum(a.sha256, sums, a.name)
            ?: throw UpdateCheckException("Update rejected: no SHA-256 checksum was published for this release.")
        return release.copy(asset = a.copy(sha256 = sha))
    }

    /** Small public file (SHA256SUMS.txt); redirects must stay on GitHub hosts. */
    private fun getSmall(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", userAgent())
        }
        try {
            val code = conn.responseCode
            if (!TrustedSources.isGitHubDownloadHost(conn.url.toString())) throw UpdateCheckException("Checksum file redirected off GitHub.")
            if (code != 200) throw HttpStatusException(code)
            val bytes = conn.inputStream.use { readBounded(it, MAX_SUMS_BYTES + 1) }
            if (bytes.size > MAX_SUMS_BYTES) throw UpdateCheckException("Checksum file too large.")
            return bytes.decodeToString()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Returns a URL the system DownloadManager can fetch WITHOUT extra headers.
     * For private repositories the API asset URL answers with a short-lived signed redirect,
     * which is resolved here so the token never leaves the app process.
     */
    suspend fun resolveDownload(release: ReleaseInfo): ResolvedDownload = withContext(Dispatchers.IO) {
        if (release.source == UpdateSource.PUBLIC || token.isBlank()) {
            return@withContext ResolvedDownload(release.asset.browserUrl, needsAuthHeader = false)
        }
        val conn = (URL(release.asset.apiUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", userAgent())
        }
        try {
            when (val code = conn.responseCode) {
                in 300..399 -> {
                    val location = conn.getHeaderField("Location")
                        ?: throw UpdateCheckException("Download link missing from GitHub response.")
                    if (!TrustedSources.isGitHubDownloadHost(location)) {
                        throw UpdateCheckException("Update rejected: download redirected off GitHub.")
                    }
                    ResolvedDownload(location, needsAuthHeader = false)
                }
                200 -> ResolvedDownload(release.asset.apiUrl, needsAuthHeader = true)
                else -> {
                    // Token problem: try the public URL as a last resort.
                    if (code == 401 || code == 403 || code == 404) {
                        ResolvedDownload(release.asset.browserUrl, needsAuthHeader = false)
                    } else {
                        throw UpdateCheckException("GitHub returned HTTP $code for the download.")
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Reads at most [limit] bytes (API 28-safe replacement for InputStream.readNBytes). */
    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < limit) {
            val n = input.read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    data class ResolvedDownload(val url: String, val needsAuthHeader: Boolean)

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    private fun get(url: String, token: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", userAgent())
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw HttpStatusException(code)
            val bytes = conn.inputStream.use { readBounded(it, MAX_API_BYTES + 1) }
            if (bytes.size > MAX_API_BYTES) throw UpdateCheckException("GitHub response too large.")
            return bytes.decodeToString()
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 15_000
        private const val MAX_API_BYTES = 2 * 1024 * 1024
        private const val MAX_SUMS_BYTES = 64 * 1024
        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

        /**
         * Picks the SHA-256 for [assetName] from GitHub's [digest] and the `SHA256SUMS.txt` text
         * ([sums], `sha256sum` format). Returns null when neither has a valid hash; throws when both
         * exist and disagree. Exposed for unit tests.
         */
        fun reconcileChecksum(digest: String?, sums: String?, assetName: String): String? {
            val fromDigest = digest?.trim()?.lowercase()?.takeIf { SHA256_HEX.matches(it) }
            val fromSums = sums?.lineSequence()
                ?.map { it.trim().split(Regex("\\s+"), limit = 2) }
                ?.firstOrNull { it.size == 2 && it[1].removePrefix("*") == assetName }
                ?.get(0)?.lowercase()?.takeIf { SHA256_HEX.matches(it) }
            if (fromDigest != null && fromSums != null && fromDigest != fromSums) {
                throw UpdateCheckException("Update rejected: published checksums don't match.")
            }
            return fromDigest ?: fromSums
        }
        private const val API_VERSION = "2022-11-28"
        private val VERSION_CODE_MARKER = Regex("""<!--\s*versionCode:\s*(\d+)\s*-->""")

        private fun userAgent() = "Yumeiro/${BuildConfig.VERSION_NAME} (Android)"

        /** Parses a GitHub `release` JSON object. Exposed for unit tests. */
        fun parseRelease(json: String, source: UpdateSource): ReleaseInfo {
            val obj = JSONObject(json)
            val assets = obj.optJSONArray("assets")
                ?: throw UpdateCheckException("The latest release has no files attached.")
            var best: JSONObject? = null
            var sumsUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (name == "SHA256SUMS.txt") { sumsUrl = a.optString("browser_download_url").ifBlank { null }; continue }
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                if (best == null || name.startsWith("release-", ignoreCase = true)) best = a
            }
            val apk = best ?: throw UpdateCheckException("The latest release doesn't contain an APK.")
            val tag = obj.optString("tag_name")
            val body = obj.optString("body")
            val digest = apk.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
            return ReleaseInfo(
                tagName = tag,
                versionName = tag.removePrefix("v").removePrefix("V"),
                versionCode = VERSION_CODE_MARKER.find(body)?.groupValues?.get(1)?.toLongOrNull(),
                title = obj.optString("name").ifBlank { tag },
                notes = body.replace(VERSION_CODE_MARKER, "").trim(),
                publishedAt = obj.optString("published_at"),
                htmlUrl = obj.optString("html_url"),
                asset = ApkAsset(
                    id = apk.optLong("id"),
                    name = apk.optString("name"),
                    sizeBytes = apk.optLong("size"),
                    apiUrl = apk.optString("url"),
                    browserUrl = apk.optString("browser_download_url"),
                    sha256 = digest?.lowercase(),
                    checksumsUrl = sumsUrl,
                ),
                source = source,
            )
        }
    }
}
