/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

// Precompiled once (never during composition, scrolling or per call): see docs/STABILITY-R15.md.
private val RX_TG_NAME = Regex("[A-Za-z][A-Za-z0-9_]{2,31}")
private val RX_TG_BG_URL = Regex("url\\(['\"]?([^'\")]+)['\"]?\\)")

/**
 * F-02 (ADR-042): public Telegram channels without a Telegram account.
 *
 * Telegram publishes a web preview of every public channel at `t.me/s/<channel>` and of every
 * public post at `t.me/<channel>/<post>?embed=1`. Those pages are what a browser shows to anyone;
 * Yumeiro reads them, lists the video posts and plays the video file the preview itself links.
 * Nothing else is touched: no Telegram API, no login, no private chats (`t.me/c/…`, `t.me/+…`).
 * A post whose video Telegram keeps for its apps ("not supported in your browser") stays there.
 *
 * Parsing and link rules are pure (unit-tested with fixture pages); [fetchChannel] / [fetchPost]
 * do the one HTTP GET each, with a 15 s limit and a 10-minute in-memory cache for channel pages.
 */
object TelegramPublic {
    /** Reserved `t.me` paths that are not channels. */
    private val RESERVED = setOf("s", "c", "joinchat", "addstickers", "addemoji", "addlist", "share", "proxy", "socks", "iv", "login", "setlanguage", "addtheme", "confirmphone", "invoice", "boost", "k", "a", "z")
    private val HOSTS = setOf("t.me", "telegram.me", "telegram.dog")

    /** Mirrors the `telegram_public` flag (set by StreamResolver.resolve); off = links take the old generic path. */
    @Volatile var enabled: Boolean = true

    /** Base address of the web preview; tests point it at a local server. */
    @Volatile internal var base: String = "https://t.me"

    const val TIMEOUT_MS = 15_000L
    const val CACHE_MS = 10 * 60_000L
    private const val CACHE_MAX = 8
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    sealed interface Link
    /** A public channel (`t.me/name`, `t.me/s/name`, `@name`). */
    data class Channel(val name: String) : Link
    /** One public post (`t.me/name/123`, `t.me/s/name/123`). */
    data class Post(val channel: String, val id: Int) : Link
    /** A private chat or invite link: needs a Telegram account, never opened here. */
    data object Private : Link

    data class VideoPost(
        val channel: String,
        val id: Int,
        val text: String,
        /** Temporary file address from the preview, or null when Telegram keeps the video for its apps. */
        val videoUrl: String?,
        val thumbUrl: String?,
        val duration: String?,
        /** ISO date from the preview, or null. */
        val date: String?,
    ) {
        val link: String get() = "https://t.me/$channel/$id"
        val playable: Boolean get() = videoUrl != null
    }

