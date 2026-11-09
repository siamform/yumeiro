/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.scrobble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackNamesTest {
    @Test fun artistChannels() {
        assertEquals("Adele", TrackNames.artist("Adele - Topic"))
        assertEquals("Adele", TrackNames.artist("AdeleVEVO"))
        assertEquals("YOASOBI", TrackNames.artist("YOASOBI Official"))
    }

    @Test fun titleNoise() {
        assertEquals("Hello", TrackNames.title("Adele - Hello (Official Music Video)", "Adele"))
        assertEquals("Song", TrackNames.title("Song [MV] | 4K", ""))
        assertEquals("Song", TrackNames.title("Song (Lyrics)", ""))
        assertEquals("Song (Remix)", TrackNames.title("Song (Remix)", ""))
    }

    @Test fun unknowns() {
        assertTrue(TrackNames.isUnknown("<unknown>")); assertTrue(TrackNames.isUnknown(" ")); assertTrue(TrackNames.isUnknown(null))
    }
}
