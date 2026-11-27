/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.MainAPI
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * F7 (Group M, idea studied in Hikari's "Clone site"; no code taken): many streaming sites run on the same
 * software under other names and addresses ("clone sites"). A clone is a second copy of an installed
 * CloudStream source that reads another address, listed as its own source ("Name · host") next to the
 * original, so both can be searched, pinned and picked. "Website address" still moves one source;
 * a clone keeps the original as it is.
 *
 * Kept in prefs `site_clones` (backed up with Sources). A clone is made from the original's class
 * (a fresh instance), so it follows extension updates; an extension whose sources can't be made again
 * (no plain constructor) can't be cloned, and the user is told so.
 */
object SiteClones {
    private const val TAG = "SiteClones"
    const val PREFS = "site_clones"
    private const val KEY = "list"
    const val MAX = 40

    data class Clone(val name: String, val of: String, val url: String, val at: Long)

    private var prefs: SharedPreferences? = null
    @Volatile private var list: List<Clone> = emptyList()
    /** Changes with every add or remove (part of the provider-list stamp). */
    private val _version = kotlinx.coroutines.flow.MutableStateFlow(0)
    val versionFlow: kotlinx.coroutines.flow.StateFlow<Int> = _version
    val version: Int get() = _version.value
    private val made = ConcurrentHashMap<String, MainAPI>()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        list = decode(p.getString(KEY, null))
        prefs = p
        _version.value++
    }

    fun all(): List<Clone> = list

    /** Test hook: forget what is in memory, so the next [init] reads the saved list again (as after a restart). */
    @androidx.annotation.VisibleForTesting
    internal fun resetForTests(l: List<Clone>? = null) { prefs = null; made.clear(); list = l ?: emptyList(); _version.value++ }

    /** The source [name] was cloned from, or null when [name] isn't a clone. */
    fun originOf(name: String): String? = list.firstOrNull { it.name == name }?.of

    fun isClone(name: String): Boolean = list.any { it.name == name }

    // region pure (unit-tested)

    fun hostOf(url: String?): String? = ProviderOverrides.normalize(url)?.let { runCatching { java.net.URI(it).host }.getOrNull() }
        ?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotBlank() }

    /** "Name · host", made unique against [taken]. */
    fun nameFor(of: String, url: String, taken: Collection<String>): String {
        val base = "$of · ${hostOf(url) ?: "clone"}"
        if (base !in taken) return base
        var n = 2
        while ("$base $n" in taken) n++
        return "$base $n"
    }

    /** Why [url] can't be a clone of [api] (null when it can). */
    fun problem(originUrl: String, url: String, clones: List<Clone>, of: String): String? {
        val clean = ProviderOverrides.normalize(url) ?: return tr(R.string.that_isnt_a_web_address, "That isn't a web address")
        val host = hostOf(clean) ?: return tr(R.string.that_isnt_a_web_address, "That isn't a web address")
        if (host == hostOf(originUrl)) return tr(R.string.that_is_the_address_already_uses, "That is the address %1\$s already uses", of)
        if (clones.any { it.of == of && hostOf(it.url) == host }) return tr(R.string.already_has_a_clone_on, "%1\$s already has a clone on %2\$s", of, host)
        if (clones.size >= MAX) return tr(R.string.you_have_clones_already_remove_one, "You have %1\$s clones already. Remove one first", MAX)
        return null
    }

    fun encode(l: List<Clone>): String = JSONArray().apply {
        l.forEach { c -> put(JSONObject().put("name", c.name).put("of", c.of).put("url", c.url).put("at", c.at)) }
    }.toString()

    fun decode(raw: String?): List<Clone> = runCatching {
        val a = JSONArray(raw ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val of = o.optString("of").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = ProviderOverrides.normalize(o.optString("url")) ?: return@mapNotNull null
            Clone(name, of, url, o.optLong("at"))
        }.distinctBy { it.name }.take(MAX)
    }.getOrDefault(emptyList())

    // endregion

    /** Adds a clone of [api] on [url]. Returns the new source's name, or the reason it can't be made. */
    fun add(context: Context, api: MainAPI, url: String, now: Long = System.currentTimeMillis()): Result<String> = runCatching {
        init(context)
        val of = originOf(api.name) ?: api.name // a clone of a clone is a clone of the original
        val originUrl = ProviderOverrides.defaultUrl(api)
        problem(originUrl, url, list, of)?.let { error(it) }
        require(api.sourcePlugin != null && instance(api) != null) { "$of can't be cloned: its extension doesn't allow a second copy" }
        val taken = list.map { it.name } + runCatching { ExtensionManager.providers().map { it.name } }.getOrDefault(emptyList())
        val c = Clone(nameFor(of, url, taken), of, ProviderOverrides.normalize(url)!!, now)
        save(list + c)
        c.name
    }

    fun remove(context: Context, name: String) {
        init(context)
        made.remove(name)
        save(list.filterNot { it.name == name })
    }

    private fun save(l: List<Clone>) {
        list = l
        prefs?.edit()?.putString(KEY, encode(l))?.apply()
        _version.value++
        runCatching { ExtensionManager.bumpGeneration() }
        runCatching { OnlineRepository.clearCaches() }
    }

    /** A fresh instance of [api]'s class, or null when the class has no plain constructor. */
    private fun instance(api: MainAPI): MainAPI? = runCatching {
        api.javaClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    }.onFailure { Log.w(TAG, "${api.name}: ${it.javaClass.simpleName}") }.getOrNull()

    /** [apis] plus the clones whose original is among them. */
    fun expand(apis: List<MainAPI>): List<MainAPI> {
        val l = list
        if (l.isEmpty()) return apis
        val byName = apis.associateBy { it.name }
        val out = ArrayList<MainAPI>(apis.size + l.size)
        out += apis
        for (c in l) {
            val origin = byName[c.of] ?: continue // its extension is off or removed: the clone waits
            if (c.name in byName) continue
            val have = made[c.name]?.takeIf { it.javaClass == origin.javaClass } // an extension update brings a new class
            val api = have ?: instance(origin)?.also { made[c.name] = it } ?: continue
            runCatching { api.name = c.name; api.mainUrl = c.url; api.sourcePlugin = origin.sourcePlugin }
            out += api
        }
        return out
    }
}