    data class Page(
        val channel: String,
        val title: String,
        val posts: List<VideoPost>,
        val before: Int?,
        /** Channel photo, subscriber count ("9.09M") and description from the preview's header; null when absent. */
        val avatarUrl: String? = null,
        val subscribers: String? = null,
        val description: String? = null,
    ) {
        /** Video posts Telegram keeps for its apps (shown with that note, counted in the header). */
        val withheld: Int get() = posts.count { !it.playable }
    }

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Failed(val message: String) : Result<Nothing>
    }

    object Messages {
        const val PRIVATE = "This is a private Telegram link. Yumeiro reads only public channels, without a Telegram account."
        const val OFFLINE = "Couldn't reach Telegram. Check the connection and try again."
        const val APP_ONLY = "Telegram shows this video only in its own app, not on its web preview, so Yumeiro can't play it."
        fun noPosts(channel: String) = "Telegram shows no public posts for @$channel. The channel may be private, renamed or have its web preview turned off."
        fun noVideo(channel: String, id: Int) = "Post $id of @$channel has no video on Telegram's web preview."
        fun layout() = "Telegram's web preview changed and Yumeiro couldn't read it. Open the post in Telegram instead."
    }

    // ---- Links (pure) ----

    fun isName(s: String): Boolean = RX_TG_NAME.matches(s) && s.lowercase() !in RESERVED

    /** Classifies a pasted link or `@name`; null when it isn't a Telegram address. */
    fun parse(input: String): Link? {
        val t = input.trim()
        if (t.startsWith("@")) return t.drop(1).takeIf { isName(it) }?.let { Channel(it) }
        // Telegram's app links: tg://resolve?domain=<name>[&post=<id>], tg://privatepost?… (private).
        if (t.startsWith("tg://", ignoreCase = true)) {
            val kind = t.substring(5).substringBefore('?').lowercase()
            val q = t.substringAfter('?', "").split('&').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].lowercase() to it[1] } }.toMap()
            return when (kind) {
                "privatepost", "join" -> Private
                "resolve" -> {
                    val name = q["domain"]?.takeIf { isName(it) } ?: return null
                    val id = q["post"]?.toIntOrNull()
                    if (id != null && id > 0) Post(name, id) else Channel(name)
                }
                else -> null
            }
        }
        val withScheme = if ("://" in t) t else "https://$t"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host !in HOSTS) return null
        val parts = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        if (parts[0] == "c" || parts[0].startsWith("+") || parts[0] == "joinchat") return Private
        val p = if (parts[0] == "s") parts.drop(1) else parts
        val name = p.getOrNull(0)?.takeIf { isName(it) } ?: return null
        val id = p.getOrNull(1)?.toIntOrNull()
        return if (id != null && id > 0) Post(name, id) else Channel(name)
    }

    /** The channel a saved-site address points to, or null. */
    fun channelOf(url: String): String? = when (val l = parse(url)) {
        is Channel -> l.name
        else -> null
    }

    fun channelUrl(name: String): String = "https://t.me/s/$name"

    // ---- Pages (pure) ----

    /** Video posts of a channel page (`t.me/s/…`) or an embedded post page, newest last as on the page. */
    fun parsePage(html: String, channel: String): Page {
        val doc = Jsoup.parse(html)
        val title = doc.selectFirst(".tgme_channel_info_header_title")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: "@$channel"
        val posts = mutableListOf<VideoPost>()
        for (msg in doc.select("div.tgme_widget_message[data-post]")) {
            val dataPost = msg.attr("data-post")
            val ch = dataPost.substringBefore('/').takeIf { isName(it) } ?: channel
            val msgId = dataPost.substringAfter('/', "").toIntOrNull() ?: continue
            // Caption first, else the file name of a video sent as a document (what the sender called it).
            val text = msg.selectFirst(".tgme_widget_message_text")?.text()?.trim()?.ifEmpty { null }
                ?: msg.selectFirst(".tgme_widget_message_document_title")?.text()?.trim().orEmpty()
            val date = msg.selectFirst("a.tgme_widget_message_date time[datetime]")?.attr("datetime")?.takeIf { it.isNotBlank() }
            val players = msg.select("a.tgme_widget_message_video_player")
            for (player in players) {
                val id = player.attr("href").substringAfterLast('/').substringBefore('?').toIntOrNull() ?: msgId
                posts += VideoPost(
                    channel = ch,
                    id = id,
                    text = text,
                    videoUrl = videoOf(player),
                    thumbUrl = thumbOf(player),
                    duration = player.selectFirst("time.message_video_duration")?.text()?.trim()?.takeIf { it.isNotEmpty() },
                    date = date,
                )
            }
        }
        val before = doc.selectFirst("a.tme_messages_more[data-before]")?.attr("data-before")?.toIntOrNull()
        val avatar = doc.selectFirst(".tgme_channel_info_header .tgme_page_photo_image img[src]")?.attr("src")?.takeIf { it.startsWith("http") }
        val subscribers = doc.select(".tgme_channel_info_counter")
            .firstOrNull { it.selectFirst(".counter_type")?.text()?.trim()?.lowercase()?.startsWith("subscriber") == true }
            ?.selectFirst(".counter_value")?.text()?.trim()?.ifEmpty { null }
        val description = doc.selectFirst(".tgme_channel_info_description")?.text()?.trim()?.ifEmpty { null }
        return Page(channel, title, posts.distinctBy { it.id }, before, avatar, subscribers, description)
    }

    private fun videoOf(player: Element): String? {
        if (player.selectFirst(".message_media_not_supported") != null) return null
        val v = player.selectFirst("video") ?: return null
        return v.attr("src").ifBlank { v.attr("data-src") }.takeIf { it.startsWith("http") }
    }

    private fun thumbOf(player: Element): String? {
        player.selectFirst("video")?.attr("poster")?.takeIf { it.startsWith("http") }?.let { return it }
        for (sel in listOf("i.tgme_widget_message_video_thumb", ".tgme_widget_message_video_thumb_wrap", ".tgme_widget_message_photo_wrap")) {
            player.selectFirst(sel)?.attr("style")?.let { RX_TG_BG_URL.find(it)?.groupValues?.get(1) }?.takeIf { it.startsWith("http") }?.let { return it }
        }
        return null
    }

    /** The playable post [id] in [page], or why it isn't. */
    fun pick(page: Page, channel: String, id: Int): Result<VideoPost> {
        val post = page.posts.firstOrNull { it.id == id } ?: page.posts.singleOrNull()
            ?: return Result.Failed(Messages.noVideo(channel, id))
        return if (post.playable) Result.Ok(post) else Result.Failed(Messages.APP_ONLY)
    }

    // ---- Network ----

    private class Cached(val at: Long, val page: Page)
    private val cache = object : LinkedHashMap<String, Cached>(CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?) = size > CACHE_MAX
    }

    fun clearCache() = synchronized(cache) { cache.clear() }

    private fun get(client: OkHttpClient, url: String): Result<String> {
        val c = client.newBuilder().callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS).build()
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Accept-Language", "en").build()
        return try {
            c.newCall(req).execute().use { r ->
                when {
                    r.isSuccessful -> Result.Ok(r.body.string())
                    r.code == 404 -> Result.Failed("")
                    else -> Result.Failed(Messages.OFFLINE)
                }
            }
        } catch (_: IOException) {
            Result.Failed(Messages.OFFLINE)
        }
    }

    /** The preview address: newest page, [before] for older posts, or Telegram's own search with [query]. */
    fun pageUrl(channel: String, before: Int? = null, query: String? = null): String {
        val q = query?.trim()?.takeIf { it.isNotEmpty() }
        val params = listOfNotNull(q?.let { "q=" + java.net.URLEncoder.encode(it, "UTF-8") }, before?.let { "before=$it" })
        return "${base.trimEnd('/')}/s/$channel" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
    }

    /** One page of a channel's video posts; [before] pages back (older posts), [query] searches the channel. Blocking. */
    fun fetchChannel(client: OkHttpClient, channel: String, before: Int? = null, now: Long = System.currentTimeMillis(), query: String? = null): Result<Page> {
        if (!isName(channel)) return Result.Failed(Messages.noPosts(channel))
        val url = pageUrl(channel, before, query)
        synchronized(cache) { cache[url]?.takeIf { now - it.at < CACHE_MS }?.let { return Result.Ok(it.page) } }
        return when (val r = get(client, url)) {
            is Result.Failed -> Result.Failed(r.message.ifEmpty { Messages.noPosts(channel) })
            is Result.Ok -> {
                val html = r.value
                if ("tgme_widget_message" !in html && "tgme_channel_info" !in html) return Result.Failed(Messages.noPosts(channel))
                val page = parsePage(html, channel)
                synchronized(cache) { cache[url] = Cached(now, page) }
                Result.Ok(page)
            }
        }
    }

    /** The video of one public post (always fresh: its file address is temporary). Blocking. */
    fun fetchPost(client: OkHttpClient, channel: String, id: Int): Result<VideoPost> {
        val url = "${base.trimEnd('/')}/$channel/$id?embed=1&mode=tme"
        return when (val r = get(client, url)) {
            is Result.Failed -> Result.Failed(r.message.ifEmpty { Messages.noVideo(channel, id) })
            is Result.Ok -> {
                val html = r.value
                if ("tgme_widget_message" !in html) return Result.Failed(if ("tgme_page" in html) Messages.noVideo(channel, id) else Messages.layout())
                pick(parsePage(html, channel), channel, id)
            }
        }
    }
}
