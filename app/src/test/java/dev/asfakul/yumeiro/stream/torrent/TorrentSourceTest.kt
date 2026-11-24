package dev.asfakul.yumeiro.stream.torrent

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.Collections

/** A fake session: pieces "arrive" when the test calls [arrive]; deadlines are recorded. */
class FakeCore(private val dir: File, private val meta: TorrentMeta) : TorrentCore {
    override val signal = Object()
    val have: MutableSet<Int> = Collections.synchronizedSet(HashSet())
    val deadlines: MutableList<Pair<Int, Int>> = Collections.synchronizedList(ArrayList())
    var err: String? = null
    var paused = false
    fun arrive(p: Int) { have += p; synchronized(signal) { signal.notifyAll() } }
    override fun addMagnet(magnet: String, root: File) = meta.hash
    override fun addTorrentFile(bytes: ByteArray, root: File) = meta.hash
    override fun addResume(resume: ByteArray, root: File): String? = meta.hash
    override fun meta(hash: String) = meta
    override fun have(hash: String, piece: Int) = piece in have
    override fun setDeadlines(hash: String, deadlines: List<Pair<Int, Int>>) { this.deadlines += deadlines; events += "deadlines" }
    override fun onlyFile(hash: String, fileIndex: Int, fileCount: Int) {}
    override fun savePath(hash: String) = dir.absolutePath
    override fun error(hash: String) = err
    override fun stats(hash: String) = 0 to 0
    override fun saveResume(hash: String, to: File) {}
    override fun pause(hash: String) { paused = true }
    override fun resume(hash: String) { paused = false }
    val dropped: MutableList<Int> = Collections.synchronizedList(ArrayList())
    override fun dropDeadlines(hash: String, pieces: List<Int>) { dropped += pieces; events += "drop" }
    val windows: MutableList<ByteArray> = Collections.synchronizedList(ArrayList())
    val events: MutableList<String> = Collections.synchronizedList(ArrayList())
    override fun streamWindow(hash: String, priorities: ByteArray) { windows += priorities; events += "window" }
    val blocks: MutableMap<Int, java.util.BitSet> = java.util.concurrent.ConcurrentHashMap()
    override fun finishedBlocks(hash: String, piece: Int): java.util.BitSet? = blocks[piece]
    fun blocksIn(p: Int, vararg b: Int) { blocks[p] = java.util.BitSet().apply { b.forEach { set(it) } }; synchronized(signal) { signal.notifyAll() } }
}

class TorrentSourceTest {
    @get:Rule val tmp = TemporaryFolder()
    private val pl = 16 * 1024
    private val data = ByteArray(5 * pl + 100) { (it % 253).toByte() }

    private fun setup(): Triple<FakeCore, TorrentSource, TorrentFile> {
        val dir = tmp.newFolder()
        // File 1 starts mid-piece 0 (offset 1000), like a pack whose first file is small.
        val f = TorrentFile(1, "Show/ep.mkv", data.size.toLong(), 1000)
        File(dir, "Show").mkdirs(); File(dir, f.path).writeBytes(data)
        val meta = TorrentMeta("ab".repeat(20), "Show", pl, 7, listOf(TorrentFile(0, "Show/a.nfo", 1000, 0), f))
        val core = FakeCore(dir, meta)
        return Triple(core, TorrentSource(core, meta, f, readAhead = 2L * pl, waitMs = 3_000), f)
    }

    @Test fun readWaitsForThePieceThenStopsAtItsEnd() {
        val (core, src, _) = setup()
        val buf = ByteArray(64 * 1024)
        val t = Thread { Thread.sleep(150); core.arrive(0) }.apply { start() }
        val n = src.read(0, buf, 0, buf.size)
        t.join()
        assertEquals(pl - 1000, n) // piece 0 holds file bytes 0 until pl - 1000
        assertArrayEquals(data.copyOfRange(0, n), buf.copyOfRange(0, n))
        assertTrue(core.deadlines.any { it == (0 to 0) })
    }

    @Test fun primeAsksForHeadAndTailAndSeekMovesTheWindow() {
        val (core, src, f) = setup()
        src.prime()
        val last = TorrentPlan.lastPiece(f, pl)
        assertEquals(listOf(0 to 0), core.deadlines.toList()) // B31-SEEK5: the first piece alone until it has bytes
        core.arrive(0); src.read(0, ByteArray(10), 0, 10)
        assertTrue(core.deadlines.map { it.first }.containsAll(listOf(0, last)))
        core.deadlines.clear()
        core.arrive(4)
        val pos = 4L * pl - 1000 + 10 // inside piece 4
        val buf = ByteArray(100)
        assertEquals(100, src.read(pos, buf, 0, 100))
        assertArrayEquals(data.copyOfRange(pos.toInt(), pos.toInt() + 100), buf)
        assertEquals(4 to 0, core.deadlines.first()) // the seek target is the most urgent
    }

