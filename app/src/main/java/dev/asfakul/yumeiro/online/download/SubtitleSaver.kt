/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * OL-5: saves the subtitles picked in the download popup next to the finished video
 * ("Ep 1.mp4" + "Ep 1.English.vtt"), so they play offline. HLS `EXT-X-MEDIA TYPE=SUBTITLES`
 * playlists are joined into one WebVTT file; extension subtitles are saved as they are.
 * Best effort: a subtitle that can't be fetched never fails the video.
 */
object SubtitleSaver {
    private const val TAG = "SubtitleSaver"
    private const val MAX_SEGMENTS = 3000
    private const val MAX_BYTES = 8L * 1024 * 1024

    suspend fun save(client: OkHttpClient, item: OnlineDownloads.Item, video: File): List<File> = withContext(Dispatchers.IO) {
        val out = ArrayList<File>()
        if (item.subsPref == DownloadTracks.SUBS_NONE) return@withContext out
        val dir = video.parentFile ?: return@withContext out
        val used = HashSet<String>()
        fun target(label: String, ext: String): File {
            var name = DownloadTracks.sidecarName(video.name, label, ext)
            var k = 2
            while (!used.add(name)) name = DownloadTracks.sidecarName(video.name, "$label $k", ext).also { k++ }
            return File(dir, name)
        }
        if (item.isHls) runCatching {
            val hls = HlsDownloader(client, item.headers)
            val master = DownloadTracks.parseMaster(hls.text(item.url), item.url)
            if (master != null) for (r in DownloadTracks.pickSubtitles(master.subtitles, item.subsPref).take(DownloadTracks.MAX_SUBS)) runCatching {
                val uri = r.uri ?: return@runCatching
                val t = hls.text(uri)
                val body = if (t.trimStart('\uFEFF').trimStart().startsWith("#EXTM3U")) {
                    DownloadTracks.joinVtt(HlsDownloader.parseMedia(t, uri).take(MAX_SEGMENTS).map { hls.text(it.url) })
                } else t
                if (body.isNotBlank()) target(r.label, "vtt").also { it.writeText(body); out += it }
            }.onFailure { Log.w(TAG, "hls subtitle ${r.label}", it) }
        }.onFailure { Log.w(TAG, "hls master", it) }
        for ((label, url) in DownloadTracks.pickNamed(item.subs, item.subsPref)) runCatching {
            val b = Request.Builder().url(url)
            item.headers.forEach { (k, v) -> runCatching { b.header(k, v) } }
            val bytes = client.newCall(b.build()).execute().use { r ->
                if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                val len = r.body.contentLength()
                if (len > MAX_BYTES) throw java.io.IOException("subtitle too large")
                r.body.bytes()
            }
            if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@runCatching
            val head = String(bytes, 0, minOf(bytes.size, 64), Charsets.UTF_8)
            target(label, DownloadTracks.subtitleExt(url, head)).also { it.writeBytes(bytes); out += it }
        }.onFailure { Log.w(TAG, "subtitle $label", it) }
        out
    }
}
