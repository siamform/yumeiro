/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.Build
import android.os.Environment
import android.os.StrictMode
import android.os.strictmode.Violation
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.update.ApkAsset
import dev.asfakul.yumeiro.update.ReleaseInfo
import dev.asfakul.yumeiro.update.UpdateManager
import dev.asfakul.yumeiro.update.UpdateSource
import dev.asfakul.yumeiro.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/**
 * G13 N3 (self-update), N3-1: starting an update download must not do disk work on the main
 * thread, and a link that is not on GitHub must still be refused. A fresh UpdateManager gets an
 * "update available" state whose APK link points at 127.0.0.1 (so nothing is downloaded or
 * installed); Download is pressed on the main thread under a StrictMode listener.
 * Old code: the updates folder was created, listed and emptied on the main thread.
 * Log tag YuUpdateTest, UPD_CASE / UPD_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class UpdateMainThreadDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext.applicationContext
    private val seen = Collections.synchronizedList(mutableListOf<String>())
    private var old: StrictMode.ThreadPolicy? = null
    private val dir by lazy { File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates") }
    private fun log(s: String) = Log.i("YuUpdateTest", s)

    @Before fun setUp() {
        assumeTrue("penaltyListener needs API 28", Build.VERSION.SDK_INT >= 28)
        // A stale file, as a half-finished earlier update leaves behind: the start must clear it.
        dir.mkdirs(); File(dir, "old.apk").writeText("old")
        inst.runOnMainSync {
            old = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                .penaltyListener({ it.run() }) { v: Violation -> seen += Log.getStackTraceString(v) }.build())
        }
    }

    @After fun tearDown() {
        inst.runOnMainSync { old?.let { StrictMode.setThreadPolicy(it) } }
    }

    @Test fun downloadStartIsOffTheMainThreadAndOffGitHubLinksAreRefused() {
        val ctor = UpdateManager::class.java.getDeclaredConstructor(android.content.Context::class.java).apply { isAccessible = true }
        val mgr = ctor.newInstance(ctx)
        val release = ReleaseInfo(
            tagName = "v999.0.0", versionName = "999.0.0", versionCode = 999_999L, title = "Test", notes = "",
            publishedAt = "", htmlUrl = "", source = UpdateSource.PUBLIC,
            asset = ApkAsset(id = 1, name = "yumeiro-999.apk", sizeBytes = 10, apiUrl = "", browserUrl = "http://127.0.0.1:9/yumeiro-999.apk", sha256 = "00"),
        )
        @Suppress("UNCHECKED_CAST")
        val state = UpdateManager::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(mgr) as MutableStateFlow<UpdateState>
        state.value = UpdateState.Available(release)
        seen.clear()
        inst.runOnMainSync { mgr.startDownload() }
        val until = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < until && mgr.state.value !is UpdateState.Failed) Thread.sleep(100)
        inst.waitForIdleSync()
        val end = mgr.state.value
        val mine = synchronized(seen) { seen.filter { "dev.asfakul.yumeiro.update." in it } }
        log("UPD_CASE a state=${end::class.simpleName} msg=${(end as? UpdateState.Failed)?.message} mainDisk=${mine.size} (all: ${seen.size}) staleLeft=${File(dir, "old.apk").exists()}")
        mine.take(3).forEach { log("UPD_CASE a stack: " + it.lines().take(14).joinToString(" < ")) }
        // Refused before anything is queued: a queued download would fail later as "Download failed (...)".
        assertTrue("a: off-GitHub link not refused: $end", end is UpdateState.Failed && !end.message.startsWith("Download failed"))
        assertFalse("a: stale update file not cleared", File(dir, "old.apk").exists())
        assertTrue("a: ${mine.size} main-thread disk calls from the update code:\n" + mine.firstOrNull().orEmpty().take(2500), mine.isEmpty())
        log("UPD_RESULT PASS")
    }
}
