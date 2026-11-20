/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import dev.asfakul.yumeiro.notify.AppNotifier.Channel as Ch
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.notify.AppNotifier
import dev.asfakul.yumeiro.notify.Inbox
import dev.asfakul.yumeiro.notify.NotifySettings
import dev.asfakul.yumeiro.notify.NotifySettings.Key
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R17 G9 (master handover: "Notification settings", real effect). Each switch on Settings →
 * Notifications is set through the same NotifySettings setter the row calls, then an item is posted
 * through the app's real path (AppNotifier.post → Inbox → NotifyPacer → NotificationManager) and the
 * result is read from Android's own list of active notifications.
 *  a every kind switch: off → not posted (no inbox entry, no notification); on → shown on its channel;
 *  b "Show notifications" off → inbox only, no system notification (as the row says);
 *  c "Yumeiro chime" off → the notification is silent; on → it alerts.
 * Log tag YuNotifyFx: NFX_CASE lines, NFX_RESULT PASS per test.
 */
@RunWith(AndroidJUnit4::class)
class NotifySettingsEffectDeviceTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val nm get() = ctx.getSystemService(NotificationManager::class.java)
    private fun log(s: String) = Log.i("YuNotifyFx", s)
    private val saved = mutableMapOf<Key, Boolean>()
    private val posted = mutableListOf<Pair<String, Int>>()

    @Before fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS")
        }
        // FINAL-41 (run 44666813073, tv-api34): the app's own DiscoveryWorker (scheduled at start on a fresh
        // install) ran during case a, showed its notification and took the pacer's one-per-12-minutes slot
        // ("NotifyPacer armed in 719 s" at 03:27:01.600), so the case's notification was queued, not shown.
        // The pacer is right; the class needs no other notifier running. Its work is cancelled (the app
        // schedules it again on the next start; P1 clears data before every class) and a running one is
        // waited out.
        val wm = androidx.work.WorkManager.getInstance(ctx)
        val tag = dev.asfakul.yumeiro.notify.DiscoveryWorker::class.java.name
        wm.cancelAllWorkByTag(tag)
        val until = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < until &&
            runCatching { wm.getWorkInfosByTag(tag).get().any { it.state == androidx.work.WorkInfo.State.RUNNING } }.getOrDefault(false)) Thread.sleep(250)
        Key.entries.forEach { saved[it] = NotifySettings.get(ctx, it) }
        NotifySettings.set(ctx, Key.ENABLED, true)
        NotifySettings.set(ctx, Key.SOUND, true)
        assertEquals("notifications must be allowed for this test", AppNotifier.Block.NONE, AppNotifier.block(ctx).let { if (it == AppNotifier.Block.CHANNEL_OFF) AppNotifier.Block.NONE else it })
    }

    @After fun tearDown() {
        posted.forEach { (id, n) -> nm.cancel(n); Inbox.remove(ctx, id) }
        saved.forEach { (k, v) -> NotifySettings.set(ctx, k, v) }
        resetPacer()
    }

    /** The pacer shows at most one notification per 12 minutes; start every case with a clean pacer. */
    private fun resetPacer() { ctx.getSharedPreferences("notify_pacer", Context.MODE_PRIVATE).edit().clear().commit() }

    private fun active(n: Int): android.service.notification.StatusBarNotification? = nm.activeNotifications.firstOrNull { it.id == n && it.packageName == ctx.packageName }
    // FINAL-29 (run 44661962064, api33-lowram): the class ran 16 s after BOOT_COMPLETED while the system was
    // still doing first-boot work (RoleService, onetimeinitializer, SetupWizard; "Notification sound delayed by
    // 1310msecs"); the posted notification was not listed within 5 s. Wait up to 15 s; the check is the same.
    private fun waitActive(n: Int, ms: Long = 15_000): android.service.notification.StatusBarNotification? {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { active(n)?.let { return it }; Thread.sleep(200) }
        return null
    }

    private fun post(kind: Inbox.Kind, tag: String): Pair<Boolean, Int> {
        resetPacer()
        val now = System.currentTimeMillis()
        val id = "nfx-$tag-$now"
        val n = 0x5F00 + posted.size
        posted += id to n
        val r = AppNotifier.post(ctx, Inbox.Item(id, kind, "NFX $tag", "Settings effect test", now), n)
        return r to n
    }
    private fun inInbox(tag: String) = Inbox.items.value.any { it.id.startsWith("nfx-$tag-") }

    @Test fun a_eachKindSwitchGatesItsNotifications() {
        val cases = listOf(
            Triple(Inbox.Kind.EPISODE, Key.EPISODES, Ch.EPISODES.id),
            Triple(Inbox.Kind.EXTENSION, Key.EXTENSIONS, Ch.DISCOVER.id),
            Triple(Inbox.Kind.PLAYLIST, Key.PLAYLISTS, Ch.DISCOVER.id),
            Triple(Inbox.Kind.NEWS, Key.NEWS, Ch.NEWS.id),
            Triple(Inbox.Kind.NUDGE, Key.NUDGES, Ch.NUDGE.id),
            Triple(Inbox.Kind.ALERT, Key.SERVICE, Ch.ALERTS.id),
        )
        for ((kind, key, channel) in cases) {
            NotifySettings.set(ctx, key, false)
            val (offR, offN) = post(kind, "${kind.name}-off")
            Thread.sleep(800)
            assertFalse("$key off: post must refuse", offR)
            assertFalse("$key off: no inbox entry", inInbox("${kind.name}-off"))
            assertTrue("$key off: no system notification", active(offN) == null)
            NotifySettings.set(ctx, key, true)
            val (onR, onN) = post(kind, "${kind.name}-on")
            assertTrue("$key on: posted", onR)
            assertTrue("$key on: in the inbox", inInbox("${kind.name}-on"))
            val sbn = waitActive(onN)
            assertTrue("$key on: system notification shown", sbn != null)
            if (Build.VERSION.SDK_INT >= 26) assertEquals("$key channel", channel, sbn!!.notification.channelId)
            log("NFX_CASE $key off=refused on=shown channel=${if (Build.VERSION.SDK_INT >= 26) sbn!!.notification.channelId else "none (API ${Build.VERSION.SDK_INT})"}")
        }
        log("NFX_RESULT PASS a")
    }

    @Test fun b_masterSwitchOffKeepsTheInboxOnly() {
        NotifySettings.set(ctx, Key.ENABLED, false)
        val (r, n) = post(Inbox.Kind.NEWS, "master-off")
        Thread.sleep(1_500)
        assertTrue("master off: still added", r)
        assertTrue("master off: in the inbox", inInbox("master-off"))
        assertTrue("master off: no system notification", active(n) == null)
        assertEquals(AppNotifier.Block.MASTER_OFF, AppNotifier.sendTest(ctx))
        log("NFX_CASE master off: inbox only, test notification refused (MASTER_OFF)")
        log("NFX_RESULT PASS b")
    }

    @Test fun c_chimeSwitchMakesNotificationsSilent() {
        NotifySettings.set(ctx, Key.SOUND, false)
        val (_, offN) = post(Inbox.Kind.NEWS, "sound-off")
        val off = waitActive(offN)?.notification
        assertTrue("sound off: shown", off != null)
        NotifySettings.set(ctx, Key.SOUND, true)
        val (_, onN) = post(Inbox.Kind.NEWS, "sound-on")
        val on = waitActive(onN)?.notification
        assertTrue("sound on: shown", on != null)
        // NotificationCompat.setSilent: alert only for a group summary (this one is never a summary).
        if (Build.VERSION.SDK_INT >= 26) {
            assertEquals("sound off is silent", Notification.GROUP_ALERT_SUMMARY, off!!.groupAlertBehavior)
            assertTrue("sound on alerts", on!!.groupAlertBehavior != Notification.GROUP_ALERT_SUMMARY)
            log("NFX_CASE chime off: groupAlert=${off.groupAlertBehavior} group=${off.group}; on: groupAlert=${on.groupAlertBehavior}")
        } else {
            // No channels below API 26: NotificationCompat.setSilent drops the sound; setSound sets ours.
            @Suppress("DEPRECATION") val offSound = off!!.sound
            @Suppress("DEPRECATION") val onSound = on!!.sound
            @Suppress("DEPRECATION") val offDefaultSound = (off.defaults and Notification.DEFAULT_SOUND) != 0
            assertTrue("sound off is silent (sound=$offSound defaultSound=$offDefaultSound)", offSound == null && !offDefaultSound)
            assertTrue("sound on has a sound", onSound != null)
            log("NFX_CASE chime (API ${Build.VERSION.SDK_INT}) off: sound=$offSound; on: sound=$onSound")
        }
        log("NFX_RESULT PASS c")
    }
}
