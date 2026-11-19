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
import dev.asfakul.yumeiro.notify.FcmPush
import dev.asfakul.yumeiro.notify.Inbox
import dev.asfakul.yumeiro.notify.NotifySettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-AIR (ADR-043), mocked FCM: the CI emulators have no Firebase project, so the test hands the app the
 * exact data map the Worker's wake carries ({y:1, kind:wake}) through the same entry the messaging service
 * uses. The real WorkManager must then hold the expedited new-episode check, and nothing is posted by
 * the wake itself. Not covered (owner test OMT): a real FCM delivery to a real phone.
 * Log tag YuAirFx: AIR_CASE lines, AIR_RESULT PASS.
 */
@RunWith(AndroidJUnit4::class)
class FcmAiringWakeDeviceTest {
    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val bgBefore = NotifySettings.get(ctx, NotifySettings.Key.BACKGROUND)
    private val wm get() = WorkManager.getInstance(ctx)
    private fun log(m: String) = Log.i("YuAirFx", m)

    @After fun tearDown() { NotifySettings.set(ctx, NotifySettings.Key.BACKGROUND, bgBefore) }

    private fun states(name: String): List<WorkInfo.State> = runCatching { wm.getWorkInfosForUniqueWork(name).get().map { it.state } }.getOrDefault(emptyList())

    @Test fun airingWakeEnqueuesTheEpisodeCheck() {
        NotifySettings.set(ctx, NotifySettings.Key.BACKGROUND, true)
        ctx.getSharedPreferences("background_work", Context.MODE_PRIVATE).edit().remove("push_wake_at").commit()
        wm.cancelUniqueWork("push_catch_up_episodes").result.get()
        wm.pruneWork().result.get()
        val inboxBefore = Inbox.items.value.size
        FcmPush.handle(ctx, mapOf("y" to "1", "kind" to "wake"), null, null, System.currentTimeMillis())
        val until = System.currentTimeMillis() + 10_000
        var s = states("push_catch_up_episodes")
        while (s.isEmpty() && System.currentTimeMillis() < until) { Thread.sleep(250); s = states("push_catch_up_episodes") }
        log("AIR_CASE wake -> push_catch_up_episodes=$s")
        assertTrue("episode check not enqueued after an FCM airing wake: $s", s.isNotEmpty())
        assertEquals("the wake itself must not post an inbox item", inboxBefore, Inbox.items.value.size)
        log("AIR_RESULT PASS")
    }
}
