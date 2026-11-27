/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.blacksource.BlackSource
import com.lagradost.cloudstream3.APIHolder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.ui.theme.ThemeMode
import dev.asfakul.yumeiro.ui.theme.ThemeSettings
import dev.asfakul.yumeiro.ui.viewmodel.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GROUP Z (beta chapter) promo captures for the release mockups: the real app in the dark theme with real
 * CloudStream anime extensions from their public repositories, real searches and two real episodes playing
 * (Solo Leveling and Demon Slayer). Shows the beta chapter's features: Yumix rows from many extensions, search
 * grouped by extension, BlackSource, the one-capsule bar, Continue watching, the player's Audio (compressor) and
 * picture-size controls, the extension list. Each capture waits for what it claims and logs `PROMO_SHOT name
 * ok|missing`; the test fails only if the anime captures are missing. Not a feature test. Log tag YuPromo.
 */
@RunWith(AndroidJUnit4::class)
class GroupZPromoShotsDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private val tv get() = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val form get() = if (tv) "tv" else "phone"
    private fun log(s: String) = Log.i("YuPromo", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun count(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size }.getOrDefault(0)
    private fun hasText(t: String) = runCatching { rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun waitTag(tag: String, ms: Long = 20_000) = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
    private fun pause(ms: Long) { val until = SystemClock.uptimeMillis() + ms; runCatching { rule.waitUntil(ms + 5_000) { SystemClock.uptimeMillis() > until } } }
    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]
    private val ok = ArrayList<String>()
    private val missing = ArrayList<String>()
    private var modeBefore: ThemeMode? = null

    private fun snap(name: String, shows: Boolean) {
        rule.waitForIdle()
        FirstRun.shot("promo_${form}_$name")
        if (shows) ok += name else missing += name
        log("PROMO_SHOT ${form}_$name ${if (shows) "ok" else "missing"} · ${FirstRun.onScreen(rule).take(260)}")
    }
    private fun step(name: String, block: () -> Unit) = runCatching(block).onFailure {
        missing += name; log("PROMO_SHOT ${form}_$name error ${it.javaClass.simpleName}: ${it.message?.take(300)}")
    }
    private fun back(n: Int = 1) = repeat(n) { FirstRun.shell("input keyevent 4"); pause(1_000) }
    private fun tab(t: String) { FirstRun.tapVisible(rule, FirstRun.navTag(rule, t)); pause(1_500) }
    private fun videosOnline() {
        tab("nav_tab_videos")
        rule.waitUntil(20_000) { has("video_source_online") || has("video_source_switch") || has("online_home") }
        if (!has("online_home")) {
            if (!has("video_source_online")) FirstRun.clickTag(rule, "video_source_switch")
            rule.waitUntil(15_000) { has("video_source_online") }
            FirstRun.clickTag(rule, "video_source_online")
        }
        waitTag("online_home", 30_000)
    }

    /** The URL of [query] on the extension called [api], from a real search on it. */
    private fun find(api: String, query: String): String? = runBlocking {
        val p = APIHolder.allProviders.toList().firstOrNull { it.name == api } ?: return@runBlocking null
        val hits = withTimeoutOrNull(60_000) { OnlineRepository.search(query, listOf(p)).toList() }.orEmpty().flatMap { it.items }
        log("find $api '$query': ${hits.take(5).map { it.name }}")
        (hits.firstOrNull { it.name.equals(query, true) } ?: hits.firstOrNull { it.name.contains(query, true) })?.url
    }

    /** Opens the real details page, plays the first episode, seeks to [seekMs] and captures [name]. */
    private fun playTitle(name: String, api: String, url: String, title: String, seekMs: Long, extra: Boolean) {
        rule.runOnUiThread { com.lagradost.cloudstream3.utils.AppContextUtils.loadResult(url, api, title) }
        runCatching { rule.waitUntil(90_000) { has("online_detail_play") || has("online_episode") || has("online_detail_error") } }
        pause(8_000)
        snap("${name}_details", has("online_detail_screen") && (has("online_episode") || has("online_detail_play")))
        if (extra) { FirstRun.scrollTo(rule, "online_episode"); pause(2_500); snap("${name}_episodes", has("online_episode")) }
        PlaybackDiagnostics.reset("")
        if (has("online_detail_play")) FirstRun.tapVisible(rule, "online_detail_play") else FirstRun.tapVisible(rule, "online_episode")
        rule.waitUntil(150_000) { PlaybackDiagnostics.state.value.firstFrame || PlaybackDiagnostics.state.value.error != null }
        val playing = PlaybackDiagnostics.state.value.firstFrame
        log("$name playing uri=${PlaybackDiagnostics.state.value.uri.take(120)} error=${PlaybackDiagnostics.state.value.error}")
        rule.runOnUiThread { PlaybackSession.playerForTest?.seekTo(seekMs) }
        pause(9_000)
        snap("${name}_playing", playing && has("video_player_root"))
        runCatching { rule.onAllNodesWithTag("video_player_root", true)[0].performClick() }
        pause(1_200)
        snap("${name}_controls", playing && has("video_player_root"))
        if (extra) {
            // The player's Audio menu (dynamic range compressor) and the picture-size controls (Group K / N).
            step("${name}_audio") {
                if (has("video_audio_track_btn")) { FirstRun.tapVisible(rule, "video_audio_track_btn"); pause(1_500); snap("${name}_audio", true); back() }
            }
            step("${name}_aspect") {
                runCatching { rule.onAllNodesWithTag("video_player_root", true)[0].performClick() }; pause(800)
                if (has("video_aspect_ratio_btn")) { FirstRun.tapVisible(rule, "video_aspect_ratio_btn"); pause(1_500); snap("${name}_aspect", true); back() }
            }
        }
        pause(25_000) // counts toward Continue watching
        rule.runOnUiThread { vm().closeVideoPlayer() }
        pause(2_000)
    }

    @After fun tearDown() {
        runCatching { rule.runOnUiThread { vm().closeVideoPlayer() } }
        runCatching { rule.runOnUiThread { vm().stopPlayback() } }
        runCatching { BlackSource.setOptedIn(ctx, false) }
        modeBefore?.let { m -> runCatching { ThemeSettings.setMode(ctx, m) } }
        log("PROMO_SUMMARY ok=${ok.size} $ok missing=$missing")
    }

    @Test fun promoShots() {
        ThemeSettings.init(ctx); modeBefore = ThemeSettings.mode.value
        rule.runOnUiThread { ThemeSettings.setMode(ctx, ThemeMode.DARK) }
        FirstRun.toTabs(rule, 120_000)
        FirstRun.dismissPrompts(rule)
        ExtensionManager.start(ctx)
        runBlocking { ExtensionManager.ensureLoaded(ctx) }
        for (url in EXT) runCatching { runBlocking { ExtensionManager.installFromUrl(ctx, url, ExtensionReview.Mode.AUTO) } }
            .onSuccess { log("install ok ${url.substringAfterLast('/')}") }.onFailure { log("install fail ${url.substringAfterLast('/')}: ${it.message?.take(160)}") }
        log("installed: ${ExtensionManager.state.value.installed.map { it.name }}")

        // 1 Yumix (Videos online) with real rows and posters from every installed anime extension.
        step("yumix") {
            videosOnline()
            runCatching { rule.waitUntil(150_000) { count("online_poster") >= 8 } }
            pause(15_000); FirstRun.dismissPrompts(rule)
            snap("yumix", count("online_poster") >= 4)
        }
        // 2 The extension list (many extensions, Group V).
        step("extensions") {
            FirstRun.tapVisible(rule, "online_extensions"); pause(4_000)
            snap("extensions", hasText("HiAnime") || hasText("AniKoto"))
            back()
        }
        // 3 Search "Demon Slayer" across every extension, grouped by extension (Group O).
        step("search") {
            waitTag("online_home")
            FirstRun.tapVisible(rule, "topbar_search_action")
            assertTrue("search did not open", waitTag("online_search_input"))
            rule.onAllNodesWithTag("online_search_input", true).onFirst().performTextReplacement(DS)
            FirstRun.shell("input keyevent 66")
            runCatching { rule.waitUntil(120_000) { count("online_poster") >= 6 } }
            FirstRun.shell("input keyevent 111"); pause(12_000)
            snap("search", (has("online_search_results") || has("online_search_groups")) && hasText("Demon Slayer"))
        }
        // 4 BlackSource genre search, opened from the search header (Group U).
        step("blacksource") {
            BlackSource.setOptedIn(ctx, true)
            if (has("online_search_blacksource")) {
                FirstRun.tapVisible(rule, "online_search_blacksource")
                waitTag("blacksource_screen"); pause(2_500)
                snap("blacksource", has("blacksource_screen"))
                back()
            }
            back()
        }
        // 5 Solo Leveling: details, episodes, playing, controls, audio and picture size.
        step("solo") {
            val url = find("HiAnime", SL) ?: HIANIME_SL
            playTitle("solo", "HiAnime", url, SL, SEEK_SL, extra = true)
        }
        // 6 Demon Slayer playing (a second real title).
        step("demon") {
            val (api, url) = listOf("HiAnime", "AniKoto", "KickassAnime", "AnimeSuge").firstNotNullOfOrNull { a -> find(a, DS)?.let { a to it } }
                ?: throw AssertionError("Demon Slayer not found")
            playTitle("demon", api, url, DS, SEEK_DS, extra = false)
        }
        // 7 Home with Continue watching and the one-capsule bar (Group L); 8 Yumix with Continue watching.
        step("home") {
            back(2); tab("nav_tab_home")
            waitTag("home_screen_root"); pause(6_000); FirstRun.dismissPrompts(rule)
            snap("home", has("home_screen_root"))
        }
        step("yumix_continue") {
            videosOnline(); pause(8_000)
            snap("yumix_continue", has("online_continue") || has("online_hero_resume"))
        }
        log("PROMO_RESULT ok=${ok.size} missing=${missing.size} $missing")
        val anime = listOf("solo_playing", "demon_playing")
        assertTrue("anime captures missing: ${anime.filter { it !in ok }}", anime.all { it in ok })
    }

    companion object {
        const val SL = "Solo Leveling"
        const val DS = "Demon Slayer"
        const val HIANIME_SL = "https://hianime.fm/watch/solo-leveling-235"
        const val SEEK_SL = 330_000L
        const val SEEK_DS = 610_000L
        private const val PH = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/"
        private const val CNC = "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/"
        val EXT = listOf(PH + "HiAnime.cs3", CNC + "AniKoto.cs3", PH + "Kickassanime.cs3", CNC + "AnimeSuge.cs3", PH + "Anichi.cs3")
    }
}
