/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources

import android.content.Context
import dev.asfakul.yumeiro.sources.tachiyomi.TachiyomiExtensions

/** Starts every non-CloudStream source bridge once per process. */
object Bridges {
    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        TachiyomiExtensions.start(app)
        dev.asfakul.yumeiro.sources.stremio.StremioAddons.start(app)
        dev.asfakul.yumeiro.sources.servers.HomeServers.start(app)
        dev.asfakul.yumeiro.sources.js.JsSources.start(app)
        dev.asfakul.yumeiro.sources.drama.YumeiroDrama.start(app)
    }
}
