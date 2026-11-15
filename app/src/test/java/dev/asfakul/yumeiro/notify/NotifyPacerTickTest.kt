package dev.asfakul.yumeiro.notify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * G12 X5-2: an alarm tick inside the 12-minute gap (boot, app update, an older alarm) must not
 * show the next alert; it re-arms for the end of the gap. Device run 43754318750 (api33) showed an
 * alert 29 s after it was queued, 11 minutes early.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotifyPacerTickTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private fun prefs() = ctx.getSharedPreferences("notify_pacer", Context.MODE_PRIVATE)

    private fun queueOne(lastShown: Long): String {
        prefs().edit().clear().putLong("last_shown", lastShown).commit()
        val title = "tick test ${System.nanoTime()}"
        var shown = false
        NotifyPacer.offer(ctx, Inbox.Item("tick-1", Inbox.Kind.NEWS, title, "body", System.currentTimeMillis()), 0x7A12) { _, _ -> shown = true }
        assertFalse("inside the gap the offer queues", shown)
        assertTrue(prefs().getString("queue", "").orEmpty().contains(title))
        return title
    }

    @Test fun earlyTickKeepsTheItemQueued() {
        val now = System.currentTimeMillis()
        val title = queueOne(lastShown = now - 60_000)
        NotifyPacer.deliverNext(ctx)
        assertTrue("still queued after an early tick", prefs().getString("queue", "").orEmpty().contains(title))
        assertEquals("last_shown unchanged", now - 60_000, prefs().getLong("last_shown", 0))
    }

    @Test fun onTimeTickShowsAndDrains() {
        val now = System.currentTimeMillis()
        // Queue inside the gap, then let the gap pass (move last_shown back), as the alarm would.
        val title = queueOne(lastShown = now - 60_000)
        prefs().edit().putLong("last_shown", now - NotifyPacer.GAP_MS - 1_000).commit()
        NotifyPacer.deliverNext(ctx)
        assertFalse("drained by an on-time tick", prefs().getString("queue", "").orEmpty().contains(title))
        assertTrue("last_shown moved to now", prefs().getLong("last_shown", 0) >= now)
    }

    @Test fun pureRule() {
        val last = 10_000_000L
        assertFalse(NotifyPacer.tickMayShow(last + 30_000, last))
        assertFalse(NotifyPacer.tickMayShow(last + NotifyPacer.GAP_MS - 10_000, last))
        assertTrue("small drift is allowed", NotifyPacer.tickMayShow(last + NotifyPacer.GAP_MS - 3_000, last))
        assertTrue(NotifyPacer.tickMayShow(last + NotifyPacer.GAP_MS, last))
        assertTrue("first ever", NotifyPacer.tickMayShow(last, 0))
    }
}
