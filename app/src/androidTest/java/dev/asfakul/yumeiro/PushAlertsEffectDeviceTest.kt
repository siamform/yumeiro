/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.asfakul.yumeiro.notify.NotifySettings
import dev.asfakul.yumeiro.notify.PushWake
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * G9 SE-5, the last switch: Settings → Notifications → On-time alerts (`toggle_push_alerts`, PushWake).
 *
 * A UnifiedPush distributor (the ntfy app) is not installed on the CI emulators, so the test plays
 * the distributor's two calls itself, with the same arguments the connector library passes:
 *  a new endpoint (an ntfy.sh topic, as ntfy hands out) → the app publishes its scheduled wake-ups
 *    to the real ntfy.sh server and ntfy accepts them as scheduled (Schedule.YES, 8+ pending);
 *  b a push message with background checks on → the feed and episode checks are enqueued now
 *    (push_catch_up work); with background checks off → nothing is enqueued;
 *  c turning the switch off clears the endpoint, the schedule and the enabled flag.
 * Not covered here (owner test): the distributor app binding to PushWakeService on a real phone.
 * Log tag YuPushFx: PUSH_CASE lines, PUSH_RESULT PASS.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PushAlertsEffectDeviceTest {
    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun log(m: String) = Log.i("YuPushFx", m)
    private val bgBefore = NotifySettings.get(ctx, NotifySettings.Key.BACKGROUND)
    private val wm get() = WorkManager.getInstance(ctx)

    @After fun tearDown() {
        runCatching { PushWake.disable(ctx) }
        NotifySettings.set(ctx, NotifySettings.Key.BACKGROUND, bgBefore)
    }

    private fun waitFor(ms: Long, ok: () -> Boolean): Boolean {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) { if (ok()) return true; Thread.sleep(500) }
        return ok()
    }

    private fun clearWakeGate() = ctx.getSharedPreferences("background_work", Context.MODE_PRIVATE).edit().remove("push_wake_at").commit()

    private fun catchUpLive(): List<WorkInfo.State> = runCatching {
        wm.getWorkInfosForUniqueWork("push_catch_up").get().map { it.state }
    }.getOrDefault(emptyList())

    @Test fun a_newEndpointSchedulesWakeUpsOnNtfy() {
        // No enable() here: run 43914724044 (api36) showed the connector answering register() for an
        // uninstalled distributor with onUnregistered ("There was a distributor, but it isn't
        // installed anymore"), which clears the endpoint by design. The refill path needs only the
        // endpoint the distributor would hand over.
        val topic = "upYumeiroCi" + java.util.UUID.randomUUID().toString().replace("-", "").take(14)
        PushWake.onNewEndpoint(ctx, "https://ntfy.sh/$topic?up=1")
        val ok = waitFor(120_000) { PushWake.state.value.let { it.schedule == PushWake.Schedule.YES && it.pending >= 8 } }
        val st = PushWake.state.value
        log("PUSH_CASE a enabled=${st.enabled} host=${st.endpointHost} schedule=${st.schedule} pending=${st.pending} error=${st.error}")
        assertTrue("SE-5 push_alerts: ntfy did not accept the scheduled wake-ups: $st", ok)
    }

    @Test fun b_pushMessageStartsChecksOnlyWhenBackgroundIsOn() {
        wm.cancelUniqueWork("push_catch_up").result.get(); wm.pruneWork().result.get()
        NotifySettings.set(ctx, NotifySettings.Key.BACKGROUND, false)
        clearWakeGate()
        PushWake.onMessage(ctx)
        Thread.sleep(1_500)
        val off = catchUpLive()
        val wakeAt = PushWake.state.value.lastWakeAt
        NotifySettings.set(ctx, NotifySettings.Key.BACKGROUND, true)
        clearWakeGate()
        PushWake.onMessage(ctx)
        val onOk = waitFor(10_000) { catchUpLive().isNotEmpty() }
        val on = catchUpLive()
        log("PUSH_CASE b backgroundOff=$off backgroundOn=$on lastWakeAt=$wakeAt")
        assertTrue("SE-5 push_alerts: a push with background checks off still enqueued checks: $off", off.isEmpty())
        assertTrue("SE-5 push_alerts: a push did not record its wake-up time", wakeAt > 0)
        assertTrue("SE-5 push_alerts: a push with background checks on enqueued no checks", onOk)
        wm.cancelUniqueWork("push_catch_up"); wm.cancelUniqueWork("push_catch_up_episodes")
    }

    @Test fun c_switchOffClearsEverything() {
        PushWake.enable(ctx, PushWake.NTFY_PACKAGE)
        PushWake.onNewEndpoint(ctx, "https://ntfy.sh/upYumeiroCiOff?up=1")
        val before = PushWake.state.value
        PushWake.disable(ctx)
        val after = PushWake.state.value
        log("PUSH_CASE c before=$before after=$after")
        assertTrue("SE-5 push_alerts: the endpoint was not stored before switching off: $before", before.hasEndpoint && before.enabled)
        assertTrue("SE-5 push_alerts: switching off left state behind: $after", !after.enabled && !after.hasEndpoint && after.pending == 0)
        log("PUSH_RESULT PASS")
    }
}
