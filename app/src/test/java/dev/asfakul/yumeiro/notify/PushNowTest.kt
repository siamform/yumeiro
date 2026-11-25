/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.FeatureFlags
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * PUSH-1 (A6): a push the owner sends arrives 1 minute after another notice was shown (for example a new-episode
 * notice). Fail-before: it went into NotifyPacer's 12-minute queue and waited for an inexact alarm, which in Doze or
 * on phones that stop closed apps' alarms meant late or never. After: shown at once, the pacer queue stays empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PushNowTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private fun pacer() = ctx.getSharedPreferences("notify_pacer", Context.MODE_PRIVATE)

    @Before fun setUp() {
        shadowOf(ctx as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        FeatureFlags.set(ctx, FeatureFlags.Flag.PUSH_NOW, true)
        pacer().edit().clear().putLong("last_shown", System.currentTimeMillis() - 60_000).commit()
    }

    @After fun tearDown() { FeatureFlags.set(ctx, FeatureFlags.Flag.PUSH_NOW, FeatureFlags.Flag.PUSH_NOW.default) }

    @Test fun anAnnouncementRightAfterAnotherNoticeIsShownNow() {
        val id = "a6-${System.nanoTime()}"
        val data = mapOf("y" to "1", "kind" to "announcement", "id" to id, "title" to "Server maintenance tonight",
            "body" to "Search may be slow for an hour.", "sent" to (System.currentTimeMillis() / 1000).toString())
        FcmPush.handle(ctx, data, null, null, System.currentTimeMillis())
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val shown = shadowOf(nm).allNotifications.map { it.extras.getString("android.title") }
        assertTrue("push not in the shade (queued behind the pacer?): $shown", "Server maintenance tonight" in shown)
        assertTrue("push went into the pacer queue", !pacer().getString("queue", "").orEmpty().contains(id))
    }
}
