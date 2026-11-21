package dev.asfakul.yumeiro.stream.torrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TorrentCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun evictsOldestButNeverThePlayingTorrent() {
        val root = tmp.newFolder()
        fun torrent(name: String, bytes: Int, age: Long) = File(root, name).apply {
            mkdirs(); File(this, "f").writeBytes(ByteArray(bytes)); setLastModified(System.currentTimeMillis() - age)
            File(root, "$name.resume").writeBytes(ByteArray(1))
        }
        val old = torrent("old", 4000, 300_000)
        val mid = torrent("mid", 4000, 200_000)
        val playing = torrent("playing", 4000, 900_000) // oldest on disk, but playing now
        TorrentStreams.evict(root, keep = "playing", budget = 9000)
        assertFalse(old.exists()); assertFalse(File(root, "old.resume").exists())
        assertTrue(mid.exists()); assertTrue(playing.exists())
    }
}
