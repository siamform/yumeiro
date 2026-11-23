/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.torrent

import dev.asfakul.yumeiro.stream.StreamResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TOR-3: on the emulator (probe 45260133429) the owner's hash ended on "Add your TorrServer address in Settings ›
 * Network". Torrent streaming is not on the Network page, and the built-in engine was never mentioned.
 */
class TorrentEngineOffTest {
    private val catalogTitle = dev.asfakul.yumeiro.ui.screens.settings.SettingsCatalog.Category.VIDEO_QUALITY.title

    @Test fun engineOffSaysWhereToTurnItOn() {
        val link = "magnet:?xt=urn:btih:bf2063a5e9a69f14716288fca7c2d6078bda7369"
        val r = StreamResolver.torrentEngineOff(link) as dev.asfakul.yumeiro.stream.Resolution.Handoff
        assertEquals(link, r.url)
        assertTrue(r.reason, r.reason.contains("built-in torrent engine"))
        assertTrue(r.reason, r.reason.contains("Settings › $catalogTitle › Torrent streaming"))
    }

    @Test fun noMessagePointsAtTheNetworkPage() {
        val src = java.io.File("src/main/java/dev/asfakul/yumeiro/stream/StreamResolver.kt").readText() +
            java.io.File("src/main/java/dev/asfakul/yumeiro/online/download/DownloadWorker.kt").readText()
        assertTrue("a torrent message still says Settings › Network", !Regex("TorrServer address in Settings › Network").containsMatchIn(src))
        assertTrue(src.contains("Settings › $catalogTitle › Torrent streaming"))
    }
}
