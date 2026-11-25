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
import org.libtorrent4j.alerts.PieceInfoAlert
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
    /** B-31 speed: finished blocks per partial piece, per torrent, from the last piece_info_alert. */
    private val blockMaps = ConcurrentHashMap<String, Map<Int, java.util.BitSet>>()
    /** B31-STALL-T: per piece, one packed int per block (state | peers << 4 | bytes << 12); only while tracing. */
    private val blockNotes = ConcurrentHashMap<String, Map<Int, IntArray>>()

    init {
        sm.addListener(object : AlertListener {
            override fun types(): IntArray = intArrayOf(
                AlertType.ADD_TORRENT.swig(), AlertType.METADATA_RECEIVED.swig(), AlertType.PIECE_FINISHED.swig(),
                AlertType.TORRENT_ERROR.swig(), AlertType.SAVE_RESUME_DATA.swig(), AlertType.METADATA_FAILED.swig(),
                AlertType.PIECE_INFO.swig(),
            )

            override fun alert(alert: Alert<*>) {
                val hash = runCatching { (alert as? TorrentAlert<*>)?.handle()?.infoHash()?.toHex() }.getOrNull()
                when (alert) {
                    is PieceInfoAlert -> if (hash != null) runCatching {
                        blockMaps[hash] = finishedMap(alert)
                        if (TorrentTrace.sink != null) blockNotes[hash] = notesMap(alert)
                    }
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
        // B-31 speed (device run 44877648670: first bytes 13.9 / 19.9 s, seek 5.3 s): reach peers sooner and
        // give up on slow ones sooner, so time-critical pieces go to the fast peers.
        fun int(t: org.libtorrent4j.swig.settings_pack.int_types, v: Int) { sp.setInteger(t.swigValue(), v) }
        fun bool(t: org.libtorrent4j.swig.settings_pack.bool_types, v: Boolean) { sp.setBoolean(t.swigValue(), v) }
        int(org.libtorrent4j.swig.settings_pack.int_types.connection_speed, if (lowEnd) 60 else 150)
        int(org.libtorrent4j.swig.settings_pack.int_types.torrent_connect_boost, if (lowEnd) 40 else 80)
        int(org.libtorrent4j.swig.settings_pack.int_types.peer_connect_timeout, 7)
        int(org.libtorrent4j.swig.settings_pack.int_types.request_timeout, 10)
        int(org.libtorrent4j.swig.settings_pack.int_types.piece_timeout, 10)
        bool(org.libtorrent4j.swig.settings_pack.bool_types.announce_to_all_trackers, true)
        bool(org.libtorrent4j.swig.settings_pack.bool_types.announce_to_all_tiers, true)
        bool(org.libtorrent4j.swig.settings_pack.bool_types.strict_end_game_mode, false)
        bool(org.libtorrent4j.swig.settings_pack.bool_types.smooth_connects, false)
        // B31-SEEK2 (device run 44880554878: seek 7.6 / 10.7 s, TV 12.7 s; budget 5 s): each peer's request queue
        // held ~3 s of data (default request_queue_time 3), and a seek's requests waited behind it. 1 s keeps peers
        // busy but lets a new time-critical piece reach the front sooner.
        int(org.libtorrent4j.swig.settings_pack.int_types.request_queue_time, 1)
        // B31-PEERS-1 (lowram run 45303433155: peers 18 -> 0 right after the first frame, then the seek waited 27 s
        // with 0-3 peers): a stream window wants only a few MB, so it is often all downloaded; libtorrent then calls
        // the torrent finished, sends upload-only and closes every seed connection (torrent::finished(), gated by
        // close_redundant_connections, https://github.com/arvidn/libtorrent/blob/RC_2_0/src/torrent.cpp). The next
        // seek had to find peers again. Off: the seeds stay connected; idle torrents are still paused after IDLE_MS.
        bool(org.libtorrent4j.swig.settings_pack.bool_types.close_redundant_connections, false)
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

    override fun dropDeadlines(hash: String, pieces: List<Int>) {
        val h = handle(hash) ?: return
        for (p in pieces) runCatching { if (!h.havePiece(p)) h.resetPieceDeadline(p) }
    }

    override fun streamWindow(hash: String, priorities: ByteArray) {
        val h = handle(hash) ?: return
        val levels = Priority.values()
        runCatching { h.prioritizePieces(Array(priorities.size) { levels[priorities[it].toInt().coerceIn(0, 7)] }) }
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

    override fun finishedBlocks(hash: String, piece: Int): java.util.BitSet? = blockMaps[hash]?.get(piece)

    override fun askBlocks(hash: String) { runCatching { handle(hash)?.swig()?.post_download_queue() } }

    /**
     * piece_info_alert lays the blocks of every partial piece one after another, `blocks_per_piece` slots each
     * (libtorrent torrent::get_download_queue). State 3 = finished (written to disk).
     */
    private fun finishedMap(a: PieceInfoAlert): Map<Int, java.util.BitSet> {
        val pieces = a.pieceInfo
        val blocks = a.blockData
        if (pieces.isEmpty()) return emptyMap()
        val perPiece = blocks.size / pieces.size
        val out = HashMap<Int, java.util.BitSet>(pieces.size)
        pieces.forEachIndexed { k, p ->
            val bits = java.util.BitSet(p.blocksInPiece())
            for (b in 0 until p.blocksInPiece()) {
                val at = k * perPiece + b
                if (at < blocks.size && blocks[at].state() == org.libtorrent4j.BlockInfo.BlockState.FINISHED) bits.set(b)
            }
            out[p.pieceIndex()] = bits
        }
        return out
    }

    private fun notesMap(a: PieceInfoAlert): Map<Int, IntArray> {
        val pieces = a.pieceInfo
        val blocks = a.blockData
        if (pieces.isEmpty()) return emptyMap()
        val perPiece = blocks.size / pieces.size
        val out = HashMap<Int, IntArray>(pieces.size)
        pieces.forEachIndexed { k, p ->
            out[p.pieceIndex()] = IntArray(p.blocksInPiece()) { b ->
                val i = blocks.getOrNull(k * perPiece + b) ?: return@IntArray 0
                i.state().swig().coerceIn(0, 15) or (i.numPeers().coerceIn(0, 255) shl 4) or (i.bytesProgress().coerceIn(0, 0x7FFFF) shl 12)
            }
        }
        return out
    }

    override fun blockNote(hash: String, piece: Int, block: Int): String? {
        val v = blockNotes[hash]?.get(piece)?.getOrNull(block) ?: return null
        val state = when (v and 15) { 0 -> "none"; 1 -> "requested"; 2 -> "writing"; 3 -> "finished"; else -> "unknown" }
        return "$state x${(v shr 4) and 255} ${v ushr 12}B"
    }

    override fun pause(hash: String) { handle(hash)?.pause() }
    override fun resume(hash: String) { handle(hash)?.resume() }
}
