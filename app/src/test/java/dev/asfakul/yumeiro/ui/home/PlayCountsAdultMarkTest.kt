/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.data.PlayCounts
import dev.asfakul.yumeiro.online.AdultContent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** NBUG-11 (ADR-023): the 18+ mark on play counts, and the one rule Most played uses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayCountsAdultMarkTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun prefs() = context.getSharedPreferences("play_counts", Context.MODE_PRIVATE)

    @Before fun setUp() { PlayCounts.resetForTests(); prefs().edit().clear().commit() }
    @After fun tearDown() { PlayCounts.resetForTests() }

    private fun reload(): List<PlayCounts.Entry> { PlayCounts.resetForTests(); PlayCounts.ensureLoaded(context); return PlayCounts.entries.value }

    @Test fun markIsSavedAndReadBack_oldFilesStayUnknown() {
        PlayCounts.record(context, PlayCounts.Kind.ONLINE, "A|u1", "a", "A", null, api = "A", url = "u1", adult = true)
        PlayCounts.record(context, PlayCounts.Kind.ONLINE, "B|u2", "b", "B", null, api = "B", url = "u2", adult = false)
        PlayCounts.record(context, PlayCounts.Kind.YOUTUBE, "yt1", "y", "ch", null)
        val back = reload().associateBy { it.id }
        assertEquals(true, back.getValue("A|u1").adult)
        assertEquals(false, back.getValue("B|u2").adult)
        assertNull(back.getValue("yt1").adult)
        // A file written before the mark existed.
        prefs().edit().putString("entries", """[{"k":"ONLINE","id":"C|u3","t":"c","s":"C","c":3,"at":1,"api":"C","url":"u3"}]""").commit()
        assertNull(reload().single().adult)
    }

    @Test fun aLaterPlayWithoutAMarkKeepsTheOldOne() {
        PlayCounts.record(context, PlayCounts.Kind.ONLINE, "A|u1", "a", "A", null, api = "A", url = "u1", adult = true)
        PlayCounts.record(context, PlayCounts.Kind.ONLINE, "A|u1", "a", "A", null, api = "A", url = "u1")
        val e = PlayCounts.entries.value.single()
        assertEquals(2, e.count); assertEquals(true, e.adult)
    }

    @Test fun learnAdultFillsOnlyUnknownOnlineEntriesAndSaves() {
        prefs().edit().putString("entries", """[
            {"k":"ONLINE","id":"C|u3","t":"c","s":"C","c":3,"at":1,"api":"C","url":"u3"},
            {"k":"ONLINE","id":"D|u4","t":"d","s":"D","c":2,"at":1,"api":"D","url":"u4","ad":false},
            {"k":"ONLINE","id":"E|u5","t":"e","s":"E","c":1,"at":1,"api":"E","url":"u5"}]""").commit()
        PlayCounts.ensureLoaded(context)
        PlayCounts.learnAdult(context) { e -> if (e.api == "C") true else null }
        val back = reload().associateBy { it.id }
        assertEquals(true, back.getValue("C|u3").adult)
        assertEquals(false, back.getValue("D|u4").adult) // known marks are never rewritten
        assertNull(back.getValue("E|u5").adult)          // still unknown
    }

    @Test fun hidePlayRule() {
        val loaded = mapOf("Adult Hub" to true, "Safe Hub" to false)
        assertTrue("stored 18+", AdultContent.hidePlay(true, "Safe Hub", "u", loaded))
        assertTrue("extension known 18+-only wins over an old false", AdultContent.hidePlay(false, "Adult Hub", "u", loaded))
        assertFalse("stored not 18+", AdultContent.hidePlay(false, "Safe Hub", "u", loaded))
        assertFalse("unknown, extension loaded and not 18+", AdultContent.hidePlay(null, "Safe Hub", "u", loaded))
        assertTrue("unknown, extension loaded and 18+", AdultContent.hidePlay(null, "Adult Hub", "u", loaded))
        assertTrue("unknown, extension not loaded: fail closed", AdultContent.hidePlay(null, "Gone Hub", "u", loaded))
    }
}
