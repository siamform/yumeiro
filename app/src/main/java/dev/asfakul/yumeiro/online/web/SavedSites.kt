/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * Websites the user saved to watch through Yumeiro's player: the site opens in the in-app page
 * browser (ad / pop-up shield on), and the video its player starts is played in Yumeiro.
 * Stored on this device only (shared preferences); nothing is pre-installed.
 */
object SavedSites {
    @Serializable
    data class Site(val url: String, val name: String, val addedAt: Long = 0, val pinned: Boolean = false) {
        val host: String get() = hostOf(url) ?: url
    }

    private const val PREFS = "yu_sites"
    private const val KEY = "sites"
    private const val MAX = 200
    private val json = Json { ignoreUnknownKeys = true }
    private val _sites = MutableStateFlow<List<Site>>(emptyList())
    val sites: StateFlow<List<Site>> = _sites.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _sites.value = decode(raw)
    }

    /** Old lists (before F-04) decode unchanged: new fields take their defaults. */
    fun decode(raw: String?): List<Site> = raw?.let { runCatching { json.decodeFromString<List<Site>>(it) }.getOrNull() }.orEmpty()

    private fun keyOf(url: String): String = hostOf(url) + runCatching { URI(url).path }.getOrNull().orEmpty().trimEnd('/')

    // F-04: list helpers (pure; unit-tested).

    /** Pinned sites first; each group keeps its saved order. */
    fun ordered(list: List<Site>): List<Site> = list.filter { it.pinned } + list.filterNot { it.pinned }

    fun filter(list: List<Site>, query: String): List<Site> {
        val q = query.trim()
        if (q.isEmpty()) return list
        return list.filter { it.name.contains(q, ignoreCase = true) || it.host.contains(q, ignoreCase = true) }
    }

    /** Moves [url] one place up or down in [list] (already [ordered]); never across the pinned / unpinned line. */
    fun move(list: List<Site>, url: String, up: Boolean): List<Site> {
        val i = list.indexOfFirst { it.url == url }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices || list[i].pinned != list[j].pinned) return list
        return list.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }

    class Merge(val sites: List<Site>, val added: Int, val duplicates: Int, val bad: Int, val overLimit: Int)

    /** Adds every link in [text] (one per line) after [existing]; never replaces or removes a saved site. */
    fun merge(existing: List<Site>, text: String, now: Long): Merge {
        val out = existing.toMutableList()
        val keys = existing.map { keyOf(it.url) }.toHashSet()
        var added = 0; var dup = 0; var bad = 0; var over = 0
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val url = normalize(line)
            when {
                url == null -> bad++
                !keys.add(keyOf(url)) -> dup++
                out.size >= MAX -> over++
                else -> { out += Site(url, defaultName(url).take(40), now); added++ }
            }
        }
        return Merge(out, added, dup, bad, over)
    }

    fun export(list: List<Site>): String = ordered(list).joinToString("\n") { it.url }

    fun importText(context: Context, text: String): Merge {
        init(context)
        val r = merge(_sites.value, text, System.currentTimeMillis())
        _sites.value = r.sites
        persist(context)
        return r
    }

    fun setPinned(context: Context, url: String, pinned: Boolean) {
        init(context)
        _sites.value = _sites.value.map { if (it.url == url) it.copy(pinned = pinned) else it }
        persist(context)
    }

    fun moveSite(context: Context, url: String, up: Boolean) {
        init(context)
        _sites.value = move(ordered(_sites.value), url, up)
        persist(context)
    }

    private fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json.encodeToString(_sites.value)).apply()
    }

    fun hostOf(url: String): String? = runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Turns what the user typed ("example.com", "https://example.com/watch") into an https URL,
     * or null when it isn't a web address. Pure; unit-tested.
     */
    fun normalize(input: String): String? {
        val t = input.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        val withScheme = when {
            t.startsWith("https://", true) || t.startsWith("http://", true) -> t
            "://" in t -> return null
            else -> "https://$t"
        }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if ('.' !in host && host != "localhost") return null
        return withScheme
    }

    /** A readable default name: "www.my-site.co.uk" → "My Site". */
    fun defaultName(url: String): String {
        val host = hostOf(url) ?: return url
        val parts = host.split('.')
        val core = if (parts.size >= 3 && parts[parts.size - 2].length <= 3) parts[parts.size - 3] else parts.getOrElse(parts.size - 2) { host }
        return core.split('-', '_').filter { it.isNotBlank() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }.ifBlank { host }
    }

    /** Adds (or renames) a site. Returns an error text, or null when saved. */
    fun add(context: Context, input: String, name: String? = null, now: Long = System.currentTimeMillis()): String? {
        init(context)
        val url = normalize(input) ?: return "That doesn't look like a web address"
        val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: defaultName(url)
        val key = keyOf(url)
        val old = _sites.value.firstOrNull { keyOf(it.url) == key }
        val rest = _sites.value.filterNot { keyOf(it.url) == key }
        _sites.value = (listOf(Site(url, n.take(40), now, old?.pinned == true)) + rest).take(MAX)
        persist(context)
        return null
    }

    fun rename(context: Context, url: String, name: String) {
        init(context)
        val n = name.trim().takeIf { it.isNotEmpty() } ?: return
        _sites.value = _sites.value.map { if (it.url == url) it.copy(name = n.take(40)) else it }
        persist(context)
    }

    fun remove(context: Context, url: String) {
        init(context)
        _sites.value = _sites.value.filterNot { it.url == url }
        persist(context)
    }

    /** For tests. */
    internal fun resetForTest() { loaded = false; _sites.value = emptyList() }
}
