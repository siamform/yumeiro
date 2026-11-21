/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream.torrent

import java.io.File

/**
 * B-31 / ADR-053: what the stream code needs from a torrent session. [LtCore] is libtorrent; unit tests use a fake.
 * Every call is cheap and thread-safe; [signal] is notified (notifyAll) whenever a piece finishes, metadata arrives,
 * a torrent is added or fails, so readers sleep on it instead of polling.
 */
interface TorrentCore {
    val signal: Object
    /** Adds a magnet into `root/<hash>` (no-op when the hash is already in the session); returns the v1 hex info-hash. */
    fun addMagnet(magnet: String, root: File): String
    /** Adds .torrent bytes into `root/<hash>`; returns the hex info-hash. */
    fun addTorrentFile(bytes: ByteArray, root: File): String
    /** Adds from saved resume data (it holds the info dictionary, so no metadata wait and no re-check); null if unusable. */
    fun addResume(resume: ByteArray, root: File): String?
    fun meta(hash: String): TorrentMeta?
    fun have(hash: String, piece: Int): Boolean
    fun setDeadlines(hash: String, deadlines: List<Pair<Int, Int>>)
    /** Only [fileIndex] downloads; the other files of a pack are skipped. */
    fun onlyFile(hash: String, fileIndex: Int, fileCount: Int)
    fun savePath(hash: String): String?
    fun error(hash: String): String?
    /** Peers connected and payload download rate (bytes/s), for the "Connecting to N peers" line. */
    fun stats(hash: String): Pair<Int, Int>
    /** Asks for resume data; [LtCore] writes it to [to] when libtorrent answers. */
    fun saveResume(hash: String, to: File)
    fun pause(hash: String)
    fun resume(hash: String)
}
