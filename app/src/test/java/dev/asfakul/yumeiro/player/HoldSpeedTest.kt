package dev.asfakul.yumeiro.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * P-2X (owner 2026-11-19): touch and hold the video plays at 2x and lifting the finger returns to the
 * speed the viewer had; a long press on the speed button toggles 1x / 2x. Live streams are never sped up.
 * Old code: no HoldSpeed (a hold only gave a haptic and, with a slide down, picture-in-picture).
 */
class HoldSpeedTest {
    @Test fun holdPlaysAtTwiceAndReleaseRestoresTheViewersSpeed() {
        val s = HoldSpeed()
        assertEquals(2f, s.start(current = 1.25f, live = false))
        assertEquals(1.25f, s.end())
        assertNull("a second release changes nothing", s.end())
    }

    @Test fun liveAndAlreadyFastVideosAreNotChanged() {
        val s = HoldSpeed()
        assertNull(s.start(current = 1f, live = true))
        assertNull(s.end())
        assertNull(s.start(current = 2f, live = false))
        assertNull(s.end())
    }

    @Test fun aSecondHoldWhileBoostedKeepsTheFirstSpeed() {
        val s = HoldSpeed()
        assertEquals(2f, s.start(current = 0.75f, live = false))
        assertNull(s.start(current = 2f, live = false))
        assertEquals(0.75f, s.end())
    }

    @Test fun quickToggleSwitchesBetweenNormalAndDouble() {
        assertEquals(2f, HoldSpeed.toggle(1f))
        assertEquals(2f, HoldSpeed.toggle(1.5f))
        assertEquals(1f, HoldSpeed.toggle(2f))
    }
}
