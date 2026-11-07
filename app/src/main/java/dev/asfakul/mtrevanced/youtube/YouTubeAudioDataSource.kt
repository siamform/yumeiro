/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.youtube

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import dev.asfakul.mtrevanced.network.NetworkClient

/**
 * Music-player data source that understands `mtyt://v/<videoId>` (YouTube Music tracks).
 *
 * The stream URL is resolved lazily on ExoPlayer's loader thread — right when the track starts —
 * so queued songs never hold expired links (the ArchiveTune / AuraMusic approach). If googlevideo
 * answers 403 (expired / client-bound URL), the link is re-extracted once and the read retried
 * transparently. Every other scheme (content://, file://, http) goes to the normal data source.
 */
@OptIn(UnstableApi::class)
class YouTubeAudioDataSource private constructor(
    private val context: Context,
    private val upstream: DataSource,
) : DataSource {

    private var opened: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val id = videoIdOf(dataSpec.uri) ?: return upstream.open(dataSpec).also { opened = upstream }
        YouTube.init(context)
        return try {
            openResolved(dataSpec, id, force = false)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode !in RETRY_CODES) throw e
            runCatching { upstream.close() }
            openResolved(dataSpec, id, force = true) // fresh URL, once
        }
    }

    private fun openResolved(spec: DataSpec, id: String, force: Boolean): Long {
        val (url, headers) = YouTubeStreams.audioBlocking(id, force)
        val resolved = spec.buildUpon()
            .setUri(Uri.parse(url))
            .setHttpRequestHeaders(spec.httpRequestHeaders + headers)
            .build()
        opened = upstream
        return upstream.open(resolved)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = (opened ?: upstream).read(buffer, offset, length)

    override fun getUri(): Uri? = (opened ?: upstream).uri

    override fun getResponseHeaders(): Map<String, List<String>> = (opened ?: upstream).responseHeaders

    override fun close() {
        try { (opened ?: upstream).close() } finally { opened = null }
    }

    class Factory(private val context: Context) : DataSource.Factory {
        private val upstream: DataSource.Factory by lazy {
            DefaultDataSource.Factory(
                context.applicationContext,
                OkHttpDataSource.Factory(NetworkClient.get(context.applicationContext)).setUserAgent(NewPipeDownloader.USER_AGENT),
            )
        }

        override fun createDataSource(): DataSource = YouTubeAudioDataSource(context.applicationContext, upstream.createDataSource())
    }

    companion object {
        const val SCHEME = "mtyt"
        private val RETRY_CODES = setOf(401, 403, 404, 410)

        /** `mtyt://v/<id>` — the id lives in the path so its case is never normalised. */
        fun uriFor(videoId: String): String = "$SCHEME://v/$videoId"

        fun videoIdOf(uri: Uri?): String? = uri?.takeIf { it.scheme == SCHEME }?.lastPathSegment?.takeIf { it.isNotBlank() }
    }
}
