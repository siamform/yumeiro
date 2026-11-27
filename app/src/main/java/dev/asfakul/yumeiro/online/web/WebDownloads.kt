/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.webkit.CookieManager
import android.webkit.WebView
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.screens.downloads.StreamDownloadAsk

/**
 * DL-WEB: a file link tapped inside an in-app web page (WebView DownloadListener) opens the same
 * download popup as the player, episodes, websites' stream bar, Telegram and YouTube (DL-ONE).
 * Before, WebView had no listener and such a tap did nothing.
 */
object WebDownloads {
    /** What to download for a WebView download event, or null when the link is not a real address (blob:, data:). Pure. */
    fun requestFor(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        pageUrl: String?,
        cookie: String?,
    ): StreamRequest? {
        val scheme = url.substringBefore(':').lowercase()
        if (scheme != "http" && scheme != "https") return null
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val format = when {
            mime == "application/vnd.apple.mpegurl" || mime == "application/x-mpegurl" || mime == "audio/mpegurl" -> StreamFormat.HLS
            mime == "application/dash+xml" -> StreamFormat.DASH
            else -> StreamFormat.AUTO
        }
        return StreamRequest(
            url = url,
            title = fileName(url, contentDisposition),
            format = format,
            userAgent = userAgent?.takeIf { it.isNotBlank() },
            referer = pageUrl?.takeIf { it.startsWith("http") },
            cookie = cookie?.takeIf { it.isNotBlank() },
            pageUrl = pageUrl,
        )
    }

    /**
     * File name from Content-Disposition (RFC 6266: filename* wins over filename), else the last path
     * part of [url], else "download". Path separators and control characters are removed. Pure.
     */
    fun fileName(url: String, contentDisposition: String?): String {
        val cd = contentDisposition.orEmpty()
        val star = Holder.STAR.find(cd)?.groupValues?.get(1)?.let { v ->
            runCatching { java.net.URLDecoder.decode(v.substringAfter("''").replace("+", "%2B"), "UTF-8") }.getOrNull()
        }
        val plain = Holder.PLAIN.find(cd)?.let { m -> m.groupValues[1].ifEmpty { m.groupValues[2] } }
        // Only the path names a file: "https://x.org/" has none (its host is not a file name).
        val path = url.substringBefore('#').substringBefore('?').substringAfter("://", "").substringAfter('/', "")
        val fromUrl = path.trimEnd('/').substringAfterLast('/')
            .let { runCatching { java.net.URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.getOrDefault(it) }
        val name = listOf(star, plain, fromUrl).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        val clean = name.map { c -> if (c == '/' || c == '\\' || c.code < 0x20) '_' else c }.joinToString("").trim().trim('.')
        return clean.ifBlank { "download" }.take(120)
    }

    /** Message shown when the page builds the file itself (blob: / data:), which only a browser can save. */
    val PAGE_MADE_FILE: String get() = tr(R.string.this_page_makes_the_file_itself, "This page makes the file itself, so it can't be saved here. Open the page in your browser to save it.")

    /** Installs the listener on [web] when the flag is on. [pageUrl] is the page the user is on now. */
    fun attach(web: WebView, pageUrl: () -> String?, onPlayable: ((StreamRequest) -> Boolean)? = null) {
        val ctx = web.context.applicationContext
        if (!FeatureFlags.on(ctx, FeatureFlags.Flag.WEB_DOWNLOAD_LISTENER)) return
        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val page = pageUrl()
            val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
            val req = requestFor(url.orEmpty(), userAgent, contentDisposition, mimeType, page, cookie)
            // ADCHAIN-2: a video or audio the page hands over as a file plays in Yumeiro's player
            // (its Download button is still there); every other file opens the download popup as before.
            if (req != null && onPlayable != null && isPlayable(mimeType, req.title, url.orEmpty()) && onPlayable(req)) return@setDownloadListener
            if (req == null) AppMessages.show(web.context, PAGE_MADE_FILE)
            else StreamDownloadAsk.ask(web.context, req, sourceUrl = page ?: req.url)
        }
    }

    private val MEDIA_EXT = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "ts", "m3u8", "mpd", "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")

    /**
     * ADCHAIN-2: the download is a file Yumeiro's player opens: a video / audio MIME type, an HLS or DASH
     * playlist, or (for the generic application/octet-stream many hosts send) a media file name. Pure.
     */
    fun isPlayable(mimeType: String?, fileName: String?, url: String): Boolean {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (mime.startsWith("video/") || mime.startsWith("audio/")) return true
        if (mime == "application/vnd.apple.mpegurl" || mime == "application/x-mpegurl" || mime == "application/dash+xml") return true
        if (mime.isNotEmpty() && mime != "application/octet-stream" && mime != "binary/octet-stream" && mime != "application/force-download") return false
        val ext = (fileName?.takeIf { '.' in it } ?: url.substringBefore('?').substringBefore('#').substringAfterLast('/'))
            .substringAfterLast('.', "").lowercase()
        return ext in MEDIA_EXT
    }

    private object Holder {
        val STAR = Regex("""filename\*\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
        val PLAIN = Regex("""filename\s*=\s*(?:"([^"]*)"|([^;]+))""", RegexOption.IGNORE_CASE)
    }
}
