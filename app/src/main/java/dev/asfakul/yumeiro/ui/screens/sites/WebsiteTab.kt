/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.sites

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import dev.asfakul.yumeiro.ui.screens.stream.PageKeep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * OL-1 + GROUP E2: the open website tabs. Playing one of a tab's videos, opening Downloads or pressing
 * Minimize / Back hides the page but keeps it alive (same WebView, same place, same sign-in); it comes back
 * when the player closes (if a play hid it) or from the "Open tab" card on the Websites page.
 *
 * Several tabs can be open ([tabs]); [tab] is the one in front. Opening a site that is already open shows
 * that tab. Memory: at most [MAX_TABS] tabs; only [liveLimit] keep their page in memory, the others keep
 * their address and reload when shown (2 on low-end phones). On low memory every hidden page is given back.
 */
object WebsiteTab {
    data class Tab(val name: String, val keep: PageKeep, val visible: Boolean, val resumeAfterPlayer: Boolean = false, val id: Long = 0L)

    const val MAX_TABS = 8

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    private val _tab = MutableStateFlow<Tab?>(null)
    /** Every open tab, oldest first. */
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()
    /** The tab in front (shown when [Tab.visible]), or null when none is open. */
    val tab: StateFlow<Tab?> = _tab.asStateFlow()
    @Volatile private var callbacks: ComponentCallbacks2? = null
    private var nextId = 1L
    /** Most recent first: who keeps a live page when over [liveLimit]. */
    private val recent = ArrayDeque<Long>()

    /** Pages kept alive at once (front tab included). Tests may change it. */
    @Volatile var liveLimit: Int = if (dev.asfakul.yumeiro.DeviceClass.lowEnd) 2 else 4

    private fun publish(list: List<Tab>, front: Tab?) {
        _tabs.value = list
        _tab.value = front
    }

    private fun touch(id: Long) {
        recent.remove(id); recent.addFirst(id)
        // Over the limit: the least recently used hidden pages give their WebView back (address kept).
        val keepIds = recent.take(liveLimit.coerceAtLeast(1)).toSet()
        _tabs.value.filter { it.id !in keepIds && it.keep.alive }.forEach { it.keep.release() }
    }

    /** Opens [url] in a new tab in front (or shows it again when a tab already has it). */
    fun open(context: Context?, url: String, name: String) {
        val list = _tabs.value
        val cur = _tab.value
        list.firstOrNull { it.keep.pageUrl == url || it.keep.address == url }?.let { t ->
            if (cur != null && cur.id != t.id) cur.keep.pause()
            val shown = t.copy(visible = true, resumeAfterPlayer = false)
            publish(list.map { if (it.id == t.id) shown else it.copy(visible = false) }, shown)
            touch(t.id)
            return
        }
        cur?.keep?.pause()
        var rest = list.map { it.copy(visible = false, resumeAfterPlayer = false) }
        // Too many tabs: the oldest one that is not in front closes.
        while (rest.size >= MAX_TABS) { val old = rest.first(); old.keep.release(); recent.remove(old.id); rest = rest.drop(1) }
        val t = Tab(name, PageKeep(url), visible = true, id = nextId++)
        publish(rest + t, t)
        touch(t.id)
        context?.let { watchMemory(it) }
    }

    /** Brings tab [id] to the front and shows it. */
    fun switchTo(id: Long) {
        val list = _tabs.value
        val t = list.firstOrNull { it.id == id } ?: return
        val cur = _tab.value
        if (cur != null && cur.id != id) cur.keep.pause()
        val shown = t.copy(visible = true, resumeAfterPlayer = false)
        publish(list.map { if (it.id == id) shown else it.copy(visible = false) }, shown)
        touch(id)
    }

    /** Hides the front tab and keeps it. [byPlay]: it comes back by itself when the player closes. */
    fun minimize(byPlay: Boolean) {
        val cur = _tab.value ?: return
        cur.keep.pause()
        val hidden = cur.copy(visible = false, resumeAfterPlayer = byPlay)
        publish(_tabs.value.map { if (it.id == cur.id) hidden else it }, hidden)
    }

    fun resume() {
        val cur = _tab.value ?: return
        switchTo(cur.id)
    }

    /** The player closed: a tab hidden by playing one of its videos comes back. */
    fun onPlayerClosed() {
        val cur = _tab.value ?: return
        if (!cur.visible && cur.resumeAfterPlayer) resume()
    }

    /** Closes the front tab. The tab next to it comes to the front, still visible if this one was. */
    fun close() { _tab.value?.let { closeTab(it.id) } }

    /** Closes tab [id]. Closing the front tab brings its neighbour to the front. */
    fun closeTab(id: Long) {
        val list = _tabs.value
        val i = list.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        val gone = list[i]
        gone.keep.release(); recent.remove(id)
        val rest = list.filterNot { it.id == id }
        val cur = _tab.value
        if (cur?.id != id) { publish(rest, cur); return }
        val next = rest.getOrNull(i.coerceAtMost(rest.size - 1))
        if (next == null) { publish(emptyList(), null); return }
        val shown = next.copy(visible = gone.visible, resumeAfterPlayer = false)
        publish(rest.map { if (it.id == next.id) shown else it }, shown)
        touch(next.id)
    }

    /** Closes every tab. */
    fun closeAll() {
        _tabs.value.forEach { it.keep.release() }
        recent.clear()
        publish(emptyList(), null)
    }

    /** Low memory: hidden tabs give their page back (the address is kept and reloads when shown). */
    fun trim(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) return
        val hidden = _tabs.value.filter { t -> !(t.visible && t.id == _tab.value?.id) && t.keep.alive }
        hidden.forEach { it.keep.release() }
        if (hidden.isNotEmpty()) { lowMemoryReleases += hidden.size; android.util.Log.i("YuWebTabs", "low memory (level $level): ${hidden.size} hidden page(s) given back; they reload when shown") }
    }

    /** Hidden pages given back because memory ran critically low (device tests read it). */
    @Volatile var lowMemoryReleases: Int = 0
        private set

    private fun watchMemory(context: Context) {
        if (callbacks != null) return
        val cb = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) = trim(level)
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            @Deprecated("Deprecated in Java") override fun onLowMemory() = trim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        }
        callbacks = cb
        context.applicationContext.registerComponentCallbacks(cb)
    }
}