    /** B-31 seek (device run 44879268223): the abandoned window must lose its overdue deadlines. */
    @Test fun seekDropsTheOldWindowDeadlinesButKeepsTheNewOne() {
        val (core, src, _) = setup()
        src.prime() // window 0..3
        core.arrive(4)
        src.read(4L * pl - 1000 + 10, ByteArray(10), 0, 10) // seek into piece 4
        assertEquals(listOf(0, 1, 2, 3), core.dropped.sorted())
        core.dropped.clear()
        core.arrive(5)
        src.read(5L * pl - 1000, ByteArray(10), 0, 10) // still inside the window: nothing dropped
        assertTrue(core.dropped.isEmpty())
    }

    /** B31-SEEK2 (device run 44880554878): after a seek the target piece is alone at the front of the queue. */
    @Test fun seekPutsTheTargetAloneAtTheFront() {
        val (core, src, _) = setup()
        src.prime()
        core.arrive(0); src.read(0, ByteArray(10), 0, 10)
        assertEquals(1 to 120, core.deadlines.first { it.first == 1 }) // playback start: no gap
        core.deadlines.clear()
        core.arrive(4)
        src.read(4L * pl - 1000 + 10, ByteArray(10), 0, 10)
        assertEquals(4 to 0, core.deadlines[0])
        assertEquals(5 to TorrentPlan.SEEK_GAP_MS + 120, core.deadlines[1])
    }

    /**
     * B31-SEEK3 (device 44883372732 / 44883375160): after a seek no block of the target piece came for 7 s / 17 s
     * while the swarm sent 2 MB/s elsewhere. Only the new window may be downloaded, written before its deadlines.
     */
    @Test fun seekDownloadsOnlyTheNewWindow() {
        val (core, src, _) = setup()
        src.prime()
        core.arrive(4)
        core.events.clear(); core.windows.clear()
        src.read(4L * pl - 1000 + 10, ByteArray(10), 0, 10)
        val p = core.windows.single()
        assertEquals(7, p[4].toInt()); assertEquals(6, p[5].toInt())
        assertEquals(0, p[6].toInt()) // not this file
        assertEquals(2, p[1].toInt()) // container head / tail kept low
        assertEquals(listOf("drop", "window", "deadlines"), core.events)
    }

    /**
     * B31-SEEK4 (TV device run 44884971496, seek 10370 ms): the tail deadlines set at open were never dropped, so they
     * stayed ahead of the seek target in libtorrent's deadline order. A seek must drop them too.
     */
    @Test fun seekDropsTheTailDeadlinesFromOpen() {
        val dir = tmp.newFolder()
        val mb = 1 shl 20
        val f = TorrentFile(0, "Movie.mkv", 20L * mb, 0)
        java.io.RandomAccessFile(File(dir, f.path), "rw").use { it.setLength(f.size) }
        val meta = TorrentMeta("cd".repeat(20), "Movie", mb, 20, listOf(f))
        val core = FakeCore(dir, meta)
        val src = TorrentSource(core, meta, f, readAhead = 4L * mb, waitMs = 3_000)
        src.prime() // head window 0..3, tail 18 / 19 with a deadline once the first piece has bytes
        core.arrive(0); src.read(0, ByteArray(10), 0, 10)
        assertTrue(core.deadlines.map { it.first }.containsAll(listOf(18, 19)))
        core.arrive(10)
        src.read(10L * mb + 5, ByteArray(10), 0, 10) // seek to the middle
        assertTrue("tail deadlines kept: ${core.dropped}", core.dropped.containsAll(listOf(18, 19)))
        assertEquals(listOf(0, 1, 2, 3, 18, 19), core.dropped.sorted())
        // A later read at the tail (MKV cues) gets its deadline back.
        core.deadlines.clear(); core.arrive(19)
        src.read(19L * mb + 5, ByteArray(10), 0, 10)
        assertEquals(19 to 0, core.deadlines.first())
    }

    /**
     * B31-SEEK5 (lowram run 44884969625: no block of the first piece for 16 s while 0.4-0.9 MB/s went elsewhere):
     * while the player waits for a piece with no bytes, that piece is the only one wanted and the only deadline;
     * the read-ahead window and the tail follow once it has bytes. Same at a seek.
     */
    @Test fun targetPieceDownloadsAloneUntilItHasBytes() {
        val dir = tmp.newFolder()
        val mb = 1 shl 20
        val f = TorrentFile(0, "Movie.mkv", 20L * mb, 0)
        java.io.RandomAccessFile(File(dir, f.path), "rw").use { it.setLength(f.size) }
        val meta = TorrentMeta("ef".repeat(20), "Movie", mb, 20, listOf(f))
        val core = FakeCore(dir, meta)
        val src = TorrentSource(core, meta, f, readAhead = 4L * mb, waitMs = 3_000)
        src.prime()
        val w0 = core.windows.last()
        assertEquals(7, w0[0].toInt())
        assertTrue("only the first piece wanted: ${w0.toList()}", (1 until 20).all { w0[it].toInt() == 0 })
        assertEquals(listOf(0 to 0), core.deadlines.toList())
        core.arrive(0); src.read(0, ByteArray(10), 0, 10)
        val w1 = core.windows.last()
        assertEquals(6, w1[1].toInt()); assertEquals(5, w1[3].toInt()); assertEquals(2, w1[19].toInt())
        assertTrue(core.deadlines.containsAll(listOf(1 to 120, 19 to 400)))
        // Seek to piece 10: while the read waits, only piece 10.
        core.deadlines.clear()
        var during: ByteArray? = null; var duringDeadlines: List<Pair<Int, Int>> = emptyList()
        val t = Thread { Thread.sleep(200); during = core.windows.last(); duringDeadlines = core.deadlines.toList(); core.arrive(10) }.apply { start() }
        src.read(10L * mb + 5, ByteArray(10), 0, 10)
        t.join()
        val w2 = during!!
        assertEquals(7, w2[10].toInt())
        assertTrue("only the seek target wanted: ${w2.toList()}", (0 until 20).filter { it != 10 }.all { w2[it].toInt() == 0 })
        assertEquals(listOf(10 to 0), duringDeadlines)
        assertEquals(6, core.windows.last()[11].toInt()) // window open after the bytes came
    }

