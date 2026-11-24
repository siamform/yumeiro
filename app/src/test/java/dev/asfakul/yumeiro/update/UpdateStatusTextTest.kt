/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update

import android.app.DownloadManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** UPD-MSG-1 (owner item 21): the updater names the real step and never the hosting service. */
class UpdateStatusTextTest {
    private val release = ReleaseInfo(
        tagName = "v2.3.0", versionName = "2.3.0", versionCode = 30L, title = "v2.3.0", notes = "",
        publishedAt = "2026-11-24T00:00:00Z", htmlUrl = "https://example.invalid/r",
        asset = ApkAsset(id = 1, name = "Yumeiro-v2.3.0.apk", sizeBytes = 50_000_000, apiUrl = "https://example.invalid/api",
            browserUrl = "https://example.invalid/a.apk", sha256 = "ab"),
        source = UpdateSource.PUBLIC,
    )

    private fun allMessages(): List<String> {
        val out = mutableListOf<String>()
        DownloadStage.entries.forEach { out += UpdateStatusText.title(it); UpdateStatusText.detail(it)?.let(out::add) }
        listOf(
            UpdateState.Checking, UpdateState.UpToDate(1), UpdateState.Available(release),
            UpdateState.Downloading(release, 0, 1, 0, queued = true, stage = DownloadStage.PREPARING),
            UpdateState.Downloading(release, 10, 100, 5), UpdateState.ReadyToInstall(release, "/x"),
            UpdateState.Installing(release),
        ).forEach { s -> UpdateStatusText.settingsLine(s)?.let(out::add) }
        (0..2000).forEach { out += UpdateStatusText.downloadFailed(it) }
        out += UpdateStatusText.COMPLETED_TITLE; out += UpdateStatusText.completedBody("2.3.0"); out += UpdateStatusText.SOURCE_LINE
        out += UpdateStatusText.INSTALLING_DETAIL
        return out
    }

    @Test fun noMessageNamesTheHostingService() {
        allMessages().forEach { m ->
            assertFalse("names the host: $m", m.contains("github", ignoreCase = true))
            assertFalse("technical: $m", m.contains("connecting to", ignoreCase = true))
        }
    }

    @Test fun nothingSaysDownloadingBeforeTheFirstByte() {
        // Queued, nothing received: the old popup said "Connecting to GitHub…" here.
        val queued = UpdateState.Downloading(release, 0, 50_000_000, 0, queued = true)
        assertEquals(DownloadStage.STARTING, queued.stage)
        assertFalse(UpdateStatusText.title(queued.stage).contains("Downloading"))
        assertTrue(UpdateStatusText.indeterminate(queued.stage))
        assertEquals(DownloadStage.STARTING, UpdateStatusText.stageOf(DownloadManager.STATUS_PENDING, 0, 0))
        assertEquals(DownloadStage.STARTING, UpdateStatusText.stageOf(DownloadManager.STATUS_RUNNING, 0, 0))
        assertEquals(UpdateStatusText.PREPARING_TITLE, UpdateStatusText.title(DownloadStage.PREPARING))
        assertEquals(UpdateStatusText.PREPARING_DETAIL, UpdateStatusText.detail(DownloadStage.PREPARING))
    }

    @Test fun bytesMeanDownloadingAndPausedForNetworkSaysSo() {
        assertEquals(DownloadStage.DOWNLOADING, UpdateStatusText.stageOf(DownloadManager.STATUS_RUNNING, 0, 1))
        assertEquals(UpdateStatusText.DOWNLOADING_TITLE, UpdateStatusText.title(DownloadStage.DOWNLOADING))
        assertNull(UpdateStatusText.detail(DownloadStage.DOWNLOADING))
        assertFalse(UpdateStatusText.indeterminate(DownloadStage.DOWNLOADING))
        assertEquals(DownloadStage.WAITING_FOR_NETWORK,
            UpdateStatusText.stageOf(DownloadManager.STATUS_PAUSED, DownloadManager.PAUSED_WAITING_FOR_NETWORK, 4096))
        assertEquals(DownloadStage.WAITING_FOR_NETWORK,
            UpdateStatusText.stageOf(DownloadManager.STATUS_PAUSED, DownloadManager.PAUSED_QUEUED_FOR_WIFI, 0))
        assertEquals("Downloading update · 10%", UpdateStatusText.settingsLine(UpdateState.Downloading(release, 10, 100, 5)))
    }

    @Test fun failuresArePlainWords() {
        assertTrue(UpdateStatusText.downloadFailed(DownloadManager.ERROR_INSUFFICIENT_SPACE).contains("storage"))
        assertFalse(UpdateStatusText.downloadFailed(1234).contains("1234"))
        assertEquals("Checking for updates…", UpdateStatusText.settingsLine(UpdateState.Checking))
        assertEquals("Update completed!", UpdateStatusText.COMPLETED_TITLE)
    }
}
