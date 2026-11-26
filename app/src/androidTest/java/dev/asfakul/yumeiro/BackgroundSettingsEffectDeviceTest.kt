/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.asfakul.yumeiro.cache.DataCache
import dev.asfakul.yumeiro.notify.DiscoveryWorker
import dev.asfakul.yumeiro.notify.FcmPush
import dev.asfakul.yumeiro.notify.NotifySettings
import dev.asfakul.yumeiro.notify.NotifySettings.Key
import dev.asfakul.yumeiro.notify.Reminders
import dev.asfakul.yumeiro.online.NewEpisodesWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G9 S1–S22 real effect of the switches no other test covered (docs/evidence/round3/settings-effects.md):
 *  a Background checks: off cancels both periodic workers in WorkManager, on schedules them;
 *  b Wi-Fi only: the scheduled work requires an unmetered network, off any network;
 *  c In-app reminders: off → no reminder is ever due;
 *  d Save loaded pages: off → the page cache neither stores nor returns pages;
 *  e Instant alerts: off → Firebase push reports Off at start (when the build has Firebase).
 * Every value is put back as it was.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundSettingsEffectDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun log(m: String) = Log.i("YuBgFx", m)
    private val saved = HashMap<Key, Boolean>()
    private var cacheWas = true

    private fun set(k: Key, v: Boolean) {
        if (k !in saved) saved[k] = NotifySettings.get(ctx, k)
        NotifySettings.set(ctx, k, v)
    }

    @After fun restore() {
        saved.forEach { (k, v) -> NotifySettings.set(ctx, k, v) }
        DataCache.Settings.set(enabled = cacheWas)
    }

    private fun infos(name: String): List<WorkInfo> = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(name).get()
    private fun active(name: String) = infos(name).any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }

    @Test fun a_backgroundChecksScheduleAndCancelWork() {
        NotifySettings.init(ctx)
        set(Key.BACKGROUND, false)
        val offD = active(DiscoveryWorker.WORK); val offE = active(NewEpisodesWorker.WORK)
        set(Key.BACKGROUND, true)
        val onD = active(DiscoveryWorker.WORK); val onE = active(NewEpisodesWorker.WORK)
        log("BGFX_CASE a off: discovery=$offD episodes=$offE · on: discovery=$onD episodes=$onE")
        assertTrue("off must cancel both workers", !offD && !offE)
        assertTrue("on must schedule both workers", onD && onE)
        log("BGFX_RESULT PASS a")
    }

    @Test fun b_wifiOnlySetsTheNetworkConstraint() {
        NotifySettings.init(ctx)
        set(Key.BACKGROUND, true)
        set(Key.WIFI_ONLY, true)
        val on = infos(DiscoveryWorker.WORK).first { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }.constraints.requiredNetworkType
        set(Key.WIFI_ONLY, false)
        val off = infos(DiscoveryWorker.WORK).first { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }.constraints.requiredNetworkType
        log("BGFX_CASE b wifiOnly on=$on off=$off")
        assertEquals(NetworkType.UNMETERED, on)
        assertEquals(NetworkType.CONNECTED, off)
        log("BGFX_RESULT PASS b")
    }

    @Test fun c_remindersOffMeansNoneDue() {
        NotifySettings.init(ctx)
        set(Key.REMINDERS, true)
        // A year ahead every reminder kind is past its wait, so one is due when the switch is on.
        Reminders.state(ctx) // records the install time now (not a year ahead, which would be inside its grace)
        val later = System.currentTimeMillis() + 365L * 24 * 3_600_000
        val on = Reminders.due(ctx, later, Reminders.SESSION_WARMUP_MS)
        set(Key.REMINDERS, false)
        val off = Reminders.due(ctx, later, Reminders.SESSION_WARMUP_MS)
        log("BGFX_CASE c reminders on=$on off=$off")
        assertNull("off must never offer a reminder", off)
        assertTrue("on: a reminder should be due a year later (got null)", on != null)
        log("BGFX_RESULT PASS c")
    }

    @Test fun d_pageCacheOffStoresNothing() {
        DataCache.Settings.init(ctx)
        DataCache.init(ctx)
        cacheWas = DataCache.Settings.enabled
        val key = "bgfx-${System.currentTimeMillis()}"
        DataCache.Settings.set(enabled = false)
        DataCache.write("yu-test", key, "off-value")
        val offRead = DataCache.read("yu-test", key)
        DataCache.Settings.set(enabled = true)
        val afterOn = DataCache.read("yu-test", key)
        DataCache.write("yu-test", key, "on-value")
        val onRead = DataCache.read("yu-test", key)
        log("BGFX_CASE d cache off read=$offRead · stored while off, read when on=$afterOn · on read=$onRead")
        assertNull(offRead); assertNull("a page written while off was stored", afterOn)
        assertEquals("on-value", onRead)
        log("BGFX_RESULT PASS d")
    }

    @Test fun e_instantAlertsOffReportsOff() {
        NotifySettings.init(ctx)
        set(Key.FCM, false)
        FcmPush.start(ctx)
        val off = FcmPush.status.value
        log("BGFX_CASE e fcm off -> status=$off")
        // Builds without Firebase config say "Not available in this build" whatever the switch says.
        assertTrue("off must report Off (or Unavailable without Firebase): $off", off == FcmPush.Status.OFF || off == FcmPush.Status.UNAVAILABLE)
        log("BGFX_RESULT PASS e status=$off")
    }
}
