/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.upcoming.TvmazeSchedule
import dev.asfakul.yumeiro.ui.screens.online.UpcomingPrefs
import dev.asfakul.yumeiro.ui.screens.online.UpcomingScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * F-22-C device check: on the real Upcoming page (live TVmaze schedule), the Series tab shows country chips;
 * picking JP lists only Japanese shows with "JP • genre" badges, a genre chip narrows the list to that genre,
 * and the choice is saved for the profile. Log tag F22C: F22C_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class F22CountryDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("F22C", s)

    private fun tvmazeUp(): Boolean = runCatching {
        OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(20)).build()
            .newCall(okhttp3.Request.Builder().url("https://api.tvmaze.com/schedule?country=JP").build()).execute().use { it.code < 500 }
    }.getOrDefault(false)

    private fun count(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun texts(tag: String): List<String> = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes()
        .mapNotNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } }

    @Test fun seriesByCountryAndGenre() {
        assumeTrue("api.tvmaze.com is unreachable from this machine", tvmazeUp())
        assertTrue("flag f22_country_genre on", FeatureFlags.isOn(FeatureFlags.Flag.F22_COUNTRY_GENRE))
        File(ctx.cacheDir, "upcoming_tvmaze_jp.json").delete()
        UpcomingPrefs.setCountry(ctx, null); UpcomingPrefs.setGenre(ctx, null)
        rule.setContent { MTPlayerTheme { UpcomingScreen(onBack = {}, onSearch = {}) } }
        rule.waitUntil(5_000) { count("upcoming_kind_series") > 0 }
        rule.onAllNodesWithTag("upcoming_kind_series", true)[0].performClick()
        rule.waitUntil(5_000) { count("upcoming_country_jp") > 0 }
        val t0 = System.currentTimeMillis()
        rule.onAllNodesWithTag("upcoming_country_jp", true)[0].performClick()
        val ok = runCatching {
            rule.waitUntil(120_000) { count("upcoming_entry_badge") > 0 || count("upcoming_error") > 0 || count("upcoming_empty") > 0 }; true
        }.getOrDefault(false)
        assertTrue("JP: nothing after 120 s", ok)
        assertEquals("JP: error shown", 0, count("upcoming_error"))
        val badges = texts("upcoming_entry_badge")
        log("F22C_RESULT jp ${System.currentTimeMillis() - t0}ms entries=${count("upcoming_entry")} badges=${badges.take(5)}")
        assertTrue("JP: no entries", badges.isNotEmpty())
        assertTrue("JP: a badge without JP: $badges", badges.all { it.startsWith("JP") })
        val genreTags = rule.onAllNodesWithTag("upcoming_genres", true).fetchSemanticsNodes()
        assertTrue("JP: no genre chips", genreTags.isNotEmpty())
        val genres = TvmazeSchedule.genresOf(TvmazeSchedule.cached(ctx, "JP", java.time.LocalDate.now()).orEmpty())
        assertTrue("JP: no genres in the saved schedule", genres.isNotEmpty())
        val g = genres.first()
        rule.onAllNodesWithTag("upcoming_genre_${g.lowercase()}", true)[0].performClick()
        rule.waitForIdle()
        val narrowed = texts("upcoming_entry_badge")
        log("F22C_RESULT genre $g entries=${count("upcoming_entry")} badges=${narrowed.take(5)}")
        assertTrue("genre $g: no entries", narrowed.isNotEmpty())
        assertTrue("genre $g: a badge without JP: $narrowed", narrowed.all { it.startsWith("JP") })
        // The badge shows two genres; the filter matches any of the show's genres, so check the data behind the list.
        val kept = TvmazeSchedule.filterGenre(TvmazeSchedule.cached(ctx, "JP", java.time.LocalDate.now()).orEmpty(), g)
        assertTrue("genre $g: filter kept nothing", kept.isNotEmpty() && kept.all { e -> e.genres.any { it.equals(g, true) } })
        rule.waitUntil(5_000) { UpcomingPrefs.country(ctx) == "JP" && UpcomingPrefs.genre(ctx) == g }
        log("F22C_RESULT PASS saved country=${UpcomingPrefs.country(ctx)} genre=${UpcomingPrefs.genre(ctx)}")
    }
}
