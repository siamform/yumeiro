/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.asfakul.yumeiro.backup.AutoBackupWorker
import dev.asfakul.yumeiro.backup.BackupMeta
import dev.asfakul.yumeiro.online.AdultContent
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G9 S1–S22: real effect of switches that had no test (docs/evidence/round3/settings-effects.md).
 * Each switch is set the way its settings row sets it, then the code that reads it is observed:
 *  a Back up automatically (`backup_auto`): off cancels the periodic auto-backup work, on schedules it;
 *  b Permanent by default (`setting_keep_default`): a new download without an explicit choice gets
 *    keep = the switch (on and off);
 *  c Keep 18+ activity private (`setting_adult_private`): with 18+ on, private hides 18+ from feeds,
 *    not private shows it; with 18+ off it is hidden either way.
 * Every value is put back. Log tag YuMoreFx: MFX_CASE lines, MFX_RESULT PASS per test.
 */
@RunWith(AndroidJUnit4::class)
class MoreSettingsEffectDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun log(m: String) = Log.i("YuMoreFx", m)
    private val restore = ArrayList<() -> Unit>()
    private val started = ArrayList<String>()

    @After fun tearDown() {
        started.forEach { runCatching { OnlineDownloads.delete(ctx, it) } }
        restore.asReversed().forEach { runCatching { it() } }
    }

    private fun autoBackupActive(): Boolean = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(AUTO_BACKUP_WORK).get()
        .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }

    /** WorkManager applies enqueue/cancel on its own executor: poll up to 5 s. */
    private fun settle(cond: () -> Boolean): Boolean {
        val until = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < until) { if (cond()) return true; Thread.sleep(200) }
        return cond()
    }

    /** As the row does: write the switch, then reschedule. */
    private fun setAutoBackup(on: Boolean) {
        BackupMeta.setAutoEnabled(ctx, on)
        AutoBackupWorker.schedule(ctx, replace = true)
    }

    @Test fun a_autoBackupSchedulesAndCancels() {
        val was = BackupMeta.autoEnabled(ctx)
        restore += { setAutoBackup(was) }
        setAutoBackup(false)
        val off = !settle { !autoBackupActive() }
        setAutoBackup(true)
        val on = settle { autoBackupActive() }
        log("MFX_CASE a auto backup off: active=$off · on: active=$on")
        assertFalse("off must cancel the auto-backup work", off)
        assertTrue("on must schedule the auto-backup work", on)
        log("MFX_RESULT PASS a")
    }

    /** Enqueues a download to a closed local port (fails at once, no data) and returns its keep flag. */
    private fun keepOfNewDownload(case: String): Boolean {
        OnlineDownloads.ensureLoaded(ctx)
        val title = "YuMoreFx $case ${System.currentTimeMillis()}"
        val err = OnlineDownloads.enqueue(ctx, title, "yu-test-morefx", "t-morefx-$title", "E1", null, emptyMap(),
            OnlineLink("Test", "test", "http://127.0.0.1:1/never.mp4", null, 720, false, false, emptyMap()))
        assertEquals("enqueue refused: $err", null, err)
        val item = OnlineDownloads.items.value.first { it.title == title }
        started += item.id
        return item.keep
    }

    @Test fun b_keepByDefaultSetsNewDownloads() {
        DownloadSettings.init(ctx)
        val was = DownloadSettings.keepByDefault.value
        restore += { DownloadSettings.setKeepByDefault(ctx, was) }
        DownloadSettings.setKeepByDefault(ctx, true)
        val on = keepOfNewDownload("on")
        DownloadSettings.setKeepByDefault(ctx, false)
        val off = keepOfNewDownload("off")
        log("MFX_CASE b keep default on → keep=$on · off → keep=$off")
        assertTrue("switch on: a new download must be permanent", on)
        assertFalse("switch off: a new download must not be permanent", off)
        log("MFX_RESULT PASS b")
    }

    @Test fun c_adultPrivateHidesFromFeeds() {
        OnlineSettings.init(ctx)
        val adultWas = OnlineSettings.adult.value
        val privateWas = OnlineSettings.adultPrivate.value
        restore += { OnlineSettings.setAdult(ctx, adultWas); OnlineSettings.setAdultPrivate(ctx, privateWas) }
        val seen = LinkedHashMap<String, Boolean>()
        for (adult in listOf(true, false)) for (private in listOf(true, false)) {
            OnlineSettings.setAdult(ctx, adult)
            OnlineSettings.setAdultPrivate(ctx, private)
            seen["adult=$adult private=$private"] = AdultContent.hideInFeeds()
        }
        log("MFX_CASE c hideInFeeds $seen")
        assertEquals(true, seen["adult=true private=true"])
        assertEquals(false, seen["adult=true private=false"])
        assertEquals(true, seen["adult=false private=true"])
        assertEquals(true, seen["adult=false private=false"])
        log("MFX_RESULT PASS c")
    }

    companion object {
        /** `AutoBackupWorker.NAME` is private; the unique work name it schedules under. */
        const val AUTO_BACKUP_WORK = "yumeiro_auto_backup"
    }
}
