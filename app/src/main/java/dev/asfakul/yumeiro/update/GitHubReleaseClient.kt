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
    /** Round 12: which app's files to pick (TV or phone); asked each check. */
    private val wantTv: () -> Boolean = { dev.asfakul.yumeiro.Edition.isTv },
) {

    class UpdateCheckException(message: String, cause: Throwable? = null) : IOException(message, cause)

    suspend fun fetchLatestRelease(): ReleaseInfo = withContext(Dispatchers.IO) {
        var authError: Throwable? = null
        if (token.isNotBlank()) {
            try {
                return@withContext withTrustedChecksum(parseRelease(get(endpoint, token), UpdateSource.AUTHENTICATED, tv = wantTv()))
            } catch (t: Throwable) {
                authError = t
            }
        }
        try {
            withTrustedChecksum(parseRelease(get(endpoint, null), UpdateSource.PUBLIC, tv = wantTv()))
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
            ?.let { url ->
                runCatching { getSmall(url) }.getOrNull()
                    ?: browserSumsUrl(url, a.browserUrl)?.let { alt -> runCatching { getSmall(alt) }.getOrNull() }
            }
        val sha = reconcileChecksum(a.sha256, sums, a.name)
            ?: throw UpdateCheckException("Update rejected: no SHA-256 checksum was published for this release.")
        return release.copy(asset = a.copy(sha256 = sha))
    }

    /** `.../releases/download/<tag>/SHA256SUMS.txt` next to the APK, used when the API link fails. */
    private fun browserSumsUrl(current: String, apkBrowserUrl: String): String? =
        if (!current.contains("api.github.com") || !apkBrowserUrl.contains("/releases/download/")) null
        else apkBrowserUrl.substringBeforeLast('/') + "/SHA256SUMS.txt"

    /** Small public file (SHA256SUMS.txt); redirects must stay on GitHub hosts. */
    private fun getSmall(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            // Needed for API asset URLs (redirects to the file); harmless for browser URLs.
            setRequestProperty("Accept", "application/octet-stream")
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
     *
     * The API asset URL (`api.github.com/.../releases/assets/{id}` with `Accept: application/octet-stream`)
     * answers with a short-lived signed link on `release-assets.githubusercontent.com`. That path is used
     * first for every repository: the `github.com/.../releases/download/...` link can fail with
     * "504 Gateway Time-out" on GitHub's edge while the API path keeps working. For private repositories
     * the token is sent here only, so it never leaves the app process.
     */
    suspend fun resolveDownload(release: ReleaseInfo): ResolvedDownload = withContext(Dispatchers.IO) {
        val useToken = release.source != UpdateSource.PUBLIC && token.isNotBlank()
        if (release.asset.apiUrl.isNotBlank()) {
            val viaApi = runCatching { signedAssetUrl(release.asset.apiUrl, useToken) }
            viaApi.getOrNull()?.let { return@withContext it }
            val err = viaApi.exceptionOrNull()
            if (err is UpdateCheckException && err.message?.startsWith("Update rejected") == true) throw err
        }
        ResolvedDownload(release.asset.browserUrl, needsAuthHeader = false)
    }

    /** Asks the API for the signed download link; null when GitHub doesn't hand one out. */
    private fun signedAssetUrl(apiUrl: String, useToken: Boolean): ResolvedDownload? {
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/octet-stream")
            if (useToken) setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", userAgent())
        }
        try {
            return when (conn.responseCode) {
                in 300..399 -> {
                    val location = conn.getHeaderField("Location") ?: return null
                    if (!TrustedSources.isGitHubDownloadHost(location)) {
                        throw UpdateCheckException("Update rejected: download redirected off GitHub.")
                    }
                    ResolvedDownload(location, needsAuthHeader = false)
                }
                200 -> if (useToken) ResolvedDownload(apiUrl, needsAuthHeader = true) else null
                else -> null
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

        private val ABI_NAMES = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

        /**
         * Round 12: Yumeiro TV files end in ".tv.apk" ("Yumeiro-TV-v1.7.0-arm64-v8a.tv.apk"). The
         * suffix is chosen so 1.6 and older updaters (which only know "-<abi>.apk" and
         * "release-…apk") never mistake a TV file for a phone one.
         */
        fun isTvAsset(name: String): Boolean = name.lowercase().endsWith(".tv.apk")

        /** The processor a per-ABI APK name is for, or null for the universal APK. */
        fun abiOf(name: String): String? {
            val base = name.lowercase().removeSuffix(".apk").removeSuffix(".tv")
            return ABI_NAMES.firstOrNull { base.endsWith("-$it") }
        }

        fun deviceAbis(): List<String> = runCatching { android.os.Build.SUPPORTED_ABIS.toList() }.getOrDefault(emptyList())

        /** Parses a GitHub `release` JSON object. Exposed for unit tests. */
        fun parseRelease(json: String, source: UpdateSource, abis: List<String> = deviceAbis(), tv: Boolean = false): ReleaseInfo {
            val obj = JSONObject(json)
            val assets = obj.optJSONArray("assets")
                ?: throw UpdateCheckException("The latest release has no files attached.")
            var best: JSONObject? = null
            val byAbi = HashMap<String, JSONObject>()
            var sumsUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                // API URL first (see resolveDownload); the browser URL stays as a fallback in getSums.
                if (name == "SHA256SUMS.txt") { sumsUrl = (a.optString("url").ifBlank { null } ?: a.optString("browser_download_url")).ifBlank { null }; continue }
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                // The phone app takes phone files only, the TV app TV files only.
                if (isTvAsset(name) != tv) continue
                // Per-processor builds: "Yumeiro-v1.2.0-arm64-v8a.apk" (smaller download).
                abiOf(name)?.let { byAbi.putIfAbsent(it, a); continue }
                if (best == null || name.startsWith("release-", ignoreCase = true)) best = a
            }
            // The device's preferred processor first; the universal APK otherwise.
            val apk = abis.firstNotNullOfOrNull { byAbi[it] } ?: best ?: byAbi.values.firstOrNull() ?: throw UpdateCheckException(if (tv) "The latest release has no Yumeiro TV file yet." else "The latest release doesn't contain an APK.")
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
