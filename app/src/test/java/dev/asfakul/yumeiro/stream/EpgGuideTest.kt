/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** F-15 (ADR-040): the Live TV guide reads XMLTV (url-tvg) and shows what is on now and next. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EpgGuideTest {
    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="sony.in"><display-name>Sony TV HD</display-name></channel>
  <channel id="news.bd"><display-name lang="en">Somoy News</display-name></channel>
  <programme start="20261119100000 +0000" stop="20261119110000 +0000" channel="sony.in"><title lang="en">Morning Show</title></programme>
  <programme start="20261119110000 +0000" stop="20261119120000 +0000" channel="sony.in"><title>CID</title></programme>
  <programme start="20261119120000 +0000" stop="20261119130000 +0000" channel="sony.in"><title>Movie &amp; More</title></programme>
  <programme start="20261119170000 +0600" stop="20261119180000 +0600" channel="news.bd"><title>Evening News</title></programme>
  <programme start="20261119110000 +0000" stop="20261119120000 +0000" channel="other"><title>Not in the list</title></programme>
</tv>"""

    /** 2026-11-19 11:30 UTC. */
    private val now = 1_795_087_800_000L

    @Test fun timeReadsTheOffset() {
        assertEquals(now - 30 * 60_000L, EpgGuide.time("20261119110000 +0000"))
        assertEquals(EpgGuide.time("20261119110000 +0000"), EpgGuide.time("20261119170000 +0600"))
        assertNull(EpgGuide.time("garbage"))
    }

    @Test fun parseKeepsOnlyTheWindowAndNamesChannels() {
        val g = EpgGuide.parse(xml.byteInputStream(), from = now, to = now + 3 * 60 * 60_000L)
        assertEquals(listOf("CID", "Movie & More"), g.programmes["sony.in"]!!.map { it.title }) // 10:00-11:00 has ended
        assertEquals("news.bd", g.idForName("SOMOY NEWS"))
        assertEquals(listOf("Evening News"), g.programmes["news.bd"]!!.map { it.title })
    }

    @Test fun nowAndNextByIdThenByName() {
        val g = EpgGuide.parse(xml.byteInputStream(), from = now, to = now + 3 * 60 * 60_000L)
        val (cur, next) = g.nowNext(StreamRequest(url = "https://x/1", title = "Whatever", tvgId = "sony.in"), now)
        assertEquals("CID", cur?.title); assertEquals("Movie & More", next?.title)
        val byName = g.nowNext(StreamRequest(url = "https://x/2", title = "Somoy News (Backup)"), now)
        assertEquals("Evening News", byName.first?.title)
        assertEquals(Pair(null, null), g.nowNext(StreamRequest(url = "https://x/3", title = "Unknown"), now))
    }

    @Test fun gzipIsDetectedFromTheBytes() {
        val bytes = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(xml.toByteArray()) } }.toByteArray()
        val g = EpgGuide.parse(EpgGuide.maybeGunzip(bytes.inputStream()), from = now, to = now + 3 * 60 * 60_000L)
        assertTrue(g.programmes.containsKey("sony.in"))
    }

    @Test fun m3uKeepsTvgIdAndEpgAddress() {
        val list = M3uParser.parseLines(sequenceOf(
            "#EXTM3U url-tvg=\"https://epg.example/guide.xml.gz,https://b.example/2.xml\"",
            "#EXTINF:-1 tvg-id=\"sony.in\" tvg-name=\"Sony\",Sony HD",
            "https://s/1.m3u8",
        ))
        assertEquals("sony.in", list.single().tvgId)
        assertEquals("https://epg.example/guide.xml.gz", EpgGuide.firstEpgUrl("#EXTM3U url-tvg=\"https://epg.example/guide.xml.gz,https://b.example/2.xml\""))
        assertTrue(M3uWriter.write(list).contains("tvg-id=\"sony.in\""))
    }
}
