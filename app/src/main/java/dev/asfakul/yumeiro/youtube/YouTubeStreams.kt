/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.asfakul.yumeiro.stream.StreamAudio
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamRequest
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File

/**
 * Turns a YouTube video into something ExoPlayer plays reliably — the NewPipe way:
 *
 *  - live → the HLS manifest;
 *  - VOD → a generated DASH manifest for the best video-only stream + the best audio stream
 *    (ranged, indexed requests: no throttling, instant seeking, up to 1080p/60 and beyond);
 *  - fallback → HLS, then the muxed 360p progressive stream.
 *
 * Every googlevideo request carries the User-Agent of the client that produced the URL
 * (Android / iOS / visionOS / web): sending a different one is the #1 cause of YouTube HTTP 403s.
 */
object YouTubeStreams {
    private const val TAG = "YouTubeStreams"

    /** Headers googlevideo expects for [url] (depends on which YouTube client minted it). */
    fun headersFor(url: String): Map<String, String> {
        val ua = runCatching {
            when {
                YoutubeParsingHelper.isAndroidStreamingUrl(url) -> YoutubeParsingHelper.getAndroidUserAgent(null)
                YoutubeParsingHelper.isIosStreamingUrl(url) -> YoutubeParsingHelper.getIosUserAgent(null)
                YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
                else -> null
            }
        }.getOrNull()
        return if (ua != null) mapOf("User-Agent" to ua)
        else mapOf(
            "User-Agent" to NewPipeDownloader.USER_AGENT,
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/",
        )
    }

    /** Original-language audio, AAC first (plays everywhere), highest bitrate. */
    fun pickAudio(info: StreamInfo): AudioStream? {
        val all = info.audioStreams.filter { it.isUrl && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.DASH) }
        val original = all.filter { runCatching { it.audioTrackType }.getOrNull().let { t -> t == null || t == AudioTrackType.ORIGINAL } }
            .ifEmpty { all }
        return original.maxWithOrNull(
            compareBy<AudioStream>({ it.format == MediaFormat.M4A }, { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate })
        )
    }

    /** Best video-only stream up to [maxHeight]; AVC (MP4) preferred for hardware decoding. */
    fun pickVideo(info: StreamInfo, maxHeight: Int): VideoStream? {
        val candidates = info.videoOnlyStreams.filter { it.isUrl && it.height in 1..maxHeight }
        return candidates.maxWithOrNull(
            compareBy<VideoStream>({ it.height }, { it.format == MediaFormat.MPEG_4 }, { it.fps })
        )
    }

    /** A playable request for video [id]. [force] re-extracts (fresh URLs after a 403/expiry). */
    /**
     * [safe] = YouTube's own fallback path after a refused / expired link was already refreshed once:
     * skip the split DASH streams (range requests are what YouTube refuses most) and use HLS or the
     * muxed progressive stream instead.
     */
    suspend fun videoRequest(context: Context, id: String, maxHeight: Int = 1080, force: Boolean = false, safe: Boolean = false): StreamRequest {
        if (force) YouTube.invalidate(id)
        val info = YouTube.info(context, id, force)
        return buildVideoRequest(context, id, info, maxHeight, safe)
    }

    private fun buildVideoRequest(context: Context, id: String, info: StreamInfo, maxHeight: Int, safe: Boolean = false): StreamRequest {
        val title = info.name.orEmpty()
        val poster = YouTube.bestImage(info.thumbnails)
        val page = YouTube.watchUrl(id)
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        val hls = info.hlsUrl?.takeIf { it.isNotBlank() }
        fun hlsRequest(url: String) = StreamRequest(
            url = url, title = title, format = StreamFormat.HLS, formatFromSource = true,
            headers = headersFor(url), logoUrl = poster, pageUrl = page, group = "YouTube",
        )
        if (live && hls != null) return hlsRequest(hls)

        if (safe) {
            if (hls != null) return hlsRequest(hls)
            info.videoStreams.filter { it.isUrl && it.height <= maxHeight }.maxByOrNull { it.height }?.let { muxed ->
                return StreamRequest(
                    url = muxed.content, title = title, format = StreamFormat.PROGRESSIVE, formatFromSource = true,
                    headers = headersFor(muxed.content), logoUrl = poster, pageUrl = page, group = "YouTube",
                )
            }
        }
        val video = pickVideo(info, maxHeight)
        val audio = pickAudio(info)
        if (video != null && audio != null) {
            val dir = File(context.cacheDir, "yt").apply { mkdirs() }
            val v = manifestFile(dir, id, video, info.duration)
            val a = manifestFile(dir, id, audio, info.duration)
            if (v != null && a != null) {
                val vh = headersFor(video.content)
                return StreamRequest(
                    url = Uri.fromFile(v).toString(), title = title, format = StreamFormat.DASH, formatFromSource = true,
                    headers = vh, logoUrl = poster, pageUrl = page, group = "YouTube",
                    audioTracks = listOf(StreamAudio(Uri.fromFile(a).toString(), label = "Original", headers = headersFor(audio.content))),
                )
            }
        }
        if (hls != null) return hlsRequest(hls)
        val muxed = info.videoStreams.filter { it.isUrl && it.height <= maxHeight }.maxByOrNull { it.height }
            ?: throw IllegalStateException("YouTube returned no playable stream for this video")
        return StreamRequest(
            url = muxed.content, title = title, format = StreamFormat.PROGRESSIVE, formatFromSource = true,
            headers = headersFor(muxed.content), logoUrl = poster, pageUrl = page, group = "YouTube",
        )
    }

    /** Writes a single-representation DASH manifest for [stream]; null if YouTube gave no index info. */
    private fun manifestFile(dir: File, id: String, stream: Stream, durationSec: Long): File? {
        val itag = stream.itagItem ?: return null
        val xml = runCatching {
            when (stream.deliveryMethod) {
                DeliveryMethod.PROGRESSIVE_HTTP -> YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(stream.content, itag, durationSec)
                DeliveryMethod.DASH -> YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(stream.content, itag, durationSec)
                else -> null
            }
        }.onFailure { Log.w(TAG, "manifest $id/${itag.id}", it) }.getOrNull() ?: return null
        return File(dir, "${id}_${itag.id}.mpd").apply { writeText(xml) }
    }

    /**
     * Fresh audio URL + headers for music playback (blocking: called on ExoPlayer loader threads).
     * [force] skips the cache after a 403 / expiry.
     */
    fun audioBlocking(id: String, force: Boolean = false): Pair<String, Map<String, String>> {
        if (force) YouTube.invalidate(id)
        val info = YouTube.infoBlocking(id, force)
        val audio = pickAudio(info)?.takeIf { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            ?: info.audioStreams.firstOrNull { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        if (audio != null) return audio.content to headersFor(audio.content)
        val hls = info.hlsUrl?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("No audio stream for $id")
        return hls to headersFor(hls)
    }
}
