/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R28 item 15: key detection, model ranking, picker groups and remembered status. */
class AiCatalogTest {
    @Test fun keysAreRecognisedByTheirShape() {
        assertEquals(AiKeys.Guess.Provider(AiPreset.GEMINI), AiKeys.guess("AIzaSyExample000000000000000000000"))
        assertEquals(AiKeys.Guess.GoogleToken, AiKeys.guess("AQ.Ab8RN6Example"))
        assertEquals(AiKeys.Guess.GoogleToken, AiKeys.guess("ya29.a0Example"))
        assertEquals(AiKeys.Guess.Provider(AiPreset.OPENROUTER), AiKeys.guess("sk-or-v1-abc"))
        assertEquals(AiKeys.Guess.Provider(AiPreset.ANTHROPIC), AiKeys.guess("sk-ant-api03-abc"))
        assertEquals(AiKeys.Guess.Provider(AiPreset.GROQ), AiKeys.guess("gsk_abc"))
        assertEquals(AiKeys.Guess.Provider(AiPreset.XAI), AiKeys.guess("xai-abc"))
        assertEquals(AiKeys.Guess.Provider(AiPreset.CEREBRAS), AiKeys.guess("csk-abc"))
        assertEquals(AiKeys.Guess.Provider(AiPreset.DEEPSEEK), AiKeys.guess("sk-" + "0123456789abcdef".repeat(2)))
        assertEquals(AiKeys.Guess.Provider(AiPreset.OPENAI), AiKeys.guess("sk-proj-Abc"))
        assertEquals(AiKeys.Guess.Unknown, AiKeys.guess("whatever"))
    }

    @Test fun aMismatchedKeyIsSaidInPlainWords() {
        assertTrue(AiKeys.mismatch("gsk_abc", AiPreset.GEMINI)!!.contains("Groq"))
        assertTrue(AiKeys.mismatch("AQ.token", AiPreset.GEMINI)!!.contains("AIza"))
        assertNull(AiKeys.mismatch("AIzaKey", AiPreset.GEMINI))
        assertNull("a custom server accepts any key", AiKeys.mismatch("gsk_abc", AiPreset.CUSTOM))
        assertNull(AiKeys.mismatch("plain", AiPreset.GROQ))
    }

    @Test fun everyPresetHasAnHttpsBaseAndKeyPage() {
        AiPreset.entries.filter { it != AiPreset.CUSTOM }.forEach {
            assertTrue(it.code, it.base.startsWith("https://"))
            assertTrue(it.code, it.keyUrl.startsWith("https://"))
        }
        assertEquals(setOf("Free tier", "Paid", "Your own server"), AiPreset.entries.map { it.group }.toSet())
        assertFalse(AiPreset.OPENAI.free); assertTrue(AiPreset.GROQ.free)
    }

    @Test fun rankingPutsAKnownGoodChatModelFirstAndDropsNonChat() {
        val models = listOf(
            AiModel("gemini-2.5-pro"), AiModel("gemini-2.5-flash"), AiModel("gemini-2.0-flash-exp"),
            AiModel("text-embedding-004", chat = false), AiModel("imagen-4"), AiModel("gemini-2.5-flash-preview-tts"),
        )
        val r = AiCatalog.rank(AiPreset.GEMINI, models).map { it.id }
        assertEquals("gemini-2.5-flash", r.first())
        assertFalse(r.contains("text-embedding-004")); assertFalse(r.contains("imagen-4")); assertFalse(r.contains("gemini-2.5-flash-preview-tts"))
        val or = AiCatalog.rank(AiPreset.OPENROUTER, listOf(AiModel("openai/gpt-5"), AiModel("meta-llama/llama-3.3-70b-instruct:free"))).map { it.id }
        assertEquals("meta-llama/llama-3.3-70b-instruct:free", or.first())
    }

    @Test fun pickerGroupsShowWhatWorksAndWhatCant() {
        val models = listOf(AiModel("gemini-2.5-flash"), AiModel("gemini-2.5-pro"), AiModel("gemini-2.0-flash"), AiModel("gemma-3"), AiModel("text-embedding-004", chat = false))
        val status = mapOf("gemini-2.5-pro" to AiModelStatus(false, "Free limit used up for now", 1), "gemini-2.0-flash" to AiModelStatus(true, "", 2))
        val g = AiCatalog.groups(AiPreset.GEMINI, models, status)
        val byTitle = g.associate { it.title to it.models.map { m -> m.id } }
        assertEquals(listOf("gemini-2.0-flash"), byTitle["Working on this key"])
        assertEquals(listOf("gemini-2.5-pro"), byTitle["Didn't answer"])
        assertEquals(listOf("text-embedding-004"), byTitle["Not for chat"])
        assertFalse(g.first { it.title == "Not for chat" }.selectable)
        assertEquals("each model once", models.size, g.sumOf { it.models.size })
        val searched = AiCatalog.groups(AiPreset.GEMINI, models, status, "gemma")
        assertEquals(listOf("gemma-3"), searched.flatMap { it.models }.map { it.id })
        assertTrue(AiCatalog.groups(AiPreset.GEMINI, models, status, "nothing-like-this").isEmpty())
    }

    @Test fun statusAndModelsSurviveARoundTrip() {
        val s = mapOf("a" to AiModelStatus(true, "", 5), "b" to AiModelStatus(false, "Key rejected", 6))
        assertEquals(s, AiCatalog.decode(AiCatalog.encode(s)))
        assertTrue(AiCatalog.decode("not json").isEmpty())
        val m = listOf(AiModel("x", "X", 128000), AiModel("e", chat = false))
        assertEquals(m, AiCatalog.decodeModels(AiCatalog.encodeModels(m)))
    }

    @Test fun failuresBecomeShortReasons() {
        assertEquals("Free limit used up for now", AiCatalog.reason(AiError.LimitReached()))
        assertEquals("Not available on this key", AiCatalog.reason(AiError.ModelGone("m")))
        assertNotNull(AiCatalog.reason(RuntimeException()))
    }
}
