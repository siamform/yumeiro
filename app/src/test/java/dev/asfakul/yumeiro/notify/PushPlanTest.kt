/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import dev.asfakul.yumeiro.notify.PushPlan.BACKSTOP_MS
import dev.asfakul.yumeiro.notify.PushPlan.HORIZON_MS
import dev.asfakul.yumeiro.notify.PushPlan.STEP_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushPlanTest {
    private val now = 1_800_000_000_000L
    private val h = 3_600_000L

    @Test fun `publish url drops the distributor query and needs https`() {
        assertEquals("https://ntfy.sh/upAbC123", PushPlan.publishUrl("https://ntfy.sh/upAbC123?up=1"))
        assertEquals("https://push.example.org/up/xyz", PushPlan.publishUrl(" https://push.example.org/up/xyz?up=1#f "))
        assertNull(PushPlan.publishUrl("http://ntfy.sh/upAbC"))
        assertNull(PushPlan.publishUrl("https://ntfy.sh/"))
        assertNull(PushPlan.publishUrl("not a url"))
        assertNull(PushPlan.publishUrl(null))
    }

    @Test fun `empty chain is filled every step for a day plus a backstop`() {
        val plan = PushPlan.plan(now, emptyList())
        val chain = plan.filter { it <= now + HORIZON_MS }
        assertEquals((HORIZON_MS / STEP_MS).toInt(), chain.size)
        assertEquals(now + STEP_MS, chain.first())
        chain.zipWithNext().forEach { (a, b) -> assertEquals(STEP_MS, b - a) }
        assertEquals(now + BACKSTOP_MS, plan.last())
        assertTrue("within ntfy's 3-day limit", plan.all { it - now in 10_000L..(72 * h) })
    }

    @Test fun `existing wake-ups are reused, only gaps are filled`() {
        val first = PushPlan.plan(now, emptyList())
        assertTrue(PushPlan.plan(now, first).isEmpty())
        // An hour later most of the chain is still pending: only the tail is added.
        val later = PushPlan.plan(now + h, first)
        assertTrue(later.size in 0..1)
        later.forEach { assertTrue(it > first.filter { t -> t <= now + HORIZON_MS }.max()) }
    }

    @Test fun `no gap is ever longer than a step`() {
        val pending = listOf(now + 2 * h, now + 9 * h, now + 30 * h)
        val all = (pending + PushPlan.plan(now, pending)).filter { it <= now + HORIZON_MS }.sorted()
        (listOf(now) + all).zipWithNext().forEach { (a, b) -> assertTrue("gap ${(b - a) / h}h", b - a <= STEP_MS) }
        assertTrue((listOf(now) + all).last() >= now + HORIZON_MS - STEP_MS)
    }

    @Test fun `daily volume stays far under the free limit`() {
        // Simulate a week of wake-ups every 3 h, each topping up the chain.
        var pending = emptyList<Long>()
        var sent = 0
        var t = now
        while (t < now + 7 * 24 * h) {
            val add = PushPlan.plan(t, pending); sent += add.size
            pending = PushPlan.prune(t, pending + add)
            t += STEP_MS
        }
        assertTrue("sent $sent in a week", sent / 7 <= 12)
    }

    @Test fun `past entries are pruned and the list is capped`() {
        val p = PushPlan.prune(now, listOf(now - 1, now, now + 5) + (1..40).map { now + it * h })
        assertFalse(p.any { it <= now })
        assertEquals(PushPlan.MAX_PENDING, p.size)
    }

    @Test fun `refill throttle`() {
        assertTrue(PushPlan.refillDue(now, 0))
        assertFalse(PushPlan.refillDue(now, now - 10 * 60_000))
        assertTrue(PushPlan.refillDue(now, now - PushPlan.REFILL_GAP_MS))
        assertTrue("clock moved back", PushPlan.refillDue(now, now + h))
    }

    @Test fun `ntfy echo tells scheduled from instant delivery`() {
        val s = now / 1000
        assertTrue(PushPlan.acceptedAsScheduled("""{"id":"x","time":${s + 10800},"event":"message"}""", now, 3 * h))
        assertFalse(PushPlan.acceptedAsScheduled("""{"id":"x","time":$s,"event":"message"}""", now, 3 * h))
        assertFalse(PushPlan.acceptedAsScheduled("Created", now, 3 * h))
        assertFalse(PushPlan.acceptedAsScheduled("", now, 3 * h))
    }
}
