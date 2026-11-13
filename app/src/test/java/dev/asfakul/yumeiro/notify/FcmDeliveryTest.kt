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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A received FCM data message really lands in the inbox and the shade, with its deep link, and obeys the switches. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FcmDeliveryTest {
    private lateinit var c: Context

    @Before fun setUp() {
        c = ApplicationProvider.getApplicationContext()
        shadowOf(c as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        Inbox.clear(c)
        NotifySettings.set(c, NotifySettings.Key.ENABLED, true)
        NotifySettings.set(c, NotifySettings.Key.SERVICE, true)
        NotifySettings.set(c, NotifySettings.Key.NEWS, true)
    }

    private fun data(kind: String, id: String, link: String? = null) = buildMap {
        put("y", "1"); put("kind", kind); put("id", id); put("title", "Maintenance tonight"); put("body", "Sources pause 02:00–02:10 UTC")
        put("sent", (System.currentTimeMillis() / 1000).toString()); link?.let { put("link", it) }
    }

    @Test fun maintenanceNoticeReachesInboxAndShadeWithLink() {
        FcmPush.handle(c, data("maintenance", "m1", "https://status.yumeiro.click"), null, null, 0)
        val item = Inbox.items.value.firstOrNull { it.id == "fcm-m1" }
        assertNotNull(item)
        assertEquals(Inbox.Kind.ALERT, item!!.kind)
        val nm = shadowOf(c.getSystemService(NotificationManager::class.java))
        val n = nm.allNotifications.firstOrNull { it.extras.getCharSequence("android.title")?.toString() == "Maintenance tonight" }
        assertNotNull("posted to the shade", n)
        assertEquals("yumeiro_alerts_v3", n!!.channelId)
        val intent = shadowOf(n.contentIntent).savedIntent
        assertEquals(AppNotifier.ACTION_OPEN_INBOX, intent.action)
        assertEquals("https://status.yumeiro.click", intent.getStringExtra(AppNotifier.EXTRA_LINK))
    }

    @Test fun sameMessageTwiceIsOneNotice() {
        FcmPush.handle(c, data("announcement", "dup"), null, null, 0)
        FcmPush.handle(c, data("announcement", "dup"), null, null, 0)
        assertEquals(1, Inbox.items.value.count { it.id == "fcm-dup" })
    }

    @Test fun switchedOffKindIsDropped() {
        NotifySettings.set(c, NotifySettings.Key.SERVICE, false)
        FcmPush.handle(c, data("maintenance", "off1"), null, null, 0)
        assertFalse(Inbox.items.value.any { it.id == "fcm-off1" })
        // Updates have no switch: always kept.
        FcmPush.handle(c, data("update", "u1"), null, null, 0)
        assertTrue(Inbox.items.value.any { it.id == "fcm-u1" && it.link == "update" })
    }

    @Test fun pushIsOffInBuildsWithoutConfig() {
        // The test build carries no Firebase config: the app must run with push simply unavailable.
        if (!FcmPush.configured()) {
            FcmPush.start(c)
            assertEquals(FcmPush.Status.UNAVAILABLE, FcmPush.status.value)
        }
    }
}
