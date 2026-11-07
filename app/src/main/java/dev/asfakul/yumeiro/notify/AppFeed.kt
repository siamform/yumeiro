/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.content.Context
import dev.asfakul.yumeiro.security.TrustedSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant

/**
 * Reads the Yumeiro feed: a small JSON file the "App feed" workflow publishes to the bot-only
 * `feed` branch (new extensions, new stream playlists, releases, announcements). Only that HTTPS
 * address is accepted (TrustedSources). New items go to the inbox and, a few at a time, to the
 * notification shade.
 */
object AppFeed {
    const val URL = "https://raw.githubusercontent.com/siamform/yumeiro/feed/feed.json"
    private const val MAX_BYTES = 512L * 1024
    private const val PREFS = "app_feed"
    /** At most this many system notifications per sync; the rest go to the inbox quietly. */
    private const val MAX_SHADE = 2
    /** On the very first sync, this many recent items are put in the inbox (no notification). */
    private const val FIRST_RUN_ITEMS = 5
    private val ACTIONS = setOf("update", "extensions", "market", "streams", "support")
    private val mutex = Mutex()

    data class FeedItem(val id: String, val kind: Inbox.Kind, val title: String, val body: String, val at: Long, val link: String?)

    fun parse(text: String): List<FeedItem> {
        val a = JSONObject(text).optJSONArray("items") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").trim().ifBlank { return@mapNotNull null }.take(120)
            val title = o.optString("title").trim().ifBlank { return@mapNotNull null }.take(120)
            val at = o.optLong("at", 0L).takeIf { it > 0 } ?: runCatching { Instant.parse(o.optString("at")).toEpochMilli() }.getOrDefault(0L)
            FeedItem(id, kindOf(o.optString("kind")), title, o.optString("body").trim().take(400), at, safeLink(o.optString("link")))
        }.sortedByDescending { it.at }
    }

    fun kindOf(s: String?): Inbox.Kind = when (s?.lowercase()) {
        "extension", "extensions" -> Inbox.Kind.EXTENSION
        "playlist", "playlists", "stream", "streams" -> Inbox.Kind.PLAYLIST
        "release", "update" -> Inbox.Kind.UPDATE
        "tip", "nudge" -> Inbox.Kind.NUDGE
        else -> Inbox.Kind.NEWS
    }

    /** Only https links or a known in-app action survive. */
    fun safeLink(s: String?): String? {
        val v = s?.trim().orEmpty()
        return when {
            v in ACTIONS -> v
            v.startsWith("https://") && v.length < 400 -> v
            else -> null
        }
    }

    /** Items to add now: everything newer than [lastAt] that isn't known yet. */
    fun fresh(items: List<FeedItem>, lastAt: Long, known: Set<String>): List<FeedItem> =
        items.filter { it.at > lastAt && it.id !in known }

    suspend fun sync(c: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val items = parse(TrustedSources.fetchCatalogText(URL, MAX_BYTES))
            val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val first = !p.contains("last_at")
            val lastAt = p.getLong("last_at", 0L)
            Inbox.ensureLoaded(c)
            val known = Inbox.items.value.map { it.id.removePrefix("feed-") }.toSet()
            val add = if (first) items.take(FIRST_RUN_ITEMS) else fresh(items, lastAt, known)
            add.forEachIndexed { i, it ->
                AppNotifier.post(c, Inbox.Item("feed-${it.id}", it.kind, it.title, it.body, it.at.takeIf { a -> a > 0 } ?: System.currentTimeMillis(), link = it.link),
                    inboxOnly = first || i >= MAX_SHADE)
            }
            val newest = items.maxOfOrNull { it.at } ?: lastAt
            p.edit().putLong("last_at", maxOf(lastAt, newest)).apply()
        }
    }
}
