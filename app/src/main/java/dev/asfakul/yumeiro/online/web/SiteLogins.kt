/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import android.content.Context
import android.webkit.CookieManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Sites the user opened or signed in to inside Yumeiro (from a firewall notice or an extension's
 * "Sign in to site"). Only these hosts get the in-app browser's cookies added to extension
 * requests — other sites never see them. Only host names are stored here; the cookies themselves
 * stay in Android's WebView cookie store (app-private).
 */
object SiteLogins {
    private const val PREFS = "site_logins"
    private const val KEY = "hosts"
    private val cache = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var loaded = false

    private fun load(ctx: Context?) {
        if (loaded || ctx == null) return
        synchronized(this) {
            if (loaded) return
            cache += ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()
            loaded = true
        }
    }

    /** True when [host] (or a parent domain of it) was signed in to. Pure matching → [matches]. */
    fun has(ctx: Context?, host: String): Boolean { load(ctx); return matches(cache, host) }

    fun matches(hosts: Set<String>, host: String): Boolean {
        val h = host.lowercase().removePrefix("www.")
        return hosts.any { s -> h == s || h.endsWith(".$s") }
    }

    fun all(ctx: Context?): Set<String> { load(ctx); return cache.toSortedSet() }

    fun add(ctx: Context?, host: String) {
        load(ctx)
        if (cache.add(host.lowercase().removePrefix("www."))) save(ctx)
    }

    /** Forgets [host] and deletes its browser cookies (signs out). */
    fun remove(ctx: Context?, host: String) {
        load(ctx)
        val h = host.lowercase().removePrefix("www.")
        cache.remove(h); save(ctx)
        runCatching {
            val cm = CookieManager.getInstance()
            for (url in listOf("https://$h/", "https://www.$h/")) {
                cm.getCookie(url).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }
                    .forEach { name -> cm.setCookie(url, "$name=; Max-Age=0; Path=/") }
            }
            cm.flush()
        }
        BrowserCheck.forget(h)
    }

    private fun save(ctx: Context?) {
        ctx ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, cache.toSet()).apply()
    }
}
