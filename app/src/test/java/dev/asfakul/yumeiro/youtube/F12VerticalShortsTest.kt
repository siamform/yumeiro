package dev.asfakul.yumeiro.youtube

// F-12 9:16 auto-open (ledger F-12): YouTube's own Short mark (vertical, up to 3 min) opens the Shorts player,
// so a 2:30 Short no longer opens the landscape player; a landscape clip in the Shorts player is fitted, not
// cropped. Fail-before: YtItem has no `vertical`, isShort() is length-only, ShortsWindow.resizeFor is missing.

import androidx.media3.ui.AspectRatioFrameLayout
import dev.asfakul.yumeiro.ui.screens.youtube.ShortsWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class F12VerticalShortsTest {
    private fun item(sec: Long, vertical: Boolean, live: Boolean = false, url: String = "https://www.youtube.com/watch?v=abcdefghijk") =
        YtItem("abcdefghijk", url, "t", "c", durationSec = sec, live = live, vertical = vertical)

    @Test fun aYouTubeMarkedShortOfTwoAndAHalfMinutesIsAShortWhenTheFlagIsOn() {
        assertTrue(item(150, vertical = true).isShort(vertical3min = true))
        assertFalse("kill switch off: length rule only", item(150, vertical = true).isShort(vertical3min = false))
    }

    @Test fun ordinaryVideosAndLiveStaySoWithTheFlagOn() {
        assertFalse(item(150, vertical = false).isShort(vertical3min = true))
        assertFalse(item(0, vertical = true, live = true).isShort(vertical3min = true))
        assertTrue("under a minute still counts", item(45, vertical = false).isShort(vertical3min = true))
        assertTrue("/shorts/ link still counts", item(150, vertical = false, url = "https://www.youtube.com/shorts/abcdefghijk").isShort(vertical3min = false))
    }

    @Test fun theExtractorsShortMarkReachesYtItem() {
        val s = StreamInfoItem(0, "https://www.youtube.com/watch?v=abcdefghijk", "A Short", StreamType.VIDEO_STREAM)
        s.duration = 150; s.isShortFormContent = true
        val yt = with(YouTube) { s.toYt(false) }!!
        assertTrue(yt.vertical)
        s.isShortFormContent = false
        assertFalse(with(YouTube) { s.toYt(false) }!!.vertical)
        s.isShortFormContent = true
        assertFalse("YouTube Music songs are never Shorts", with(YouTube) { s.toYt(true) }!!.vertical)
    }

    @Test fun portraitFillsAndLandscapeIsFittedInTheShortsPlayer() {
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, ShortsWindow.resizeFor(1080, 1920, fitLandscape = true))
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, ShortsWindow.resizeFor(1080, 1080, fitLandscape = true))
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, ShortsWindow.resizeFor(1920, 1080, fitLandscape = true))
        assertEquals("kill switch off: always fill", AspectRatioFrameLayout.RESIZE_MODE_ZOOM, ShortsWindow.resizeFor(1920, 1080, fitLandscape = false))
        assertEquals("unknown size fills", AspectRatioFrameLayout.RESIZE_MODE_ZOOM, ShortsWindow.resizeFor(0, 0, fitLandscape = true))
    }
}
