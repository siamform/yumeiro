/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.ContentValues
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.screens.profiles.ProfileSession
import dev.asfakul.yumeiro.ui.theme.AccentTone
import dev.asfakul.yumeiro.ui.viewmodel.MainViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * Social and README images for 1.9.0: the real app on a fresh install, with the starter extensions it
 * installs from the real market, a real Creative Commons film (Big Buck Bunny, Blender Foundation,
 * CC BY 3.0, from Wikimedia Commons) and a real song (SoundHelix Song 1, free to use). Saved as social_*
 * screenshots. Each shot waits for the thing it claims to show and logs `SOCIAL_SHOT name ok|missing`;
 * the test fails only if fewer than 8 shots show what they claim. Not a feature test and not a checklist
 * row. Log tag YuSocial.
 */
@RunWith(AndroidJUnit4::class)
class SocialShotsDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private fun log(s: String) = Log.i("YuSocial", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun waitTag(tag: String, ms: Long = 20_000) = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
    private fun pause(ms: Long) { val until = SystemClock.uptimeMillis() + ms; runCatching { rule.waitUntil(ms + 5_000) { SystemClock.uptimeMillis() > until } } }
    private fun vm() = ViewModelProvider(rule.activity)[MainViewModel::class.java]
    private val ok = ArrayList<String>()
    private val missing = ArrayList<String>()
    private var song: android.net.Uri? = null
    private val addedProfiles = ArrayList<String>()

    private fun snap(name: String, shows: Boolean) {
        FirstRun.shot("social_$name")
        if (shows) ok += name else missing += name
        log("SOCIAL_SHOT $name ${if (shows) "ok" else "missing"} · ${FirstRun.onScreen(rule).take(300)}")
    }

    private fun step(name: String, block: () -> Unit) = runCatching(block).onFailure {
        missing += name; log("SOCIAL_SHOT $name error ${it.javaClass.simpleName}: ${it.message?.take(300)}")
    }

    private fun back(n: Int = 1) = repeat(n) { FirstRun.shell("input keyevent 4"); pause(900) }

    private fun settingsHome() {
        if (has("settings_home")) return
        FirstRun.tapVisible(rule, "topbar_settings_action")
        waitTag("settings_home")
    }

    private fun settingsPage(cat: String, ready: String? = null): Boolean {
        settingsHome()
        FirstRun.scrollTo(rule, "settings_cat_$cat")
        FirstRun.tapVisible(rule, "settings_cat_$cat")
        pause(1_500)
        return ready?.let { waitTag(it) } ?: !has("settings_home")
    }

    @After fun tearDown() {
        runCatching { rule.runOnUiThread { vm().closeVideoPlayer() } }
        runCatching { rule.runOnUiThread { vm().stopPlayback() } }
        addedProfiles.forEach { id -> runCatching { Profiles.remove(ctx, id) } }
        song?.let { runCatching { ctx.contentResolver.delete(it, null, null) } }
        log("SOCIAL_SUMMARY ok=${ok.size} ${ok} missing=${missing}")
    }

    @Test fun socialShots() {
        FirstRun.toTabs(rule, 120_000)
        FirstRun.dismissPrompts(rule)

        // 1 Videos tab with rows from the starter extensions installed on first run (the real market).
        step("extensions_home") {
            runCatching { rule.waitUntil(120_000) { ExtensionManager.state.value.installed.isNotEmpty() } }
            log("installed: ${ExtensionManager.state.value.installed.map { it.name }}")
            FirstRun.tapVisible(rule, FirstRun.navTag(rule, "nav_tab_videos"))
            runCatching { rule.waitUntil(90_000) { has("online_poster") } }
            pause(6_000); FirstRun.dismissPrompts(rule)
            snap("extensions_home", ExtensionManager.state.value.installed.isNotEmpty() && has("online_poster"))
        }

        // 2 Home tab.
        step("home") {
            FirstRun.tapVisible(rule, FirstRun.navTag(rule, "nav_tab_home"))
            waitTag("home_screen_root"); pause(4_000); FirstRun.dismissPrompts(rule)
            snap("home", has("home_screen_root"))
        }

        // 2b GROUP Z: Library tab (mockup set).
        step("library") {
            FirstRun.tapVisible(rule, FirstRun.navTag(rule, "nav_tab_library"))
            waitTag("library_screen_root"); pause(3_000); FirstRun.dismissPrompts(rule)
            snap("library", has("library_screen_root"))
            FirstRun.tapVisible(rule, FirstRun.navTag(rule, "nav_tab_home")); pause(1_500)
        }

        // 3 Settings home: the new subject groups.
        step("settings_home") {
            settingsHome(); pause(1_500)
            snap("settings_home", has("settings_home") && has("settings_cat_profiles"))
        }

        // 4 Settings search finds Telegram.
        step("settings_search") {
            settingsHome()
            // GROUP C: the search field opens from the header magnifier.
            if (!has("settings_search")) { rule.onAllNodesWithTag("settings_search_open", true)[0].performClick(); pause(600) }
            rule.onAllNodesWithTag("settings_search", true)[0].performTextReplacement("telegram")
            pause(1_500)
            snap("settings_search", has("settings_cat_telegram") || FirstRun.onScreen(rule).contains("Telegram"))
            rule.onAllNodesWithTag("settings_search", true)[0].performTextReplacement("")
            rule.onAllNodesWithTag("settings_search_open", true)[0].performClick()
            pause(800)
        }

        // 5 Sources page: the installed extensions.
        step("sources") {
            val open = settingsPage("sources")
            pause(3_000)
            snap("sources", open && ExtensionManager.state.value.installed.isNotEmpty())
            back(); settingsHome()
        }

        // 6 Profiles page (own page under You) with two extra profiles.
        step("profiles") {
            listOf("Mitu" to AccentTone.SAKURA, "Rafi" to AccentTone.AO).forEach { (n, t) ->
                addedProfiles += Profiles.add(ctx, n, t).id
            }
            snap("profiles", settingsPage("profiles", "profiles_settings"))
            back(); settingsHome()
        }

        // 7 "Who's watching?" as it shows at start when there are several profiles.
        step("whos_watching") {
            rule.runOnUiThread { ProfileSession.chosen.value = false }
            val shows = waitTag("profile_gate")
            pause(1_500)
            snap("whos_watching", shows)
            rule.runOnUiThread { ProfileSession.chosen.value = true }
            pause(1_500)
        }

        // 8 Real film playing.
        step("video_playing") {
            rule.runOnUiThread {
                IncomingMedia.open(ctx, StreamRequest(url = FILM, title = "Big Buck Bunny (Blender Foundation, CC BY 3.0)"), positionMs = 62_000L)
            }
            rule.waitUntil(120_000) { PlaybackDiagnostics.state.value.firstFrame || PlaybackDiagnostics.state.value.error != null }
            pause(8_000)
            val playing = PlaybackDiagnostics.state.value.firstFrame && PlaybackDiagnostics.state.value.error == null
            snap("video_playing", playing && has("video_player_root"))
            // The controls, as after a tap.
            runCatching { rule.onAllNodesWithTag("video_player_root", true)[0].performClick() }
            pause(1_200)
            snap("video_controls", playing && has("video_player_root"))
            pause(40_000) // counts toward today's statistics
            rule.runOnUiThread { vm().closeVideoPlayer() }
            pause(1_500)
        }

        // 9 Real song playing in Now Playing.
        step("music_playing") {
            song = putSong()
            val uri = song ?: throw AssertionError("song not saved")
            val t = AudioTrack(id = android.content.ContentUris.parseId(uri).toString(), title = "Song 1", artist = "SoundHelix",
                album = "SoundHelix examples", durationMs = 372_000, uri = uri.toString())
            rule.runOnUiThread { vm().scanMedia() }
            pause(4_000)
            rule.runOnUiThread { vm().playTrack(t, listOf(t)) }
            waitTag("now_playing_screen_root", 30_000)
            runCatching { rule.waitUntil(30_000) { vm().playbackState.value.isPlaying } }
            pause(6_000)
            snap("music_playing", has("now_playing_screen_root") && vm().playbackState.value.isPlaying)
            pause(40_000) // counts toward today's statistics
            rule.runOnUiThread { vm().closeNowPlaying() }
            pause(1_500)
        }

        // 10 Music tab with the song and the mini player.
        step("music_tab") {
            FirstRun.tapVisible(rule, FirstRun.navTag(rule, "nav_tab_music"))
            pause(3_000)
            snap("music_tab", has("mini_player_bar"))
            runCatching { rule.runOnUiThread { vm().stopPlayback() } }
        }

        // 11 Statistics page after the film and the song.
        step("stats") {
            snap("stats", settingsPage("stats", "stats_screen").also { pause(2_000) })
            back(); settingsHome()
        }

        // 12 A public Telegram channel, no login.
        step("telegram") {
            settingsPage("telegram", "websites_screen")
            rule.onAllNodesWithTag("site_add_field", true)[0].performTextReplacement("@durov")
            FirstRun.tapVisible(rule, "site_add")
            pause(2_000)
            snap("telegram_sites", has("site_row"))
            FirstRun.tapVisible(rule, "site_row")
            runCatching { rule.waitUntil(45_000) { FirstRun.onScreen(rule).contains("tg_post_") || has("tg_header") } }
            pause(5_000)
            snap("telegram_channel", has("tg_channel") && !has("tg_error"))
            back(2); settingsHome()
        }

        // 13–15 Subject pages.
        step("video_quality") { snap("video_quality", settingsPage("video_quality")); back(); settingsHome() }
        step("music_settings") { snap("music_settings", settingsPage("music")); back(); settingsHome() }
        step("downloads") { snap("downloads", settingsPage("downloads")); back(); settingsHome() }
        step("privacy") { snap("privacy", settingsPage("privacy", "privacy_lock_sections")); back(); settingsHome() }

        log("SOCIAL_RESULT ok=${ok.size} missing=${missing.size} $missing")
        assertTrue("only ${ok.size} shots showed what they claim; missing $missing", ok.size >= 8)
    }

    /** Saves the song into Music/Yumeiro social so the library shows it as a real file. */
    private fun putSong(): android.net.Uri? {
        listOf("android.permission.READ_MEDIA_AUDIO", "android.permission.READ_EXTERNAL_STORAGE")
            .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }
        if (Build.VERSION.SDK_INT < 29) return null
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "SoundHelix-Song-1.mp3")
            put(MediaStore.Audio.Media.TITLE, "Song 1")
            put(MediaStore.Audio.Media.ARTIST, "SoundHelix")
            put(MediaStore.Audio.Media.ALBUM, "SoundHelix examples")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Yumeiro social")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val c = URL(SONG).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000; c.readTimeout = 60_000
        c.inputStream.use { input -> ctx.contentResolver.openOutputStream(uri)!!.use { input.copyTo(it) } }
        ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    companion object {
        const val FILM = "https://upload.wikimedia.org/wikipedia/commons/transcoded/c/c0/Big_Buck_Bunny_4K.webm/Big_Buck_Bunny_4K.webm.720p.vp9.webm"
        const val SONG = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
    }
}
