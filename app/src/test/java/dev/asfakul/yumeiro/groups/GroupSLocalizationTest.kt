/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groups

import dev.asfakul.yumeiro.l10n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * F2 (Group S): every shipped language has every string, the same format placeholders as English, the plural forms
 * its grammar needs, and is listed for Android's per-app language page. A guard keeps the number of hard-coded
 * `Text("…")` literals from growing again.
 */
class GroupSLocalizationTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    private val java = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
    private val placeholder = Regex("%(?:\\d+\\$)?[sd]|%%")
    private val literalText = Regex("\\bText\\(\\s*(?:text\\s*=\\s*)?\"")

    private fun elements(file: File, tag: String): List<Element> {
        if (!file.isFile) return emptyList()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val n = doc.getElementsByTagName(tag)
        return (0 until n.length).map { n.item(it) as Element }
    }

    private fun strings(dir: String) = elements(File(res, "$dir/strings.xml"), "string")
        .filter { it.getAttribute("translatable") != "false" }.associate { it.getAttribute("name") to it.textContent }

    private fun plurals(dir: String) = elements(File(res, "$dir/plurals.xml"), "plurals").associate { p ->
        p.getAttribute("name") to (0 until p.getElementsByTagName("item").length)
            .map { (p.getElementsByTagName("item").item(it) as Element).getAttribute("quantity") }.toSet()
    }

    private val translated = AppLanguage.SHIPPED.map { it.tag }.filter { it != "en" }

    @Test fun f2_normalizeKeepsOnlyShippedLanguages() {
        assertEquals("", AppLanguage.normalize(""))
        assertEquals("bn", AppLanguage.normalize("bn-BD"))
        assertEquals("bn", AppLanguage.normalize("bn_IN"))
        assertEquals("ar", AppLanguage.normalize("ar-EG"))
        assertEquals("en", AppLanguage.normalize("EN-gb"))
        assertEquals("", AppLanguage.normalize("xx-YY"))
        assertTrue(AppLanguage.SHIPPED.single { it.tag == "ar" }.rtl)
        assertTrue(AppLanguage.SHIPPED.single { it.tag == "en" }.complete)
    }

    @Test fun f2_everyLanguageHasEveryStringWithTheSamePlaceholders() {
        val en = strings("values")
        assertTrue("English strings found", en.size > 800)
        for (lang in translated) {
            val tr = strings("values-$lang")
            val missing = en.keys - tr.keys
            assertTrue("$lang misses ${missing.size} strings: ${missing.take(10)}", missing.isEmpty())
            assertTrue("$lang has unknown keys: ${tr.keys - en.keys}", (tr.keys - en.keys).isEmpty())
            for ((k, v) in en) {
                val want = placeholder.findAll(v).map { it.value }.sorted().toList()
                val got = placeholder.findAll(tr.getValue(k)).map { it.value }.sorted().toList()
                assertEquals("$lang/$k placeholders", want, got)
                assertTrue("$lang/$k is empty", tr.getValue(k).isNotBlank())
            }
        }
    }

    @Test fun f2_pluralsHaveTheFormsEachGrammarNeeds() {
        val need = mapOf("en" to setOf("one", "other"), "bn" to setOf("one", "other"),
            "ar" to setOf("zero", "one", "two", "few", "many", "other"))
        val en = plurals("values")
        assertTrue("English plurals found", en.isNotEmpty())
        for (lang in translated + "en") {
            val p = plurals(if (lang == "en") "values" else "values-$lang")
            for (name in en.keys) {
                val forms = p[name]
                assertTrue("$lang has no plural $name", forms != null)
                assertTrue("$lang/$name forms $forms miss ${need.getValue(lang) - forms!!}", forms.containsAll(need.getValue(lang)))
            }
        }
    }

    @Test fun f2_perAppLanguageListMatchesShippedLanguages() {
        val listed = elements(File(res, "xml/locales_config.xml"), "locale").map { it.getAttribute("android:name") }.toSet()
        assertEquals(AppLanguage.SHIPPED.map { it.tag }.toSet(), listed)
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.isFile }.readText()
        assertTrue("manifest points at the locale list", manifest.contains("android:localeConfig=\"@xml/locales_config\""))
    }

    @Test fun f2_spacesAtTheEdgesAreQuotedSoAndroidKeepsThem() {
        for (dir in listOf("values") + translated.map { "values-$it" }) {
            val raw = File(res, "$dir/strings.xml").readText()
            val bad = Regex("<string name=\"([^\"]+)\">([^\"<][^<]*)</string>").findAll(raw)
                .filter { it.groupValues[2] != it.groupValues[2].trim() }.map { it.groupValues[1] }.toList()
            assertTrue("$dir: unquoted edge spaces in $bad", bad.isEmpty())
        }
    }

    /** Guard: the remaining hard-coded Text literals (tracked in AUDIT_S) may shrink, never grow. */
    @Test fun f2_hardCodedTextLiteralsDoNotGrow() {
        val count = java.walkTopDown().filter { it.isFile && it.extension == "kt" && "/widget/" !in it.path.replace('\\', '/') }
            .sumOf { literalText.findAll(it.readText()).count() }
        println("F2 hard-coded Text literals: $count")
        assertTrue("hard-coded Text(\"…\") literals grew to $count (limit $LIMIT): use stringResource", count <= LIMIT)
    }

    private companion object { const val LIMIT = 128 }
}
