/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.notify.Inbox
import dev.asfakul.yumeiro.notify.NotifyPacer
import dev.asfakul.yumeiro.notify.NotifySettings
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G12 X5: a paced alert reaches the shade while the app is closed and the phone is in Doze.
 *
 * Two phases, run by `scripts/device-check.sh` with the app killed in between:
 *  - `a`: the previous alert was shown 11 minutes ago, so a new one must wait about 70 s; it is
 *    queued and the pacer arms its allow-while-idle alarm. Phase A writes the title and its pid.
 *  - the script presses Home, kills the process (`am kill`), forces deep Doze
 *    (`dumpsys deviceidle force-idle`) and waits for the title in `dumpsys notification`.
 *  - `b` (only with `-e closed 1`): the alert is in the shade, posted by the alarm, and the
 *    queue is empty.
 */
@RunWith(AndroidJUnit4::class)
class ClosedAppAlertDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private fun log(s: String) = Log.i("YuClosed", s)
    private fun state() = ctx.getSharedPreferences("closed_alert_test", Context.MODE_PRIVATE)
    private fun pacer() = ctx.getSharedPreferences("notify_pacer", Context.MODE_PRIVATE)

    @Test fun a_queueAndArm() {
        if (Build.VERSION.SDK_INT >= 33) FirstRun.shell("pm grant ${ctx.packageName} android.permission.POST_NOTIFICATIONS")
        NotifySettings.set(ctx, NotifySettings.Key.ENABLED, true)
        NotifySettings.set(ctx, NotifySettings.Key.NEWS, true)
        val now = System.currentTimeMillis()
        pacer().edit().remove("queue").putLong("last_shown", now - NotifyPacer.GAP_MS + 70_000).commit()
        val title = "Yumeiro closed-app check $now"
        var shownNow = false
        NotifyPacer.offer(ctx, Inbox.Item("closed-$now", Inbox.Kind.NEWS, title, "Delivered by the pacer alarm.", now), 0x7A11) { _, _ -> shownNow = true }
        assertTrue("must wait for the pacer, not show at once", !shownNow)
        Thread.sleep(300) // apply() is async
        assertTrue("queued", pacer().getString("queue", "").orEmpty().contains(title))
        val alarm = FirstRun.shell("dumpsys alarm").lineSequence().filter { NotifyPacer.ACTION_NEXT in it }.toList()
        log("alarm lines: ${alarm.size} ${alarm.firstOrNull()?.trim()?.take(160)}")
        assertTrue("pacer alarm armed", alarm.isNotEmpty())
        state().edit().putString("title", title).putLong("queued_at", now).putInt("pid", android.os.Process.myPid()).commit()
        log("CLOSED_A title=$title pid=${android.os.Process.myPid()}")
    }

    @Test fun b_deliveredWhileClosed() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("phase b runs only from device-check.sh after the app was killed", args.getString("closed") == "1")
        val title = state().getString("title", null)
        val oldPid = state().getInt("pid", -1)
        val queuedAt = state().getLong("queued_at", 0L)
        assertTrue("phase a ran", title != null)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val found = nm.activeNotifications.firstOrNull { it.notification.extras.getCharSequence("android.title")?.toString() == title }
        assertTrue("the alert is in the shade", found != null)
        val delay = found!!.postTime - queuedAt
        // The script proved the process was gone after the kill (`pidof` empty) and logged the pid
        // of the process the alarm started; here: posted by the alarm, not at queue time.
        assertTrue("posted by the alarm about 70 s later, not at once (delay $delay ms)", delay >= 55_000)
        // Only our item must have left the queue: the process the alarm started also runs the app's
        // real catch-up checks, which may queue their own alerts (run 43749978183, api33: "31 new
        // extensions" from the extension feed).
        val queue = pacer().getString("queue", "[]").orEmpty()
        assertTrue("our alert left the queue: $queue", !queue.contains(title!!))
        log("CLOSED_RESULT PASS posted ${delay} ms after queueing · pid $oldPid -> ${android.os.Process.myPid()}")
        nm.cancel(found.id)
    }
}
