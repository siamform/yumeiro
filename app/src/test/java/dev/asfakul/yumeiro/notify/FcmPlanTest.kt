/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmPlanTest {
    private fun msg(vararg kv: Pair<String, String>) = mapOf("y" to "1", "id" to "abc123", "title" to "Hello", "body" to "World", *kv)

    @Test fun configNeedsEveryPartAndARealAppId() {
        assertTrue(FcmPlan.configured("yumeiro-app", "1:123456:android:abcdef", "AIzaKey", "123456"))
        assertFalse(FcmPlan.configured("", "1:123456:android:abcdef", "AIzaKey", "123456"))
        assertFalse(FcmPlan.configured("p", "1123456androidabcdef", "AIzaKey", "123456"))
        assertFalse(FcmPlan.configured("p", "1:1:android:x", "", "1"))
    }

    @Test fun topicsFollowSwitchesAndChannel() {
        assertEquals(setOf("announcements", "maintenance", "updates"), FcmPlan.topics("release", true, true, true))
        assertEquals(setOf("updates"), FcmPlan.topics("release", false, false, true))
        assertEquals(setOf("preview-announcements", "preview-updates"), FcmPlan.topics("preview", true, false, true))
        val (add, drop) = FcmPlan.diff(setOf("announcements", "updates"), setOf("updates", "maintenance"))
        assertEquals(setOf("maintenance"), add)
        assertEquals(setOf("announcements"), drop)
    }

    @Test fun linksAreHttpsOrKnownActions() {
        assertEquals("https://yumeiro.click/n", FcmPlan.safeLink("https://yumeiro.click/n"))
        assertEquals("update", FcmPlan.safeLink(" update "))
        for (bad in listOf("http://x.y", "intent://scan#Intent;end", "javascript:alert(1)", "file:///sdcard/a", "https://u:p@x.y", "settings", "", null))
            assertNull(bad, FcmPlan.safeLink(bad))
    }

    @Test fun parsesWorkerMessages() {
        val m = FcmPlan.parse(msg("kind" to "maintenance", "link" to "https://status.yumeiro.click", "sent" to "1000", "exp" to "5000", "min_v" to "40"), null, null, 0)!!
        assertEquals(Inbox.Kind.ALERT, m.kind)
        assertEquals(1_000_000L, m.atMs)
        assertEquals(40, m.minVersion)
        assertEquals("https://status.yumeiro.click", m.link)
        assertEquals(Inbox.Kind.NEWS, FcmPlan.parse(msg("kind" to "announcement"), null, null, 1)!!.kind)
        assertEquals(Inbox.Kind.ALERT, FcmPlan.parse(msg("kind" to "direct"), null, null, 1)!!.kind)
        // Updates open the update dialog by default; a bad link is dropped, not trusted.
        val u = FcmPlan.parse(msg("kind" to "update", "link" to "http://evil"), null, null, 1)!!
        assertEquals(Inbox.Kind.UPDATE, u.kind)
        assertEquals("update", u.link)
    }

    @Test fun rejectsMalformedWorkerMessages() {
        assertNull(FcmPlan.parse(msg("kind" to "spam"), null, null, 1))
        assertNull(FcmPlan.parse(msg("kind" to "announcement", "id" to "bad id!"), null, null, 1))
        assertNull(FcmPlan.parse(msg("kind" to "announcement", "title" to "  "), null, null, 1))
        val long = FcmPlan.parse(msg("kind" to "announcement", "title" to "t".repeat(200), "body" to "b".repeat(2000)), null, null, 1)!!
        assertEquals(FcmPlan.MAX_TITLE, long.title.length)
        assertEquals(FcmPlan.MAX_BODY, long.body.length)
    }

    @Test fun consoleMessagesBecomeAnnouncements() {
        val m = FcmPlan.parse(emptyMap(), "From the console", "Hi", 120_000)!!
        assertEquals(Inbox.Kind.NEWS, m.kind)
        assertTrue(m.id.startsWith("console-"))
        // Same text in the same minute dedups; a new minute is a new notice.
        assertEquals(m.id, FcmPlan.parse(emptyMap(), "From the console", "Hi", 150_000)!!.id)
        assertNotEquals(m.id, FcmPlan.parse(emptyMap(), "From the console", "Hi", 200_000)!!.id)
        assertNull(FcmPlan.parse(emptyMap(), null, "no title", 1))
    }

    @Test fun versionWindowAndExpiry() {
        val m = FcmPlan.parse(msg("kind" to "maintenance", "min_v" to "10", "max_v" to "20", "exp" to "500"), null, null, 1)!!
        assertTrue(FcmPlan.forThisApp(m, 15, 100))
        assertFalse(FcmPlan.forThisApp(m, 9, 100))
        assertFalse(FcmPlan.forThisApp(m, 21, 100))
        assertFalse("expired maintenance is dropped", FcmPlan.forThisApp(m, 15, 500))
    }

    @Test fun checkInIsWeeklyUnlessSomethingChanged() {
        val day = 24 * 3600 * 1000L
        assertFalse(FcmPlan.due("a", "a", 6 * day, 0))
        assertTrue(FcmPlan.due("a", "a", 7 * day, 0))
        assertTrue(FcmPlan.due("b", "a", day, 0))
        assertTrue("clock went back", FcmPlan.due("a", "a", 0, day))
    }

    @Test fun fingerprintChangesWithTopicsOrVersion() {
        val a = FcmPlan.fingerprint("fid", setOf("a", "b"), 1)
        assertEquals(a, FcmPlan.fingerprint("fid", setOf("b", "a"), 1))
        assertNotEquals(a, FcmPlan.fingerprint("fid", setOf("a"), 1))
        assertNotEquals(a, FcmPlan.fingerprint("fid", setOf("a", "b"), 2))
    }
}
