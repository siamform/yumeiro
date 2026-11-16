/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.Build
import android.os.StrictMode
import android.os.SystemClock
import android.os.strictmode.Violation
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadConfirmBody
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadPrompt
import dev.asfakul.yumeiro.ui.screens.settings.DownloadSettingsCard
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * ST-19: `OnlineDownloads.dir` (getExternalFilesDir + mkdirs) and `freeBytes` (StatFs) ran during
 * composition: StrictMode DiskReadViolation ×6 from DownloadSettingsCard.kt:99 in run 43767144680.
 * Each screen is composed under a main-thread StrictMode policy; no violation may come from
 * OnlineDownloads. [detectorCatchesADirectCall] proves the detector sees the old call pattern.
 */
@RunWith(AndroidJUnit4::class)
class StorageMainThreadDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val seen = Collections.synchronizedList(mutableListOf<String>())
    private var old: StrictMode.ThreadPolicy? = null
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun watch() {
        assumeTrue("penaltyListener needs API 28", Build.VERSION.SDK_INT >= 28)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            old = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                .penaltyListener({ it.run() }) { v: Violation -> seen += Log.getStackTraceString(v) }.build())
        }
    }

    @After fun restore() { old?.let { p -> InstrumentationRegistry.getInstrumentation().runOnMainSync { StrictMode.setThreadPolicy(p) } } }

    private fun ours() = seen.filter { "OnlineDownloads" in it }

    private fun settle() { rule.waitForIdle(); SystemClock.sleep(500); rule.waitForIdle() }

    @Test fun detectorCatchesADirectCall() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { OnlineDownloads.storagePath(ctx, "") }
        SystemClock.sleep(300)
        assertTrue("StrictMode listener saw the main-thread call", ours().isNotEmpty())
    }

    @Test fun settingsAndPromptReadStorageOffMain() {
        val req = DownloadPrompt.Request(DownloadPrompt.Type.EPISODES, "Test show", "3 episodes", 3, "Test show", "720p",
            sizeBytes = 1_000_000, defaultKeep = true, start = { null })
        rule.setContent { Column { DownloadSettingsCard(); DownloadConfirmBody(req, true, {}, false, {}) } }
        settle()
        val hits = ours()
        Log.i("YuStorage", "violations from OnlineDownloads: ${hits.size} (all: ${seen.size})")
        assertTrue("main-thread storage reads:\n" + hits.joinToString("\n---\n").take(3000), hits.isEmpty())
    }
}
