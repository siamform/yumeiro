/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BUG-2: "open this channel" from places outside the YouTube page (the watch page's channel row,
 * a shared channel link). The app switches to Videos → YouTube, whose page takes the request once.
 */
object YtChannelLaunch {
    data class Request(val url: String, val name: String)

    private val _request = MutableStateFlow<Request?>(null)
    val request: StateFlow<Request?> = _request.asStateFlow()

    fun open(url: String, name: String) { _request.value = Request(url, name) }

    /** Returns the request and clears it (the YouTube page shows it). */
    fun take(): Request? = _request.value.also { _request.value = null }
}
