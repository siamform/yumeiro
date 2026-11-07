/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.online

import org.json.JSONArray
import org.json.JSONObject

/**
 * Data model of CloudStream-compatible extensions.
 *
 * CloudStream distributes extensions through *repositories*: a `repo.json` manifest
 * (`name`, `description`, `manifestVersion`, `pluginLists`) whose `pluginLists` point at one or more
 * `plugins.json` files. Every plugin entry describes one `.cs3` file — a zip holding `classes.dex`
 * and a `manifest.json` (`pluginClassName`, `version`, `requiresResources`) — which is loaded at
 * runtime and registers MainAPI providers (sites) and extractors.
 */
data class ExtensionRepo(
    val url: String,
    val name: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val pluginLists: List<String> = emptyList(),
)

/** One plugin entry of a `plugins.json` file (CloudStream's `SitePlugin`). */
data class RemoteExtension(
    val internalName: String,
    val name: String,
    val url: String,
    val version: Int,
    val apiVersion: Int = 1,
    /** 0 = down, 1 = ok, 2 = slow, 3 = beta (CloudStream convention). */
    val status: Int = 1,
    val description: String? = null,
    val authors: List<String> = emptyList(),
    val tvTypes: List<String> = emptyList(),
    val language: String? = null,
    val iconUrl: String? = null,
    val fileSize: Long? = null,
    /** sha256 of the .cs3 (hex), when known. */
    val sha256: String? = null,
    /** Original download URL when [url] is a mirror. */
    val sourceUrl: String? = null,
    val repoUrl: String,
    val repoName: String,
    /** True when this entry came from the curated Extension Market (validated by the workflow). */
    val verified: Boolean = false,
    val stars: Int? = null,
    val updated: Long = 0,
    /** Where the market found it: "curated" (checked + mirrored), "community" (CloudStream's community list), "discovered" (GitHub search), or "user" (a repository the user added). */
    val origin: String = "user",
    /** Market ranking: popularity, freshness and health. Higher is better. */
    val score: Double = 0.0,
    /** 1-based market rank among unique extensions (0 = unranked). */
    val rank: Int = 0,
    /** Best copy when several repositories publish the same extension. */
    val primary: Boolean = true,
    /** How many repositories publish this extension. */
    val copies: Int = 1,
    /** Broad categories from [tvTypes]: Movies, TV, Anime, Asian drama, Live, Documentary, Cartoon, Other. */
    val categories: List<String> = emptyList(),
    /** Set by the market when this file is known not to load (in this app or anywhere): why. */
    val compatNote: String? = null,
) {
    /** Stable identity of an extension across repositories and app restarts. */
    val key: String get() = keyOf(repoUrl, internalName)
    val isNsfw: Boolean get() = tvTypes.any { it.equals("NSFW", ignoreCase = true) }
    val isDown: Boolean get() = status == 0
    val statusLabel: String get() = when (status) { 0 -> "Down"; 2 -> "Slow"; 3 -> "Beta"; else -> "Working" }

    companion object {
        fun keyOf(repoUrl: String, internalName: String): String =
            normalizeRepoUrl(repoUrl).lowercase() + "#" + internalName
    }
}

/** An extension installed on this device. */
data class InstalledExtension(
    val internalName: String,
    val name: String,
    val version: Int,
    val repoUrl: String,
    val repoName: String,
    val fileName: String,
    val sourceUrl: String,
    val sha256: String,
    val description: String? = null,
    val authors: List<String> = emptyList(),
    val tvTypes: List<String> = emptyList(),
    val language: String? = null,
    val iconUrl: String? = null,
    val enabled: Boolean = true,
    val autoUpdate: Boolean = true,
    val installedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Added from a local file or direct link (no repository to update from). */
    val manual: Boolean = false,
) {
    val key: String get() = RemoteExtension.keyOf(repoUrl, internalName)

    fun toJson(): JSONObject = JSONObject()
        .put("internalName", internalName).put("name", name).put("version", version)
        .put("repoUrl", repoUrl).put("repoName", repoName).put("fileName", fileName)
        .put("sourceUrl", sourceUrl).put("sha256", sha256).put("description", description)
        .put("authors", JSONArray(authors)).put("tvTypes", JSONArray(tvTypes))
        .put("language", language).put("iconUrl", iconUrl).put("enabled", enabled)
        .put("autoUpdate", autoUpdate).put("installedAt", installedAt).put("updatedAt", updatedAt)
        .put("manual", manual)

    companion object {
        fun fromJson(o: JSONObject): InstalledExtension? {
            val internal = o.optString("internalName").trim().ifBlank { return null }
            val file = o.optString("fileName").trim().ifBlank { return null }
            return InstalledExtension(
                internalName = internal,
                name = o.optString("name").ifBlank { internal },
                version = o.optInt("version", 0),
                repoUrl = o.optString("repoUrl"),
                repoName = o.optString("repoName").ifBlank { "Manual" },
                fileName = file,
                sourceUrl = o.optString("sourceUrl"),
                sha256 = o.optString("sha256"),
                description = o.optStringOrNull("description"),
                authors = o.optJSONArray("authors").strings(),
                tvTypes = o.optJSONArray("tvTypes").strings(),
                language = o.optStringOrNull("language"),
                iconUrl = o.optStringOrNull("iconUrl"),
                enabled = o.optBoolean("enabled", true),
                autoUpdate = o.optBoolean("autoUpdate", true),
                installedAt = o.optLong("installedAt"),
                updatedAt = o.optLong("updatedAt"),
                manual = o.optBoolean("manual", false),
            )
        }
    }
}

