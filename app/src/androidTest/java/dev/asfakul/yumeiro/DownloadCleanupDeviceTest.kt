package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Status
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * G8 "Downloads fixes" row (562c6e3 series folders + auto-delete timer, 0e5cc1a Permanent vs Offline):
 * real downloads through WorkManager → DownloadWorker into one series folder, one Permanent and one
 * Offline. The auto-delete pass two days later (timer 1 day) removes the Offline one (file and list
 * entry) and keeps the Permanent one with its bytes; the daily pass is scheduled by the app start.
 */
@RunWith(AndroidJUnit4::class)
class DownloadCleanupDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private lateinit var server: MockWebServer
    private val blob = ByteArray(200_000) { (it % 239).toByte() }
    private var daysBefore = 0
    private val folder = "YuDlTest Series ${System.currentTimeMillis()}"
    private val ids = ArrayList<String>()

    @Before fun setUp() {
        DownloadSettings.init(ctx)
        daysBefore = DownloadSettings.autoDeleteDays.value
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(Buffer().write(blob)).build()
        }
        server.start()
        OnlineDownloads.ensureLoaded(ctx)
    }

    @After fun tearDown() {
        DownloadSettings.setAutoDeleteDays(ctx, daysBefore)
        ids.forEach { runCatching { OnlineDownloads.delete(ctx, it) } }
        runCatching { server.close() }
    }

    private suspend fun download(label: String, keep: Boolean): OnlineDownloads.Item {
        val title = "YuDlTest cleanup $label ${System.currentTimeMillis()}"
        val link = OnlineLink("Test", "test", server.url("/$label.mp4").toString(), null, 720, false, false, emptyMap())
        assertEquals(null, OnlineDownloads.enqueue(ctx, title, "yu-test", "t-clean-$title", label, null, emptyMap(), link, folder = folder, keep = keep))
        val id = OnlineDownloads.items.value.first { it.title == title }.id
        ids += id
        val until = System.currentTimeMillis() + 60_000
        var item = OnlineDownloads.get(id)
        while (item?.status != Status.DONE && item?.status != Status.FAILED && System.currentTimeMillis() < until) { delay(200); item = OnlineDownloads.get(id) }
        assertEquals("$label: ${item?.error}", Status.DONE, item?.status)
        return item!!
    }

    @Test fun offlineGoesAfterTheTimerPermanentStays() = runBlocking<Unit> {
        val keep = download("E1", keep = true)
        val offline = download("E2", keep = false)
        val dir = OnlineDownloads.folderDir(ctx, folder)
        val keepFile = OnlineDownloads.fileOf(ctx, keep)
        val offFile = OnlineDownloads.fileOf(ctx, offline)
        Log.i("YuDownloadTest", "CLEANUP files=${dir.list()?.sorted()} keepAt=${keepFile.parentFile?.name} offAt=${offFile.parentFile?.name}")
        assertEquals("series folder", dir.canonicalPath, keepFile.parentFile!!.canonicalPath)
        assertEquals("series folder", dir.canonicalPath, offFile.parentFile!!.canonicalPath)
        assertArrayEquals(blob, keepFile.readBytes())
        assertArrayEquals(blob, offFile.readBytes())

        DownloadSettings.setAutoDeleteDays(ctx, 1)
        // Before the timer: nothing goes.
        assertEquals(0, OnlineDownloads.cleanup(ctx, now = offline.finishedAt + 3_600_000L))
        assertTrue(offFile.exists())
        // Two days later: the Offline one goes, the Permanent one stays.
        val removed = OnlineDownloads.cleanup(ctx, now = maxOf(keep.finishedAt, offline.finishedAt) + 2 * 86_400_000L)
        val line = "CLEANUP removed=$removed keepListed=${OnlineDownloads.get(keep.id) != null} keepFile=${keepFile.exists()} " +
            "offListed=${OnlineDownloads.get(offline.id) != null} offFile=${offFile.exists()} folder=${dir.list()?.sorted()}"
        Log.i("YuDownloadTest", line)
        assertEquals(line, 1, removed)
        assertTrue(line, OnlineDownloads.get(keep.id) != null && keepFile.exists())
        assertArrayEquals(line, blob, keepFile.readBytes())
        assertTrue(line, OnlineDownloads.get(offline.id) == null && !offFile.exists())

        // The app start schedules the daily pass.
        val infos = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork("dl_cleanup").get(10, TimeUnit.SECONDS)
        Log.i("YuDownloadTest", "CLEANUP work=${infos.map { it.state }}")
        assertTrue("daily cleanup not scheduled: ${infos.map { it.state }}", infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING })

        // Deleting the last one also removes the empty series folder.
        OnlineDownloads.delete(ctx, keep.id)
        DownloadSettings.setAutoDeleteDays(ctx, 1)
        OnlineDownloads.cleanup(ctx)
        Log.i("YuDownloadTest", "CLEANUP folderLeft=${dir.exists()}")
        assertTrue("empty series folder left: ${File(dir.path).list()?.toList()}", !dir.exists())
        Log.i("YuDownloadTest", "CLEANUP_RESULT PASS")
    }
}
