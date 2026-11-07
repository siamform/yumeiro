/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update

/** A release published on GitHub that contains an installable APK asset. */
data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    /** Optional versionCode embedded by the release workflow as `<!-- versionCode: N -->`. */
    val versionCode: Long?,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val htmlUrl: String,
    val asset: ApkAsset,
    val source: UpdateSource,
)

data class ApkAsset(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    /** REST API URL (works for private repositories with a token). */
    val apiUrl: String,
    /** Public browser URL (works for public repositories without a token). */
    val browserUrl: String,
    /** Lower-case hex SHA-256 published by GitHub (`digest` field), if available. */
    val sha256: String?,
    /** Browser URL of the CI-generated `SHA256SUMS.txt` attached to the same release, if any. */
    val checksumsUrl: String? = null,
)

/** Which channel answered the update check. */
enum class UpdateSource { AUTHENTICATED, PUBLIC }

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(
        val release: ReleaseInfo,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
        val queued: Boolean = false,
    ) : UpdateState {
        val progress: Float
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }
    data class ReadyToInstall(val release: ReleaseInfo, val apkPath: String) : UpdateState
    data class Installing(val release: ReleaseInfo) : UpdateState
    data class Failed(val message: String, val release: ReleaseInfo? = null) : UpdateState
}

/** Minimal semantic-version comparison (MAJOR.MINOR.PATCH, ignores pre-release / build suffixes). */
object SemVer {
    fun parse(raw: String): List<Int> =
        raw.trim()
            .removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
            .let { parts -> (parts + listOf(0, 0, 0)).take(3) }

    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        for (i in 0 until 3) {
            val c = pa[i].compareTo(pb[i])
            if (c != 0) return c
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0
}

/** Persists a [ReleaseInfo] so a background download can be restored after process death. */
internal object ReleaseCodec {
    fun encode(r: ReleaseInfo): String = org.json.JSONObject().apply {
        put("tag", r.tagName); put("name", r.versionName); put("code", r.versionCode ?: -1L)
        put("title", r.title); put("notes", r.notes); put("published", r.publishedAt)
        put("html", r.htmlUrl); put("source", r.source.name)
        put("assetId", r.asset.id); put("assetName", r.asset.name); put("assetSize", r.asset.sizeBytes)
        put("assetApi", r.asset.apiUrl); put("assetUrl", r.asset.browserUrl); put("sha256", r.asset.sha256 ?: "")
        put("sums", r.asset.checksumsUrl ?: "")
    }.toString()

    fun decode(json: String?): ReleaseInfo? = runCatching {
        val o = org.json.JSONObject(json ?: return null)
        ReleaseInfo(
            tagName = o.getString("tag"),
            versionName = o.getString("name"),
            versionCode = o.optLong("code", -1L).takeIf { it > 0 },
            title = o.optString("title"),
            notes = o.optString("notes"),
            publishedAt = o.optString("published"),
            htmlUrl = o.optString("html"),
            asset = ApkAsset(
                id = o.optLong("assetId"),
                name = o.getString("assetName"),
                sizeBytes = o.optLong("assetSize"),
                apiUrl = o.optString("assetApi"),
                browserUrl = o.optString("assetUrl"),
                sha256 = o.optString("sha256").ifBlank { null },
                checksumsUrl = o.optString("sums").ifBlank { null },
            ),
            source = UpdateSource.valueOf(o.optString("source", UpdateSource.PUBLIC.name)),
        )
    }.getOrNull()
}
