/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import dev.asfakul.yumeiro.stream.local.ByteSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.io.RandomAccessFile

/**
 * F-02 / ADR-052: a Telegram video as a [ByteSource] for [dev.asfakul.yumeiro.stream.local.LocalStreamServer].
 * A read asks TDLib (`downloadFile`, priority 32, from the read position, no limit) only when the position is outside
 * the part TDLib is already fetching, then sleeps on the client's file lock until an `updateFile` brings bytes
 * (event-driven, no timer polling). The file TDLib writes is read directly; nothing is copied.
 * Runs on the local server's threads, never on the main thread.
 */
class TdFileSource(
    private val client: TdClient,
    private val fileId: Int,
    override val length: Long,
    override val mimeType: String,
    private val waitMs: Long = 25_000,
    /** A read this far ahead of what TDLib has is still "on the way": no new request (avoids restart thrash). */
    private val aheadBytes: Long = 4L * 1024 * 1024,
) : ByteSource {
    private val fileGuard = Any()
    private var raf: RandomAccessFile? = null
    private var rafPath = ""
    @Volatile private var askedAt = Long.MIN_VALUE

    // Not synchronized as a whole: a player that left a connection waiting must not block its next one.
    override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int {
        if (position >= length) return -1
        val deadline = System.currentTimeMillis() + waitMs
        while (true) {
            if (client.closed) throw IOException("Telegram is closed")
            val f = client.file(fileId)
            val ready = if (f != null) readyFrom(f, position) else 0L
            if (ready > 0) {
                val path = f!!.obj("local")?.str("path").orEmpty()
                if (path.isNotEmpty()) return readFile(path, position, buffer, offset, minOf(count.toLong(), ready).toInt())
            }
            if (f == null || !onTheWay(f, position)) request(position)
            if (System.currentTimeMillis() > deadline) throw IOException("Telegram didn't send this part in time")
            synchronized(client.fileLock) { client.fileLock.wait(400) }
        }
    }

    private fun request(position: Long) {
        askedAt = position
        val f = runCatching {
            runBlocking {
                client.call("downloadFile") {
                    put("file_id", fileId); put("priority", 32); put("offset", position); put("limit", 0); put("synchronous", false)
                }
            }
        }.getOrElse { throw IOException(TgAuth.errorText(it)) }
        client.remember(f)
    }

    private fun onTheWay(f: JsonObject, position: Long): Boolean {
        val local = f.obj("local") ?: return false
        if (!local.bool("is_downloading_active")) return false
        val start = local.long("download_offset")
        val end = start + local.long("downloaded_prefix_size")
        return askedAt != Long.MIN_VALUE && position >= start && position <= end + aheadBytes
    }

    private fun readFile(path: String, position: Long, buffer: ByteArray, offset: Int, count: Int): Int = synchronized(fileGuard) {
        if (raf == null || rafPath != path) {
            runCatching { raf?.close() }
            raf = RandomAccessFile(path, "r"); rafPath = path
        }
        val r = raf!!
        r.seek(position)
        val n = r.read(buffer, offset, count)
        if (n < 0) throw IOException("Telegram's file is shorter than expected")
        n
    }

    override fun release(position: Long) {
        // Keep the file open for the next request of the same player; it closes with the source.
    }

    fun close() {
        synchronized(fileGuard) { runCatching { raf?.close() }; raf = null }
        runCatching { client.send("cancelDownloadFile") { put("file_id", fileId); put("only_if_pending", false) } }
    }

    companion object {
        /** How many bytes from [position] are on disk, from TDLib's file object alone. */
        fun readyFrom(f: JsonObject, position: Long): Long {
            val local = f.obj("local") ?: return 0L
            val size = f.long("size").takeIf { it > 0 } ?: f.long("expected_size")
            if (local.bool("is_downloading_completed")) return (size - position).coerceAtLeast(0L)
            val start = local.long("download_offset")
            val end = start + local.long("downloaded_prefix_size")
            return if (position in start until end) end - position else 0L
        }
    }
}
