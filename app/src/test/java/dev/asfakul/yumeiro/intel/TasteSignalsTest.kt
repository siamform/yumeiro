/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.sources.manga.MangaChapter
import dev.asfakul.yumeiro.sources.manga.MangaItem
import dev.asfakul.yumeiro.sources.manga.MangaLibrary
import dev.asfakul.yumeiro.ui.theme.AccentTone
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

/** B20 S2: the taste signal store (events, weights, isolation, persistence, import) and its metadata. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TasteSignalsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val day = 86_400_000L
    private var now = 1_800_000_000_000L
    private fun ref(key: String = "o|api|/film", cat: MediaCategory = MediaCategory.MOVIE, title: String = "Film") =
        TasteSignals.Ref(key, cat, title, sourceTags = listOf("Thriller", "Mystery"))

    @Before fun setUp() {
        Profiles.resetForTests(ctx)
        TasteSignals.resetForTests()
        TasteSignals.clock = { now }
        File(ctx.filesDir, "intel").deleteRecursively()
        dev.asfakul.yumeiro.security.Privacy.setIncognito(ctx, false)
    }
    @After fun tearDown() {
        TasteSignals.resetForTests()
        MetaCache.disabled = true
        MetaCache.resetForTests()
        File(ctx.filesDir, "intel").deleteRecursively()
        Profiles.resetForTests(ctx)
    }

    @Test fun shortSessionIsOnlyAnOpenAndAFilmWatchedToTheEndIsComplete() {
        val r = ref()
        TasteSignals.played(ctx, r, null, 50_000, 6_000_000)
        val first = TasteSignals.get(ctx, r.key)!!
        assertFalse("50 s of a 100 min film is not a start", first.started)
        TasteSignals.played(ctx, r, null, 3_000_000, 6_000_000)
        assertTrue(TasteSignals.get(ctx, r.key)!!.started)
        assertTrue(TasteSignals.get(ctx, r.key)!!.substantial)
        assertFalse(TasteSignals.get(ctx, r.key)!!.completed)
        TasteSignals.played(ctx, r, null, 5_500_000, 6_000_000)
        assertTrue("a film watched to 92 % is complete", TasteSignals.get(ctx, r.key)!!.completed)
    }

    @Test fun seriesCompletesAtTheLastEpisodeOrEightyPercent() {
        val r = ref("o|api|/show", MediaCategory.SERIES, "Show")
        for (i in 0 until 7) TasteSignals.played(ctx, r, "ep$i", 1_400_000, 1_440_000, i, 10)
        assertFalse("7 of 10 episodes is not complete", TasteSignals.get(ctx, r.key)!!.completed)
        TasteSignals.played(ctx, r, "ep7", 1_400_000, 1_440_000, 7, 10)
        assertTrue("8 of 10 episodes finished completes a series", TasteSignals.get(ctx, r.key)!!.completed)
        val r2 = ref("o|api|/show2", MediaCategory.SERIES, "Show 2")
        TasteSignals.played(ctx, r2, "last", 1_400_000, 1_440_000, 11, 12)
        assertTrue("the last episode finished completes it", TasteSignals.get(ctx, r2.key)!!.completed)
    }

    @Test fun watchingAFinishedPartAgainAWeekLaterIsARewatch() {
        val r = ref()
        TasteSignals.played(ctx, r, null, 5_800_000, 6_000_000)
        now += 2 * day
        TasteSignals.played(ctx, r, null, 200_000, 6_000_000)
        TasteSignals.played(ctx, r, null, 5_800_000, 6_000_000)
        assertEquals("two days later is not a rewatch", 0, TasteSignals.get(ctx, r.key)!!.rewatches)
        now += 8 * day
        TasteSignals.played(ctx, r, null, 200_000, 6_000_000)
        TasteSignals.played(ctx, r, null, 5_800_000, 6_000_000)
        assertEquals(1, TasteSignals.get(ctx, r.key)!!.rewatches)
    }

    @Test fun weightsFollowThePlan() {
        val open = TasteSignals.Entry("a", MediaCategory.ANIME, "a", opens = 1, lastAt = now)
        val started = open.copy(parts = mapOf("_" to TasteSignals.Part(0.2f, true, 0)))
        val done = started.copy(completions = 1, parts = mapOf("_" to TasteSignals.Part(1f, true, now)))
        val loved = done.copy(feedback = TasteSignals.Feedback.LOVED, feedbackAt = now)
        val no = done.copy(feedback = TasteSignals.Feedback.NOT_FOR_ME, feedbackAt = now)
        assertTrue(open.weight(now) < started.weight(now))
        assertTrue(started.weight(now) < done.weight(now))
        assertTrue("Loved weighs more than complete", done.weight(now) < loved.weight(now))
        assertTrue("Not for me is negative", no.weight(now) < 0)
        val old = done.copy(lastAt = now - 360 * day)
        assertEquals("360 days fade to a quarter", done.weight(now) * 0.25, old.weight(now), 1e-6)
        val dropped = started.copy(parts = mapOf("_" to TasteSignals.Part(0.06f, true, 0)), lastAt = now - 30 * day)
        assertTrue(dropped.dropped(now))
    }

    @Test fun incognitoAndAdultTitlesRecordNothing() {
        dev.asfakul.yumeiro.security.Privacy.setIncognito(ctx, true)
        TasteSignals.opened(ctx, ref())
        dev.asfakul.yumeiro.security.Privacy.setIncognito(ctx, false)
        assertNull(TasteSignals.get(ctx, ref().key))
        TasteSignals.setFeedback(ctx, ref().copy(adult = true), TasteSignals.Feedback.LOVED)
        assertNull("18+ titles never shape a taste", TasteSignals.get(ctx, ref().key))
    }

    @Test fun eachProfileKeepsItsOwnTaste() {
        val kid = Profiles.add(ctx, "Kids", AccentTone.AO)
        Profiles.select(ctx, kid.id)
        TasteSignals.setFeedback(ctx, ref(), TasteSignals.Feedback.LOVED)
        TasteSignals.flush()
        assertTrue(File(ctx.filesDir, "profiles/${kid.id}/intel/signals.json").isFile)
        Profiles.select(ctx, Profiles.DEFAULT)
        assertNull("the Kids profile's rating leaked into the default profile", TasteSignals.get(ctx, ref().key))
        TasteSignals.setFeedback(ctx, ref(), TasteSignals.Feedback.NOT_FOR_ME)
        TasteSignals.flush()
        Profiles.select(ctx, kid.id)
        assertEquals(TasteSignals.Feedback.LOVED, TasteSignals.get(ctx, ref().key)?.feedback)
        Profiles.select(ctx, Profiles.DEFAULT)
        assertEquals(TasteSignals.Feedback.NOT_FOR_ME, TasteSignals.get(ctx, ref().key)?.feedback)
        assertTrue(File(ctx.filesDir, "intel/signals.json").isFile)
    }

    @Test fun storeSurvivesARestartAndRoundTrips() {
        TasteSignals.played(ctx, ref(), null, 5_800_000, 6_000_000)
        TasteSignals.setFeedback(ctx, ref(), TasteSignals.Feedback.LIKED)
        TasteSignals.flush()
        val before = TasteSignals.get(ctx, ref().key)!!
        TasteSignals.unload()
        val after = TasteSignals.get(ctx, ref().key)!!
        assertEquals(before.copy(parts = emptyMap()), after.copy(parts = emptyMap()))
        assertEquals(before.parts.keys, after.parts.keys)
        assertEquals(TasteSignals.decode(TasteSignals.encode(before)), TasteSignals.decode(TasteSignals.encode(after)))
    }

    @Test fun myListFavoritesAndReadingFeedTheStore() {
        OnlineBookmarks.unloadForTests()
        OnlineBookmarks.toggleFavorite(ctx, OnlineItem("Show X", "/x", "api", null, emptyMap(), "TvSeries"))
        val e = TasteSignals.get(ctx, TasteRefs.onlineKey("api", "/x"))
        assertNotNull(e); assertTrue(e!!.favorite && e.saved)
        assertEquals(MediaCategory.SERIES, e.category)
        OnlineBookmarks.toggleFavorite(ctx, OnlineItem("Show X", "/x", "api", null, emptyMap(), "TvSeries"))
        assertFalse(TasteSignals.get(ctx, TasteRefs.onlineKey("api", "/x"))!!.saved)

        MangaLibrary.unloadForTests()
        val item = MangaItem("prov", "/m/1", "Manga One", null)
        MangaLibrary.progress(ctx, item, MangaChapter("/m/1/c9", "Chapter 9"), 12, finished = true, isLast = true)
        val m = TasteSignals.get(ctx, TasteRefs.mangaKey("prov", "/m/1"))
        assertNotNull(m); assertTrue(m!!.completed)
        MangaLibrary.unloadForTests()
        OnlineBookmarks.unloadForTests()
    }

    @Test fun firstUseImportsTheExistingLibraries() {
        MangaLibrary.unloadForTests()
        TasteSignals.unload()
        val item = MangaItem("prov", "/m/2", "Manga Two", null)
        MangaLibrary.toggleSaved(ctx, item)
        MangaLibrary.flush()
        TasteSignals.forget(ctx) { true }
        assertFalse(TasteSignals.imported(ctx))
        TasteImport.ensure(ctx)
        assertTrue(TasteSignals.imported(ctx))
        assertTrue(TasteSignals.get(ctx, TasteRefs.mangaKey("prov", "/m/2"))!!.saved)
        MangaLibrary.unloadForTests()
    }

    @Test fun backupCarriesTheTasteAndALockedDefaultKeepsItOut() {
        assertEquals(Backup.Section.LIBRARY, Backup.sectionOf("files/intel/signals.json"))
        assertEquals(Backup.Section.LIBRARY, Backup.sectionOf("files/profiles/p2/intel/signals.json"))
        assertTrue(Profiles.hiddenFromBackup(ctx, "files/intel/signals.json", setOf(Profiles.DEFAULT)))
        assertTrue(Profiles.hiddenFromBackup(ctx, "files/profiles/p2/intel/signals.json", setOf("p2")))
    }

    @Test fun categoriesFromSourceTypes() {
        assertEquals(MediaCategory.ANIME, MediaCategory.ofType("Anime"))
        assertEquals(MediaCategory.ANIME, MediaCategory.ofType("AnimeMovie"))
        assertEquals(MediaCategory.ANIME, MediaCategory.ofType("OVA"))
        assertEquals(MediaCategory.MOVIE, MediaCategory.ofType("Movie"))
        assertEquals(MediaCategory.SERIES, MediaCategory.ofType("TvSeries"))
        assertEquals(MediaCategory.SERIES, MediaCategory.ofType("AsianDrama"))
        assertEquals(MediaCategory.SERIES, MediaCategory.ofType("TV series"))
        assertNull(MediaCategory.ofType("Live"))
        assertNull(MediaCategory.ofType(null))
    }

    // region metadata

    private val miraiNikki = JSONObject(
        """{"id":10620,"idMal":10620,"type":"ANIME","format":"TV","isAdult":false,
        "title":{"romaji":"Mirai Nikki","english":"The Future Diary","native":"未来日記"},"synonyms":["Future Diary"],
        "startDate":{"year":2011,"month":10,"day":9},"seasonYear":2011,"genres":["Action","Psychological","Thriller"],
        "tags":[{"name":"Battle Royale","rank":98,"category":"Theme-Action","isMediaSpoiler":false,"isAdult":false},
                {"name":"Death Game","rank":97,"category":"Theme-Other","isMediaSpoiler":false,"isAdult":false},
                {"name":"Yandere","rank":92,"category":"Cast-Traits","isMediaSpoiler":false,"isAdult":false},
                {"name":"Urban","rank":60,"category":"Setting-Universe","isMediaSpoiler":false,"isAdult":false},
                {"name":"Twist","rank":70,"category":"Theme-Other","isMediaSpoiler":true,"isAdult":false},
                {"name":"Nudity","rank":40,"category":"Sexual Content","isMediaSpoiler":false,"isAdult":true},
                {"name":"Minor","rank":10,"category":"Theme-Other","isMediaSpoiler":false,"isAdult":false}],
        "popularity":420000,"averageScore":73,"coverImage":{"large":"https://img.example/mn.jpg"},
        "studios":{"nodes":[{"name":"asread."}]},"staff":{"edges":[{"role":"Original Creator","node":{"name":{"full":"Sakae Esuno"}}}]},
        "description":"Yukiteru <i>Amano</i> keeps a diary."}"""
    )

    @Test fun aniListMetadataKeepsAspectsAndDropsAdultTags() {
        val m = MetaCache.parseAniList(miraiNikki)!!
        assertEquals("The Future Diary", m.title)
        assertEquals(10620, m.ids.anilist)
        assertEquals(2011, m.year)
        val byName = m.tags.associateBy { it.name }
        assertEquals(Aspect.CHARACTERS, byName["Yandere"]?.aspect)
        assertEquals(Aspect.THEMES, byName["Death Game"]?.aspect)
        assertEquals(Aspect.SETTING, byName["Urban"]?.aspect)
        assertEquals(Aspect.GENRE, byName["Thriller"]?.aspect)
        assertEquals(Aspect.CREATORS, byName["Sakae Esuno"]?.aspect)
        assertTrue(byName["Twist"]!!.spoiler)
        assertNull("adult tags are dropped", byName["Nudity"])
        assertNull("tags under rank 20 are noise", byName["Minor"])
        assertEquals("Yukiteru Amano keeps a diary.", m.description)
        assertEquals(m, MetaCache.decode(MetaCache.encode(m)))
        assertEquals(m, MetaCache.pick(listOf(m), "Mirai Nikki", 2011))
        assertNull("an unrelated name is not a match", MetaCache.pick(listOf(m), "Naruto", null))
    }

    @Test fun filmMetadataUsesGenresAndKeywords() {
        val m = MetaCache.parseHubTitle("""{"id":"movie:1","type":"movie","title":"Looper","tmdb":59967,"year":2012,"genres":["Action","Science Fiction"],"keywords":["time travel","assassin"],"rating":7,"votes":10000}""", "movie")!!
        assertEquals(59967, m.ids.tmdb)
        assertEquals(Aspect.THEMES, m.tags.first { it.name == "time travel" }.aspect)
        assertEquals(Aspect.GENRE, m.tags.first { it.name == "Action" }.aspect)
    }

    @Test fun lookupsPutRealTagsOnTheEntryOfTheProfileThatAsked() = kotlinx.coroutines.runBlocking {
        MetaCache.resetForTests()
        File(ctx.cacheDir, "intel-meta").deleteRecursively()
        var calls = 0
        val real = MetaCache.aniList
        MetaCache.aniList = { _, _ -> calls++; JSONObject().put("Page", JSONObject().put("media", org.json.JSONArray().put(miraiNikki))) }
        MetaCache.disabled = false
        val r = TasteSignals.Ref("o|api|/mn", MediaCategory.ANIME, "Mirai Nikki", year = 2011)
        TasteSignals.opened(ctx, r)
        MetaCache.idle()
        val e = TasteSignals.get(ctx, r.key)!!
        assertTrue(e.metaAt > 0)
        assertEquals(10620, e.ids.anilist)
        assertTrue(e.tags.any { it.name == "Yandere" && it.aspect == Aspect.CHARACTERS })
        // The answer is cached (public metadata): the same title in another profile asks nobody.
        val kid = Profiles.add(ctx, "Kids", AccentTone.AO)
        Profiles.select(ctx, kid.id)
        TasteSignals.opened(ctx, r)
        MetaCache.idle()
        assertEquals(1, calls)
        assertTrue(TasteSignals.get(ctx, r.key)!!.tags.any { it.name == "Yandere" })
        Profiles.select(ctx, Profiles.DEFAULT)
        MetaCache.aniList = real
    }

    // endregion
}
