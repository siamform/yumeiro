/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F-AIR (ADR-043). Production 2026-11-19: 406 Firebase phones, 1 UnifiedPush device, 0 follows. Firebase
 * phones never told the Worker which shows they follow and had no handler for an airing wake, so they never
 * got an on-time episode alert. These fail on the old code (no follows in the check-in, wake ignored).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FcmAiringWakeTest {
    private lateinit var c: Context
    private val wakeAt get() = c.getSharedPreferences("background_work", Context.MODE_PRIVATE).getLong("push_wake_at", 0L)

    @Before fun setUp() {
        c = ApplicationProvider.getApplicationContext()
        Inbox.clear(c)
        c.getSharedPreferences("background_work", Context.MODE_PRIVATE).edit().clear().commit()
        NotifySettings.set(c, NotifySettings.Key.ENABLED, true)
        NotifySettings.set(c, NotifySettings.Key.BACKGROUND, true)
    }

    @Test fun wakeStartsTheEpisodeCheckAndShowsNothingByItself() {
        assertTrue(FcmPlan.isWake(mapOf("y" to "1", "kind" to "wake")))
        assertFalse(FcmPlan.isWake(mapOf("kind" to "wake"))) // console message, not ours
        FcmPush.handle(c, mapOf("y" to "1", "kind" to "wake"), null, null, 0)
        assertTrue("the wake did not reach BackgroundWork.wake", wakeAt > 0)
        assertEquals("a wake must not become an inbox item", 0, Inbox.items.value.size)
    }

    @Test fun wakeObeysTheBackgroundSwitch() {
        NotifySettings.set(c, NotifySettings.Key.BACKGROUND, false)
        FcmPush.handle(c, mapOf("y" to "1", "kind" to "wake"), null, null, 0)
        assertEquals(0L, wakeAt)
    }

    @Test fun checkInCarriesFollowsAndAiringTimes() {
        val j = JSONObject(FcmPlan.cloudBody("fid", setOf("updates"), setOf("updates"), 9, "release", listOf(7, 42), listOf(42 to 1_800_000_000L)))
        assertEquals("[7,42]", j.getJSONArray("anilist").toString())
        assertEquals("[[42,1800000000]]", j.getJSONArray("airing").toString())
        // Episode alerts off: an explicit empty list, so the Worker forgets this phone's shows.
        assertEquals(0, JSONObject(FcmPlan.cloudBody("fid", emptySet(), emptySet(), 9, "release")).getJSONArray("anilist").length())
    }

    @Test fun followChangeTriggersACheckInAndFollowersRefreshEvery3Days() {
        val a = FcmPlan.fingerprint("fid", setOf("updates"), 9, setOf("updates"), listOf(1))
        assertNotEquals(a, FcmPlan.fingerprint("fid", setOf("updates"), 9, setOf("updates"), listOf(1, 2)))
        val day = 86_400_000L
        assertEquals(CloudAlertsPlan.REFRESH_MS, FcmPlan.refreshMs(listOf(1)))
        assertEquals(FcmPlan.REFRESH_MS, FcmPlan.refreshMs(emptyList()))
        assertTrue(FcmPlan.due("a", "a", 3 * day, 0, FcmPlan.refreshMs(listOf(1))))
        assertFalse(FcmPlan.due("a", "a", 3 * day, 0, FcmPlan.refreshMs(emptyList())))
    }
}
