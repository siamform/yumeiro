/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One search entry point per tab: the magnifier in the top bar. Pages with their own search
 * (YouTube, YouTube Music) no longer pin a wide box to the top; the top bar asks them to open it
 * here, and they show the field (focused) only while it is wanted.
 */
object TabSearch {
    const val YT_VIDEO = "yt_video"
    const val YT_MUSIC = "yt_music"

    private val _open = MutableStateFlow<String?>(null)
    val open: StateFlow<String?> = _open.asStateFlow()

    fun toggle(key: String) { _open.value = if (_open.value == key) null else key }
    fun close(key: String) { if (_open.value == key) _open.value = null }
}
