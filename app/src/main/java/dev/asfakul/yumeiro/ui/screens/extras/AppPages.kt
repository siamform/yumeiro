/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.extras

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Full-screen pages that can be opened from anywhere (top-bar bell, a notification, a reminder,
 * Settings): the notification inbox and the Support page. Drawn over the current tab.
 */
object AppPages {
    enum class Page { INBOX, SUPPORT }
    private val _page = MutableStateFlow<Page?>(null)
    val page: StateFlow<Page?> = _page.asStateFlow()
    fun open(p: Page) { _page.value = p }
    fun close() { _page.value = null }
}

/** Which popup currently owns the screen, so two popups never show at once. */
object PopupGate {
    private val _owner = MutableStateFlow<String?>(null)
    val owner: StateFlow<String?> = _owner.asStateFlow()
    @Synchronized fun claim(id: String): Boolean { if (_owner.value != null && _owner.value != id) return false; _owner.value = id; return true }
    @Synchronized fun release(id: String) { if (_owner.value == id) _owner.value = null }
}

object Links {
    /** Opens [url] in the matching app (Facebook, Instagram, …) or the browser. False when nothing could open it. */
    fun open(context: Context, url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }
}
