/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.l10n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F19 guard. Every `tr(R.string.key, "English")` in the app carries the same English as values/strings.xml (the
 * text translators work from can never drift from the text that shows), every translated file only has keys the
 * English file has with the same `%1$s` placeholders (a missing or extra placeholder would crash or lose a value),
 * and the runtime falls back to English before resources exist.
 */
class L10nGuardTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "src/main/res/values/strings.xml").exists() || File(it, "app/src/main/res/values/strings.xml").exists() }
        .let { if (File(it, "src/main/res").exists()) it else File(it, "app") }
    private val res = File(root, "src/main/res")
    private val src = File(root, "src/main/java")

    @After fun tearDown() = Tr.reset()

    private fun strings(f: File): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val text = f.readText()
        var i = 0
        while (true) {
            val a = text.indexOf("<string name=\"", i); if (a < 0) break
            val nameEnd = text.indexOf('"', a + 14)
            val name = text.substring(a + 14, nameEnd)
            val open = text.indexOf('>', nameEnd) + 1
            val close = text.indexOf("</string>", open)
            out[name] = unescapeXml(text.substring(open, close))
            i = close
        }
        return out
    }

    private fun unescapeXml(s: String) = s.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun unescapeKotlin(s: String): String {
        val b = StringBuilder(); var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> b.append('\n'); 't' -> b.append('\t'); '"' -> b.append('"'); '\\' -> b.append('\\')
                    '$' -> b.append('$'); '\'' -> b.append('\'')
                    'u' -> { b.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                    else -> b.append(n)
                }
                i += 2
            } else { b.append(c); i++ }
        }
        return b.toString()
    }

    /** All `tr(R.string.k, "…"` calls: key → English as written in Kotlin. */
    private fun calls(): List<Triple<String, String, String>> {
        val out = ArrayList<Triple<String, String, String>>()
        src.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val t = f.readText()
            var i = 0
            while (true) {
                val a = t.indexOf("tr(R.string.", i); if (a < 0) break
                var k = a + 12
                while (k < t.length && (t[k].isLetterOrDigit() || t[k] == '_')) k++
                val key = t.substring(a + 12, k)
                var q = k
                while (q < t.length && (t[q] == ',' || t[q] == ' ')) q++
                if (q < t.length && t[q] == '"') {
                    var e = q + 1
                    while (e < t.length && t[e] != '"') { if (t[e] == '\\') e++; e++ }
                    out += Triple(f.name, key, unescapeKotlin(t.substring(q + 1, e)))
                }
                i = k
            }
        }
        return out
    }

    @Test fun everyCallMatchesTheEnglishFile() {
        val en = strings(File(res, "values/strings.xml"))
        val c = calls()
        assertTrue("the app's text goes through tr() (${c.size} calls)", c.size > 2000)
        val wrong = c.filter { (_, k, text) -> en[k] != text }.map { (f, k, text) -> "$f: $k = \"$text\" but strings.xml has \"${en[k]}\"" }
        assertEquals(wrong.take(20).joinToString("\n"), 0, wrong.size)
    }

    private fun placeholders(s: String) = Regex("%(\\d+)\\$[sd]").findAll(s).map { it.value }.toSortedSet()

    @Test fun translationsKeepKeysAndPlaceholders() {
        val en = strings(File(res, "values/strings.xml"))
        val dirs = res.listFiles()!!.filter { it.name.startsWith("values-") && File(it, "strings.xml").exists() }
        assertTrue("translated languages exist", dirs.isNotEmpty())
        val bad = ArrayList<String>()
        for (d in dirs) for ((k, v) in strings(File(d, "strings.xml"))) {
            val e = en[k]
            if (e == null) bad += "${d.name}: $k is not in the English file"
            else if (placeholders(e) != placeholders(v)) bad += "${d.name}: $k placeholders ${placeholders(v)} ≠ ${placeholders(e)}"
        }
        assertEquals(bad.take(20).joinToString("\n"), 0, bad.size)
    }

    @Test fun englishFallbackBeforeResources() {
        Tr.reset()
        assertEquals("Torrent can't play", tr(0, "Torrent can't play"))
        assertEquals("Episode 3 of Show", tr(0, "Episode %1\$s of %2\$s", 3, "Show"))
        assertEquals("1 title", trPlural(0, 1, "%1\$d title", "%1\$d titles"))
        assertEquals("4 titles", trPlural(0, 4, "%1\$d title", "%1\$d titles"))
    }

    @Test fun languageChangeIsDetected() {
        Tr.reset()
        assertFalse("nothing made yet", Tr.staleFor("bn"))
        Tr.markProcess("")
        assertFalse(Tr.staleFor(""))
        assertTrue("a new language after text was made: restart", Tr.staleFor("bn"))
        Tr.markProcess("bn")
        assertFalse("a new process starts in the new language", Tr.staleFor("bn"))
    }
}
