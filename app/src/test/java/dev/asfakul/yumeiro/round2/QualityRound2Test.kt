/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.round2

import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.download.YtDownloads
import dev.asfakul.yumeiro.player.QualityEngine
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.youtube.YouTube
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Round 2, order 2: one quality list for player and downloader; never a one-option selector. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QualityRound2Test {
    @Test fun engineLevelsForSingleRendition() {
        assertEquals(listOf(1080, 720, 480, 360, 240, 144), QualityEngine.engineLevels(720))
        assertEquals(listOf(1440, 1080, 720, 480, 360, 240), QualityEngine.engineLevels(1080))
        assertEquals(listOf(2160, 1440, 1080, 720, 480), QualityEngine.engineLevels(2160))
        assertEquals(listOf(480, 360, 240, 144), QualityEngine.engineLevels(360))
    }

    @Test fun singleStreamNeverShowsOneOption() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val plain = StreamRequest(url = "https://example.com/v.mp4", title = "x")
        val alts = QualityEngine.alternatives(ctx, plain, 720)
        assertTrue(alts.size >= 4)
        assertEquals(1, alts.count { it.current })
        assertEquals("Original from the source", alts.first { it.current }.note)
        assertTrue(alts.any { it.upscale && it.engineHeight == 1080 })
        // Local video (no request) gets the same levels.
        assertTrue(QualityEngine.alternatives(ctx, null, 1080).size >= 4)
        // Picking an engine level moves "current" to it.
        val picked = QualityEngine.alternatives(ctx, plain, 720, engineHeight = 480)
        assertEquals(480, picked.single { it.current }.height)
    }

    @Test fun youTubeRecognisedWithoutTag() {
        val r = StreamRequest(url = "https://rr1---sn.googlevideo.com/videoplayback?x=1", title = "x", pageUrl = "https://www.youtube.com/watch?v=aqz-KE-bpKQ")
        assertEquals("aqz-KE-bpKQ", QualityEngine.youTubeId(r))
    }

    /** Live: the player list for a YouTube video equals the download sheet's heights. */
    @Test fun youTubePlayerMatchesDownloader() = runBlocking {
        assumeTrue(System.getProperty("yumeiro.live") == "true")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val id = "aqz-KE-bpKQ" // Big Buck Bunny 60fps 4K, (c) Blender Foundation, CC BY 3.0
        val dl = YtDownloads.options(YouTube.info(ctx, id, false)).filter { !it.audioOnly }.map { QualityEngine.snap(it.height) }.distinct().sortedDescending()
        val r = StreamRequest(url = "https://www.youtube.com/watch?v=$id", title = "Big Buck Bunny")
        val player = QualityEngine.alternatives(ctx, r, 720).map { it.height }
        File("build/quality-live").mkdirs()
        File("build/quality-live/youtube.txt").writeText("download sheet: $dl\nplayer: $player\n")
        assertTrue("player=$player dl=$dl", player.size >= 4)
        assertTrue("player=$player dl=$dl", player.containsAll(dl))
    }
}
