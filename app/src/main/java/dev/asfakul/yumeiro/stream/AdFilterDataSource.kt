/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayOutputStream

/**
 * Wraps the player's data source and runs HLS media playlists through [HlsAdFilter] before the
 * HLS parser sees them. Everything else (segments, files, keys) streams through untouched.
 */
@OptIn(UnstableApi::class)
class AdFilterDataSource(private val upstream: DataSource, private val owner: Factory? = null) : DataSource {
    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        /**
         * Off while playing a stream whose audio is a separate playlist (HLS audio renditions or an
         * extension's extra audio tracks). Each playlist is filtered on its own, so cutting "ads"
         * from the video but not identically from the audio shifted them apart: video without
         * sound, and a black, frozen picture after switching the audio language.
         */
        @Volatile var disabled = false
        override fun createDataSource(): DataSource = AdFilterDataSource(upstream.createDataSource(), this)
    }

    private var buffer: ByteArray? = null
    private var readPos = 0
    private var bufferedUri: Uri? = null
    private var bufferedHeaders: Map<String, List<String>> = emptyMap()

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val length = upstream.open(dataSpec)
        if (dataSpec.position != 0L || !looksLikePlaylist(dataSpec.uri, upstream.responseHeaders)) return length
        val bytes = readAll(length) ?: return length
        bufferedUri = upstream.uri
        bufferedHeaders = upstream.responseHeaders
        upstream.close()
        val text = bytes.toString(Charsets.UTF_8)
        if (hasSeparateAudio(text)) owner?.disabled = true
        if (owner?.disabled == true) { buffer = bytes; readPos = 0; return bytes.size.toLong() }
        val cleaned = HlsAdFilter.clean(text, (bufferedUri ?: dataSpec.uri).toString())
        if (cleaned.removedSegments > 0) Log.i(TAG, "Removed ${cleaned.removedSegments} ad segment(s), ${cleaned.removedSeconds.toInt()}s")
        buffer = if (cleaned.removedSegments > 0) cleaned.text.toByteArray(Charsets.UTF_8) else bytes
        readPos = 0
        return buffer!!.size.toLong()
    }

    /** Reads the whole (small) playlist. Gives up on anything that isn't small text. */
    private fun readAll(length: Long): ByteArray? {
        if (length != C.LENGTH_UNSET.toLong() && length > MAX_BYTES) return null
        val out = ByteArrayOutputStream(if (length > 0) length.toInt() else 16 * 1024)
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val n = upstream.read(chunk, 0, chunk.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(chunk, 0, n)
            if (out.size() > MAX_BYTES) throw java.io.IOException("Playlist too large")
        }
        return out.toByteArray()
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        val b = buffer ?: return upstream.read(target, offset, length)
        if (length == 0) return 0
        if (readPos >= b.size) return C.RESULT_END_OF_INPUT
        val n = minOf(length, b.size - readPos)
        System.arraycopy(b, readPos, target, offset, n)
        readPos += n
        return n
    }

    override fun getUri(): Uri? = if (buffer != null) bufferedUri else upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = if (buffer != null) bufferedHeaders else upstream.responseHeaders

    override fun close() {
        if (buffer != null) { buffer = null; bufferedUri = null; bufferedHeaders = emptyMap() } else upstream.close()
    }

    companion object {
        private const val TAG = "AdFilter"
        private const val MAX_BYTES = 4L * 1024 * 1024

        private val SEPARATE_AUDIO = Regex("#EXT-X-MEDIA:[^\\n]*TYPE=AUDIO[^\\n]*URI=\"", RegexOption.IGNORE_CASE)

        /** A master playlist whose audio comes from its own playlists. Pure → unit tested. */
        fun hasSeparateAudio(text: String): Boolean = "#EXT-X-STREAM-INF" in text && SEPARATE_AUDIO.containsMatchIn(text)

        fun looksLikePlaylist(uri: Uri, headers: Map<String, List<String>>): Boolean {
            val path = uri.path.orEmpty().lowercase()
            if (path.endsWith(".m3u8") || path.endsWith(".m3u")) return true
            val type = headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value?.firstOrNull()?.lowercase().orEmpty()
            return "mpegurl" in type
        }
    }
}
