/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream.torrent

import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.Vectors
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.SaveResumeDataAlert
import org.libtorrent4j.alerts.TorrentAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.libtorrent
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * B-31 / ADR-053: one libtorrent session for the app's life (started on the first torrent, never per link).
 * Sized for a 2 GB phone or TV when [lowEnd]: fewer connections, a smaller disk queue. DHT, LSD and PEX are on
 * (libtorrent's defaults) so magnets without trackers still find peers; torrents are not auto-managed, so
 * libtorrent's queue never pauses the one that is playing.
 */
internal class LtCore(lowEnd: Boolean) : TorrentCore {
    override val signal = Object()
    private val sm = SessionManager(false)
    private val errors = ConcurrentHashMap<String, String>()
    private val resumeTargets = ConcurrentHashMap<String, File>()

    init {
        sm.addListener(object : AlertListener {
            override fun types(): IntArray = intArrayOf(
                AlertType.ADD_TORRENT.swig(), AlertType.METADATA_RECEIVED.swig(), AlertType.PIECE_FINISHED.swig(),
                AlertType.TORRENT_ERROR.swig(), AlertType.SAVE_RESUME_DATA.swig(), AlertType.METADATA_FAILED.swig(),
            )

            override fun alert(alert: Alert<*>) {
                val hash = runCatching { (alert as? TorrentAlert<*>)?.handle()?.infoHash()?.toHex() }.getOrNull()
                when (alert) {
                    is TorrentErrorAlert -> if (hash != null) errors[hash] = alert.error().message
                    is SaveResumeDataAlert -> if (hash != null) resumeTargets.remove(hash)?.let { f ->
                        runCatching {
                            val tmp = File(f.parentFile, f.name + ".part")
                            tmp.writeBytes(AddTorrentParams.writeResumeDataBuf(alert.params()))
                            tmp.renameTo(f)
                        }
                    }
                    else -> Unit
                }
                synchronized(signal) { signal.notifyAll() }
            }
        })
        val sp = SettingsPack()
            .connectionsLimit(if (lowEnd) 80 else 200)
            .activeDownloads(4)
            .activeSeeds(2)
            .alertQueueSize(4000)
            .maxQueuedDiskBytes(if (lowEnd) 4 shl 20 else 16 shl 20)
        sp.setEnableDht(true)
        sp.setEnableLsd(true)
        sm.start(SessionParams(sp))
    }

    private fun handle(hash: String): TorrentHandle? = runCatching { sm.find(Sha1Hash.parseHex(hash)) }.getOrNull()?.takeIf { it.isValid }

    private fun add(p: AddTorrentParams, saveDir: File) {
        saveDir.mkdirs()
        p.savePath = saveDir.absolutePath
        p.flags = p.flags.and_(TorrentFlags.AUTO_MANAGED.inv()).and_(TorrentFlags.PAUSED.inv())
        SessionHandle(sm.swig()).asyncAddTorrent(p)
    }

    override fun addMagnet(magnet: String, root: File): String {
        val hash = TorrentPlan.infoHash(magnet) ?: throw IllegalArgumentException("not a magnet")
        if (handle(hash) == null) add(AddTorrentParams.parseMagnetUri(magnet), File(root, hash))
        return hash
    }

    override fun addTorrentFile(bytes: ByteArray, root: File): String {
        val ti = TorrentInfo(bytes)
        val hash = ti.infoHash().toHex()
        if (handle(hash) == null) {
            val p = AddTorrentParams()
            p.torrentInfo = ti
            p.trackers = (p.trackers + TorrentPlan.TRACKERS).distinct()
            add(p, File(root, hash))
        }
        return hash
    }

    override fun addResume(resume: ByteArray, root: File): String? {
        val ec = error_code()
        val raw = libtorrent.read_resume_data_ex(Vectors.bytes2byte_vector(resume), ec)
        if (ec.value() != 0) return null
        val p = AddTorrentParams(raw)
        val hash = p.infoHashes.best.toHex()
        if (p.torrentInfo == null) return null
        if (handle(hash) == null) add(p, File(root, hash))
        return hash
    }

    override fun meta(hash: String): TorrentMeta? {
        val ti = handle(hash)?.torrentFile() ?: return null
        val fs = ti.files()
        val files = (0 until fs.numFiles()).map { i -> TorrentFile(i, fs.filePath(i), fs.fileSize(i), fs.fileOffset(i), fs.padFileAt(i)) }
        return TorrentMeta(hash, ti.name(), ti.pieceLength(), ti.numPieces(), files)
    }

    override fun have(hash: String, piece: Int): Boolean = handle(hash)?.havePiece(piece) == true

    override fun setDeadlines(hash: String, deadlines: List<Pair<Int, Int>>) {
        val h = handle(hash) ?: return
        for ((p, ms) in deadlines) if (!h.havePiece(p)) h.setPieceDeadline(p, ms)
    }

    override fun onlyFile(hash: String, fileIndex: Int, fileCount: Int) {
        val h = handle(hash) ?: return
        h.prioritizeFiles(Array(fileCount) { if (it == fileIndex) Priority.DEFAULT else Priority.IGNORE })
    }

    override fun savePath(hash: String): String? = handle(hash)?.savePath()
    override fun error(hash: String): String? = errors[hash]

    override fun stats(hash: String): Pair<Int, Int> {
        val s = handle(hash)?.status() ?: return 0 to 0
        return s.numPeers() to s.downloadPayloadRate()
    }

    override fun saveResume(hash: String, to: File) {
        val h = handle(hash) ?: return
        resumeTargets[hash] = to
        h.saveResumeData(TorrentHandle.SAVE_INFO_DICT)
    }

    override fun pause(hash: String) { handle(hash)?.pause() }
    override fun resume(hash: String) { handle(hash)?.resume() }
}
