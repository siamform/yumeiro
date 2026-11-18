/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.manga

import dev.asfakul.yumeiro.sources.manga.MangaPagePlan.PAGE_BUDGET
import dev.asfakul.yumeiro.sources.manga.MangaPagePlan.PAGE_BUDGET_LOW_RAM
import dev.asfakul.yumeiro.sources.manga.MangaPagePlan.STRIP_HEIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** G7-MR1: tall pages are decoded in strips under a per-page budget; ordinary pages are unchanged. */
class MangaPagePlanTest {
    private fun covers(p: MangaPagePlan.Plan, h: Int) {
        assertEquals(0, p.bands.first().top)
        assertEquals(h, p.bands.last().bottom)
        p.bands.zipWithNext().forEach { (a, b) -> assertEquals(a.bottom, b.top) }
        p.bands.forEach { assertTrue("decoded strip taller than $STRIP_HEIGHT", (it.bottom - it.top + p.sample - 1) / p.sample <= STRIP_HEIGHT) }
    }

    @Test fun ordinaryPageIsOneBitmapAsBefore() {
        val p = MangaPagePlan.plan(800, 1200, PAGE_BUDGET)
        assertEquals(1, p.sample); assertEquals(1, p.bands.size); assertEquals(800, p.outWidth); assertEquals(1200, p.outHeight)
        val wide = MangaPagePlan.plan(2400, 3000, PAGE_BUDGET)
        assertEquals("width cap 1600 kept", 2, wide.sample); assertEquals(1, wide.bands.size)
    }

    @Test fun tallPageIsStripsAtFullResolution() {
        val p = MangaPagePlan.plan(1000, 12_000, PAGE_BUDGET)
        assertEquals(1, p.sample); assertEquals(6, p.bands.size); covers(p, 12_000)
        val low = MangaPagePlan.plan(1000, 12_000, PAGE_BUDGET_LOW_RAM)
        assertEquals("48 MB page still fits the low-RAM budget", 1, low.sample)
    }

    @Test fun pageOverTheBudgetIsSampledAndStaysUnderIt() {
        for ((budget, w, h) in listOf(Triple(PAGE_BUDGET, 1440, 18_500), Triple(PAGE_BUDGET_LOW_RAM, 1440, 18_500), Triple(PAGE_BUDGET, 1200, 120_000))) {
            val p = MangaPagePlan.plan(w, h, budget)
            assertTrue("bytes ${p.bytes} over $budget", p.bytes <= budget)
            assertTrue(p.sample >= 2)
            covers(p, h)
        }
        assertEquals(2, MangaPagePlan.plan(1440, 18_500, PAGE_BUDGET).sample)
    }

    @Test(expected = IllegalArgumentException::class) fun notAnImage() { MangaPagePlan.plan(0, 0, PAGE_BUDGET) }
}
