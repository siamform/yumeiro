/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.content.Context
import android.net.Uri

/**
 * Saves a playable stream link with the built-in downloader. Shared by the Stream screen's
 * "Download" button and the player's watch page for links and website videos (BUG-7 7a: the
 * player had no download action for them, only the Play link page did).
 */
object StreamDownload {
    sealed interface Outcome {
        data class Queued(val title: String) : Outcome
        data class Refused(val message: String) : Outcome
    }

    /**
     * True when [req] can be saved as one file. NF-2b: DASH is saved too (DashGrab joins its tracks);
     * Smooth Streaming and any DRM (Widevine, ClearKey) are refused. Live HLS is refused by the
     * downloader once it sees the playlist.
     */
    fun canSave(req: StreamRequest): Boolean = refusal(req) == null

    /** Pure parts, unit-tested: the title a saved link gets. */
    fun titleFor(req: StreamRequest, sourceUrl: String): String =
        req.title.ifBlank { runCatching { Uri.parse(req.url).lastPathSegment?.substringBeforeLast('.') }.getOrNull()?.ifBlank { null } ?: PlatformRules.hostOf(sourceUrl) ?: "Video" }

    /** Why [req] can't be saved, or null when it can. Pure (unit-tested). */
    fun refusal(req: StreamRequest): String? = when {
        req.drmScheme != DrmScheme.NONE || !req.clearKeys.isNullOrBlank() -> "Protected video, can't be saved. You can still play it."
        req.resolvedFormat() == StreamFormat.SMOOTH -> "This stream type (Smooth Streaming) can't be saved. You can still play it."
        else -> null
    }

    /** The download link for [req] (already resolved/playable). Pure (unit-tested). */
    fun linkFor(req: StreamRequest, sourceUrl: String = req.url): dev.asfakul.yumeiro.online.OnlineLink {
        val fmt = req.resolvedFormat()
        val headers = req.requestHeaders() + listOfNotNull(req.userAgent?.let { "User-Agent" to it })
        return dev.asfakul.yumeiro.online.OnlineLink(
            name = titleFor(req, sourceUrl), source = PlatformRules.hostOf(sourceUrl) ?: "Link", url = req.url, referer = req.referer, quality = 0,
            isHls = fmt == StreamFormat.HLS, isDash = fmt == StreamFormat.DASH, headers = headers,
            // NF-2b: a video with its audio in a separate file was saved silent; the worker joins both.
            audioTracks = if (fmt == StreamFormat.DASH) emptyList() else req.audioTracks.map { it.url to it.headers },
        )
    }

    /** Queues [req] (already resolved/playable). [sourceUrl] is what the user opened (page or link). */
    fun enqueue(
        context: Context, req: StreamRequest, sourceUrl: String = req.url, keep: Boolean? = null,
        /** OL-5: quality / audio / subtitles picked in the popup, and the subtitle files the page offered. */
        pick: dev.asfakul.yumeiro.online.download.DownloadTracks.Pick? = null, subs: List<Pair<String, String>> = emptyList(),
    ): Outcome {
        refusal(req)?.let { return Outcome.Refused(it) }
        val link = linkFor(req, sourceUrl)
        val title = link.name
        val err = dev.asfakul.yumeiro.online.download.OnlineDownloads.enqueue(context, title, "Network", sourceUrl, null, req.logoUrl, emptyMap(), link, keep = keep, pick = pick, subs = subs)
        return if (err != null) Outcome.Refused(err) else Outcome.Queued(title)
    }
}
