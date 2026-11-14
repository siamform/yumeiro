/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.ContentUris
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.diagnostics.CrashExport
import dev.asfakul.yumeiro.diagnostics.CrashLog
import dev.asfakul.yumeiro.online.download.DashGrab
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * R15, Phase 5 on a real device: a DASH stream (separate video and audio) is saved and joined into
 * one playable file, and a crash report is written to Downloads as a file without any user step.
 */
@RunWith(AndroidJUnit4::class)
class ForceDownloadDeviceTest {
    @get:Rule val hang = HangDumpRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()

    @Test fun dashStreamIsSavedWithVideoAndAudio() = runBlocking {
        val url = "https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd"
        val dir = File(ctx.getExternalFilesDir(null), "stress").apply { mkdirs() }
        val out = File(dir, "dash-angel-one.mp4").apply { delete() }
        val t0 = System.currentTimeMillis()
        val saved = DashGrab(client, emptyMap()).download(url, out, maxHeight = 360, lowEnd = true) { _, _ -> }
        val ms = System.currentTimeMillis() - t0
        assertTrue(out.exists()); assertEquals(out.length(), saved.bytes)
        val ex = MediaExtractor().apply { setDataSource(out.path) }
        val mimes = (0 until ex.trackCount).map { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
        val durUs = (0 until ex.trackCount).maxOf { i -> ex.getTrackFormat(i).let { if (it.containsKey(MediaFormat.KEY_DURATION)) it.getLong(MediaFormat.KEY_DURATION) else 0L } }
        ex.release()
        val line = "YuForce dash saved bytes=${saved.bytes} webm=${saved.webm} tracks=$mimes durationUs=$durUs in ${ms}ms"
        Log.i("YuStress", line)
        File(dir, "force-report.txt").appendText(line + "\n")
        assertTrue("video track: $mimes", mimes.any { it.startsWith("video/") })
        assertTrue("audio track: $mimes", mimes.any { it.startsWith("audio/") })
        assertTrue("whole minute saved: $durUs", durUs > 55_000_000L)
        assertTrue("no leftovers", dir.listFiles()!!.none { it.name.startsWith(out.name + ".") })
    }

    /** SegmentTemplate stream (about 160 numbered segments per track), the common case on video sites. */
    @Test fun segmentedDashStreamIsSavedWithVideoAndAudio() = runBlocking {
        val url = "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd"
        val dir = File(ctx.getExternalFilesDir(null), "stress").apply { mkdirs() }
        val out = File(dir, "dash-bbb.mp4").apply { delete() }
        val t0 = System.currentTimeMillis()
        val saved = DashGrab(client, emptyMap()).download(url, out, maxHeight = 180, lowEnd = true) { _, _ -> }
        val ms = System.currentTimeMillis() - t0
        val ex = MediaExtractor().apply { setDataSource(out.path) }
        val mimes = (0 until ex.trackCount).map { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
        val durUs = (0 until ex.trackCount).maxOf { i -> ex.getTrackFormat(i).let { if (it.containsKey(MediaFormat.KEY_DURATION)) it.getLong(MediaFormat.KEY_DURATION) else 0L } }
        ex.release()
        val line = "YuForce segmented dash saved bytes=${saved.bytes} tracks=$mimes durationUs=$durUs in ${ms}ms"
        Log.i("YuStress", line)
        File(dir, "force-report.txt").appendText(line + "\n")
        assertTrue(mimes.toString(), mimes.any { it.startsWith("video/") } && mimes.any { it.startsWith("audio/") })
        assertTrue("whole stream saved: $durUs", durUs > 600_000_000L)
        out.delete()
    }

    @Test fun crashReportIsExportedAsAFileRightAway() {
        val dir = File(ctx.filesDir, "crash")
        // A large report (the kind that is too big to copy by hand).
        val body = buildString { repeat(4000) { append("at dev.asfakul.yumeiro.Frame").append(it).append("(Frame.kt:").append(it).append(")\n") } }
        val f = CrashLog.write(dir, "Crash", "Thread: device-test", IllegalStateException("device test crash"), body = body)
        assertTrue(CrashExport.export(ctx, f))
        val found = findExported("yumeiro-" + f.name)
        assertTrue("exported file in Downloads/${CrashExport.FOLDER}", found != null)
        assertEquals(f.length(), found!!.second)
        // A report left behind by a process that died before the copy: exported on the next start.
        val pending = CrashLog.write(dir, "Freeze", "Main thread", null, body = "pending report\n" + body, at = System.currentTimeMillis() + 1)
        CrashExport.exportPending(ctx)
        val second = findExported("yumeiro-" + pending.name)
        assertTrue("pending report exported", second != null)
        val line = "YuForce crash export ok: ${f.name} ${f.length()} bytes, pending ${pending.name}"
        Log.i("YuStress", line)
        File(File(ctx.getExternalFilesDir(null), "stress").apply { mkdirs() }, "force-report.txt").appendText(line + "\n")
        listOfNotNull(found.first, second!!.first).forEach { u -> runCatching { ctx.contentResolver.delete(u, null, null) } }
        f.delete(); pending.delete()
    }

    private fun findExported(name: String): Pair<android.net.Uri, Long>? {
        if (Build.VERSION.SDK_INT < 29) {
            val f = File(File(ctx.getExternalFilesDir(null), "Crash reports"), name)
            return if (f.exists()) android.net.Uri.fromFile(f) to f.length() else null
        }
        ctx.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.SIZE),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(name), null)?.use { c ->
            if (c.moveToFirst()) return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)) to c.getLong(1)
        }
        return null
    }
}