/** Parsing of CloudStream repository files. Lenient: bad entries are skipped, never thrown. */
object ExtensionFormats {
    private val NAME = Regex("[A-Za-z0-9_.\\-]{1,120}")

    private val CATEGORY = mapOf(
        "anime" to "Anime", "animemovie" to "Anime", "ova" to "Anime",
        "movie" to "Movies", "tvseries" to "TV", "asiandrama" to "Asian drama", "cartoon" to "Cartoon",
        "documentary" to "Documentary", "live" to "Live", "torrent" to "Torrent", "music" to "Music",
        "audiobook" to "Audio", "audio" to "Audio", "podcast" to "Audio", "others" to "Other", "nsfw" to "18+",
    )

    /** Broad market categories for CloudStream tvTypes (same mapping as the market workflow). */
    fun categoriesOf(tvTypes: List<String>): List<String> =
        tvTypes.map { CATEGORY[it.lowercase().replace("_", "")] ?: "Other" }.distinct().ifEmpty { listOf("Other") }

    /** Parses `repo.json`. Throws when the file isn't a repository manifest. */
    fun parseRepo(url: String, text: String): ExtensionRepo {
        val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        require(trimmed.startsWith("{")) { "Not a CloudStream repository" }
        val o = JSONObject(trimmed)
        val lists = o.optJSONArray("pluginLists").strings()
            .map { resolve(url, it) }
            .filter { it.startsWith("https://") || it.startsWith("http://") }
            .distinct()
        require(lists.isNotEmpty()) { "This repository has no plugin lists" }
        return ExtensionRepo(
            url = url,
            name = o.optString("name").trim().ifBlank { repoNameFromUrl(url) },
            description = o.optStringOrNull("description"),
            iconUrl = o.optStringOrNull("iconUrl"),
            pluginLists = lists,
        )
    }

    /** Parses a `plugins.json` list. */
    fun parsePlugins(listUrl: String, text: String, repo: ExtensionRepo): List<RemoteExtension> {
        val arr = JSONArray(text.trimStart('\uFEFF', ' ', '\n', '\r', '\t'))
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { plugin(it, listUrl, repo) } }
            .distinctBy { it.internalName }
    }

    private fun plugin(o: JSONObject, listUrl: String, repo: ExtensionRepo): RemoteExtension? {
        val internal = o.optString("internalName").trim().takeIf { NAME.matches(it) } ?: return null
        val url = o.optString("url").trim().ifBlank { return null }.let { resolve(listUrl, it) }
        if (!url.startsWith("https://") && !url.startsWith("http://")) return null
        return RemoteExtension(
            internalName = internal,
            name = o.optString("name").trim().ifBlank { internal },
            url = url,
            version = o.optInt("version", 0),
            apiVersion = o.optInt("apiVersion", 1),
            status = o.optInt("status", 1),
            description = o.optStringOrNull("description"),
            authors = o.optJSONArray("authors").strings(),
            tvTypes = o.optJSONArray("tvTypes").strings(),
            categories = categoriesOf(o.optJSONArray("tvTypes").strings()),
            language = o.optStringOrNull("language"),
            iconUrl = o.optStringOrNull("iconUrl")?.let { resolve(listUrl, it) },
            fileSize = if (o.has("fileSize") && !o.isNull("fileSize")) o.optLong("fileSize") else null,
            sha256 = o.optStringOrNull("fileHash")?.removePrefix("sha256-")?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) },
            repoUrl = repo.url,
            repoName = repo.name,
        )
    }

    /** Relative links in repo/plugin lists are resolved against the file they appear in. */
    fun resolve(base: String, link: String): String = runCatching {
        if (link.startsWith("http://") || link.startsWith("https://")) link else java.net.URI(base).resolve(link).toString()
    }.getOrDefault(link)

    fun repoNameFromUrl(url: String): String = runCatching {
        val u = java.net.URI(url)
        val parts = u.path.trim('/').split('/')
        if (u.host.contains("githubusercontent") && parts.size >= 2) "${parts[0]}/${parts[1]}" else u.host
    }.getOrDefault(url)
}

/**
 * Turns what users paste into a repository URL: full repo.json links stay as they are, GitHub repo
 * pages map to their raw `repo.json`, and CloudStream short codes are not supported (no lookup
 * service is used).
 */
fun normalizeRepoUrl(input: String): String {
    val s = input.trim().removeSuffix("/")
    val gh = Regex("^https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?(?:/(?:tree|blob)/([^/]+)(/.*)?)?$").find(s)
    if (gh != null) {
        val (owner, repo, branch, path) = gh.destructured
        val p = path.trim('/')
        return when {
            p.endsWith(".json") -> "https://raw.githubusercontent.com/$owner/$repo/${branch.ifBlank { "HEAD" }}/$p"
            else -> "https://raw.githubusercontent.com/$owner/$repo/${branch.ifBlank { "HEAD" }}/${if (p.isBlank()) "" else "$p/"}repo.json"
        }
    }
    return s
}

internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).trim().ifBlank { null } }

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).trim().ifBlank { null }
