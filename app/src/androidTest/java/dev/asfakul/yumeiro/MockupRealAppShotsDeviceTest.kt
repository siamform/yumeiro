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
 * GROUP Z mockups, round 2 (owner 2026-11-26: "use real extension and real video"). The real app in the dark
 * theme, with real CloudStream anime extensions installed from their public repositories (the ones
 * MockupProbeDeviceTest proved find and play "Solo Leveling" on CI: HiAnime, AniKoto, KickassAnime, AnimeSuge,
 * plus Anichi), a real search, the real details page and a real episode playing in Yumeiro's player.
 * Every shot waits for what it claims and logs `REAL_SHOT name ok|missing`; the test fails if the anime
 * shots (search, details, playing) are missing. Not a feature test. Log tag YuReal.
 */
@RunWith(AndroidJUnit4::class)
class MockupRealAppShotsDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private val tv get() = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val form get() = if (tv) "tv" else "phone"
    private fun log(s: String) = Log.i("YuReal", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun hasText(t: String) = runCatching { rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun waitTag(tag: String, ms: Long = 20_000) = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
    private fun pause(ms: Long) { val until = SystemClock.uptimeMillis() + ms; runCatching { rule.waitUntil(ms + 5_000) { SystemClock.uptimeMillis() > until } } }
    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]
    private val ok = ArrayList<String>()
    private val missing = ArrayList<String>()
    private var song: android.net.Uri? = null
    private var modeBefore: ThemeMode? = null

    private fun snap(name: String, shows: Boolean) {
        rule.waitForIdle()
        FirstRun.shot("real_${form}_$name")
        if (shows) ok += name else missing += name
        log("REAL_SHOT ${form}_$name ${if (shows) "ok" else "missing"} · ${FirstRun.onScreen(rule).take(260)}")
    }
    private fun step(name: String, block: () -> Unit) = runCatching(block).onFailure {
        missing += name; log("REAL_SHOT ${form}_$name error ${it.javaClass.simpleName}: ${it.message?.take(300)}")
    }
    private fun back(n: Int = 1) = repeat(n) { FirstRun.shell("input keyevent 4"); pause(1_000) }
    private fun tab(t: String) { FirstRun.tapVisible(rule, FirstRun.navTag(rule, t)); pause(1_500) }
    private fun settingsHome() { if (has("settings_home")) return; FirstRun.tapVisible(rule, "topbar_settings_action"); waitTag("settings_home") }
    private fun settingsPage(cat: String, ready: String? = null): Boolean {
        settingsHome(); FirstRun.scrollTo(rule, "settings_cat_$cat"); FirstRun.tapVisible(rule, "settings_cat_$cat"); pause(1_500)
        return ready?.let { waitTag(it) } ?: !has("settings_home")
    }
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

    @After fun tearDown() {
        runCatching { rule.runOnUiThread { vm().closeVideoPlayer() } }
        runCatching { rule.runOnUiThread { vm().stopPlayback() } }
        song?.let { runCatching { ctx.contentResolver.delete(it, null, null) } }
        runCatching { SavedSites.remove(ctx, WIKI) }; runCatching { SavedSites.remove(ctx, TG) }
        modeBefore?.let { m -> runCatching { ThemeSettings.setMode(ctx, m) } }
        log("REAL_SUMMARY ok=${ok.size} $ok missing=$missing")
    }

    @Test fun realShots() {
        ThemeSettings.init(ctx); modeBefore = ThemeSettings.mode.value
        rule.runOnUiThread { ThemeSettings.setMode(ctx, ThemeMode.DARK) }
        FirstRun.toTabs(rule, 120_000)
        FirstRun.dismissPrompts(rule)
        ExtensionManager.start(ctx)
        runBlocking { ExtensionManager.ensureLoaded(ctx) }
        for (url in EXT) runCatching { runBlocking { ExtensionManager.installFromUrl(ctx, url, ExtensionReview.Mode.AUTO) } }
            .onSuccess { log("install ok ${url.substringAfterLast('/')}") }.onFailure { log("install fail ${url.substringAfterLast('/')}: ${it.message?.take(160)}") }
        log("installed: ${ExtensionManager.state.value.installed.map { it.name }}")

        // 1 Videos (Yumix) home with the anime extensions' real rows and posters.
        step("videos_home") {
            videosOnline()
            runCatching { rule.waitUntil(120_000) { rule.onAllNodesWithTag("online_poster", true).fetchSemanticsNodes().size >= 6 } }
            pause(12_000); FirstRun.dismissPrompts(rule)
            snap("videos_home", has("online_poster"))
        }
        // 2 Filter pattern: the "Show videos from" sheet listing the real extensions.
        step("filter_pattern") {
            FirstRun.tapVisible(rule, "online_filter"); pause(2_000)
            snap("filter_pattern", hasText("Show videos from"))
            back()
        }
        // 3 Extensions screen and 4 the market.
        step("extensions") {
            FirstRun.tapVisible(rule, "online_extensions"); pause(4_000)
            snap("extensions", hasText("HiAnime") || hasText("AniKoto"))
            back()
        }
        step("market") {
            FirstRun.tapVisible(rule, "online_market"); pause(8_000)
            snap("market", !has("online_home"))
            back()
        }
        // 5 Search "Solo Leveling" across the installed extensions.
        step("search") {
            waitTag("online_home")
            FirstRun.tapVisible(rule, "topbar_search_action")
            assertTrue("search did not open", waitTag("online_search_input"))
            rule.onAllNodesWithTag("online_search_input", true).onFirst().performTextReplacement(QUERY)
            FirstRun.shell("input keyevent 66")
            runCatching { rule.waitUntil(90_000) { rule.onAllNodesWithTag("online_poster", true).fetchSemanticsNodes().size >= 4 } }
            FirstRun.shell("input keyevent 111"); pause(10_000)
            snap("search", (has("online_search_results") || has("online_search_groups")) && hasText(QUERY))
        }
        // 6 The real details page (HiAnime "Solo Leveling", 24 episodes, Sub and Dub).
        step("details") {
            rule.runOnUiThread { com.lagradost.cloudstream3.utils.AppContextUtils.loadResult(HIANIME_URL, "HiAnime", QUERY) }
            runCatching { rule.waitUntil(90_000) { has("online_detail_play") || has("online_episode") || has("online_detail_error") } }
            pause(8_000)
            snap("details", has("online_detail_screen") && (has("online_episode") || has("online_detail_play")))
            FirstRun.scrollTo(rule, "online_episode"); pause(2_500)
            snap("episodes", has("online_episode"))
        }
        // 7 A real episode playing in Yumeiro's player, then the controls and the source panel.
        step("playing") {
            PlaybackDiagnostics.reset("")
            if (has("online_detail_play")) FirstRun.tapVisible(rule, "online_detail_play") else FirstRun.tapVisible(rule, "online_episode")
            rule.waitUntil(150_000) { PlaybackDiagnostics.state.value.firstFrame || PlaybackDiagnostics.state.value.error != null }
            val playing = PlaybackDiagnostics.state.value.firstFrame
            log("playing uri=${PlaybackDiagnostics.state.value.uri.take(120)} error=${PlaybackDiagnostics.state.value.error}")
            // Past the cold open, to an animated scene.
            rule.runOnUiThread { PlaybackSession.playerForTest?.seekTo(SEEK_MS) }
            pause(9_000)
            snap("playing", playing && has("video_player_root"))
            runCatching { rule.onAllNodesWithTag("video_player_root", true)[0].performClick() }
            pause(1_200)
            snap("controls", playing && has("video_player_root"))
            pause(4_000)
            if (has("online_watch_panel")) snap("watch_panel", true)
            pause(30_000) // counts toward Continue watching and today's statistics
            rule.runOnUiThread { vm().closeVideoPlayer() }
            pause(2_000)
        }
        // 8 Home with Continue watching, 9 Library.
        step("home") {
            back(2); tab("nav_tab_home")
            waitTag("home_screen_root"); pause(5_000); FirstRun.dismissPrompts(rule)
            snap("home", has("home_screen_root"))
        }
        step("library") {
            tab("nav_tab_library"); waitTag("library_screen_root"); pause(4_000)
            snap("library", has("library_screen_root"))
        }
        step("videos_continue") {
            videosOnline(); pause(6_000)
            snap("videos_continue", has("online_continue") || has("online_hero_resume"))
        }
        // 10 Music: a real free song (SoundHelix Song 1) in Now Playing and the Music tab.
        step("music_playing") {
            song = putSong()
            val uri = song ?: throw AssertionError("song not saved")
            val t = AudioTrack(id = android.content.ContentUris.parseId(uri).toString(), title = "Song 1", artist = "SoundHelix",
                album = "SoundHelix examples", durationMs = 372_000, uri = uri.toString())
            rule.runOnUiThread { vm().scanMedia() }; pause(4_000)
            rule.runOnUiThread { vm().playTrack(t, listOf(t)) }
            waitTag("now_playing_screen_root", 30_000)
            runCatching { rule.waitUntil(30_000) { vm().playbackState.value.isPlaying } }
            pause(6_000)
            snap("music_playing", has("now_playing_screen_root") && vm().playbackState.value.isPlaying)
            rule.runOnUiThread { vm().closeNowPlaying() }; pause(1_500)
            tab("nav_tab_music"); pause(3_000)
            snap("music_tab", has("mini_player_bar"))
            runCatching { rule.runOnUiThread { vm().stopPlayback() } }
        }
        // 11 Settings home and the settings search pattern.
        step("settings") {
            settingsHome(); pause(1_500)
            snap("settings", has("settings_home"))
            if (!has("settings_search")) { rule.onAllNodesWithTag("settings_search_open", true)[0].performClick(); pause(600) }
            rule.onAllNodesWithTag("settings_search", true)[0].performTextReplacement("subtitle")
            pause(1_500)
            snap("search_pattern", FirstRun.onScreen(rule).contains("ubtitle"))
            rule.onAllNodesWithTag("settings_search", true)[0].performTextReplacement("")
            rule.onAllNodesWithTag("settings_search_open", true)[0].performClick(); pause(800)
        }
        // 12 A public Telegram channel (@durov), no login.
        step("telegram") {
            inst.runOnMainSync { SavedSites.add(ctx, TG, "Durov's Channel") }
            settingsPage("telegram"); runCatching { rule.waitUntil(20_000) { has("telegram_screen") || has("websites_screen") } }
            pause(2_000)
            snap("telegram_list", hasText("Durov"))
            rule.onAllNodesWithText("Durov", substring = true, useUnmergedTree = true).onFirst().performClick()
            runCatching { rule.waitUntil(45_000) { FirstRun.onScreen(rule).contains("tg_post_") || has("tg_header") } }
            pause(6_000)
            snap("telegram", has("tg_channel") && !has("tg_error"))
            back(2); settingsHome()
        }
        // 13 A real website (Wikipedia's Solo Leveling article) in the built-in browser.
        step("website") {
            inst.runOnMainSync { SavedSites.add(ctx, WIKI, "Wikipedia") }
            settingsPage("websites", "websites_screen"); pause(1_500)
            snap("websites_list", hasText("Wikipedia"))
            rule.onAllNodesWithText("Wikipedia", substring = false, useUnmergedTree = true).onFirst().performClick()
            waitTag("browser_bar"); pause(10_000)
            snap("website_page", has("browser_bar"))
            FirstRun.tapVisible(rule, "browser_menu"); pause(900)
            snap("website_menu", has("browser_menu_nav"))
            back(3)
        }
        log("REAL_RESULT ok=${ok.size} missing=${missing.size} $missing")
        val anime = listOf("search", "details", "playing")
        assertTrue("anime shots missing: ${anime.filter { it !in ok }}", anime.all { it in ok })
    }

    private fun putSong(): android.net.Uri? {
        listOf("android.permission.READ_MEDIA_AUDIO", "android.permission.READ_EXTERNAL_STORAGE")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        if (Build.VERSION.SDK_INT < 29) return null
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "SoundHelix-Song-1.mp3"); put(MediaStore.Audio.Media.TITLE, "Song 1")
            put(MediaStore.Audio.Media.ARTIST, "SoundHelix"); put(MediaStore.Audio.Media.ALBUM, "SoundHelix examples")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg"); put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Yumeiro mockup")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val c = java.net.URL(SONG).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 20_000; c.readTimeout = 60_000
        c.inputStream.use { i -> ctx.contentResolver.openOutputStream(uri)!!.use { o -> i.copyTo(o) } }
        return uri
    }

    companion object {
        const val QUERY = "Solo Leveling"
        const val HIANIME_URL = "https://hianime.fm/watch/solo-leveling-235"
        const val SEEK_MS = 330_000L
        const val WIKI = "https://en.wikipedia.org/wiki/Solo_Leveling"
        const val TG = "https://t.me/durov"
        const val SONG = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        private const val PH = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/"
        private const val CNC = "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/"
        val EXT = listOf(PH + "HiAnime.cs3", CNC + "AniKoto.cs3", PH + "Kickassanime.cs3", CNC + "AnimeSuge.cs3", PH + "Anichi.cs3")
    }
}
