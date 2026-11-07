/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.content.Context
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Source-specific "get me a fresh link" hooks, used by the player when a stream expires or is
 * refused (401/403/410). Sources whose URLs are signed and short-lived (YouTube) register one, so
 * recovery re-extracts from the source instead of re-probing a dead URL.
 */
object StreamRefreshers {
    interface Refresher {
        /** Whether this refresher owns [request] (cheap, no network). */
        fun handles(request: StreamRequest): Boolean
        /** A fresh request for [request], or null if it can't be refreshed. */
        suspend fun refresh(context: Context, request: StreamRequest): StreamRequest?
    }

    private val refreshers = CopyOnWriteArrayList<Refresher>()

    fun register(r: Refresher) { refreshers += r }

    fun find(request: StreamRequest): Refresher? = refreshers.firstOrNull { runCatching { it.handles(request) }.getOrDefault(false) }

    suspend fun refresh(context: Context, request: StreamRequest): StreamRequest? {
        for (r in refreshers.filter { runCatching { it.handles(request) }.getOrDefault(false) }) {
            val fresh = runCatching { r.refresh(context, request) }
                .onFailure { Log.w("StreamRefreshers", "refresh failed", it) }.getOrNull()
            if (fresh != null) return fresh
        }
        return null
    }
}
