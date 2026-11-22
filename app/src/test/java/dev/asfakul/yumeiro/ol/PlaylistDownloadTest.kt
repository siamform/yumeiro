/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Kind
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Status
import dev.asfakul.yumeiro.online.download.PlaylistDownload
import dev.asfakul.yumeiro.online.download.YtDownloadOptions
import dev.asfakul.yumeiro.ui.screens.youtube.PlaylistDownloadButton
import dev.asfakul.yumeiro.ui.screens.youtube.playlistDownloadStatus
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** OL-12 (owner): "Download playlist" queues every song as one batch, skips saved ones, shows live progress. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PlaylistDownloadTest {
    @get:Rule val rule = createComposeRule()

    private fun track(id: String) = PlaylistDownload.Track(id, "Song $id", "Artist", null)
    private fun item(yt: String, status: Status, bytes: Long = 0, total: Long = -1, kind: Kind = Kind.YT_AUDIO) = OnlineDownloads.Item(
        id = "i_$yt$status", title = yt, api = "YouTube", titleUrl = "", episodeLabel = null, posterUrl = null, posterHeaders = emptyMap(),
        url = "", isHls = false, headers = emptyMap(), fileName = "$yt.m4a", status = status, bytes = bytes, total = total, kind = kind, ytId = yt,
    )

    @Test fun ol12_queueSkipsSavedDownloadingAndDuplicatesButRetriesFailed() {
        val tracks = listOf("a", "b", "c", "d", "e", "a").map(::track)
        val items = listOf(
            item("a", Status.DONE), item("b", Status.RUNNING), item("c", Status.FAILED),
            item("d", Status.DONE, kind = Kind.YT_VIDEO), // a saved video is not the song's audio
        )
        assertEquals(listOf("c", "d", "e"), PlaylistDownload.toQueue(tracks, items).map { it.id })
    }

    @Test fun ol12_batchProgressCountsEachSongOnce() {
        val ids = listOf("a", "b", "c", "d")
        val items = listOf(item("a", Status.DONE), item("a", Status.FAILED), item("b", Status.RUNNING, bytes = 50, total = 100), item("c", Status.FAILED))
        val p = PlaylistDownload.progress(ids, items)
        assertEquals(4, p.total); assertEquals(1, p.done); assertEquals(1, p.active); assertEquals(1, p.failed)
        assertEquals(1.5f / 4, p.fraction, 0.001f)
        assertEquals("Downloading · 1 of 4 done · 1 failed", PlaylistDownload.label(p))
        val all = PlaylistDownload.progress(listOf("a"), items)
        assertTrue(all.allDone); assertEquals("All 1 songs downloaded", PlaylistDownload.label(all))
        assertEquals("Getting every song…", playlistDownloadStatus(p, fetching = true))
    }

    @Test fun ol12_bestAudioPrefersM4aThenBitrate() {
        fun a(kbps: Int, c: String, itag: Int) = YtDownloadOptions.Choice("Audio $kbps kbps", "", -1, itag, c, 0, true)
        val video = YtDownloadOptions.Choice("1080p", "", 137, 140, "mp4", 0, false, 1080)
        val best = YtDownloadOptions.bestAudio(listOf(video, a(160, "webm", 251), a(48, "m4a", 139), a(128, "m4a", 140)))
        assertEquals(140, best!!.audioItag)
        assertEquals(251, YtDownloadOptions.bestAudio(listOf(a(160, "webm", 251), a(70, "webm", 250)))!!.audioItag)
        assertEquals(YtDownloadOptions.AUTO_AUDIO, YtDownloadOptions.AUTO_AUDIO_CHOICE.audioItag)
        assertTrue(YtDownloadOptions.AUTO_AUDIO_CHOICE.audioOnly)
    }

    @Test fun ol12_headerButtonIsTappableAndShowsBatchState() {
        var taps = 0
        var p by androidx.compose.runtime.mutableStateOf(PlaylistDownload.Progress(20, 0, 0, 0, 0f))
        rule.setContent { MTPlayerTheme { Row { PlaylistDownloadButton(p, fetching = false) { taps++ } } } }
        rule.onNodeWithTag("ytmusic_download_all").assertHasClickAction().assertIsEnabled().performClick()
        assertEquals(1, taps)
        p = PlaylistDownload.Progress(20, 3, 2, 0, 0.2f)
        rule.onNodeWithTag("ytmusic_download_progress").assertExists()
        p = PlaylistDownload.Progress(20, 20, 0, 0, 1f)
        rule.onNodeWithTag("ytmusic_download_all").assertIsNotEnabled()
    }
}
