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
    data class Site(val url: String, val name: String, val addedAt: Long = 0) {
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
        _sites.value = raw?.let { runCatching { json.decodeFromString<List<Site>>(it) }.getOrNull() }.orEmpty()
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
        val key = hostOf(url) + URI(url).path.orEmpty().trimEnd('/')
        val rest = _sites.value.filterNot { hostOf(it.url) + runCatching { URI(it.url).path }.getOrNull().orEmpty().trimEnd('/') == key }
        _sites.value = (listOf(Site(url, n.take(40), now)) + rest).take(MAX)
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
