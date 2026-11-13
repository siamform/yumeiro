/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRow
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.ui.screens.watch.PlaybackFactsRow
import dev.asfakul.yumeiro.ui.theme.DarkBackground
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Round 10 Yumix pieces, rendered and clicked (no network: posters show their placeholders). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class YumixShowcaseTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun item(api: String, n: Int, type: String? = null) = OnlineItem("$api title $n", "u/$api/$n", api, "https://invalid.example/$api/$n.jpg", type = type)
    private val feed = listOf(
        "Anix" to listOf(OnlineRow("Anix", "Latest Episodes", (0 until 8).map { item("Anix", it, "Anime") }), OnlineRow("Anix", "Romance", (10 until 16).map { item("Anix", it, "Anime") })),
        "Flix" to listOf(OnlineRow("Flix", "Movies", (0 until 8).map { item("Flix", it, "Movie") }), OnlineRow("Flix", "Live Football", (20 until 26).map { item("Flix", it) })),
    )
    private val resume = OnlineHistory.Entry("Anix", "u/Anix/r", "Frieren: Beyond Journey's End", null, emptyMap(), "Anime", "1", "e5", "S1 · E5", 14 * 60_000, 24 * 60_000, 0)

    private fun save(name: String) {
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun page(content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { MTPlayerTheme { Column(Modifier.fillMaxSize().background(DarkBackground).verticalScroll(rememberScrollState())) { content() } } }
        rule.mainClock.advanceTimeBy(900)
    }

    @Test fun allFeedTopHalf() {
        var resumed: String? = null
        var picked: String? by mutableStateOf(null)
        page {
            val latest = YumixFeed.latest(feed)
            val (slides, newKeys) = YumixFeed.bannerSlides(feed.flatMap { it.second.first().items.take(3) }, latest)
            HeroCarousel(slides, onPlay = {}, onInfo = {}, resume = resume, onResume = { resumed = it.name }, newKeys = newKeys)
            PosterShelf("Fresh from your sources", "Mixed from 2 of 2 extensions", feed.flatMap { it.second[1].items }, horizontal = false, onClick = {}, onSeeAll = null)
            CategoryBar(YumixFeed.categories(feed), picked, onPick = { picked = it })
            YumixFeed.categories(feed).firstOrNull { it.first.name == picked }?.second?.let { list -> categoryGridRows(list).forEach { CategoryGridRow(it) {} } }
            PosterShelf("Latest episodes", "New from 1 extension(s)", latest, horizontal = false, onClick = {}, onSeeAll = null)
        }
        save("r10_yumix_all_feed")
        rule.onNodeWithTag("online_hero_resume_btn").performClick()
        assertEquals("Frieren: Beyond Journey's End", resumed)
        rule.onNodeWithTag("online_category_all").assertIsSelected()
        rule.onNodeWithTag("online_category_SPORTS").performClick()
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("online_category_SPORTS").assertIsSelected()
        assertEquals("SPORTS", picked)
        rule.onNodeWithTag("online_category_SPORTS").performClick()
        rule.mainClock.advanceTimeBy(400)
        assertEquals(null, picked)
        save("r10_yumix_category_sports")
    }

    @Test fun filterSheetPicksAndClears() {
        var sel: String? by mutableStateOf("Flix")
        page {
            ActiveFilterBar(sel ?: "—", onChange = {}, onClear = { sel = null })
            var open by remember { mutableStateOf(true) }
            if (open) SourceFilterSheet(listOf("Anix", "Flix", "SlowOne", "Broken"), sel, status = {
                when (it) { "SlowOne" -> SourceStatus.Loading; "Broken" -> SourceStatus.Failed; else -> SourceStatus.Ready(42) }
            }, onPick = { sel = it }, onDismiss = { open = false })
        }
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("online_filter_Flix").assertIsSelected()
        rule.onNodeWithTag("online_filter_Anix").performClick()
        rule.mainClock.advanceTimeBy(400)
        assertEquals("Anix", sel)
        rule.onNodeWithTag("online_filter_all").performClick()
        rule.mainClock.advanceTimeBy(400)
        assertNull(sel)
    }

    @Test fun filterSheetSearchNarrowsLongLists() {
        // Real-world extension names (CloudStream repos) so the search reads like the app.
        val names = listOf("HiAnime", "AnimePahe", "Anizone", "Gogoanime", "SuperStream", "Moviesdrive", "Cinemathek", "Pelisplus", "Animé Fénix", "VidSrc")
        var sel: String? by mutableStateOf(null)
        page {
            var open by remember { mutableStateOf(true) }
            if (open) SourceFilterSheet(names, sel, status = { SourceStatus.Ready(12) }, onPick = { sel = it }, onDismiss = { open = false })
        }
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("online_filter_search").assertExists().performTextInput("anim")
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("online_filter_AnimePahe").assertExists()
        rule.onNodeWithTag("online_filter_Animé Fénix").assertExists()
        rule.onNodeWithTag("online_filter_SuperStream").assertDoesNotExist()
        rule.onNodeWithTag("online_filter_all").assertDoesNotExist()
        save("r12_yumix_filter_search")
        rule.onNodeWithTag("online_filter_search").performTextReplacement("zzz")
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("online_filter_empty").assertExists()
        rule.onNodeWithTag("online_filter_search_clear").performClick()
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("online_filter_all").assertExists()
        rule.onNodeWithTag("online_filter_search").performTextReplacement("fenix")
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("online_filter_Animé Fénix").performClick()
        rule.mainClock.advanceTimeBy(400)
        assertEquals("Animé Fénix", sel)
    }

    @Test fun playbackFactsUnderPlayer() {
        PlaybackDiagnostics.reset("https://x/y.m3u8")
        PlaybackDiagnostics.update { it.copy(state = "READY", firstFrame = true, width = 1920, height = 1080, durationMs = 23 * 60_000 + 40_000, audioLanguage = "ja", audioTracks = 2, audioChannels = 2, bitrate = 2_500_000) }
        page { PlaybackFactsRow() }
        save("r10_playback_facts_full")
        PlaybackDiagnostics.update { it.copy(durationMs = 0, live = false, audioLanguage = null, audioLabel = null, audioTracks = 0, bitrate = 0) }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag("watch_facts_missing").assertExists()
        save("r10_playback_facts_missing")
    }
}
