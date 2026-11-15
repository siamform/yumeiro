package dev.asfakul.yumeiro.online.download

import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Item
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Status
import dev.asfakul.yumeiro.online.download.RangeResume.Action
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R16 (G8): resuming a .part file and a system stop of the worker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DownloadResumeTest {

    @Test fun aCompletePartIsFinishedNotFailed() {
        assertEquals(Action.COMPLETE, RangeResume.decide(1000, 416, "bytes */1000"))
        assertEquals(Action.RESTART, RangeResume.decide(1000, 416, "bytes */2000")) // the file changed
        assertEquals(Action.RESTART, RangeResume.decide(1000, 416, null))
    }

    @Test fun aRangeAnswerIsAppendedOnlyAtTheRightOffset() {
        assertEquals(Action.APPEND, RangeResume.decide(500, 206, "bytes 500-999/1000"))
        assertEquals(Action.APPEND, RangeResume.decide(500, 206, null))
        assertEquals(Action.RESTART, RangeResume.decide(500, 206, "bytes 0-999/1000"))
        assertEquals(Action.APPEND, RangeResume.decide(500, 200, null))
    }

    private fun item(id: String, status: Status) = Item(
        id, "T", "Api", "u", "Ep 1", null, emptyMap(), "", false, emptyMap(), "f$id.mp4", status = status,
        kind = OnlineDownloads.Kind.EPISODE,
    )

    @Test fun aSystemStopLeavesTheDownloadWaitingNotPaused() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        OnlineDownloads.setItemsForTest(ctx, listOf(item("run", Status.RUNNING), item("user", Status.PAUSED), item("done", Status.DONE)))
        listOf("run", "user", "done").forEach { OnlineDownloads.stopped(it) }
        assertEquals(Status.QUEUED, OnlineDownloads.get("run")?.status)  // WorkManager runs it again
        assertEquals(Status.PAUSED, OnlineDownloads.get("user")?.status) // the user's pause stays
        assertEquals(Status.DONE, OnlineDownloads.get("done")?.status)
    }

    @Test fun aLateProgressTickKeepsTheUsersPause() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        OnlineDownloads.setItemsForTest(ctx, listOf(item("p", Status.PAUSED), item("q", Status.QUEUED)))
        OnlineDownloads.progress("p", 10, 100); OnlineDownloads.stopped("p")
        OnlineDownloads.progress("q", 10, 100)
        assertEquals(Status.PAUSED, OnlineDownloads.get("p")?.status) // device run 43741091128 showed QUEUED
        assertEquals(Status.RUNNING, OnlineDownloads.get("q")?.status)
    }
}

class HlsResumeTest {
    @Test fun resumeCutsBackToTheRecordedSegments() {
        assertEquals(5 to 5000L, HlsResume.read(HlsResume.write(5, 5000), 7000)) // 2 extra segments are cut
        assertEquals(5 to 5000L, HlsResume.read("5:5000", 5000))
        assertEquals(0 to 0L, HlsResume.read("5:5000", 4000))  // file shorter than recorded: start over
        assertEquals(0 to 0L, HlsResume.read("5", 7000))       // old format: start over (safe)
        assertEquals(0 to 0L, HlsResume.read(null, 7000))
        assertEquals(0 to 0L, HlsResume.read("x:y", 7000))
    }
}
