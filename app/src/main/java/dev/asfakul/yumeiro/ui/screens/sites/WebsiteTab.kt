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
 * OL-1: the open website tab. Playing one of its videos, opening Downloads or pressing Minimize /
 * Back hides the page but keeps it alive (same WebView, same place, same sign-in) instead of closing
 * it; it comes back when the player closes (if a play hid it) or from the "Open tab" card on the
 * Websites page. One tab at a time (a second site replaces it); Close ends it. On low memory a hidden
 * tab drops its page and reloads its last address when shown again.
 */
object WebsiteTab {
    data class Tab(val name: String, val keep: PageKeep, val visible: Boolean, val resumeAfterPlayer: Boolean = false)

    private val _tab = MutableStateFlow<Tab?>(null)
    val tab: StateFlow<Tab?> = _tab.asStateFlow()
    @Volatile private var callbacks: ComponentCallbacks2? = null

    /** Opens [url] (or shows it again when it is already the open tab). */
    fun open(context: Context?, url: String, name: String) {
        val cur = _tab.value
        if (cur != null && cur.keep.pageUrl == url) { _tab.value = cur.copy(visible = true, resumeAfterPlayer = false); return }
        cur?.keep?.release()
        _tab.value = Tab(name, PageKeep(url), visible = true)
        context?.let { watchMemory(it) }
    }

    /** Hides the tab and keeps it. [byPlay]: it comes back by itself when the player closes. */
    fun minimize(byPlay: Boolean) {
        val cur = _tab.value ?: return
        cur.keep.pause()
        _tab.value = cur.copy(visible = false, resumeAfterPlayer = byPlay)
    }

    fun resume() {
        val cur = _tab.value ?: return
        _tab.value = cur.copy(visible = true, resumeAfterPlayer = false)
    }

    /** The player closed: a tab hidden by playing one of its videos comes back. */
    fun onPlayerClosed() {
        val cur = _tab.value ?: return
        if (!cur.visible && cur.resumeAfterPlayer) resume()
    }

    fun close() {
        _tab.value?.keep?.release()
        _tab.value = null
    }

    /** Low memory: a hidden tab gives its page back (its address is kept and reloads on [resume]). */
    fun trim(level: Int) {
        val cur = _tab.value ?: return
        if (!cur.visible && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) cur.keep.release()
    }

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
