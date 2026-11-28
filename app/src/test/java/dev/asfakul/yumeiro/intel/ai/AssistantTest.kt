/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.intel.Discovery
import dev.asfakul.yumeiro.intel.MediaCategory
import dev.asfakul.yumeiro.intel.MetaCache
import dev.asfakul.yumeiro.intel.TasteSignals
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.security.Privacy
import dev.asfakul.yumeiro.ui.theme.AccentTone
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** B21/B22 S5: reading the request, the answer contract, title checks and the per-profile chat history. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AssistantTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() { dev.asfakul.yumeiro.AppStartupForTests.await(); Profiles.resetForTests(ctx); AssistantChats.unload(); Privacy.setIncognito(ctx, false) }
    @After fun tearDown() { runCatching { AssistantChats.clear(ctx) }; AssistantChats.unload(); Privacy.setIncognito(ctx, false); Profiles.resetForTests(ctx) }

    @Test fun categoriesInKeepsTheOrderTheyAreNamed() {
        assertEquals(listOf(MediaCategory.MANGA, MediaCategory.ANIME), Assistant.categoriesIn("Manga that matches my favourite anime"))
        assertEquals(listOf(MediaCategory.MOVIE), Assistant.categoriesIn("a good film for tonight"))
        assertEquals(emptyList<MediaCategory>(), Assistant.categoriesIn("surprise me"))
    }

    @Test fun seedsInFindsTheNamedTitleOnly() {
        assertEquals(listOf("Future Diary"), Assistant.seedsIn("Anime similar to Future Diary"))
        assertEquals(listOf("Steins;Gate"), Assistant.seedsIn("something like Steins;Gate?"))
        assertEquals(listOf("Monster"), Assistant.seedsIn("Shows like \"Monster\" but shorter"))
        // "I like" is taste, not a reference title; categories are not titles either.
        assertEquals(emptyList<String>(), Assistant.seedsIn("I like dark stories"))
        assertEquals(emptyList<String>(), Assistant.seedsIn("similar to my anime"))
    }

    @Test fun rewatchAndUnderratedWords() {
        assertTrue(Assistant.wantsRewatch("something I can rewatch"))
        assertFalse(Assistant.wantsRewatch("something new"))
        assertTrue(Assistant.wantsUnderrated("underrated psychological manga"))
        assertTrue(Assistant.wantsUnderrated("hidden gems"))
    }

    @Test fun tagsInUsesTheRealVocabularyAndAliases() {
        val vocab = listOf("Death Game", "Psychological", "Thriller", "Time Loop", "Romance", "Yandere", "Iyashikei")
        val t = Assistant.tagsIn("psychological survival game anime with a yandere", vocab)
        assertTrue(t.toString(), "Death Game" in t && "Psychological" in t && "Yandere" in t)
        assertEquals(listOf("Iyashikei"), Assistant.tagsIn("something cozy", vocab))
        // An alias is only used when AniList really has the tag.
        assertEquals(emptyList<String>(), Assistant.tagsIn("survival game", listOf("Romance")))
        assertTrue(Assistant.tagsIn("romances", vocab).contains("Romance"))
    }

    @Test fun parseReadsTheContractAndKeepsProse() {
        val json = """Sure! {"reply":"Here you go","clarify":"","sections":[{"name":"Closest","items":[
            {"title":"Btooom!","year":2012,"category":"anime","tags":["Death Game"],"why":"Survival","differences":"Less romance",
             "reception":"Mixed","discovery":"Popular","confidence":"High"},{"title":"  ","year":0}]}]}"""
        val a = Assistant.parse(json, MediaCategory.ANIME)
        assertEquals("Here you go", a.reply)
        assertEquals(1, a.sections.size)
        val p = a.picks.single()
        assertEquals("Btooom!", p.title); assertEquals(2012, p.year); assertEquals(MediaCategory.ANIME, p.category)
        assertFalse("nothing is verified until checked", p.verified)
        val prose = Assistant.parse("I couldn't find anything.", null)
        assertEquals("I couldn't find anything.", prose.reply)
        assertTrue(prose.sections.isEmpty())
    }

    private fun rec(title: String, year: Int, anilist: Int) = Discovery.Rec("ani|$anilist", MediaCategory.ANIME, title, year = year, ids = TasteSignals.Ids(anilist = anilist))
    private fun pick(title: String, year: Int? = null, cat: MediaCategory? = MediaCategory.ANIME) =
        Assistant.Pick(title, year, cat, emptyList(), "why", "", "", "", "", verified = false)

    @Test fun verifyMarksDropsAndChecks() = runBlocking {
        val evidence = listOf(
            Assistant.Ev(rec("Btooom!", 2012, 1), listOf("Btooom"), "tag", "AniList"),
            Assistant.Ev(rec("Mirai Nikki", 2011, 2), listOf("Future Diary"), "seed", "AniList"),
            Assistant.Ev(rec("Danganronpa", 2013, 3), emptyList(), "tag", "AniList"),
        )
        val entries = listOf(
            TasteSignals.Entry("ani|2", MediaCategory.ANIME, "Mirai Nikki", ids = TasteSignals.Ids(anilist = 2), completions = 1),
            TasteSignals.Entry("ani|3", MediaCategory.ANIME, "Danganronpa", ids = TasteSignals.Ids(anilist = 3), dismissedAt = 5),
        )
        val answer = Assistant.Answer("ok", sections = listOf(
            Assistant.Section("A", listOf(pick("Btooom", 2012), pick("Future Diary"), pick("Danganronpa"), pick("Made Up Show", 2020), pick("Real Lookup"))),
            Assistant.Section("B", listOf(pick("Btooom!"))),
        ))
        var lookups = 0
        val lookup: suspend (Assistant.Pick) -> MetaCache.Meta? = { p ->
            lookups++
            if (p.title == "Real Lookup") MetaCache.Meta("Real Lookup", MediaCategory.ANIME, TasteSignals.Ids(anilist = 9), emptyList(), year = 2019) else null
        }
        val v = Assistant.verify(ctx, answer, evidence, entries, rewatch = false, lookup = lookup)
        val titles = v.picks.map { it.title }
        assertEquals(listOf("Btooom!", "Made Up Show", "Real Lookup"), titles)
        assertTrue("evidence match is verified with the real title", v.picks[0].verified)
        assertFalse("an unknown title is shown as unverified", v.picks[1].verified)
        assertNotNull(v.picks[1].note)
        assertTrue(v.picks[2].verified); assertEquals(2019, v.picks[2].year)
        assertEquals("only titles outside the evidence are looked up", 2, lookups)
        assertEquals("the second section only repeated a title", 1, v.sections.size)

        // Asking for a rewatch keeps a finished title, with a note; dismissed stays gone.
        val r = Assistant.verify(ctx, answer, evidence, entries, rewatch = true, lookup = { null })
        val finished = r.picks.first { it.title == "Mirai Nikki" }
        assertEquals("You finished this", finished.note)
        assertFalse(r.picks.any { it.title == "Danganronpa" })
    }

    private fun chat(id: String, title: String) = AssistantChats.Chat(id, title, MediaCategory.ANIME, listOf(
        AssistantChats.Turn(true, "Anime like Monster", at = 1),
        AssistantChats.Turn(false, "", Assistant.Answer("Here", sections = listOf(Assistant.Section("Closest", listOf(pick("Pluto").copy(verified = true))))), at = 2),
    ), at = System.currentTimeMillis())

    @Test fun chatsRoundTripAndStayInTheirProfile() {
        val c = chat("c1", "Like Monster")
        val back = AssistantChats.decode(JSONObject(AssistantChats.encode(c).toString()))!!
        assertEquals(c.title, back.title); assertEquals(2, back.turns.size)
        assertEquals("Pluto", back.turns[1].answer!!.picks.single().title)

        AssistantChats.put(ctx, c)
        assertEquals(listOf("Pluto"), AssistantChats.recentlySuggested(ctx))
        AssistantChats.unload()
        assertNotNull("saved to this profile's file", AssistantChats.get(ctx, "c1"))

        val kid = Profiles.add(ctx, "Kids", AccentTone.AO)
        Profiles.select(ctx, kid.id)
        AssistantChats.ensure(ctx)
        assertTrue("another profile sees no chats", AssistantChats.chats.value.isEmpty())
        assertEquals(emptyList<String>(), AssistantChats.recentlySuggested(ctx))

        Profiles.select(ctx, Profiles.DEFAULT)
        assertNotNull(AssistantChats.get(ctx, "c1"))
        AssistantChats.delete(ctx, "c1")
        AssistantChats.unload()
        assertEquals(null, AssistantChats.get(ctx, "c1"))
    }

    @Test fun incognitoSavesNothing() {
        Privacy.setIncognito(ctx, true)
        AssistantChats.put(ctx, chat("c2", "Private"))
        AssistantChats.unload()
        assertEquals("nothing written in Incognito", null, AssistantChats.get(ctx, "c2"))
    }
}
