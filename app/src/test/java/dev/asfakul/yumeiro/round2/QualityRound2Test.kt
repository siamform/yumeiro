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

    @Test fun youTubePlayerListIsTheDownloadList() {
        val V = dev.asfakul.yumeiro.online.download.YtDownloadOptions
        val videos = listOf(
            V.Video(137, 1080, 30, "mp4", "avc1.640028", 50_000_000, true), V.Video(248, 1080, 30, "webm", "vp9", 40_000_000, true),
            V.Video(136, 720, 30, "mp4", "avc1.4d401f", 25_000_000, true), V.Video(135, 480, 30, "mp4", "avc1.4d401e", 12_000_000, true),
            V.Video(134, 360, 30, "mp4", "avc1.4d401e", 8_000_000, true), V.Video(160, 144, 30, "mp4", "avc1.4d400c", 2_000_000, true),
            V.Video(313, 2160, 30, "webm", "vp9", 300_000_000, true), V.Video(18, 360, 30, "mp4", "avc1.42001E", 9_000_000, false),
        )
        val audios = listOf(V.Audio(140, 128_000, "m4a", "mp4a.40.2", 3_000_000), V.Audio(251, 160_000, "webm", "opus", 3_500_000))
        val dl = V.build(videos, audios, 34)
        val dlHeights = dl.filter { !it.audioOnly }.map { it.height }.distinct().sortedDescending()
        assertEquals(listOf(2160, 1080, 720, 480, 360, 144), dlHeights)
        assertEquals(dlHeights, QualityEngine.youTubeLadder(dl))
    }

    /** Live: the player list for a YouTube video equals the download sheet's heights. */
    @Test fun youTubePlayerMatchesDownloader() = runBlocking {
        assumeTrue(System.getProperty("yumeiro.live") == "true")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val id = "aqz-KE-bpKQ" // Big Buck Bunny 60fps 4K, (c) Blender Foundation, CC BY 3.0
        val info = runCatching { YouTube.info(ctx, id, false) }
        // YouTube refuses anonymous access from data-centre IPs ("confirm you're not a bot").
        assumeTrue("YouTube blocked this IP: ${info.exceptionOrNull()?.message}", info.isSuccess)
        val dl = YtDownloads.options(info.getOrThrow()).filter { !it.audioOnly }.map { QualityEngine.snap(it.height) }.distinct().sortedDescending()
        val r = StreamRequest(url = "https://www.youtube.com/watch?v=$id", title = "Big Buck Bunny")
        val player = QualityEngine.alternatives(ctx, r, 720).map { it.height }
        File("build/quality-live").mkdirs()
        File("build/quality-live/youtube.txt").writeText("download sheet: $dl\nplayer: $player\n")
        assertTrue("player=$player dl=$dl", player.size >= 4)
        assertTrue("player=$player dl=$dl", player.containsAll(dl))
    }
}
