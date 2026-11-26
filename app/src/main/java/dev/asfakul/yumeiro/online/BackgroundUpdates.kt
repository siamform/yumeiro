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
        /** B11: extensions whose automatic update failed [FAIL_STREAK] checks in a row (not a one-off network blip). */
        val stuck: List<String> = emptyList(),
    )

    private val running = Mutex()
    @Volatile private var lastRun = 0L

    /** Cheap in-process gate so a burst of starts (rotation, quick app switches) checks once. */
    internal const val MIN_GAP_MS = 60_000L
    internal fun due(now: Long, last: Long): Boolean = last == 0L || now - last >= MIN_GAP_MS || now < last

    /** B11: an automatic update that failed this many checks in a row (6 h apart) is a real failure worth a message. */
    const val FAIL_STREAK = 3
    private const val PREFS = "yu_ext_update_quiet"
    private const val STREAKS = "streaks"

    /** Runs both checks unless one is already running or one ran in the last minute. Never throws. */
    suspend fun run(context: Context, now: Long = System.currentTimeMillis()): Outcome? = withContext(Dispatchers.IO) {
        if (!due(now, lastRun) || !running.tryLock()) return@withContext null
        try {
            lastRun = now
            val ext = runCatching { ExtensionManager.sync(context) }.getOrDefault(ExtensionManager.SyncResult())
            val lists = runCatching { PlaylistStore.init(context); PlaylistStore.autoUpdateMarket(context) }.getOrNull()
            val stuck = runCatching { trackStreaks(context, ext) }.getOrDefault(emptyList())
            Outcome(ext, lists?.updated.orEmpty(), stuck)
        } finally { running.unlock() }
    }

    /** Pure: the new failure streaks after one check, and the extensions that just reached [FAIL_STREAK]. Unit-tested. */
    fun nextStreaks(prev: Map<String, Int>, r: ExtensionManager.SyncResult): Pair<Map<String, Int>, List<String>> {
        // The market list could not be read (offline, DNS): nothing was tried, so nothing counts either way.
        if (r.error != null) return prev to emptyList()
        val next = prev.toMutableMap()
        r.updated.forEach { next.remove(it) }
        val reached = ArrayList<String>()
        r.failed.forEach { n -> val c = (next[n] ?: 0) + 1; next[n] = c; if (c == FAIL_STREAK) reached += n }
        return next to reached
    }

    private fun trackStreaks(context: Context, r: ExtensionManager.SyncResult): List<String> {
        if (r.updated.isEmpty() && r.failed.isEmpty()) return emptyList()
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prev = p.getString(STREAKS, null).orEmpty().split('\n').mapNotNull { l ->
            val n = l.substringBeforeLast('\t', ""); val c = l.substringAfterLast('\t', "").toIntOrNull()
            if (n.isEmpty() || c == null) null else n to c
        }.toMap()
        val (next, reached) = nextStreaks(prev, r)
        p.edit().putString(STREAKS, next.entries.take(200).joinToString("\n") { it.key + "\t" + it.value }).apply()
        return reached
    }

    /**
     * What interrupts the user (a message on screen) for [o]. Pure, unit-tested.
     *
     * B11 (Beta chapter, owner: "extension auto-update must be silent; a popup only on real failures"): a successful
     * update and a playlist refresh used to put "3 extensions updated" on screen after almost every launch. They are
     * silent now (kept in the inbox, already read: [quietNotes]). Left on screen: an update the safety check refused
     * (it waits for the user's review) and one that failed [FAIL_STREAK] checks in a row.
     */
    fun messages(o: Outcome): List<String> = buildList {
        val h = o.extensions.heldBack
        if (h.isNotEmpty()) add(
            (if (h.size == 1) "${h[0]} update was held back" else "${h.size} extension updates were held back") +
                " by the safety check. Open Extensions and tap Update to review",
        )
        val s = o.stuck
        if (s.isNotEmpty()) add(
            (if (s.size == 1) "${s[0]} couldn't update after $FAIL_STREAK tries" else "${s.size} extensions couldn't update after $FAIL_STREAK tries") +
                ". Open Extensions to update or reinstall",
        )
    }

    /** B11: what the inbox keeps (no message on screen, no unread badge). Pure, unit-tested. */
    fun quietNotes(o: Outcome): List<String> = buildList {
        val u = o.extensions.updated
        if (u.isNotEmpty()) add(if (u.size == 1) "${u[0]} extension updated" else "${u.size} extensions updated: ${u.joinToString()}")
        if (o.playlistsUpdated.isNotEmpty()) add("Updated ${o.playlistsUpdated.joinToString()}")
    }

    /** B11: files the quiet notes in the inbox, already read, one entry per check. */
    fun fileQuietly(context: Context, o: Outcome, now: Long = System.currentTimeMillis()) {
        val notes = quietNotes(o)
        if (notes.isEmpty()) return
        runCatching {
            dev.asfakul.yumeiro.notify.Inbox.add(context, dev.asfakul.yumeiro.notify.Inbox.Item(
                id = "ext-auto-update-$now", kind = dev.asfakul.yumeiro.notify.Inbox.Kind.EXTENSION,
                title = notes.first(), body = notes.joinToString(". "), at = now, read = true, link = "extensions",
            ))
        }
    }
}
