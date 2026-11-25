/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.ui.screens.online.SearchHistoryRow
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * SEARCH-ML-1 (owner list 24): a search with line breaks is kept as one search, keeps its breaks,
 * and is shown whole in the recent-search list and the typeahead; one-line searches are unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-night-xxhdpi")
class MultilineSearchHistoryTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val ml = "I want to search something\nthat needs a new line\nand contains multiple lines."
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun aMultilineSearchIsOneEntryWithItsBreaks() {
        RecentSearches.clearAll(ctx)
        RecentSearches.add(ctx, "library", "frieren")
        RecentSearches.add(ctx, "library", ml)
        assertEquals(listOf(ml, "frieren"), RecentSearches.load(ctx, "library"))
        // Before the fix the same calls gave 4 entries: the three lines and "frieren".
    }

    @Test fun windowsBreaksAndOuterSpacesAreCleaned() {
        RecentSearches.clearAll(ctx)
        RecentSearches.add(ctx, "youtube", "  one\r\ntwo\rthree  ")
        assertEquals(listOf("one\ntwo\nthree"), RecentSearches.load(ctx, "youtube"))
    }

    @Test fun theSameSearchMovesToTheTopAndTheListStaysCapped() {
        RecentSearches.clearAll(ctx)
        repeat(20) { RecentSearches.add(ctx, "hues", "q$it") }
        RecentSearches.add(ctx, "hues", "Q5")
        val l = RecentSearches.load(ctx, "hues")
        assertEquals(15, l.size)
        assertEquals("Q5", l.first())
        assertFalse(l.drop(1).any { it.equals("q5", true) })
    }

    @Test fun listsSavedTheOldWayStillLoad() {
        // What the app wrote before SEARCH-ML-1: searches joined by line breaks.
        ctx.getSharedPreferences("search_recent", Context.MODE_PRIVATE).edit().putString("library", "naruto\none piece\n[oshi no ko]").commit()
        assertEquals(listOf("naruto", "one piece", "[oshi no ko]"), RecentSearches.load(ctx, "library"))
        // The next search saves the new way and keeps the old ones.
        RecentSearches.add(ctx, "library", ml)
        assertEquals(listOf(ml, "naruto", "one piece", "[oshi no ko]"), RecentSearches.load(ctx, "library"))
        assertTrue(ctx.getSharedPreferences("search_recent", Context.MODE_PRIVATE).getString("library", "")!!.startsWith("["))
    }

    @Test fun anOldListThatStartsWithABracketIsNotMistakenForJson() {
        assertEquals(listOf("[oshi no ko]", "bleach"), SearchHistoryCodec.decode("[oshi no ko]\nbleach"))
        assertEquals(emptyList<String>(), SearchHistoryCodec.decode("[]"))
        assertEquals(listOf("[x]"), SearchHistoryCodec.decode(SearchHistoryCodec.encode(listOf("[x]"))))
    }

    @Test fun onlineSearchHistoryKeepsTheBreaksToo() {
        val p = ctx.getSharedPreferences("online", Context.MODE_PRIVATE)
        p.edit().clear().commit()
        dev.asfakul.yumeiro.ui.screens.online.SearchHistory.add(ctx, "solo leveling")
        dev.asfakul.yumeiro.ui.screens.online.SearchHistory.add(ctx, ml)
        assertEquals(listOf(ml, "solo leveling"), dev.asfakul.yumeiro.ui.screens.online.SearchHistory.load(ctx))
        p.edit().putString("online_search_history", "a\nb").commit()
        assertEquals(listOf("a", "b"), dev.asfakul.yumeiro.ui.screens.online.SearchHistory.load(ctx))
    }

    @Test fun aOneLinePillShowsEveryLine() {
        assertEquals("I want to search something / that needs a new line / and contains multiple lines.", SearchHistoryCodec.oneLine(ml))
        assertEquals("frieren", SearchHistoryCodec.oneLine("frieren"))
    }

    private fun lines(n: SemanticsNodeInteraction): Int {
        val out = mutableListOf<TextLayoutResult>()
        n.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
        return out.first().lineCount
    }

    private fun save(name: String) {
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun recentSearchRowsShowTheWholeSearch() {
        rule.setContent {
            MTPlayerTheme {
                Column(Modifier.fillMaxWidth().background(DarkBackground)) {
                    SearchHistoryRow("solo leveling") {}
                    SearchHistoryRow(ml) {}
                    SuggestionList(
                        query = "search",
                        suggestions = listOf(Suggestion(ml, Suggestion.Kind.RECENT), Suggestion("search party", Suggestion.Kind.QUERY)),
                        onPick = {}, onFill = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        val history = rule.onAllNodes(hasTestTag("online_search_history"))
        val single = history[0].fetchSemanticsNode().size.height
        val multi = history[1].fetchSemanticsNode().size.height
        assertTrue("multiline row ($multi px) must be taller than a one-line row ($single px)", multi > single)
        val texts = rule.onAllNodes(hasTestTag("online_search_history_text"), useUnmergedTree = true)
        assertEquals(1, lines(texts[0]))
        assertTrue(lines(texts[1]) >= 3)
        // The typeahead's recent row: every line, nothing cut.
        val rows = rule.onAllNodes(hasTestTag("search_suggestions_row"))
        val recentH = rows[0].fetchSemanticsNode().size.height
        val queryH = rows[1].fetchSemanticsNode().size.height
        assertTrue("recent multiline suggestion ($recentH) taller than a one-line one ($queryH)", recentH > queryH)
        save("search_ml1_history")
    }
}
