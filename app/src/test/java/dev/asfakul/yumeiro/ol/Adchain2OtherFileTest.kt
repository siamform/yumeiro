/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.screens.downloads.StreamDownloadAsk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADCHAIN-2: a pack a page hands over is offered as a file, not as "this video" (field probe 45411197165). */
// StreamDownloadAsk loads Android classes when it starts, so this runs on Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Adchain2OtherFileTest {
    private fun r(url: String) = StreamRequest(url = url)

    @Test fun aZipFromThePageIsAFile() {
        assertTrue(StreamDownloadAsk.isOtherFile(r("http://10.0.2.2:8765/attach.zip"), "probe-pack.zip"))
        assertTrue("the file name decides when the link has none", StreamDownloadAsk.isOtherFile(r("https://x.org/get?id=9"), "Season 1.rar"))
        assertTrue(StreamDownloadAsk.isOtherFile(r("https://x.org/app.apk"), "download"))
    }

    @Test fun videosAndOrdinaryTitlesStayVideos() {
        assertFalse(StreamDownloadAsk.isOtherFile(r("http://10.0.2.2:8765/attach"), "Probe clip.mp4"))
        assertFalse("a dotted title is not an extension", StreamDownloadAsk.isOtherFile(r("https://cdn.x/v/9"), "Dr. Stone S01.E02"))
        assertFalse(StreamDownloadAsk.isOtherFile(r("https://cdn.x/master.m3u8"), "Episode 3"))
        assertFalse(StreamDownloadAsk.isOtherFile(r("https://cdn.x/a"), "download"))
    }
}
