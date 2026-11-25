/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.tracking.v2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TRACK-2 (item 26.3): queue rules, title matching, chapter numbers, file round trip, the off switch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackV2Test {
    private fun item(unit: Int, pending: Set<String> = setOf("anilist"), key: String = "MANGA|src|/m/1", season: Int? = null) =
        TrackItem(key, TrackKind.MANGA, "One Piece", null, unit, season, pending = pending)

    @Test fun higherChapterReplacesLowerAndLowerIsIgnored() {
        var q = TrackQueue.enqueue(emptyList(), item(3))
        q = TrackQueue.enqueue(q, item(40))
        assertEquals(1, q.size); assertEquals(40, q[0].unit)
        val before = q
        q = TrackQueue.enqueue(q, item(3)) // re-reading chapter 3 must not send 3 after 40
        assertSame(before, q); assertEquals(40, q[0].unit)
    }

    @Test fun sameChapterMergesTrackers() {
        var q = TrackQueue.enqueue(emptyList(), item(5, setOf("anilist")))
        q = TrackQueue.enqueue(q, item(5, setOf("mal")))
        assertEquals(setOf("anilist", "mal"), q.single().pending)
    }

    @Test fun nothingToSendIsNotQueued() {
        assertTrue(TrackQueue.enqueue(emptyList(), item(5, emptySet())).isEmpty())
        assertTrue(TrackQueue.enqueue(emptyList(), item(0)).isEmpty())
    }

    @Test fun failedTrackerWaitsWithBackoffOthersFinish() {
        val now = 1_000_000L
        var q = TrackQueue.enqueue(emptyList(), item(7, setOf("anilist", "mal")))
        q = TrackQueue.afterRound(q, q[0].key, done = setOf("anilist"), error = "mal: HTTP 503", now = now)
        assertEquals(setOf("mal"), q[0].pending); assertEquals(1, q[0].tries)
        assertEquals(now + 60_000L, q[0].nextAt); assertEquals("mal: HTTP 503", q[0].lastError)
        assertTrue(TrackQueue.due(q, now).isEmpty()); assertEquals(1, TrackQueue.due(q, now + 60_000L).size)
        q = TrackQueue.afterRound(q, q[0].key, done = setOf("mal"), error = null, now = now)
        assertTrue(q.isEmpty())
    }

    @Test fun givesUpAfterMaxTries() {
        var q = TrackQueue.enqueue(emptyList(), item(7))
        repeat(TrackQueue.MAX_TRIES) { q = TrackQueue.afterRound(q, item(7).key, emptySet(), "down", 0L) }
        assertTrue(q.isEmpty())
        assertEquals(12 * 3_600_000L, TrackQueue.backoff(99))
    }

    @Test fun jsonRoundTrip() {
        val a = TrackItem("ANIME|api|/a", TrackKind.ANIME, "Frieren", 2023, 12, 1, 28, 52991, 154587, "tt1", 9, setOf("mal", "kitsu"), 2, 99L, "x", 5L)
        val b = item(3)
        assertEquals(listOf(a, b), TrackQueue.decode(TrackQueue.encode(listOf(a, b))))
        assertTrue(TrackQueue.decode("[{\"kind\":\"NOPE\"},{}]").isEmpty())
    }

    @Test fun chapterNumbers() {
        assertEquals(12, TrackQueue.chapterNumber("Chapter 12"))
        assertEquals(12, TrackQueue.chapterNumber("Ch. 12.5 - The end"))
        assertEquals(7, TrackQueue.chapterNumber("Vol.2 Chapter 7"))
        assertEquals(12, TrackQueue.chapterNumber("第12話"))
        assertEquals(45, TrackQueue.chapterNumber("Boss 45"))
        assertNull(TrackQueue.chapterNumber("Vol 2"))
        assertNull(TrackQueue.chapterNumber("Prologue"))
        assertEquals(31, TrackQueue.chapterNumber("whatever", 31.0))
    }

    private fun media(vararg m: JSONObject) = JSONArray().apply { m.forEach { put(it) } }
    private fun m(id: Int, mal: Int, year: Int, vararg titles: String) = JSONObject().put("id", id).put("idMal", mal).put("format", "TV").put("episodes", 12)
        .put("startDate", JSONObject().put("year", year)).put("title", JSONObject().put("romaji", titles[0]).put("english", titles.getOrNull(1) ?: JSONObject.NULL))
        .put("synonyms", JSONArray(titles.drop(2)))

    @Test fun matcherPicksRightTitleAndYear() {
        val c = TrackMatch.candidates(media(m(1, 10, 1999, "Hunter x Hunter"), m(2, 20, 2011, "Hunter x Hunter (2011)", "Hunter x Hunter")))
        assertEquals(2, TrackMatch.pick("Hunter x Hunter (Dub)", 2011, c)?.aniListId)
        assertEquals(1, TrackMatch.pick("Hunter x Hunter", 1999, c)?.aniListId)
    }

    @Test fun matcherRefusesWeakMatches() {
        val c = TrackMatch.candidates(media(m(1, 10, 2020, "Kimetsu no Yaiba", "Demon Slayer")))
        assertNull(TrackMatch.pick("Demon Lord Retry", null, c))
        assertNotNull(TrackMatch.pick("Demon Slayer [1080p]", null, c))
        assertEquals("one piece", TrackMatch.normalize("  One-Piece!! "))
        assertEquals(1.0, TrackMatch.similarity("Pokémon", "Pokemon"), 0.0)
    }

    @Test fun readingPlanNeverLowersAndCompletes() {
        assertNull(TrackReading.plan(3, 40, null))
        assertEquals(41 to false, TrackReading.plan(41, 40, 100))
        assertEquals(100 to true, TrackReading.plan(100, 40, 100))
    }

    @Test fun offByDefaultAndOffDoesNothing() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        TrackV2.init(c)
        assertFalse(TrackV2.enabled.value)
        TrackV2.onMangaChapter(c, "src", "/m", "Title", "Chapter 3", null, null)
        assertTrue(TrackV2.queue.value.isEmpty())
    }
}
