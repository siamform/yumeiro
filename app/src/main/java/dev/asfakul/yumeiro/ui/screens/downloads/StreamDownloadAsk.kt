/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.downloads

import android.content.Context
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.download.DownloadGroups
import dev.asfakul.yumeiro.online.download.DownloadTracks
import dev.asfakul.yumeiro.online.download.HlsDownloader
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
        // OL-5: quality / audio / subtitle choices. An HLS master is read first (off the main thread, 6 s cap).
        val tracksOn = FeatureFlags.on(context, FeatureFlags.Flag.DOWNLOAD_TRACKS)
        val named = if (tracksOn) namedSubs(req) else emptyList()
        if (tracksOn && req.resolvedFormat() == StreamFormat.HLS) {
            scope.launch {
                val master = withContext(Dispatchers.IO) { withTimeoutOrNull(6_000) { runCatching { probe(context, req, sourceUrl) }.getOrNull() } }
                show(context, req, sourceUrl, offerFor(master, named), named)
            }
        } else show(context, req, sourceUrl, if (tracksOn) offerFor(null, named) else null, named)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** OL-5: subtitle files the page / source offered with the stream (label to URL). Pure. */
    fun namedSubs(req: StreamRequest): List<Pair<String, String>> =
        req.subtitles.map { (it.label.ifBlank { it.language.orEmpty() }.ifBlank { "Subtitles" }) to it.url }.distinctBy { it.second }

    /** OL-5: what the popup offers for [master] (may be null) plus the page's own subtitles. Pure. */
    fun offerFor(master: DownloadTracks.Master?, named: List<Pair<String, String>>): DownloadTracks.Offer? {
        val o = master?.let(DownloadTracks::offerOf) ?: DownloadTracks.Offer()
        return o.copy(subtitles = (o.subtitles + named.map { it.first }).distinct()).takeIf { it.hasChoices }
    }

    private fun probe(context: Context, req: StreamRequest, sourceUrl: String): DownloadTracks.Master? {
        val link = StreamDownload.linkFor(req, sourceUrl)
        val text = HlsDownloader(
            // The blocking call itself gives up after 6 s, so the popup never waits longer.
            dev.asfakul.yumeiro.network.NetworkClient.get(context).newBuilder().callTimeout(6, java.util.concurrent.TimeUnit.SECONDS).build(),
            OnlineDownloads.headersFor(link),
        ).text(req.url)
        return DownloadTracks.parseMaster(text, req.url)
    }

    private fun show(context: Context, req: StreamRequest, sourceUrl: String, offer: DownloadTracks.Offer?, named: List<Pair<String, String>>) {
        fun enqueue(keep: Boolean, pick: DownloadTracks.Pick?) = when (val o = StreamDownload.enqueue(context, req, sourceUrl, keep, pick, named)) {
            is StreamDownload.Outcome.Refused -> o.message
            is StreamDownload.Outcome.Queued -> null
        }
        DownloadPrompt.ask(context, DownloadPrompt.Request(
            type = DownloadPrompt.Type.LINK,
            title = StreamDownload.titleFor(req, sourceUrl),
            what = "this video", count = 1,
            folder = DownloadGroups.LOOSE,
            quality = qualityText(req),
            defaultKeep = DownloadSettings.keepByDefault.value,
            offer = offer,
            // OWN-18: size for the picked quality, subtitles and format.
            facts = listOfNotNull(
                when (req.resolvedFormat()) { StreamFormat.HLS -> "HLS stream"; StreamFormat.DASH -> "DASH stream"; else -> "Video file" },
                named.size.takeIf { it > 0 }?.let { "$it subtitle ${if (it == 1) "file" else "files"}" },
            ),
            sizeFor = { h ->
                val link = StreamDownload.linkFor(req, sourceUrl)
                dev.asfakul.yumeiro.online.download.DownloadSize.probe(dev.asfakul.yumeiro.network.NetworkClient.get(context), req.url,
                    OnlineDownloads.headersFor(link), req.resolvedFormat() == StreamFormat.HLS, req.resolvedFormat() == StreamFormat.DASH, h)
            },
            startWith = if (offer != null) { keep, pick -> enqueue(keep, pick) } else null,
        ) { keep -> enqueue(keep, null) })
    }
}
