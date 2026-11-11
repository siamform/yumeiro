package dev.asfakul.yumeiro.ui

import dev.asfakul.yumeiro.ui.components.ScrollMemory
import dev.asfakul.yumeiro.ui.components.TabSwipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationFlowTest {
    @Test fun swipeRightToLeftGoesToTheNextTab() {
        assertEquals(1, TabSwipe.decide(dx = -300f, dy = 20f, velocityX = 0f, distancePx = 200f, flingPx = 2000f))
        assertEquals(-1, TabSwipe.decide(dx = 300f, dy = 20f, velocityX = 0f, distancePx = 200f, flingPx = 2000f))
    }

    @Test fun verticalOrShortMovesAreNotTabSwipes() {
        assertEquals(0, TabSwipe.decide(dx = -300f, dy = 260f, velocityX = 0f, distancePx = 200f, flingPx = 2000f))
        assertEquals(0, TabSwipe.decide(dx = -60f, dy = 0f, velocityX = 0f, distancePx = 200f, flingPx = 2000f))
        // A quick flick counts even when it is short.
        assertEquals(1, TabSwipe.decide(dx = -90f, dy = 0f, velocityX = -3000f, distancePx = 200f, flingPx = 2000f))
    }

    @Test fun tabTargetsStopAtTheEnds() {
        assertEquals(1, TabSwipe.target(0, 1, 5))
        assertNull(TabSwipe.target(0, -1, 5))
        assertNull(TabSwipe.target(4, 1, 5))
        assertNull(TabSwipe.target(-1, 1, 5)) // not on a tab page
    }

    @Test fun scrollPositionsAreKeptForAWhile() {
        ScrollMemory.clear()
        ScrollMemory.put("home", 12, 40, now = 1_000L)
        assertEquals(12, ScrollMemory.get("home", now = 2_000L)?.index)
        assertNull(ScrollMemory.get("home", now = 1_000L + ScrollMemory.TTL_MS + 1))
        ScrollMemory.put("home", 0, 0, now = 3_000L)
        assertNull(ScrollMemory.get("home", now = 3_000L))
    }

    @Test fun staticPagesAreReusedInsteadOfStacked() {
        val sub = dev.asfakul.yumeiro.ui.navigation.SubNav
        org.junit.Assert.assertTrue(sub.reusable(dev.asfakul.yumeiro.ui.navigation.Screen.Extensions.route))
        org.junit.Assert.assertTrue(sub.reusable(dev.asfakul.yumeiro.ui.navigation.Screen.ExtensionMarket.route))
        org.junit.Assert.assertFalse(sub.reusable("videos/online/title?api=api&url=https%3A%2F%2Fx%2Fy"))
        org.junit.Assert.assertFalse(sub.reusable(dev.asfakul.yumeiro.ui.navigation.Screen.SourcesHub.route(1)))
        org.junit.Assert.assertFalse(sub.reusable("stream/playlist/abc"))
    }
}
