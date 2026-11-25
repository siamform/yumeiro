/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.online.upcoming.Upcoming
import dev.asfakul.yumeiro.ui.components.SongDetailsDialog
import dev.asfakul.yumeiro.ui.screens.online.UpcomingScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * GROUP H screens on the device (phones and TV). Log tag YuGroupH; GROUPH_<item>_RESULT lines are evidence.
 *  h8 (owner item 8): Song info for a track whose library row has no duration and no size (0, as MediaStore gives
 *     for some files) shows the real duration and file size read from the file. Fail-before: "0:00" and "0 B".
 *  h9 (owner item 9): Upcoming → Movies → Country offers countries and the list follows the pick (Wikidata,
 *     live). India in Series (TVmaze has almost no Indian channels) says why and offers other choices instead of a
 *     blank page. Fail-before: the Movies country sheet said "worldwide" and India Series was just empty.
 */
@RunWith(AndroidJUnit4::class)
class GroupHScreensDeviceTest {
    // v2 rule (StandardTestDispatcher): the v1 rule's unconfined dispatcher resumed UpcomingScreen's effect on the
    // Dispatchers.IO thread after withContext and recomposed there (CalledFromWrongThreadException, then a
    // SlotTable crash on destroy; TV api24 run 45713636288). That is a test-harness thread, not app behaviour.
    @get:Rule val rule = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupH", s)
    private fun count(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun tap(tag: String) { rule.onAllNodesWithTag(tag, true)[0].performClick(); rule.waitForIdle() }
    private fun texts(): List<String> = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any text") { it.config.contains(SemanticsProperties.Text) }, true)
        .fetchSemanticsNodes().flatMap { n -> n.config[SemanticsProperties.Text].map { it.text } }

    @Test fun h8_songInfoShowsDurationAndSize() {
        val f = File(ctx.cacheDir, "grouph_song.m4a")
        InstrumentationRegistry.getInstrumentation().context.assets.open("yt_audio_only.m4a").use { i -> f.outputStream().use { i.copyTo(it) } }
        val track = AudioTrack(id = "grouph", title = "Group H song", artist = "Yumeiro", album = "Tests", durationMs = 0L, uri = android.net.Uri.fromFile(f).toString(), sizeBytes = 0L)
        rule.setContent { MTPlayerTheme { SongDetailsDialog(track) {} } }
        val ok = runCatching { rule.waitUntil(15_000) { texts().none { it == "0:00" || it == "Unknown" || it == "0 B" } && texts().any { "KB" in it || "MB" in it } }; true }.getOrDefault(false)
        val all = texts()
        log("h8 file=${f.length()} texts=$all")
        FirstRun.shot("grouph_h8_song_info")
        f.delete()
        assertTrue("H-8: song info shows duration and size: $all", ok)
        log("GROUPH_H8_RESULT PASS")
    }

    private fun kind(k: String) {
        if (count("upcoming_kind_$k") == 0) { tap("upcoming_category_button"); rule.waitUntil(5_000) { count("upcoming_kind_$k") > 0 } }
        tap("upcoming_kind_$k")
        if (count("upcoming_category_sheet_done") > 0) tap("upcoming_category_sheet_done")
        rule.waitUntil(5_000) { count("upcoming_category_sheet") == 0 }
    }
    private fun country(c: String): Boolean {
        tap("upcoming_country_button")
        rule.waitUntil(5_000) { count("upcoming_country_sheet") > 0 }
        val has = count("upcoming_country_${c.lowercase()}") > 0
        if (has) tap("upcoming_country_${c.lowercase()}")
        if (count("upcoming_country_sheet_done") > 0) tap("upcoming_country_sheet_done")
        if (count("upcoming_country_sheet") > 0) androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { count("upcoming_country_sheet") == 0 }
        return has
    }
    private fun settled(ms: Long = 90_000) = runCatching {
        rule.waitUntil(ms) { count("upcoming_loading") == 0 && (count("upcoming_entry") > 0 || count("upcoming_error") > 0 || count("upcoming_empty") > 0 || count("upcoming_empty_country") > 0) }; true
    }.getOrDefault(false)

    private fun up(url: String) = runCatching {
        okhttp3.OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(20)).build()
            .newCall(okhttp3.Request.Builder().url(url).head().build()).execute().use { it.code < 500 }
    }.getOrDefault(false)

    @Test fun h9_upcomingMoviesByCountryAndIndiaSeries() {
        assumeTrue("query.wikidata.org is unreachable", up("https://query.wikidata.org/"))
        File(ctx.cacheDir, "upcoming_movies_us.json").delete()
        rule.setContent { MTPlayerTheme { UpcomingScreen(onBack = {}, onSearch = {}) } }
        rule.waitUntil(5_000) { count("upcoming_category_button") > 0 }
        kind("movies")
        settled()
        val usOffered = country("US")
        assertTrue("H-9: Movies offers a country schedule (US chip)", usOffered)
        var usOk = settled(120_000)
        // Run 45717326746 (api36 run 1): query.wikidata.org timed out twice (SocketTimeoutException after the 40 s
        // read + one retry) and the screen showed its error card. Do what a person does: "Try again", once.
        if (count("upcoming_entry") == 0 && count("upcoming_error") > 0) {
            log("h9 movies US: error card, tapping Try again once")
            FirstRun.shot("grouph_h9_movies_us_error")
            rule.onAllNodesWithTag("upcoming_retry", true)[0].performClick()
            usOk = settled(120_000)
        }
        val us = count("upcoming_entry")
        log("h9 movies US settled=$usOk entries=$us empty=${count("upcoming_empty_country")} error=${count("upcoming_error")}")
        FirstRun.shot("grouph_h9_movies_us")
        assertTrue("H-9: US movies in the next two weeks listed", us > 0)
        country("IN")
        settled(120_000)
        val inMovies = count("upcoming_entry"); val inMoviesEmpty = count("upcoming_empty_country")
        log("h9 movies IN entries=$inMovies emptyCountry=$inMoviesEmpty")
        assertTrue("India movies: entries or an explained empty state", inMovies > 0 || inMoviesEmpty > 0)
        kind("series")
        settled(120_000)
        val inSeries = count("upcoming_entry"); val explained = count("upcoming_empty_country")
        log("h9 series IN entries=$inSeries emptyCountry=$explained chips=${listOf("streaming", "movies", "all").map { count("upcoming_empty_$it") }}")
        FirstRun.shot("grouph_h9_series_in")
        assertTrue("India series: entries or an explained empty state with choices", inSeries > 0 || (explained > 0 && count("upcoming_empty_movies") > 0 && count("upcoming_empty_streaming") > 0))
        if (inSeries == 0) {
            tap("upcoming_empty_movies")
            rule.waitForIdle(); settled(120_000)
            log("h9 chip → movies IN entries=${count("upcoming_entry")} emptyCountry=${count("upcoming_empty_country")}")
            assertTrue("the Movies chip switched to Movies", texts().any { "Movies" in it })
        }
        log("GROUPH_H9_RESULT PASS us=$us inMovies=$inMovies inSeries=$inSeries")
    }
}
