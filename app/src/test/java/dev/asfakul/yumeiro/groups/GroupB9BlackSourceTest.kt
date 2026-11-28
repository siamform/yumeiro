/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groups

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.blacksource.BlackSource
import dev.asfakul.yumeiro.online.blacksource.BlackSourceAniList
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** B9: BlackSource advanced filters (the Worker's allow-lists) and the source multi-select across ecosystems. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupB9BlackSourceTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    @After fun reset() { BlackSource.baseForTest = null; BlackSource.setSourcesOff(ctx, emptySet()) }

    @Test fun advancedFiltersGoIntoTheRequestInAFixedOrder() {
        BlackSource.baseForTest = "http://x/v1/blacksource"
        val f = BlackSource.Filter(
            genres = setOf("Drama"), without = setOf("Romance", "Ecchi"), formats = setOf("MOVIE", "TV"), statuses = setOf("FINISHED"),
            yearFrom = 2015, yearTo = 2020, minScore = 80, sort = "score",
        )
        assertEquals(
            "http://x/v1/blacksource/search?genres=Drama&without=Ecchi%2CRomance&formats=TV%2CMOVIE&statuses=FINISHED&from=2015&to=2020&score=80&sort=score",
            BlackSource.searchUrl(f),
        )
        assertEquals(10, f.count)
        // A narrowing filter alone is a search (the Worker accepts it too).
        assertFalse(BlackSource.Filter(formats = setOf("MOVIE")).empty)
        assertFalse(BlackSource.Filter(yearFrom = 2024).empty)
        assertTrue(BlackSource.Filter().empty)
    }

    @Test fun valuesOffTheListsAreDroppedAndYearsSwapped() {
        val c = BlackSource.Filter(
            genres = setOf("Drama", "Hentai"), without = setOf("Drama", "Horror", "Nope"), formats = setOf("TV", "MUSIC_VIDEO"),
            statuses = setOf("CANCELLED", "RELEASING"), yearFrom = 2022, yearTo = 2001, minScore = 85, sort = "DROP", demographic = "Adults",
        ).cleaned()
        assertEquals(setOf("Drama"), c.genres)
        assertEquals("a genre can't be wanted and left out at once", setOf("Horror"), c.without)
        assertEquals(setOf("TV"), c.formats)
        assertEquals(setOf("RELEASING"), c.statuses)
        assertEquals(2001, c.yearFrom); assertEquals(2022, c.yearTo)
        assertEquals(null, c.minScore); assertEquals("popular", c.sort); assertEquals(null, c.demographic)
        assertEquals(null, BlackSource.Filter(yearFrom = 19).cleaned().yearFrom)
    }

    @Test fun parseKeepsStatusAndScoreFromTheLists() {
        val p = BlackSource.parse("""{"items":[{"id":1,"title":"A","status":"FINISHED","score":87},{"id":2,"title":"B","status":"<x>","score":900}],"more":true}""")
        assertEquals("FINISHED", p.items[0].status); assertEquals(87, p.items[0].score)
        assertEquals(null, p.items[1].status); assertEquals(null, p.items[1].score)
    }

    @Test fun ownGenresMustNotHoldALeftOutGenre() {
        val f = BlackSource.Filter(genres = setOf("Fantasy"), without = setOf("Romance"))
        assertTrue(BlackSource.providerTagsMatch(listOf("Fantasy", "Action"), f))
        assertFalse(BlackSource.providerTagsMatch(listOf("Fantasy", "romance"), f))
    }

    @Test fun sourcesAreChosenAcrossEcosystemsAndKept() {
        val all = listOf("CS One", "CS Two", "Ani One", "JS One")
        var off = emptySet<String>()
        assertEquals(all, BlackSource.chosen(all, off) { it })
        // One ecosystem's switch: all on → all off; the others stay.
        off = BlackSource.toggleGroup(listOf("CS One", "CS Two"), off)
        assertEquals(listOf("Ani One", "JS One"), BlackSource.chosen(all, off) { it })
        // Partly on → the switch turns the whole group on.
        off = BlackSource.toggleGroup(listOf("CS One", "CS Two"), off - "CS Two")
        assertEquals(all, BlackSource.chosen(all, off) { it })
        BlackSource.setSourcesOff(ctx, setOf("JS One"))
        assertEquals(setOf("JS One"), BlackSource.sourcesOff(ctx))
        // A source installed later is asked until it is turned off.
        assertEquals(listOf("CS One", "New One"), BlackSource.chosen(listOf("CS One", "JS One", "New One"), BlackSource.sourcesOff(ctx)) { it })
    }

    @Test fun extraTimeNoteCountsEveryLookup() {
        assertEquals(36, BlackSource.lookups(30, 3, words = false))
        assertEquals(39, BlackSource.lookups(30, 3, words = true))
        assertEquals(10, BlackSource.lookups(5, 2, words = false))
        assertEquals(0, BlackSource.lookups(30, 0, words = true))
    }

    @Test fun phoneAsksAniListWithTheFixedQueryAndChosenValuesOnly() {
        val b = BlackSourceAniList.body(BlackSource.Filter(
            words = "  frieren ", genres = setOf("Fantasy", "Nope"), tags = listOf("Magic", "<script>"), demographic = "Shounen",
            without = setOf("Romance"), formats = setOf("TV"), statuses = setOf("FINISHED"), yearFrom = 2023, yearTo = 2020, minScore = 80, sort = "score",
        ), page = 2)!!
        assertEquals(BlackSourceAniList.QUERY, b.getString("query"))
        val v = b.getJSONObject("variables")
        assertEquals(2, v.getInt("page")); assertEquals("frieren", v.getString("search"))
        assertEquals("[\"SEARCH_MATCH\",\"SCORE_DESC\"]", v.getJSONArray("sort").toString())
        assertEquals("[\"Fantasy\"]", v.getJSONArray("genres").toString())
        assertEquals("[\"Magic\",\"Shounen\"]", v.getJSONArray("tags").toString())
        assertEquals("[\"Romance\"]", v.getJSONArray("without").toString())
        assertEquals(20191231, v.getInt("after")); assertEquals(20240101, v.getInt("before")); assertEquals(79, v.getInt("score"))
        assertEquals(null, BlackSourceAniList.body(BlackSource.Filter()))
    }

    private fun media(id: Int, genres: String, fmt: String = "MOVIE", status: String = "FINISHED", start: Int = 2016, score: Int = 85, extra: String = "") =
        """{"id":$id,"title":{"romaji":"T$id","english":"E$id"},"genres":[$genres],"format":"$fmt","status":"$status",
           "seasonYear":$start,"startDate":{"year":$start},"averageScore":$score,"episodes":1,"coverImage":{"large":"https://c/$id.jpg"},
           "tags":[{"name":"Shounen","category":"Demographic","rank":90},{"name":"Secret","rank":90,"isAdult":true}$extra]}"""

    @Test fun everyAniListTitleIsCheckedAgainOnThePhone() {
        val f = BlackSource.Filter(genres = setOf("Drama"), without = setOf("Romance"), formats = setOf("MOVIE"), statuses = setOf("FINISHED"),
            yearFrom = 2015, yearTo = 2020, minScore = 80)
        val rows = listOf(
            media(1, "\"Drama\""), media(2, "\"Drama\",\"Romance\""), media(3, "\"Drama\"", fmt = "TV"),
            media(4, "\"Drama\"", status = "RELEASING"), media(5, "\"Drama\"", start = 2021), media(6, "\"Drama\"", score = 79),
            media(7, "\"Drama\",\"Hentai\""), media(8, "\"Action\""), """{"id":0,"title":{"romaji":"bad"}}""", "\"junk\"",
        )
        val p = BlackSourceAniList.shape("""{"data":{"Page":{"pageInfo":{"hasNextPage":true},"media":[${rows.joinToString(",")}]}}}""", f)!!
        assertEquals(listOf(1), p.items.map { it.id })
        val t = p.items[0]
        assertEquals("T1", t.title); assertEquals("E1", t.english); assertEquals("Shounen", t.demographic)
        assertEquals("FINISHED", t.status); assertEquals(85, t.score); assertEquals(2016, t.year); assertEquals("https://c/1.jpg", t.cover)
        assertFalse("adult tags never reach the phone list", t.tags.contains("Secret"))
        assertTrue(p.more)
        assertEquals(null, BlackSourceAniList.shape("""{"errors":[{"message":"blocked"}]}""", f))
        assertEquals(null, BlackSourceAniList.shape("not json", f))
        assertTrue(JSONObject(media(9, "\"Drama\"")).has("id"))
    }
}
