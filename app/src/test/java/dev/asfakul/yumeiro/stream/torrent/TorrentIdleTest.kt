package dev.asfakul.yumeiro.stream.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * TOR-IDLE-1: a torrent nobody read for 2 min is paused. Before, its source was closed too, so when the same
 * player read on (after a long pause) the read failed and pieces not on disk could never arrive.
 */
class TorrentIdleTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun aReadAfterTheIdlePauseResumesTheTorrent() {
        val dir = tmp.newFolder()
        val pl = 16 * 1024
        val f = TorrentFile(0, "Movie.mp4", 4L * pl, 0)
        File(dir, f.path).writeBytes(ByteArray(4 * pl) { 7 })
        val meta = TorrentMeta("de".repeat(20), "Movie", pl, 4, listOf(f))
        val core = FakeCore(dir, meta)
        val root = tmp.newFolder()
        val src = TorrentSource(core, meta, f, readAhead = 2L * pl, waitMs = 3_000,
            touched = { TorrentStreams.touch(core, meta.hash, root) })
        synchronized(TorrentStreams) { TorrentStreams.active[meta.hash] = src }
        try {
            src.prime(); core.arrive(0)
            assertEquals(10, src.read(0, ByteArray(10), 0, 10))
            TorrentStreams.idle(core, meta.hash, root) // what the 2-min timer runs
            assertTrue("idle pauses the engine", core.paused)
            core.arrive(1)
            assertEquals("the source still reads after the idle pause", 10, src.read(pl.toLong(), ByteArray(10), 0, 10))
            assertFalse("the read resumed the torrent", core.paused)
        } finally {
            synchronized(TorrentStreams) { TorrentStreams.active.remove(meta.hash) }
        }
    }
}
