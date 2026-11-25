/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.novel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** NOVEL-1 (item 26): the pure parts — LNReader index, plugin answers, chapter text, library file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Novel26Test {
    private fun j(s: String) = Json.parseToJsonElement(s)

    @Test fun indexEntriesAreRead() {
        val p = NovelPlugin.from(j("""{"id":"novelfire","name":"Novel Fire","site":"https://novelfire.net/","lang":"\u200eEnglish","version":"1.1.2","url":"https://x/novelfire.js","iconUrl":"https://x/i.png"}""") as JsonObject)!!
        assertEquals("English", p.language); assertEquals("en", p.languageCode)
        assertEquals(p, NovelPlugin.from(p.toJson()))
        assertNull("no code url", NovelPlugin.from(j("""{"id":"a","url":""}""") as JsonObject))
        assertNull(NovelPlugin.from(j("""{"id":"a","url":"https://x","lang":"Multi"}""") as JsonObject)!!.languageCode)
        assertEquals("ar", NovelPlugin.from(j("""{"id":"a","url":"https://x","lang":"\u200eالعربية"}""") as JsonObject)!!.languageCode)
    }

    @Test fun answersBecomeModels() {
        val items = NovelParse.items("p", "https://site.org/", j("""[{"name":"A","path":"novel/a","cover":"/c/a.jpg"},{"path":"novel/b"},{"name":"dup","path":"novel/a"},{"name":"no path"}]"""))
        assertEquals(listOf("A", "novel/b"), items.map { it.name })
        assertEquals("https://site.org/c/a.jpg", items[0].cover)
        assertNull(items[1].cover)
        assertEquals("https://cdn.x/i.jpg", NovelParse.absolute("https://site.org", "//cdn.x/i.jpg"))
        assertEquals("http://h/x", NovelParse.absolute("", "http://h/x"))
        assertNull(NovelParse.absolute("", "rel"))

        val d = NovelParse.details("p", "https://site.org", "novel/a", j(
            """{"name":"A","author":"W","genres":"Action, ,Drama","totalPages":3,"chapters":[{"name":"C3","path":"c3","chapterNumber":3},{"name":"C2","path":"c2","chapterNumber":2},{"name":"C1","path":"c1","chapterNumber":1}]}""",
        ))!!
        assertEquals(listOf("Action", "Drama"), d.genres)
        assertEquals(listOf("C1", "C2", "C3"), d.chapters.map { it.name })
        assertEquals(3, d.totalPages)
        val g = NovelParse.details("p", "", "x", j("""{"genres":["A"," B "],"chapters":[{"name":"b","path":"b"},{"name":"a","path":"a"}]}"""))!!
        assertEquals(listOf("A", "B"), g.genres)
        assertEquals("no numbers: plugin order kept", listOf("b", "a"), g.chapters.map { it.name })
        assertEquals("x", g.name); assertEquals(1, g.totalPages)
        assertNull(NovelParse.details("p", "", "x", j("[]")))
    }

    @Test fun chapterHtmlBecomesParagraphs() {
        val ps = NovelText.paragraphs(
            """<h2>Chapter 7</h2><p>One  two&nbsp;three.</p>Line a<br>line b<br><br>  <br>Next para<script>x()</script>""" +
                """<div class="ads">BUY</div><ins class="adsbygoogle"></ins><p></p><div><p>Nested</p>tail</div>""",
        )
        assertEquals(listOf("Chapter 7", "One two three.", "Line a\nline b", "Next para", "Nested", "tail"), ps.map { it.text })
        assertTrue(ps[0].heading); assertFalse(ps[1].heading)
        assertTrue(NovelText.paragraphs("").isEmpty())
    }

    @Test fun libraryFileRoundTrips() {
        val a = NovelLibrary.Entry("p", "novel/a", "A", "https://c", saved = true, chapterPath = "c2", chapterName = "C2", chapterIndex = 1, position = 0.5f, readAt = 9, addedAt = 3)
        val b = NovelLibrary.Entry("p", "novel/b", "B", null)
        val back = NovelLibrary.decode(NovelLibrary.encode(listOf(a, b)))
        assertEquals(listOf(a, b), back)
        assertEquals("broken rows are skipped", 1, NovelLibrary.decode("""[{"p":"","u":"x"},{"p":"q","u":"y"},7]""").size)
    }

    @Test fun errorsAreReadable() {
        assertTrue(NovelEngine.friendly("Error: Cloudflare challenge").contains("check"))
        assertEquals("This source can't do that.", NovelEngine.friendly("unknown method: parsePage"))
        assertEquals("line one", NovelEngine.friendly("line one\n  at stack"))
    }
}
