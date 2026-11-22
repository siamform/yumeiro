package dev.asfakul.yumeiro.ui.screens.video

// F-12 vertical mode and Shorts chrome (ADR-056). Fail-before: VerticalMode, DragRule, DragMode.SWIPE_UP,
// ShortsWindow.chromeMayHide / tap and the flags VERTICAL_MODE / SHORTS_AUTOHIDE do not exist.

import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.ui.screens.youtube.ShortsWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F12VerticalModeTest {
    @Test fun nineBySixteenIsPortraitButThreeByFourAndLandscapeAreNot() {
        assertTrue(VerticalMode.isPortrait(1080, 1920))
        assertTrue("the test clip", VerticalMode.isPortrait(180, 320))
        assertTrue("9:19.5", VerticalMode.isPortrait(1080, 2340))
        assertFalse("3:4", VerticalMode.isPortrait(960, 1280))
        assertFalse("square", VerticalMode.isPortrait(720, 720))
        assertFalse("16:9", VerticalMode.isPortrait(1920, 1080))
        assertFalse("unknown size", VerticalMode.isPortrait(0, 0))
    }

    @Test fun rotationAndNonSquarePixelsAreTakenIntoAccount() {
        assertTrue("a phone clip stored 1920x1080 with a 90° turn", VerticalMode.isPortrait(1920, 1080, rotationDegrees = 90))
        assertFalse("1080x1920 turned 270° is landscape", VerticalMode.isPortrait(1080, 1920, rotationDegrees = 270))
        assertTrue("180° keeps it", VerticalMode.isPortrait(1080, 1920, rotationDegrees = 180))
        assertFalse("anamorphic: 720x1280 shown 2x wide", VerticalMode.isPortrait(720, 1280, pixelRatio = 2f))
    }

    @Test fun autoEnterOnlyOnAPhoneFullPlayerThatTheUserHasNotLeft() {
        fun go(enabled: Boolean = true, tv: Boolean = false, tablet: Boolean = false, minimized: Boolean = false,
               pip: Boolean = false, portrait: Boolean = true, userLeft: Boolean = false, already: Boolean = false) =
            VerticalMode.autoEnter(enabled, tv, tablet, minimized, pip, portrait, userLeft, already)
        assertTrue(go())
        assertFalse("flag off", go(enabled = false))
        assertFalse("TV", go(tv = true))
        assertFalse("tablet", go(tablet = true))
        assertFalse("floating", go(minimized = true))
        assertFalse("PiP", go(pip = true))
        assertFalse("landscape video", go(portrait = false))
        assertFalse("the user left vertical mode this session", go(userLeft = true))
        assertFalse("already on", go(already = true))
    }

    @Test fun centreSwipesFollowTheQueueAndALockedScreenIgnoresThem() {
        assertEquals(VerticalMode.Swipe.NEXT, VerticalMode.swipeUp(hasNext = true, locked = false))
        assertEquals("last item", VerticalMode.Swipe.NONE, VerticalMode.swipeUp(hasNext = false, locked = false))
        assertEquals(VerticalMode.Swipe.NONE, VerticalMode.swipeUp(hasNext = true, locked = true))
        assertEquals(VerticalMode.Swipe.PREVIOUS, VerticalMode.swipeDown(hasPrevious = true, locked = false))
        assertEquals("first item: down leaves vertical mode", VerticalMode.Swipe.LEAVE, VerticalMode.swipeDown(hasPrevious = false, locked = false))
        assertEquals(VerticalMode.Swipe.NONE, VerticalMode.swipeDown(hasPrevious = true, locked = true))
    }

    @Test fun onlyTheCentreThirdSwipesUpAndSidesStayBrightnessAndVolume() {
        val w = 1000f
        fun pick(x: Float, dx: Float, dy: Float, up: Boolean = true, down: Boolean = true, held: Boolean = false) =
            DragRule.pick(held, dx, dy, x, w, swipeDown = down, swipeUp = up)
        assertEquals(DragMode.SWIPE_UP, pick(500f, 0f, -80f))
        assertEquals(DragMode.SWIPE_DOWN, pick(500f, 0f, 80f))
        assertEquals("no queue: centre up is volume as before", DragMode.VOLUME, pick(500f, 0f, -80f, up = false))
        assertEquals(DragMode.BRIGHTNESS, pick(120f, 0f, -80f))
        assertEquals(DragMode.VOLUME, pick(880f, 0f, -80f))
        assertEquals(DragMode.SEEK, pick(500f, 90f, -20f))
        assertEquals("hold then up does nothing", DragMode.NONE, pick(500f, 0f, -80f, held = true))
        assertEquals(DragMode.HOLD_SWIPE_DOWN, pick(500f, 0f, 80f, held = true))
    }

    @Test fun shortsChromeHidesOnlyWhilePlayingAndATapWhileHiddenOnlyShowsIt() {
        assertTrue(ShortsWindow.chromeMayHide(autohide = true, playing = true, buffering = false, error = false))
        assertFalse("paused", ShortsWindow.chromeMayHide(true, playing = false, buffering = false, error = false))
        assertFalse("buffering", ShortsWindow.chromeMayHide(true, playing = true, buffering = true, error = false))
        assertFalse("error", ShortsWindow.chromeMayHide(true, playing = true, buffering = false, error = true))
        assertFalse("flag off", ShortsWindow.chromeMayHide(false, playing = true, buffering = false, error = false))
        assertEquals(ShortsWindow.Tap.SHOW_CHROME, ShortsWindow.tap(chromeShown = false))
        assertEquals(ShortsWindow.Tap.TOGGLE_PLAY, ShortsWindow.tap(chromeShown = true))
        assertEquals(3_500L, ShortsWindow.CHROME_IDLE_MS)
    }

    @Test fun theNewBehaviourHasItsOwnKillSwitches() {
        assertTrue(FeatureFlags.Flag.VERTICAL_MODE.default)
        assertTrue(FeatureFlags.Flag.SHORTS_AUTOHIDE.default)
        assertEquals("f12_vertical_mode", FeatureFlags.Flag.VERTICAL_MODE.key)
        assertEquals("f12_shorts_autohide", FeatureFlags.Flag.SHORTS_AUTOHIDE.key)
    }

    /** F-12-FLAG: F-12 is on for everyone once Verified (device 44916676685 / 44916693769, CI 44918012619). */
    @Test fun f12IsOnByDefault() {
        assertTrue("f12_vertical_shorts must be on by default", FeatureFlags.Flag.SHORTS_VERTICAL.default)
        assertEquals("f12_vertical_shorts", FeatureFlags.Flag.SHORTS_VERTICAL.key)
    }
}
