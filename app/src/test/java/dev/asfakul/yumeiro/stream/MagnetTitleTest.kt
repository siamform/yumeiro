package dev.asfakul.yumeiro.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MAG-TITLE-1: a shared Sintel magnet played as "Network stream" (field probe 45328880256, api33-lowram):
 * a magnet URI has no path or host, so the title fell back. The player now uses the magnet's `dn` name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MagnetTitleTest {
    private val sintel = "magnet:?xt=urn:btih:08ada5a7a6183aae1e09d831df6748d566095a10&dn=Sintel&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337"

    @Test fun aSharedMagnetIsTitledByItsName() {
        assertEquals("Sintel", guessTitle(sintel))
        assertEquals("Sintel", StreamRequest(url = sintel).displayTitle)
    }

    @Test fun encodedNamesAreDecoded() {
        assertEquals("Big Buck Bunny (2008)", magnetName("magnet:?xt=urn:btih:dd82&dn=Big+Buck+Bunny+%282008%29"))
    }

    @Test fun aMagnetWithoutANameKeepsTheFallback() {
        assertNull(magnetName("magnet:?xt=urn:btih:BF2063A5E9A69F14716288FCA7C2D6078BDA7369"))
        assertEquals("Network stream", guessTitle("magnet:?xt=urn:btih:BF2063A5E9A69F14716288FCA7C2D6078BDA7369"))
        assertNull(magnetName("https://example.com/video.mp4?dn=x"))
    }
}
