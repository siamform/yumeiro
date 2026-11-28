/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.ui.theme.AccentTone
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** B20 S3: the taste model, the ranking (match, damping, MMR, gems, new) and the For You store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiscoveryTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val day = 86_400_000L
    private var now = 1_800_000_000_000L

    private fun t(name: String, aspect: Aspect = Aspect.THEMES, w: Float = 0.9f, spoiler: Boolean = false) = Tag(name, aspect, w, spoiler)
    private fun entry(key: String, cat: MediaCategory, tags: List<Tag>, completed: Boolean = true, feedback: TasteSignals.Feedback? = null, al: Int? = null) =
        TasteSignals.Entry(
            key, cat, key, ids = TasteSignals.Ids(anilist = al), tags = tags, metaAt = now, opens = 1, lastOpenAt = now,
            parts = mapOf("_" to TasteSignals.Part(1f, true, now)), completions = if (completed) 1 else 0, completedAt = if (completed) now else 0,
            firstAt = now, lastAt = now, feedback = feedback, feedbackAt = if (feedback != null) now else 0,
        )
    private fun meta(id: Int, title: String, tags: List<Tag>, pop: Int = 10_000, score: Int = 75, cat: MediaCategory = MediaCategory.ANIME, released: String? = null) =
        MetaCache.Meta(title, cat, TasteSignals.Ids(anilist = id), tags, 2020, null, pop, score, "TV", releasedAt = released)

    private val dark = listOf(t("Psychological"), t("Tragedy"), t("Anti-Hero", Aspect.CHARACTERS), t("Drama", Aspect.GENRE, 1f))
    private val cozy = listOf(t("Iyashikei"), t("Slice of Life", Aspect.GENRE, 1f), t("Food"), t("Female Protagonist", Aspect.CHARACTERS))

    @Before fun setUp() {
        Profiles.resetForTests(ctx)
        TasteSignals.resetForTests()
        Discovery.resetForTests()
        TasteSignals.clock = { now }
        Discovery.clock = { now }
        MetaCache.resetForTests()
        MetaCache.spacingMs = 0
        File(ctx.filesDir, "intel").deleteRecursively()
        dev.asfakul.yumeiro.security.Privacy.setIncognito(ctx, false)
    }
    private val realAniList = MetaCache.aniList

    @After fun tearDown() {
        MetaCache.aniList = realAniList
        TasteSignals.resetForTests()
        Discovery.resetForTests()
        MetaCache.disabled = true
        MetaCache.resetForTests()
        dev.asfakul.yumeiro.security.Privacy.setIncognito(ctx, false)
        File(ctx.filesDir, "intel").deleteRecursively()
        Profiles.resetForTests(ctx)
    }

    // region taste profile

    @Test fun oneTitleIsNotATasteAndSharedTagsWeighMore() {
        val one = TasteProfile.build(listOf(entry("a", MediaCategory.ANIME, dark)), MediaCategory.ANIME, now)
        assertEquals(1, one.titles)
        assertNull("one title gives no shelf", Discovery.tasteFor(listOf(entry("a", MediaCategory.ANIME, dark)), MediaCategory.ANIME, now))
        val two = TasteProfile.build(listOf(entry("a", MediaCategory.ANIME, dark), entry("b", MediaCategory.ANIME, listOf(t("Psychological"), t("Food")))), MediaCategory.ANIME, now)
        val themes = two.positive.getValue(Aspect.THEMES)
        assertTrue("a tag on both titles outweighs a tag on one", themes.getValue("psychological") > themes.getValue("tragedy"))
        assertEquals("psychological", two.top(1, Aspect.THEMES).first().first)
    }

    @Test fun notForMeBuildsANegativeTasteAndOtherCategoriesStayOut() {
        val p = TasteProfile.build(listOf(
            entry("a", MediaCategory.ANIME, dark), entry("b", MediaCategory.ANIME, dark),
            entry("c", MediaCategory.ANIME, cozy, feedback = TasteSignals.Feedback.NOT_FOR_ME),
            entry("m", MediaCategory.MOVIE, listOf(t("Heist"))),
        ), MediaCategory.ANIME, now)
        assertEquals(2, p.titles)
        assertTrue(p.negative.getValue(Aspect.THEMES).containsKey("iyashikei"))
        assertFalse("a film's tags never enter the anime taste", p.positive.values.any { it.containsKey("heist") })
    }

    @Test fun thinCategoriesBorrowTheClosestTaste() {
        val anime = listOf(entry("a", MediaCategory.ANIME, dark), entry("b", MediaCategory.ANIME, dark))
        assertEquals(MediaCategory.ANIME, Discovery.tasteFor(anime, MediaCategory.MANGA, now)?.category)
        assertEquals(MediaCategory.ANIME, Discovery.tasteFor(anime, MediaCategory.NOVEL, now)?.category)
        assertNull("films never borrow anime taste", Discovery.tasteFor(anime, MediaCategory.MOVIE, now))
        val films = listOf(entry("x", MediaCategory.SERIES, dark), entry("y", MediaCategory.SERIES, dark))
        assertEquals(MediaCategory.SERIES, Discovery.tasteFor(films, MediaCategory.MOVIE, now)?.category)
        val manga = anime + listOf(entry("m1", MediaCategory.MANGA, cozy), entry("m2", MediaCategory.MANGA, cozy))
        assertEquals("its own taste first", MediaCategory.MANGA, Discovery.tasteFor(manga, MediaCategory.MANGA, now)?.category)
    }

    // endregion

    // region similarity

    @Test fun matchFavoursSharedStoryTagsAndFameIsDampedNotDecisive() {
        val taste = TasteProfile.build(listOf(entry("a", MediaCategory.ANIME, dark), entry("b", MediaCategory.ANIME, dark)), MediaCategory.ANIME, now)
        val idf = Similarity.idf(listOf(dark, cozy, dark))
        assertTrue(Similarity.match(taste.positive, dark, idf) > 0.9)
        assertTrue(Similarity.match(taste.positive, cozy, idf) < 0.1)
        assertEquals(1.0, Similarity.damp(0), 1e-9)
        assertTrue(Similarity.damp(500_000) < Similarity.damp(5_000))
        assertTrue("damping is mild", Similarity.damp(500_000) > 0.5)
        val famousOff = Similarity.score(taste, Similarity.Candidate("f", MediaCategory.ANIME, "F", cozy + t("Tragedy"), popularity = 900_000), idf)
        val quietOn = Similarity.score(taste, Similarity.Candidate("q", MediaCategory.ANIME, "Q", dark, popularity = 2_000), idf)
        assertTrue("a better match beats fame", quietOn.score > famousOff.score)
    }

    @Test fun reasonsNeverNameASpoilerTag() {
        val taste = TasteProfile.build(listOf(
            entry("a", MediaCategory.ANIME, dark + t("Twist Ending", spoiler = false)), entry("b", MediaCategory.ANIME, dark + t("Twist Ending")),
        ), MediaCategory.ANIME, now)
        val cand = dark + t("Twist Ending", spoiler = true)
        val r = Similarity.reasons(taste.positive, taste.names, cand, Similarity.idf(listOf(cand)))
        assertTrue(r.isNotEmpty())
        assertFalse(r.any { it.name == "Twist Ending" })
        assertEquals(1.0, r.sumOf { it.share }, 1e-6)
    }

    @Test fun mmrSpreadsTheShelfOverDifferentIdeas() {
        val c = { k: String, tags: List<Tag>, s: Double -> Similarity.Scored(Similarity.Candidate(k, MediaCategory.ANIME, k, tags), s, s, emptyList()) }
        val list = listOf(c("d1", dark, 0.95), c("d2", dark, 0.94), c("d3", dark, 0.93), c("z", cozy, 0.80))
        val pick = Similarity.mmr(list, 2)
        assertEquals("d1", pick[0].candidate.key)
        assertEquals("the second pick is the different idea, not a copy", "z", pick[1].candidate.key)
        assertEquals(4, Similarity.mmr(list, 9).size)
    }

    @Test fun gemsAreQuietAndWellRatedAndNewMeansThisSeason() {
        val pool = (1..10).map { Similarity.Candidate("k$it", MediaCategory.ANIME, "T$it", dark, popularity = it * 10_000, score = if (it == 2) 60 else 80) }
        val g = Similarity.gems(pool)
        assertTrue("k1" in g)
        assertFalse("rated under 70 is no gem", "k2" in g)
        assertFalse("a popular title is no gem", "k9" in g)
        assertTrue(Similarity.gems(pool.take(4)).isEmpty())
        val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        assertTrue(Similarity.isNew(today.minusDays(30).toString(), now))
        assertFalse(Similarity.isNew(today.minusDays(400).toString(), now))
        assertFalse(Similarity.isNew(today.plusDays(10).toString(), now))
        assertFalse(Similarity.isNew(null, now))
    }

    // endregion

    // region ranking and store

    @Test fun rankDropsUnrelatedTitlesAndCarriesReasons() {
        val entries = listOf(entry("a", MediaCategory.ANIME, dark), entry("b", MediaCategory.ANIME, dark))
        val taste = Discovery.tasteFor(entries, MediaCategory.ANIME, now)!!
        val found = listOf(
            Candidates.Found(meta(1, "Dark One", dark), listOf("a")),
            Candidates.Found(meta(2, "Cozy One", listOf(t("Iyashikei"), t("Cooking")))),
            Candidates.Found(meta(3, "Half", listOf(t("Psychological"), t("Food")))),
        )
        val recs = Discovery.rank(MediaCategory.ANIME, taste, found, entries, now)
        assertEquals("al:1", recs.first().key)
        assertFalse("a title sharing nothing is not a pick", recs.any { it.key == "al:2" })
        assertTrue(recs.first().match in 1..99)
        assertTrue(recs.first().reasons.isNotEmpty())
        assertEquals(listOf("a"), recs.first().because)
    }

    @Test fun recSurvivesJson() {
        val r = Discovery.Rec("al:5", MediaCategory.MANGA, "Title", "https://x/c.jpg", 2019, TasteSignals.Ids(5, 6), 77,
            listOf(Similarity.Reason("Tragedy", Aspect.THEMES, 0.6)), listOf("Seed"), gem = true, new = false, popularity = 1200, score = 81, format = "MANGA",
            tags = listOf(Tag("Tragedy", Aspect.THEMES, 0.9f)))
        val back = Discovery.decodeRec(MediaCategory.MANGA, JSONObject(Discovery.encodeRec(r).toString()))
        assertEquals(r.copy(reasons = back!!.reasons, tags = back.tags), back)
        assertEquals("Tragedy", back.reasons.single().name)
        assertEquals(Aspect.THEMES, back.tags.single().aspect)
        assertNull(Discovery.decodeRec(MediaCategory.MANGA, JSONObject().put("k", "x")))
    }

    private fun aniNode(id: Int, title: String, tags: List<Pair<String, String>>, pop: Int = 20_000, score: Int = 80, genre: String = "Drama"): JSONObject =
        JSONObject().put("id", id).put("type", "ANIME").put("format", "TV").put("isAdult", false)
            .put("title", JSONObject().put("english", title)).put("popularity", pop).put("averageScore", score)
            .put("startDate", JSONObject().put("year", 2021).put("month", 1).put("day", 1))
            .put("genres", JSONArray().put(genre))
            .put("tags", JSONArray().apply { tags.forEach { (n, cat) -> put(JSONObject().put("name", n).put("category", cat).put("rank", 90)) } })

    private fun watch(key: String, title: String, al: Int) {
        val ref = TasteSignals.Ref(key, MediaCategory.ANIME, title)
        TasteSignals.played(ctx, ref, null, 1_400_000, 1_440_000)
        TasteSignals.applyMeta(ctx, Profiles.currentId(ctx), key, TasteSignals.Ids(anilist = al), dark, 2020)
    }

    @Test fun refreshBuildsAShelfPerProfileAndKeepsItOffline() = runBlocking {
        // The background lookup queue stays off (MetaCache.disabled): only the candidate searches use the fake.
        var calls = 0
        MetaCache.aniList = { _, _ ->
            calls++
            val media = JSONArray().put(aniNode(101, "Shadow Court", listOf("Psychological" to "Theme-Drama", "Anti-Hero" to "Cast-Main Cast")))
                .put(aniNode(102, "Garden Tea", listOf("Iyashikei" to "Theme-Slice of Life"), genre = "Slice of Life"))
            JSONObject().put("Page", JSONObject().put("media", media))
                .put("Media", JSONObject().put("recommendations", JSONObject().put("nodes", JSONArray().put(JSONObject().put("rating", 5).put("mediaRecommendation", aniNode(103, "Grief Line", listOf("Tragedy" to "Theme-Drama")))))))
        }
        watch("o|api|/a", "A", 1); watch("o|api|/b", "B", 2)
        val shelf = Discovery.refresh(ctx, MediaCategory.ANIME)
        assertNotNull(shelf)
        val keys = shelf!!.recs.map { it.key }
        assertTrue("al:101" in keys)
        assertFalse("a cozy title sharing nothing is not picked", "al:102" in keys)
        assertTrue("seed recommendations come in", "al:103" in keys)
        assertNull(shelf.crossFrom)
        val before = calls
        assertEquals("fresh shelf: no new network call", shelf, Discovery.refresh(ctx, MediaCategory.ANIME))
        assertEquals(before, calls)
        // Offline: a forced refresh that fails keeps the old shelf.
        MetaCache.aniList = { _, _ -> throw java.io.IOException("offline") }
        assertEquals(shelf.recs, Discovery.refresh(ctx, MediaCategory.ANIME, force = true)?.recs)
        // Survives a restart (unload) from the per-profile file.
        Discovery.unload()
        assertEquals(keys, Discovery.shelf(ctx, MediaCategory.ANIME)?.recs?.map { it.key })
        // Another profile sees nothing of it.
        val other = Profiles.add(ctx, "Kid", AccentTone.entries.first())
        Profiles.select(ctx, other.id)
        TasteSignals.unload(); Discovery.unload()
        assertNull(Discovery.shelf(ctx, MediaCategory.ANIME))
        assertNull("the new profile has no taste: no shelf is invented", Discovery.refresh(ctx, MediaCategory.ANIME, force = true))
    }

    @Test fun notInterestedRemovesThePickAndUndoPutsItBack() {
        val r1 = Discovery.Rec("al:1", MediaCategory.ANIME, "One", tags = dark)
        val r2 = Discovery.Rec("al:2", MediaCategory.ANIME, "Two", tags = cozy)
        seedShelf(listOf(r1, r2))
        assertEquals(setOf("al:1", "al:2"), Discovery.shelf(ctx, MediaCategory.ANIME)!!.recs.map { it.key }.toSet())
        val before = Discovery.dismiss(ctx, r1)
        assertEquals(listOf("al:2"), Discovery.shelf(ctx, MediaCategory.ANIME)!!.recs.map { it.key })
        assertTrue(TasteSignals.get(ctx, "al:1")!!.dismissedAt > 0)
        assertTrue("a dismissed pick counts a little against its tags", TasteSignals.get(ctx, "al:1")!!.weight(now) < 0)
        Discovery.undoDismiss(ctx, r1, before, 0)
        assertEquals("back at the front", listOf("al:1", "al:2"), Discovery.shelf(ctx, MediaCategory.ANIME)!!.recs.map { it.key })
        assertEquals(0L, TasteSignals.get(ctx, "al:1")?.dismissedAt ?: 0L)
    }

    /** Puts a shelf in the store through a real refresh with a one-title fake. */
    private fun seedShelf(recs: List<Discovery.Rec>) = runBlocking {
        // The background lookup queue stays off (MetaCache.disabled): only the candidate searches use the fake.
        MetaCache.aniList = { _, _ ->
            JSONObject().put("Page", JSONObject().put("media", JSONArray().apply {
                recs.forEach { r -> put(aniNode(r.key.removePrefix("al:").toInt(), r.title, listOf("Psychological" to "Theme-Drama", "Iyashikei" to "Theme-Slice of Life"))) }
            }))
        }
        watch("o|api|/a", "A", 900); watch("o|api|/b", "B", 901)
        TasteSignals.applyMeta(ctx, Profiles.currentId(ctx), "o|api|/b", TasteSignals.Ids(anilist = 901), dark + cozy, 2020)
        TasteSignals.applyMeta(ctx, Profiles.currentId(ctx), "o|api|/a", TasteSignals.Ids(anilist = 900), dark + cozy, 2020)
        val s = Discovery.refresh(ctx, MediaCategory.ANIME, force = true)
        assertEquals(recs.map { it.key }.toSet(), s!!.recs.map { it.key }.toSet())
    }

    @Test fun completionPromptOnlyForAFinishedPickAndOnlyOnce() {
        val ref = TasteSignals.Ref("o|api|/pick", MediaCategory.ANIME, "Shadow Court")
        TasteSignals.played(ctx, ref, null, 1_400_000, 1_440_000)
        assertNull("finished, but never picked from For You", Discovery.promptFor(ctx))
        Discovery.opened(ctx, Discovery.Rec("al:101", MediaCategory.ANIME, "Shadow Court"))
        assertEquals(ref.key, Discovery.promptFor(ctx)?.key)
        TasteSignals.prompted(ctx, ref)
        assertNull("asked once (Skip counts)", Discovery.promptFor(ctx))
    }

    @Test fun ratedOrOldCompletionsAreNotAskedAndIncognitoRemembersNothing() {
        val ref = TasteSignals.Ref("o|api|/pick", MediaCategory.ANIME, "Shadow Court")
        dev.asfakul.yumeiro.security.Privacy.setIncognito(ctx, true)
        Discovery.opened(ctx, Discovery.Rec("al:101", MediaCategory.ANIME, "Shadow Court"))
        dev.asfakul.yumeiro.security.Privacy.setIncognito(ctx, false)
        TasteSignals.played(ctx, ref, null, 1_400_000, 1_440_000)
        assertNull("an Incognito open leaves no trace", Discovery.promptFor(ctx))
        Discovery.opened(ctx, Discovery.Rec("al:101", MediaCategory.ANIME, "Shadow Court"))
        TasteSignals.setFeedback(ctx, ref, TasteSignals.Feedback.LIKED)
        assertNull("already rated", Discovery.promptFor(ctx))
        TasteSignals.setFeedback(ctx, ref, null)
        now += 31 * day
        assertNull("finished over 30 days ago", Discovery.promptFor(ctx))
    }

    // endregion
}
