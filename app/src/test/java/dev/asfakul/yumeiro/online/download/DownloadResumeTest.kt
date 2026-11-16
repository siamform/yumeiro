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

/** G8 (R17): saves from several worker threads at once must not put an older list on disk. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DownloadListSaveRaceTest {
    private fun item(id: String) = Item(
        id, "T", "Api", "u", "Ep $id", null, emptyMap(), "", false, emptyMap(), "f$id.mp4", status = Status.RUNNING,
        kind = OnlineDownloads.Kind.EPISODE,
    )

    @Test fun parallelFinishesAllReachTheFile() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val f = java.io.File(ctx.filesDir, "online_downloads.json")
        val ids = (1..8).map { "w$it" }
        var lost = 0
        repeat(40) { round ->
            OnlineDownloads.setItemsForTest(ctx, ids.map(::item))
            val start = java.util.concurrent.CountDownLatch(1)
            val threads = ids.map { id -> Thread { start.await(); OnlineDownloads.progress(id, 5, 10); OnlineDownloads.finished(id, 10) } }
            threads.forEach { it.start() }; start.countDown(); threads.forEach { it.join() }
            val saved = org.json.JSONObject(f.readText()).getJSONArray("items")
            val done = (0 until saved.length()).count { saved.getJSONObject(it).optString("status") == "DONE" }
            if (done != ids.size) { lost++; println("SAVE_RACE round=$round done_on_disk=$done/${ids.size}") }
        }
        assertEquals("rounds where the file missed a finished download", 0, lost)
    }
}

/** G8 (R17): deleting a download removes every work file it made, and nothing of another one. */
class DownloadDeleteFilesTest {
    @Test fun deleteRemovesEveryWorkFile() {
        val dir = java.nio.file.Files.createTempDirectory("dl").toFile()
        val name = "Show 720p-1a2b3c.mp4"
        val mine = listOf("", ".part", ".part.v", ".part.a", ".part.chunks", ".part.idx", ".part.len", ".part.v.chunks", ".part.a.chunks", ".v", ".a")
        mine.forEach { java.io.File(dir, name + it).writeText("x") }
        val others = listOf("Show 720p-9z9z9z.mp4.part", "Show 720p-1a2b3c.mp4x", "Other-1a2b3c.mkv.part.v")
        others.forEach { java.io.File(dir, it).writeText("y") }
        assertEquals(mine.size, OnlineDownloads.removeFiles(java.io.File(dir, name)))
        assertEquals(others.sorted(), dir.list()!!.sorted())
        dir.deleteRecursively()
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
