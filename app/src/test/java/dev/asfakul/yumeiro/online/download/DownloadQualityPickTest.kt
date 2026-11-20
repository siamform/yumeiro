/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * NF-2b: the download prompt offers a quality for one download. The worker uses that height, and the
 * Settings value when nothing was picked (-1, every download queued before 2.1.0) or the value is unknown.
 */
class DownloadQualityPickTest {
    @Test fun pickedHeightWinsOverTheSetting() {
        assertEquals(720, DownloadSettings.heightFor(720, setting = 1080))
        assertEquals(0, DownloadSettings.heightFor(0, setting = 480))
        assertEquals(360, DownloadSettings.heightFor(360, setting = 0))
    }

    @Test fun nothingPickedKeepsTheSetting() {
        assertEquals(1080, DownloadSettings.heightFor(-1, setting = 1080))
        assertEquals(480, DownloadSettings.heightFor(999, setting = 480))
    }

    @Test fun itemsDefaultToTheSetting() {
        val i = OnlineDownloads.Item("id", "t", "api", "u", null, null, emptyMap(), "", false, emptyMap(), "")
        assertEquals(-1, i.maxHeight)
    }
}
