/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import android.content.Context
import dev.asfakul.yumeiro.stream.LinkHosts
import dev.asfakul.yumeiro.stream.MediaKind
import dev.asfakul.yumeiro.stream.StreamSniffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ADCHAIN-2 (owner list 23, ADR-062): the two switches of Yumeiro's page browser, kept apart because
 * they do different things:
 *  - [filterAds]: the ad and pop-up filter (ad hosts never load, pop-ups and pop-unders are held back,
 *    click-catcher layers are switched off, generic ad boxes are hidden);
 *  - [resolveLinks]: the link-shortener walker (ADCHAIN-1) that waits out the site's timers, taps its
 *    Continue / Get Link buttons and opens the real link (media goes to Yumeiro's player).
 * Turning either off never clears cookies or the page's sign-in: it only stops Yumeiro acting on pages.
 * The page browser also has a Filter button for the current page only (a session switch, not saved).
 */
object BrowserFilters {
    private const val PREFS = "yu_browser_filters"
    private val _filterAds = MutableStateFlow(true)
    private val _resolveLinks = MutableStateFlow(true)
    val filterAds: StateFlow<Boolean> = _filterAds
    val resolveLinks: StateFlow<Boolean> = _resolveLinks
    @Volatile private var loaded = false

    /** Reads the saved switches. Call off the main thread (Bridges.start); safe to call again. */
    fun start(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _filterAds.value = p.getBoolean("filter_ads", true)
        _resolveLinks.value = p.getBoolean("resolve_links", true)
        loaded = true
    }

    fun setFilterAds(context: Context, on: Boolean) {
        _filterAds.value = on
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("filter_ads", on).apply()
    }

    fun setResolveLinks(context: Context, on: Boolean) {
        _resolveLinks.value = on
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("resolve_links", on).apply()
    }

    val isLoaded: Boolean get() = loaded

    // The page browser's own Filter button: a choice for one page (web view), never saved.
    private val pages = java.util.Collections.synchronizedMap(java.util.WeakHashMap<android.webkit.WebView, Boolean>())
    private val shields = java.util.Collections.synchronizedMap(java.util.WeakHashMap<android.webkit.WebView, androidx.webkit.ScriptHandler>())

    /** Is the filter on for [view]: its own page choice, else the saved switch. Any thread. */
    fun pageOn(view: android.webkit.WebView?): Boolean = view?.let { pages[it] } ?: _filterAds.value

    /** The page's own choice, or null when it follows the saved switch. */
    fun pageChoice(view: android.webkit.WebView?): Boolean? = view?.let { pages[it] }

    /**
     * Turns the filter on or off for [view] only, and adds / removes the pop-up shield script that runs
     * before the page's own scripts. Cookies, storage and sign-ins are left as they are. Main thread.
     */
    fun setPage(view: android.webkit.WebView, on: Boolean) {
        pages[view] = on
        if (on) { if (shields[view] == null) SafeWebView.addAdShield(view)?.let { shields[view] = it } }
        else shields.remove(view)?.let { h -> runCatching { h.remove() } }
    }

    /** Adds the shield to a new web view when the filter is on for it. Main thread. */
    fun shield(view: android.webkit.WebView) {
        if (pageOn(view) && shields[view] == null) SafeWebView.addAdShield(view)?.let { shields[view] = it }
    }
}

/** ADCHAIN-2: what the end of a link chain is, so media opens in Yumeiro and pages stay pages. Pure. */
object ChainDestination {
    sealed interface Kind {
        /** A video or audio file / playlist: Yumeiro's player plays [url]. */
        data class Media(val url: String, val label: String) : Kind
        /** A file host whose share page maps to its documented file address (Pixeldrain, Drive, Dropbox, OneDrive). */
        data class FileHost(val url: String, val label: String) : Kind
        /** A file host that only gives the file to its own web page (Gofile): the page's player is started and its stream caught. */
        data class HostPage(val label: String) : Kind
        /** Any other page: it stays open in the browser. */
        data object Page : Kind
    }

    private val HOST_PAGES = listOf("gofile.io" to "Gofile", "mediafire.com" to "MediaFire", "krakenfiles.com" to "KrakenFiles")

    fun of(url: String?): Kind {
        val u = url?.trim().orEmpty()
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) return Kind.Page
        StreamSniffer.fromUrl(u)?.let { s ->
            if (s.kind == MediaKind.HLS || s.kind == MediaKind.DASH || s.kind == MediaKind.VIDEO || s.kind == MediaKind.AUDIO) {
                return Kind.Media(u, s.kind.label)
            }
        }
        when (val r = runCatching { LinkHosts.classify(u) }.getOrNull()) {
            is LinkHosts.Direct -> return Kind.FileHost(r.url, r.host)
            else -> Unit
        }
        val host = hostOf(u) ?: return Kind.Page
        HOST_PAGES.firstOrNull { (h, _) -> host == h || host.endsWith(".$h") }?.let { return Kind.HostPage(it.second) }
        return Kind.Page
    }

    /** Lower-case host of [url] without "www.", or null. No regex (hot path). */
    fun hostOf(url: String?): String? {
        val rest = url?.substringAfter("://", "")?.takeIf { it.isNotEmpty() } ?: return null
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfter('@').substringBefore(':').lowercase()
        return host.removePrefix("www.").takeIf { it.isNotEmpty() }
    }

    /** Same site: equal hosts, or one is a sub-domain of the other's registrable part (last two labels). */
    fun sameSite(a: String?, b: String?): Boolean {
        val ha = hostOf(a) ?: return false
        val hb = hostOf(b) ?: return false
        if (ha == hb) return true
        fun base(h: String) = h.split('.').takeLast(2).joinToString(".")
        return base(ha) == base(hb)
    }
}

/**
 * ADCHAIN-2: what happens to a new window (target=_blank, window.open) and to a top-level jump the page
 * makes by itself. Pure, unit tested. Ad hosts are recognised by [AdFilter]'s list, never by a guess.
 */
object PopupPolicy {
    /** OPEN_HERE: load the address in this page; CAPTURE: let a hidden helper learn the address first (filter off, script-made window). */
    enum class Decision { OPEN_HERE, CAPTURE, BLOCK }

    /**
     * A page asked for a new window to [target] (null when Android can't tell where it goes).
     * [gesture] = the person tapped; [link] = the tap was on a real link (not a script-made window).
     */
    fun window(current: String?, target: String?, gesture: Boolean, link: Boolean, filter: Boolean, chainActive: Boolean): Decision {
        val t = target?.trim().orEmpty()
        val web = t.startsWith("http://", true) || t.startsWith("https://", true)
        // Filter off: a normal browser opens what the person asked for (no tap, no window).
        if (!filter) return if (!gesture) Decision.BLOCK else if (web) Decision.OPEN_HERE else if (t.isEmpty()) Decision.CAPTURE else Decision.BLOCK
        if (!web || AdFilter.isAd(t)) return Decision.BLOCK
        if (ChainDestination.sameSite(current, t)) return Decision.OPEN_HERE
        // On a shortener page every tap opens an ad window: only the walker's own page changes count.
        if (chainActive) return Decision.BLOCK
        return if (gesture && link) Decision.OPEN_HERE else Decision.BLOCK
    }

    /** The page itself moves to [target] in the main frame: true = stop it (an ad redirect). */
    fun blockJump(current: String?, target: String?, filter: Boolean): Boolean {
        if (!filter || target.isNullOrBlank()) return false
        if (ChainDestination.sameSite(current, target)) return false
        return AdFilter.isAd(target)
    }
}
