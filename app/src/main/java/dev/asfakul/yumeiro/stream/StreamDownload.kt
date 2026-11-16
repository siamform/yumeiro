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

    /** True when [req] can be saved as one file (not DASH / SmoothStreaming / DRM). */
    fun canSave(req: StreamRequest): Boolean {
        val fmt = req.resolvedFormat()
        return fmt != StreamFormat.DASH && fmt != StreamFormat.SMOOTH && req.drmScheme == DrmScheme.NONE
    }

    /** Pure parts, unit-tested: the title a saved link gets. */
    fun titleFor(req: StreamRequest, sourceUrl: String): String =
        req.title.ifBlank { runCatching { Uri.parse(req.url).lastPathSegment?.substringBeforeLast('.') }.getOrNull()?.ifBlank { null } ?: PlatformRules.hostOf(sourceUrl) ?: "Video" }

    /** Queues [req] (already resolved/playable). [sourceUrl] is what the user opened (page or link). */
    fun enqueue(context: Context, req: StreamRequest, sourceUrl: String = req.url): Outcome {
        if (!canSave(req)) return Outcome.Refused("This stream can't be saved (DASH or protected). You can still play it.")
        val fmt = req.resolvedFormat()
        val headers = req.requestHeaders() + listOfNotNull(req.userAgent?.let { "User-Agent" to it })
        val title = titleFor(req, sourceUrl)
        val link = dev.asfakul.yumeiro.online.OnlineLink(
            name = title, source = PlatformRules.hostOf(sourceUrl) ?: "Link", url = req.url, referer = req.referer, quality = 0,
            isHls = fmt == StreamFormat.HLS, isDash = false, headers = headers,
        )
        val err = dev.asfakul.yumeiro.online.download.OnlineDownloads.enqueue(context, title, "Network", sourceUrl, null, req.logoUrl, emptyMap(), link)
        return if (err != null) Outcome.Refused(err) else Outcome.Queued(title)
    }
}
