package dev.asfakul.yumeiro.player

// F-07 (ledger F-07, docs/proposals/FEATURE-F-07-external-player.md): hand-off to VLC / mpv / MX Player /
// Just Player with the stream's headers and subtitles in each app's documented extras, and the watched position
// read back for resume. Fail-before: ExternalPlayers does not exist.

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class F07ExternalPlayersTest {
    private val headers = mapOf("Referer" to "https://site.example/", "User-Agent" to "UA/1")
    private val subs = listOf(ExternalPlayers.Sub("https://s.example/en.vtt", "English"), ExternalPlayers.Sub("https://s.example/bn.srt", "Bangla"))

    @Test fun mxAndJustGetHeadersAsKeyValuePairsAndAllSubtitles() {
        for (app in listOf(ExternalPlayers.App.MX, ExternalPlayers.App.MX_PRO, ExternalPlayers.App.JUST)) {
            val x = ExternalPlayers.extras(app, "Ep 1", 61_000L, headers, subs, selectedSub = 1)
            assertArrayEquals(app.name, arrayOf("Referer", "https://site.example/", "User-Agent", "UA/1"), x["headers"] as Array<*>)
            assertEquals(61_000, x["position"])
            assertEquals(listOf("https://s.example/en.vtt", "https://s.example/bn.srt"), x["subs"])
            assertEquals(listOf("English", "Bangla"), (x["subs.name"] as Array<*>).toList())
            assertEquals(listOf("https://s.example/bn.srt"), x["subs.enable"])
            assertEquals(true, x["return_result"])
            assertTrue(ExternalPlayers.passesHeaders(app))
        }
    }

    @Test fun vlcGetsALongPositionAndOneSubtitleAndNoHeaders() {
        val x = ExternalPlayers.extras(ExternalPlayers.App.VLC, "Movie", 5_000L, headers, subs, selectedSub = 0)
        assertEquals(5_000L, x["position"])
        assertEquals(false, x["from_start"])
        assertEquals("https://s.example/en.vtt", x["subtitles_location"])
        assertFalse("headers" in x)
        assertFalse(ExternalPlayers.passesHeaders(ExternalPlayers.App.VLC))
    }

    @Test fun mpvGetsSubsAndIntPosition() {
        val x = ExternalPlayers.extras(ExternalPlayers.App.MPV, "Movie", 0L, headers, subs, selectedSub = null)
        assertEquals(0, x["position"])
        assertEquals(2, (x["subs"] as List<*>).size)
        assertFalse("subs.enable" in x)
    }

    @Test fun theGenericChooserGetsTheWidelyReadExtras() {
        val x = ExternalPlayers.extras(null, "T", 1_000L, headers, emptyList(), null)
        assertEquals("T", x["title"])
        assertEquals(1_000, x["position"])
        assertTrue("headers" in x)
        assertFalse("subs" in x)
        // F-07-RES: MX / Just Player return the position only when asked; the chooser can land on them.
        assertEquals(true, x["return_result"])
    }

    @Test fun noHeadersMeansNoHeadersExtra() {
        assertFalse("headers" in ExternalPlayers.extras(ExternalPlayers.App.MX, "T", 0L, emptyMap(), emptyList(), null))
    }

    @Test fun resultsAreReadFromEachAppsKeys() {
        assertEquals(ExternalPlayers.Watched(90_000L, 1_200_000L), ExternalPlayers.result(mapOf("position" to 90_000, "duration" to 1_200_000)))
        assertEquals(ExternalPlayers.Watched(5_000L, 60_000L), ExternalPlayers.result(mapOf("extra_position" to 5_000L, "extra_duration" to 60_000L)))
        // MX reports end_by=playback_completion with no position at the end.
        assertEquals(ExternalPlayers.Watched(60_000L, 60_000L), ExternalPlayers.result(mapOf("end_by" to "playback_completion", "duration" to 60_000)))
        assertNull(ExternalPlayers.result(emptyMap()))
        assertNull(ExternalPlayers.result(mapOf("position" to -1)))
    }

    @Test fun headersNeverIncludeBlankOrMultiLineValues() {
        val x = ExternalPlayers.extras(ExternalPlayers.App.MX, "T", 0L, mapOf("A" to "", "B" to "x\r\nEvil: 1", "C" to "ok"), emptyList(), null)
        assertArrayEquals(arrayOf("C", "ok"), x["headers"] as Array<*>)
    }
}
