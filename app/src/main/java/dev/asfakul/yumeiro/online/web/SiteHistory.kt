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

/**
 * WEB-RECENT-1 (owner list 5): websites the user opened in Yumeiro's browser are added to "Recently visited"
 * on the Websites page by themselves, one entry per site, newest first, with a switch to turn it off and a
 * Clear. Saved sites are never touched. On this device only.
 */
object SiteHistory {
    @Serializable
    data class Visit(val url: String, val name: String, val at: Long, val iconUrl: String? = null)

    const val MAX = 30
    private const val PREFS = "yu_sites_recent"
    private const val KEY = "visits"
    private const val KEY_ON = "on"
    private val json = Json { ignoreUnknownKeys = true }
    private val _visits = MutableStateFlow<List<Visit>>(emptyList())
    val visits: StateFlow<List<Visit>> = _visits.asStateFlow()
    private val _on = MutableStateFlow(true)
    val on: StateFlow<Boolean> = _on.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context, PREFS), Context.MODE_PRIVATE)
        _on.value = p.getBoolean(KEY_ON, true)
        _visits.value = p.getString(KEY, null)?.let { runCatching { json.decodeFromString<List<Visit>>(it) }.getOrNull() }.orEmpty()
    }

    /** One entry per site (www. and sub-pages count once); the newest visit moves it to the top. Pure. */
    fun record(list: List<Visit>, url: String, title: String?, now: Long): List<Visit> {
        val norm = SavedSites.normalize(url)?.takeIf { it.startsWith("http") } ?: return list
        val host = SavedSites.hostOf(norm) ?: return list
        // A site's address is its home page, not the video page the user happened to be on.
        val home = "https://" + (runCatching { java.net.URI(norm).host }.getOrNull() ?: return list)
        val old = list.firstOrNull { SavedSites.hostOf(it.url) == host }
        val name = title?.trim()?.takeIf { it.isNotEmpty() }?.let { SiteMeta.siteNameFromTitle(it, home) }?.take(40) ?: old?.name ?: SavedSites.defaultName(home)
        return (listOf(Visit(home, name, now, old?.iconUrl)) + list.filterNot { SavedSites.hostOf(it.url) == host }).take(MAX)
    }

    /** Sites in [visits] that are not saved yet (the saved list already shows those). */
    fun unsaved(visits: List<Visit>, saved: List<SavedSites.Site>): List<Visit> {
        val have = saved.mapNotNull { SavedSites.hostOf(it.url) }.toHashSet()
        return visits.filter { SavedSites.hostOf(it.url) !in have }
    }

    fun visit(context: Context, url: String, title: String?) {
        init(context)
        if (!_on.value) return
        // Telegram channels and Yumeiro's own pages are not websites the user browses.
        if (SavedSites.kindOf(url) != SavedSites.Kind.WEBSITE) return
        val next = record(_visits.value, url, title, System.currentTimeMillis())
        if (next == _visits.value) return
        _visits.value = next
        persist(context)
    }

    fun setOn(context: Context, on: Boolean) {
        init(context)
        _on.value = on
        context.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context, PREFS), Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
        if (!on) clear(context)
    }

    fun forget(context: Context, url: String) {
        init(context)
        val host = SavedSites.hostOf(url)
        _visits.value = _visits.value.filterNot { SavedSites.hostOf(it.url) == host }
        persist(context)
    }

    fun clear(context: Context) {
        init(context)
        _visits.value = emptyList()
        persist(context)
    }

    private fun persist(context: Context) {
        context.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context, PREFS), Context.MODE_PRIVATE).edit().putString(KEY, json.encodeToString(_visits.value)).apply()
    }

    internal fun resetForTest() { loaded = false; _visits.value = emptyList(); _on.value = true }
}
