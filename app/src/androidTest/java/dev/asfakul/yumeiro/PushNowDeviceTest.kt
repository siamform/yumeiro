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
import dev.asfakul.yumeiro.notify.FcmPush
import dev.asfakul.yumeiro.notify.NotifySettings
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PUSH-1 (A6) on the real NotificationManager: an announcement the owner sends arrives one minute after another
 * notice was shown. It must be in the shade at once, through the same entry the messaging service uses (also when
 * the process was started by the message, which is what happens with the app closed). Fail-before (main 131a2681):
 * it was queued behind the 12-minute pacer and the shade stayed empty. Real FCM delivery to a real phone: MANUAL_TESTS.
 * Log tag YuPush.
 */
@RunWith(AndroidJUnit4::class)
class PushNowDeviceTest {
    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Before fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${ctx.packageName} android.permission.POST_NOTIFICATIONS").close()
        NotifySettings.init(ctx)
        ctx.getSharedPreferences("notify_pacer", Context.MODE_PRIVATE).edit().clear()
            .putLong("last_shown", System.currentTimeMillis() - 60_000).commit()
    }

    @Test fun anAnnouncementRightAfterAnotherNoticeIsInTheShadeAtOnce() {
        val id = "a6dev${System.nanoTime() % 1_000_000}"
        val title = "Push check $id"
        FcmPush.handle(ctx, mapOf("y" to "1", "kind" to "announcement", "id" to id, "title" to title, "body" to "Shown at once.",
            "sent" to (System.currentTimeMillis() / 1000).toString()), null, null, System.currentTimeMillis())
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val until = System.currentTimeMillis() + 5_000
        var shown = false
        while (!shown && System.currentTimeMillis() < until) {
            shown = nm.activeNotifications.any { it.notification.extras.getCharSequence("android.title")?.toString() == title }
            if (!shown) Thread.sleep(200)
        }
        Log.w("YuPush", "PUSH_CASE shown=$shown queue=${ctx.getSharedPreferences("notify_pacer", Context.MODE_PRIVATE).getString("queue", "")?.length}")
        assertTrue("the push is not in the shade (held by the pacer?)", shown)
        nm.cancel(("fcm-$id").hashCode())
    }
}
