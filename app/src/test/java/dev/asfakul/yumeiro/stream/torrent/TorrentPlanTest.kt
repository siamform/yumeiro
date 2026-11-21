package dev.asfakul.yumeiro.stream.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentPlanTest {
    private val hash = "bf2063a5e9a69f14716288fca7c2d6078bda7369"

    @Test fun infoHashFromMagnetHexBase32AndBare() {
        assertEquals(hash, TorrentPlan.infoHash("magnet:?xt=urn:btih:BF2063A5E9A69F14716288FCA7C2D6078BDA7369&dn=x"))
        assertEquals(hash, TorrentPlan.infoHash("BF2063A5E9A69F14716288FCA7C2D6078BDA7369"))
        assertEquals(hash, TorrentPlan.infoHash("urn:btih:$hash"))
        // Base32 of the same 20 bytes.
        val b32 = base32(hexBytes(hash))
        assertEquals(hash, TorrentPlan.infoHash("magnet:?xt=urn:btih:$b32"))
        assertNull(TorrentPlan.infoHash("https://example.com/a.torrent"))
        assertNull(TorrentPlan.infoHash("magnet:?dn=no-hash"))
    }

    @Test fun selectOnlyAndTrackers() {
        assertEquals(3, TorrentPlan.selectOnly("magnet:?xt=urn:btih:$hash&so=3,5-7"))
        assertNull(TorrentPlan.selectOnly("magnet:?xt=urn:btih:$hash"))
        val m = "magnet:?xt=urn:btih:$hash&tr=" + java.net.URLEncoder.encode(TorrentPlan.TRACKERS[0], "UTF-8")
        val out = TorrentPlan.withTrackers(m)
        assertEquals(1, Regex(Regex.escape(java.net.URLEncoder.encode(TorrentPlan.TRACKERS[0], "UTF-8"))).findAll(out).count())
        assertEquals(TorrentPlan.TRACKERS.size, out.split("&tr=").size - 1)
    }

    @Test fun pickFilePrefersSelectEpisodeThenLargestVideo() {
        val files = listOf(
            TorrentFile(0, "Show/Show.S01E01.mkv", 900, 0),
            TorrentFile(1, "Show/Show.S01E02.mkv", 800, 900),
            TorrentFile(2, "Show/sample.mkv", 5000, 1700),
            TorrentFile(3, "Show/.pad/1", 10, 6700, pad = true),
            TorrentFile(4, "Show/cover.jpg", 99999, 6710),
        )
        assertEquals(1, TorrentPlan.pickFile(files, selectOnly = 1)?.index)
        assertEquals(1, TorrentPlan.pickFile(files, title = "Show s1e2 1080p")?.index)
        assertEquals(0, TorrentPlan.pickFile(files)?.index) // samples skipped, largest real video
        assertEquals(0, TorrentPlan.pickFile(files, selectOnly = 3)?.index) // a pad file is never played
        assertEquals(4, TorrentPlan.pickFile(listOf(files[4]))?.index)
        assertNull(TorrentPlan.pickFile(emptyList()))
    }

    @Test fun piecesDeadlinesAndWindow() {
        val f = TorrentFile(1, "a.mp4", 10_000_000, 3_000_000)
        val pl = 1 shl 20
        assertEquals(2, TorrentPlan.firstPiece(f, pl))
        assertEquals(12, TorrentPlan.lastPiece(f, pl))
        assertEquals(2, TorrentPlan.pieceAt(f, 0, pl))
        assertEquals(3, TorrentPlan.pieceAt(f, 1_000_000, pl))
        val d = TorrentPlan.deadlines(10, 12, pl, 64L shl 20)
        assertEquals(listOf(10 to 0, 11 to 120, 12 to 240), d) // never past the file
        assertEquals(4, TorrentPlan.deadlines(2, 12, pl, 1).size) // at least 4 pieces
        val ht = TorrentPlan.headTail(f, pl)
        assertTrue(ht.containsAll(listOf(2, 3, 11, 12)))
        assertTrue(TorrentPlan.insideWindow(5, 4, 12))
        assertFalse(TorrentPlan.insideWindow(9, 4, 12)) // past half the window: send new deadlines
        assertFalse(TorrentPlan.insideWindow(3, 4, 12)) // seek back
        assertFalse(TorrentPlan.insideWindow(3, -1, -1))
        assertTrue(TorrentPlan.readAheadBytes(true) < TorrentPlan.readAheadBytes(false))
    }

    @Test fun mime() {
        assertEquals("video/x-matroska", TorrentPlan.mimeFor("x/A.MKV"))
        assertEquals("audio/flac", TorrentPlan.mimeFor("b.flac"))
    }

    private fun hexBytes(h: String) = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun base32(b: ByteArray): String {
        val a = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"; var buf = 0; var bits = 0; val sb = StringBuilder()
        for (x in b) { buf = (buf shl 8) or (x.toInt() and 0xff); bits += 8; while (bits >= 5) { bits -= 5; sb.append(a[(buf shr bits) and 31]) } }
        if (bits > 0) sb.append(a[(buf shl (5 - bits)) and 31])
        return sb.toString()
    }
}
