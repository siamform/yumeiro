/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.data.PlayCounts
import dev.asfakul.yumeiro.online.NewEpisodesWorker
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRow
import dev.asfakul.yumeiro.security.Privacy
import dev.asfakul.yumeiro.ui.screens.home.HomeMoods
import dev.asfakul.yumeiro.ui.screens.home.newArrivals
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Data behind the R5 Home: play counts, new arrivals, moods and new-episode checks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() { PlayCounts.resetForTests(); context.getSharedPreferences("play_counts", Context.MODE_PRIVATE).edit().clear().commit() }
    @After fun tearDown() { Privacy.setIncognito(context, false); PlayCounts.resetForTests() }

    private fun item(api: String, n: Int) = OnlineItem("$api title $n", "https://$api.example/$n", api, null)
    private fun row(api: String, title: String, vararg n: Int) = OnlineRow(api, title, n.map { item(api, it) })

    @Test fun playCountsRankByCountThenRecency() {
        PlayCounts.record(context, PlayCounts.Kind.YOUTUBE, "a", "A", "ch", null, now = 1)
        PlayCounts.record(context, PlayCounts.Kind.YOUTUBE, "b", "B", "ch", null, now = 2)
        PlayCounts.record(context, PlayCounts.Kind.YOUTUBE, "a", "A", "ch", null, now = 3)
        PlayCounts.record(context, PlayCounts.Kind.DEVICE, "c", "C", "", null, now = 4)
        val e = PlayCounts.entries.value
        assertEquals(listOf("a", "c", "b"), e.map { it.id })
        assertEquals(2, e.first().count)
    }

    @Test fun playCountsSurviveRestart() {
        PlayCounts.record(context, PlayCounts.Kind.ONLINE, "api|u", "Show", "Api", "https://img", mapOf("Referer" to "x"), api = "Api", url = "u")
        PlayCounts.resetForTests()
        PlayCounts.ensureLoaded(context)
        val e = PlayCounts.entries.value.single()
        assertEquals("Show", e.title); assertEquals("x", e.headers["Referer"]); assertEquals("u", e.url)
    }

    @Test fun incognitoRecordsNothing() {
        Privacy.setIncognito(context, true)
        PlayCounts.record(context, PlayCounts.Kind.YOUTUBE, "a", "A", "ch", null)
        assertTrue(PlayCounts.entries.value.isEmpty())
    }

    @Test fun newArrivalsPreferLatestRowsAndInterleaveExtensions() {
        val out = newArrivals(listOf(
            "One" to listOf(row("One", "Popular", 1, 2), row("One", "Latest episodes", 3, 4, 5)),
            "Two" to listOf(row("Two", "Trending", 6, 7)),
        ), max = 4)
        // One's "Latest" row wins over its first row; Two has no arrival row, so its first row is used.
        assertEquals(listOf("One title 3", "Two title 6", "One title 4", "Two title 7"), out.map { it.name })
    }

    @Test fun moodFollowsTheClock() {
        assertEquals("morning", HomeMoods.forHour(7).key)
        assertEquals("day", HomeMoods.forHour(13).key)
        assertEquals("evening", HomeMoods.forHour(19).key)
        assertEquals("night", HomeMoods.forHour(2).key)
        val r = java.util.Random(1)
        repeat(20) { assertNotEquals("morning", HomeMoods.shuffle(HomeMoods.MORNING, listOf("city pop"), r).key) }
    }

    @Test fun moodPicksComeFromMatchingRowsOrStillShowSomething() {
        val channels = listOf("One" to listOf(row("One", "Action hits", 1, 2), row("One", "Romance", 3)))
        assertTrue(HomeMoods.extensionPicks(HomeMoods.DAY, channels, 1).all { it.name in setOf("One title 1", "One title 2") })
        assertTrue(HomeMoods.extensionPicks(HomeMoods.FOCUS, channels, 1).isNotEmpty())
    }

    @Test fun onlyOpenSeriesAreChecked() {
        fun b(name: String, s: OnlineBookmarks.Status, type: String? = "TvSeries") = OnlineBookmarks.Bookmark("Api", "u/$name", name, null, emptyMap(), type, s, 0L)
        val due = NewEpisodesWorker.due(listOf(
            b("plan", OnlineBookmarks.Status.PLAN), b("done", OnlineBookmarks.Status.COMPLETED), b("drop", OnlineBookmarks.Status.DROPPED),
            b("film", OnlineBookmarks.Status.WATCHING, "Movie"), b("watch", OnlineBookmarks.Status.WATCHING),
        ))
        assertEquals(listOf("watch", "plan"), due.map { it.name })
    }
}
