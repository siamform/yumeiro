/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.MainAPI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * User overrides per source, kept across extension updates: a different website address
 * (sites move domains often; no need to wait for an extension update).
 */
object ProviderOverrides {
    private var prefs: SharedPreferences? = null
    private val defaults = HashMap<String, String>()

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("provider_overrides", Context.MODE_PRIVATE)
    }

    fun mainUrl(api: String): String? = prefs?.getString("url:$api", null)

    /** Original address the extension shipped with. */
    fun defaultUrl(api: MainAPI): String = defaults[api.name] ?: api.mainUrl

    /** Sets (or clears with null/blank) the address of [api] and applies it now. */
    fun setMainUrl(api: MainAPI, url: String?) {
        defaults.putIfAbsent(api.name, api.mainUrl)
        val clean = normalize(url)
        prefs?.edit()?.apply { if (clean == null) remove("url:${api.name}") else putString("url:${api.name}", clean) }?.apply()
        api.mainUrl = clean ?: defaults[api.name] ?: api.mainUrl
        OnlineRepository.clearCaches()
    }

    /** Applies saved overrides to freshly loaded providers. */
    fun apply(apis: List<MainAPI>) {
        val p = prefs ?: return
        apis.forEach { api ->
            val o = p.getString("url:${api.name}", null) ?: return@forEach
            if (api.mainUrl != o) { defaults.putIfAbsent(api.name, api.mainUrl); runCatching { api.mainUrl = o } }
        }
    }

    internal fun normalize(url: String?): String? {
        val t = url?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return null
        val withScheme = if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
        return withScheme.takeIf { it.toHttpUrlOrNull() != null }
    }
}
