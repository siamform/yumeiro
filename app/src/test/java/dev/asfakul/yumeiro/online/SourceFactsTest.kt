package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceFactsTest {
    private fun link(name: String, q: Int = 1080, url: String = "https://cdn/x.mkv", hls: Boolean = false, source: String = "Src") =
        OnlineLink(name, source, url, null, q, hls, false, emptyMap())

    @Test fun readsQualityReleaseFormatSizeAudio() {
        val f = SourceFacts.of(link("Movie.2024.1080p.WEB-DL.DDP5.1.Atmos [2.4 GB]"))
        assertEquals("1080p", f.quality)
        assertEquals("WEB-DL", f.release)
        assertEquals("MKV", f.format)
        assertEquals("2.4 GB", f.size)
        assertTrue(f.audio.contains("Atmos"))
        assertTrue(f.audio.contains("5.1"))
        assertTrue(f.line.startsWith("1080p · WEB-DL · MKV · 2.4 GB"))
    }

    @Test fun hlsAndQualityFromName() {
        val f = SourceFacts.of(link("Server 2 - 720p", q = 0, url = "https://cdn/a/index.m3u8", hls = true))
        assertEquals("720p", f.quality)
        assertEquals("HLS", f.format)
        assertNull(f.size)
    }

    @Test fun fourKAndCamWarning() {
        val f = SourceFacts.of(link("Film 2160p HDCAM 700MB", q = -1))
        assertEquals("4K", f.quality)
        assertTrue(f.low)
        assertEquals("700 MB", f.size)
    }

    @Test fun sizeParsing() {
        assertEquals(1610612736L, SourceFacts.sizeBytes("x 1.5GB y"))
        assertEquals(734003200L, SourceFacts.sizeBytes("700 MB"))
        assertEquals(1288490188L, SourceFacts.sizeBytes("1,2 GiB"))
        assertNull(SourceFacts.sizeBytes("S01E02 1080p"))
        assertNull(SourceFacts.sizeBytes("0 MB"))
        assertEquals("12 GB", SourceFacts.formatSize(12L shl 30))
    }

    @Test fun multiAudio() {
        assertTrue(SourceFacts.audio("Dual Audio [Hindi-English] AAC 2.0").containsAll(listOf("Multi audio", "AAC", "2.0")))
    }
}
