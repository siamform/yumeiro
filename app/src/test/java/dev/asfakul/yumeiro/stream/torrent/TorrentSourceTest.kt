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
    override fun setDeadlines(hash: String, deadlines: List<Pair<Int, Int>>) { this.deadlines += deadlines }
    override fun onlyFile(hash: String, fileIndex: Int, fileCount: Int) {}
    override fun savePath(hash: String) = dir.absolutePath
    override fun error(hash: String) = err
    override fun stats(hash: String) = 0 to 0
    override fun saveResume(hash: String, to: File) {}
    override fun pause(hash: String) { paused = true }
    override fun resume(hash: String) { paused = false }
    val dropped: MutableList<Int> = Collections.synchronizedList(ArrayList())
    override fun dropDeadlines(hash: String, pieces: List<Int>) { dropped += pieces }
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
