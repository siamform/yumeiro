/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import androidx.media3.common.MimeTypes
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamAudio
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.StreamSubtitle
import java.util.Locale

/** Hands a resolved online link to the app's player (same path as Stream / shared links). */
object OnlinePlayback {
    fun request(title: String, link: OnlineLink, subtitles: List<OnlineSubtitle>, poster: String? = null): StreamRequest {
        val headers = link.headers.filterKeys { k -> !k.equals("User-Agent", true) && !k.equals("Referer", true) }
        return StreamRequest(
            url = link.url,
            title = title,
            format = formatOf(link),
            // The extension already told us what the link is: play it straight away (a network
            // probe first costs time and can use up one-time tokens). If it turns out wrong, the
            // player re-detects the format after the first container error.
            formatFromSource = true,
            // Like CloudStream: a desktop-browser UA unless the extension asks for another one. Many
            // video hosts answer 401/403 to the player's default UA.
            userAgent = link.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: com.lagradost.cloudstream3.USER_AGENT,
            referer = link.referer ?: link.headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value,
            headers = headers,
            logoUrl = poster,
            group = link.source,
            subtitles = subtitles.distinctBy { it.url }.take(40).map { s ->
                StreamSubtitle(url = s.url, label = s.language, language = languageTag(s.language), mimeType = subtitleMime(s.url), headers = s.headers)
            },
            audioTracks = link.audioTracks.map { (url, h) -> StreamAudio(url = url, headers = h) },
        )
    }

    /**
     * Format the extension declared. A plain "video" link is a progressive file (as in CloudStream),
     * unless its URL plainly says it is a manifest; extensions sometimes mislabel those.
     */
    internal fun formatOf(link: OnlineLink): StreamFormat {
        if (link.isHls) return StreamFormat.HLS
        if (link.isDash) return StreamFormat.DASH
        val path = link.url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> StreamFormat.HLS
            path.endsWith(".mpd") -> StreamFormat.DASH
            else -> StreamFormat.PROGRESSIVE
        }
    }

    /**
     * Registers the HTTP interceptors the player needs for [link]: browser-check clearance cookies
     * (for CDNs behind a challenge) and the extension's own video interceptor (signed requests).
     */
    fun registerInterceptors(api: com.lagradost.cloudstream3.MainAPI?, link: OnlineLink) {
        val own = link.raw?.let { raw -> runCatching { api?.getVideoInterceptor(raw) }.getOrNull() }
        dev.asfakul.yumeiro.stream.StreamInterceptors.register(
            link.url, listOfNotNull(dev.asfakul.yumeiro.online.web.BrowserCheck.MediaInterceptor, own),
        )
    }

    fun play(context: Context, title: String, link: OnlineLink, subtitles: List<OnlineSubtitle>, poster: String? = null) {
        IncomingMedia.open(context, request(title, link, subtitles, poster), remember = false)
    }

    /** "English" / "en" / "Português (Brasil)" → BCP-47 tag when recognisable. */
    fun languageTag(label: String): String? {
        val l = label.trim().lowercase(Locale.ROOT)
        if (l.isEmpty()) return null
        if (Regex("^[a-z]{2,3}([-_][a-z]{2,4})?$").matches(l)) return l.replace('_', '-')
        val word = l.substringBefore(' ').substringBefore('(').substringBefore('-').trim()
        return Locale.getAvailableLocales().firstOrNull { loc ->
            val name = loc.getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT)
            name.isNotEmpty() && (name == word || name == l)
        }?.language
    }

    fun subtitleMime(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        return when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.TEXT_VTT
        }
    }
}
