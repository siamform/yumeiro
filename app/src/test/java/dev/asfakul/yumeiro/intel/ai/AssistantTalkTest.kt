/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel.ai

import dev.asfakul.yumeiro.intel.Aspect
import dev.asfakul.yumeiro.intel.MediaCategory
import dev.asfakul.yumeiro.intel.Tag
import dev.asfakul.yumeiro.intel.TasteSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R28 items 16 and 32: commands, routing, history budget, chat replies and starters. */
class AssistantTalkTest {
    @Test fun slashCommandsParseWithAliases() {
        assertEquals(AssistantTalk.Parsed("recom", "suggest some yandere female lead manhwa"), AssistantTalk.parse("/recom suggest some yandere female lead manhwa"))
        assertEquals("recom", AssistantTalk.parse("/rec x")!!.command)
        assertEquals(AssistantTalk.Parsed("translate", "仲間"), AssistantTalk.parse("/tr 仲間"))
        assertEquals(AssistantTalk.Parsed("glossary", "add nakama = friend"), AssistantTalk.parse("  /glossary add nakama = friend"))
        assertEquals(AssistantTalk.Parsed("help", ""), AssistantTalk.parse("/HELP"))
        assertNull(AssistantTalk.parse("/unknown thing"))
        assertNull(AssistantTalk.parse("recom x"))
    }

    @Test fun typingSlashSuggestsCommands() {
        assertEquals(AssistantTalk.COMMANDS.size, AssistantTalk.suggestions("/").size)
        assertEquals(listOf("recom", "remember"), AssistantTalk.suggestions("/re").map { it.name })
        assertEquals(listOf("translate"), AssistantTalk.suggestions("/tr").map { it.name })
        assertTrue("stops after a space", AssistantTalk.suggestions("/recom ").isEmpty())
        assertTrue(AssistantTalk.suggestions("hello").isEmpty())
    }

    @Test fun titleRequestsGoToResearchAndOtherMessagesToChat() {
        assertTrue(AssistantTalk.wantsTitles("Recommend anime similar to Future Diary"))
        assertTrue(AssistantTalk.wantsTitles("what should I watch tonight"))
        assertTrue(AssistantTalk.wantsTitles("Find underrated anime with yandere girls"))
        assertFalse(AssistantTalk.wantsTitles("Why did you pick the second one?"))
        assertFalse(AssistantTalk.wantsTitles("Explain the ending of Steins;Gate without spoilers"))
        assertTrue(AssistantTalk.isCorrection("That's a mistranslation, nakama means comrade"))
        assertFalse(AssistantTalk.isCorrection("Tell me more about it"))
    }

    @Test fun historyKeepsTheNewestTurnsThatFit() {
        val h = (1..10).map { AiMessage(it % 2 == 1, "m$it " + "x".repeat(100)) }
        val kept = AssistantTalk.fit(h, fixed = 0, budget = 400)
        assertTrue(kept.isNotEmpty() && kept.size < h.size)
        assertEquals(h.last(), kept.last())
        assertTrue(AssistantTalk.fit(h, fixed = 1_000, budget = 400).isEmpty())
        assertEquals(24_000, AssistantTalk.budget(0))
        assertEquals(6_000, AssistantTalk.budget(1_000))
        assertEquals(120_000, AssistantTalk.budget(1_048_576))
    }

    @Test fun chatRepliesParseJsonOrProse() {
        val (t, f, g) = AssistantTalk.parseChat("""{"reply":"Sure.","follow_ups":["More like it?","Why?"],"glossary":{"source":"nakama","target":"comrade"}}""")
        assertEquals("Sure.", t); assertEquals(2, f.size); assertEquals(AiPersonal.Term("nakama", "comrade"), g)
        val (t2, f2, g2) = AssistantTalk.parseChat("Just text.")
        assertEquals("Just text.", t2); assertTrue(f2.isEmpty()); assertNull(g2)
        assertEquals("Fenced.", AssistantTalk.parseChat("```json\n{\"reply\":\"Fenced.\",\"glossary\":null}\n```").first)
    }

    @Test fun startersUseTheProfilesOwnTitles() {
        val loved = TasteSignals.Entry("a|1", MediaCategory.ANIME, "Future Diary", feedback = TasteSignals.Feedback.LOVED, feedbackAt = 10,
            tags = listOf(Tag("Yandere", Aspect.CHARACTERS, 0.9f)))
        val finished = TasteSignals.Entry("a|2", MediaCategory.ANIME, "Another", completions = 1, completedAt = 5)
        val manga = TasteSignals.Entry("m|1", MediaCategory.MANGA, "Berserk", favorite = true)
        val s = AssistantTalk.starters(MediaCategory.ANIME, listOf(loved, finished, manga), listOf("Surprise me"))
        assertEquals("Recommend anime similar to Future Diary", s[0])
        assertTrue(s.contains("What should I try after finishing Another?"))
        assertTrue(s.contains("Underrated anime with yandere"))
        assertFalse(s.any { it.contains("Berserk") })
        assertEquals(listOf("Surprise me"), AssistantTalk.starters(MediaCategory.MOVIE, listOf(loved), listOf("Surprise me")))
    }
}
