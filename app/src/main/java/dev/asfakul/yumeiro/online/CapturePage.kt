/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

/**
 * NBUG-12: which web page the last-resort headless capture loads when every link was refused.
 *
 * Before, the episode data came second, right after embed referers. Many extensions (Porn4fans and
 * other KVS sites, most "direct file" sources) put the media file itself in the data, so the
 * capture opened the refused `.mp4` in a WebView, got the same 403, found no player and failed —
 * the title page, which plays fine, was never tried.
 *
 * A candidate is a page only when it is http(s), is not one of the refused stream URLs and does
 * not look like a media file or manifest. Order: an embed referer with a path, the episode data,
 * the title page.
 */
internal object CapturePage {
    private val MEDIA_EXT = listOf(
        ".mp4", ".m4v", ".mkv", ".webm", ".mov", ".avi", ".flv", ".wmv", ".3gp", ".ts", ".m2ts",
        ".m3u8", ".m3u", ".mpd", ".ism", ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".flac", ".wav",
    )

    /** True when [url]'s path ends in a media / manifest extension (a trailing slash is ignored). */
    fun looksLikeMedia(url: String): Boolean {
        val path = url.substringAfter("://", url).substringAfter('/', "")
            .substringBefore('#').substringBefore('?').trimEnd('/').lowercase()
        if (path.endsWith("/manifest") || path.endsWith(".ism/manifest")) return true
        return MEDIA_EXT.any { path.endsWith(it) }
    }

    private fun web(u: String?): String? = u?.trim()?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }

    /**
     * @param referers the referer of every link, in link order
     * @param streams the URLs of every link (never a page)
     * @param data the episode / movie data from the extension
     * @param titleUrl the title's own page ([OnlineDetail.url])
     */
    fun pick(referers: List<String?>, streams: Collection<String>, data: String?, titleUrl: String?): String? {
        val refused = streams.mapTo(HashSet()) { it.trim() }
        fun page(u: String?) = web(u)?.takeIf { it !in refused && !looksLikeMedia(it) }
        // A referer with a path is an embed page; a bare origin ("https://host/") is not worth loading.
        return referers.firstNotNullOfOrNull { r -> page(r)?.takeIf { it.substringAfter("://").substringAfter('/', "").isNotBlank() } }
            ?: page(data)
            ?: page(titleUrl)
    }
}
