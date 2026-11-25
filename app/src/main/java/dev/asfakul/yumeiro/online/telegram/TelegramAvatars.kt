/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram

import android.content.Context
import dev.asfakul.yumeiro.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * TG-AVATAR-1 (owner list 8A): the photo of each saved public channel, from its own t.me/s preview header (the same
 * page the channel screen reads, so opening the channel afterwards is served from TelegramPublic's cache). Remembered
 * on disk for [TTL_MS] so the Telegram page shows the photos at once and doesn't fetch every channel on every visit.
 * Before, every channel row showed t.me's favicon, so all channels looked the same.
 */
object TelegramAvatars {
    private const val PREFS = "telegram_channel_avatars"
    internal const val TTL_MS = 3L * 24 * 60 * 60 * 1000
    private val gate = Semaphore(3)

    /** Photo URL remembered for [channel] and when, or null. "" means the channel has no photo. */
    internal data class Entry(val url: String, val at: Long)

    internal fun decode(raw: String?): Entry? {
        raw ?: return null
        val cut = raw.indexOf('|').takeIf { it > 0 } ?: return null
        val at = raw.substring(0, cut).toLongOrNull() ?: return null
        return Entry(raw.substring(cut + 1), at)
    }
    internal fun encode(e: Entry) = "${e.at}|${e.url}"
    internal fun fresh(e: Entry?, now: Long) = e != null && now - e.at in 0 until TTL_MS

    /** Remembered photo (possibly stale) without network. Off the main thread (SharedPreferences). */
    fun cached(context: Context, channel: String): String? =
        decode(prefs(context).getString(channel.lowercase(), null))?.url?.ifBlank { null }

    /** Photo for [channel]: the remembered one while fresh, else read from the channel's preview. Null if none. */
    suspend fun get(context: Context, channel: String, now: Long = System.currentTimeMillis()): String? = withContext(Dispatchers.IO) {
        val key = channel.lowercase()
        val known = decode(prefs(context).getString(key, null))
        if (fresh(known, now)) return@withContext known!!.url.ifBlank { null }
        val page = gate.withPermit { TelegramPublic.fetchChannel(NetworkClient.get(context), channel) }
        when (page) {
            is TelegramPublic.Result.Ok -> { remember(context, channel, page.value.avatarUrl.orEmpty(), now); page.value.avatarUrl }
            // Offline or blocked: keep showing the old photo, try again next time.
            is TelegramPublic.Result.Failed -> known?.url?.ifBlank { null }
        }
    }

    /** The channel screen read the header anyway: keep its photo. */
    fun remember(context: Context, channel: String, url: String, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putString(channel.lowercase(), encode(Entry(url, now))).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