    /**
     * B31-SEEK6 (lowram run 45268091524, no playback 30 s after the seek): the seek target had 6 of 8 blocks but not
     * the one the read needed, so the held window never opened, nothing else was wanted and the peers left.
     */
    @Test fun heldWindowOpensOnAnyBlockOfTheTarget() {
        val dir = tmp.newFolder()
        val mb = 1 shl 20
        val f = TorrentFile(0, "Movie.mp4", 20L * mb, 0)
        java.io.RandomAccessFile(File(dir, f.path), "rw").use { it.setLength(f.size) }
        val meta = TorrentMeta("cd".repeat(20), "Movie", mb, 20, listOf(f))
        val core = FakeCore(dir, meta)
        val src = TorrentSource(core, meta, f, readAhead = 4L * mb, waitMs = 3_000)
        src.prime(); core.arrive(0); src.read(0, ByteArray(10), 0, 10)
        var opened: ByteArray? = null
        val t = Thread {
            Thread.sleep(150); core.blocksIn(10, 0, 1, 2, 3, 4, 5) // block 60 of 64 (the one read) still missing
            Thread.sleep(300); opened = core.windows.last(); core.arrive(10)
        }.apply { start() }
        src.read(10L * mb + 60 * 16 * 1024, ByteArray(10), 0, 10)
        t.join()
        assertEquals("window open once the target had blocks: ${opened!!.toList()}", 6, opened!![11].toInt())
    }

    /**
     * B31-SEEK6 (api36 run 45268091524, seek 13.6 s): all ~500 pieces of the 64 MB read-ahead had deadlines, so each
     * was at top priority. Only the next few pieces get deadlines; the rest of the window is wanted without one, and
     * the deadlines slide on as playback reads past the middle of them.
     */
    @Test fun onlyTheNextPiecesHaveDeadlinesAndTheySlide() {
        val dir = tmp.newFolder()
        val pl = 128 * 1024
        val f = TorrentFile(0, "Sintel.mp4", 1000L * pl, 0)
        java.io.RandomAccessFile(File(dir, f.path), "rw").use { it.setLength(f.size) }
        val meta = TorrentMeta("ce".repeat(20), "Sintel", pl, 1000, listOf(f))
        val core = FakeCore(dir, meta)
        val src = TorrentSource(core, meta, f, readAhead = 64L shl 20, waitMs = 3_000)
        val urgent = TorrentPlan.urgentPieces(pl)
        assertEquals(16, urgent)
        src.prime(); core.arrive(0); src.read(0, ByteArray(10), 0, 10)
        val timed = core.deadlines.map { it.first }.filter { it < 900 }.distinct()
        assertEquals("timed pieces: $timed", (0 until urgent).toList(), timed.sorted())
        assertEquals(5, core.windows.last()[300].toInt()) // still wanted, no deadline
        core.deadlines.clear()
        core.arrive(9); src.read(9L * pl, ByteArray(10), 0, 10)
        assertEquals((16..24).toList(), core.deadlines.map { it.first })
        assertEquals(16 to 7 * 120, core.deadlines.first())
        // A seek drops the timed pieces (0..24) and the tail asked at open (B31-SEEK4), not the 500-piece window.
        val tail = TorrentPlan.headTail(f, pl).filter { it > 500 }
        core.dropped.clear(); core.arrive(500); src.read(500L * pl, ByteArray(10), 0, 10)
        assertEquals(((0..24) + tail).sorted(), core.dropped.distinct().sorted())
    }

    @Test fun timeoutAndErrorBecomeIOException() {
        val (core, src, _) = setup()
        val start = System.currentTimeMillis()
        try { src.read(0, ByteArray(10), 0, 10); fail() } catch (e: IOException) { assertTrue(e.message!!.contains("in time")) }
        assertTrue(System.currentTimeMillis() - start >= 2_900)
        core.err = "disk full"
        try { src.read(0, ByteArray(10), 0, 10); fail() } catch (e: IOException) { assertTrue(e.message!!.contains("disk full")) }
        assertEquals(-1, src.read(data.size.toLong(), ByteArray(10), 0, 10))
    }
}
