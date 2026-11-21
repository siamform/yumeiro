/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.downloads

import android.content.Context
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.download.DownloadGroups
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.stream.StreamDownload
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.components.AppMessages

/**
 * OL-4: website, link and Telegram downloads go through the same [DownloadPrompt] (Offline copy /
 * Permanent, where it goes, free space) as episodes and YouTube, instead of starting at once.
 * A stream that can't be saved (DRM, Smooth Streaming) says so and asks nothing.
 */
object StreamDownloadAsk {
    /** Quality line for the prompt: what kind of file this link is. Pure (unit-tested). */
    fun qualityText(req: StreamRequest): String = when (req.resolvedFormat()) {
        StreamFormat.HLS -> "Best of the stream (HLS)"
        StreamFormat.DASH -> "Best of the stream (DASH)"
        else -> "Original file"
    }

    /** Asks, then queues [req]. [onRefused] gets the reason a stream can't be saved or queued. */
    fun ask(context: Context, req: StreamRequest, sourceUrl: String = req.url, onRefused: (String) -> Unit = { AppMessages.show(context, it) }) {
        StreamDownload.refusal(req)?.let { onRefused(it); return }
        if (!FeatureFlags.on(context, FeatureFlags.Flag.STREAM_DOWNLOAD_PROMPT)) {
            // Old path: start at once.
            when (val o = StreamDownload.enqueue(context, req, sourceUrl)) {
                is StreamDownload.Outcome.Refused -> onRefused(o.message)
                is StreamDownload.Outcome.Queued -> AppMessages.show(context, "Downloading \"${o.title}\"", AppMessages.Kind.INFO, "View downloads") { DownloadsNav.open() }
            }
            return
        }
        DownloadSettings.init(context)
        DownloadPrompt.ask(context, DownloadPrompt.Request(
            type = DownloadPrompt.Type.LINK,
            title = StreamDownload.titleFor(req, sourceUrl),
            what = "this video", count = 1,
            folder = DownloadGroups.LOOSE,
            quality = qualityText(req),
            defaultKeep = DownloadSettings.keepByDefault.value,
        ) { keep ->
            when (val o = StreamDownload.enqueue(context, req, sourceUrl, keep)) {
                is StreamDownload.Outcome.Refused -> o.message
                is StreamDownload.Outcome.Queued -> null
            }
        })
    }
}
