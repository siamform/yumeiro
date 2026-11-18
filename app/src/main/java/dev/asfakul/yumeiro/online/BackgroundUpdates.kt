/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import dev.asfakul.yumeiro.stream.PlaylistStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

/**
 * NBUG-16 NB16-7: the background update checks (extensions, market playlists), run each time the app
 * comes to the front, not only when the activity is created (a TV keeps it for days) or the Live TV
 * screen is open. Each check keeps its own 6 h gate ([ExtensionMarket.CHECK_INTERVAL_MS],
 * [dev.asfakul.yumeiro.stream.PlaylistMarket]), so coming back often costs no network.
 */
object BackgroundUpdates {
    data class Outcome(
        val extensions: ExtensionManager.SyncResult = ExtensionManager.SyncResult(),
        val playlistsUpdated: List<String> = emptyList(),
    )

    private val running = Mutex()
    @Volatile private var lastRun = 0L

    /** Cheap in-process gate so a burst of starts (rotation, quick app switches) checks once. */
    internal const val MIN_GAP_MS = 60_000L
    internal fun due(now: Long, last: Long): Boolean = last == 0L || now - last >= MIN_GAP_MS || now < last

    /** Runs both checks unless one is already running or one ran in the last minute. Never throws. */
    suspend fun run(context: Context, now: Long = System.currentTimeMillis()): Outcome? = withContext(Dispatchers.IO) {
        if (!due(now, lastRun) || !running.tryLock()) return@withContext null
        try {
            lastRun = now
            val ext = runCatching { ExtensionManager.sync(context) }.getOrDefault(ExtensionManager.SyncResult())
            val lists = runCatching { PlaylistStore.init(context); PlaylistStore.autoUpdateMarket(context) }.getOrNull()
            Outcome(ext, lists?.updated.orEmpty())
        } finally { running.unlock() }
    }

    /** User-facing lines for [o] (pure, unit-tested). */
    fun messages(o: Outcome): List<String> = buildList {
        val u = o.extensions.updated
        if (u.isNotEmpty()) add(if (u.size == 1) "${u[0]} extension updated" else "${u.size} extensions updated")
        val h = o.extensions.heldBack
        if (h.isNotEmpty()) add(
            (if (h.size == 1) "${h[0]} update was held back" else "${h.size} extension updates were held back") +
                " by the safety check. Open Extensions and tap Update to review",
        )
        if (o.playlistsUpdated.isNotEmpty()) add("Updated ${o.playlistsUpdated.joinToString()}")
    }
}
