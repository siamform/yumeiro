/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.real

import android.Manifest
import android.app.Application
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.MainActivity
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.RemoteExtension
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Real-content screenshots for the README and the website: the real app (MainActivity), real
 * network, the real Internet Archive CloudStream extension (installed the way the app installs
 * it) and the real YouTube Music feed. Nothing on screen is mocked except the video picture: the
 * test JVM cannot decode video, so a frame grabbed with ffmpeg from the same stream the extension
 * resolved is drawn into the player's content frame.
 *
 * Opt-in, needs network:
 *   YUMEIRO_IA_JAR     dex2jar output of InternetArchiveProvider.cs3 (the extension's code as a JVM jar)
 *   YUMEIRO_EXT_DIR    folder with SoraAnime.jar and CineStream.jar (dex2jar output of the real .cs3 files);
 *                      they fill Yumix with real anime (AniList catalogue) and movies and series (TMDb, Kitsu and Simkl catalogues).
 *                      The Internet Archive extension is installed later, only to play the public-domain film.
 *   YUMEIRO_FRAME      a frame from the film the tour opens (JPEG/PNG)
 *   YUMEIRO_REAL_OUT   output folder (default build/screenshots/real)
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-night-xxhdpi")
class RealAppShotsTest {
    private val jar: String? = System.getenv("YUMEIRO_IA_JAR")
    private val frame: String? = System.getenv("YUMEIRO_FRAME")
    private val extDir: String = System.getenv("YUMEIRO_EXT_DIR") ?: (System.getProperty("user.home") + "/shots-work/ext")
    private var tv = false
    private val grant = object : org.junit.rules.ExternalResource() {
        override fun before() {
            assumeTrue("set YUMEIRO_IA_JAR", jar != null && File(jar).exists())
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS)
            dev.asfakul.yumeiro.online.OnboardingGate.release()
            dev.asfakul.yumeiro.network.Connectivity.overrideForTests(true)
            val jars = (listOf(File(jar!!)) + FEED.map { File(extDir, it.first + ".jar") }.filter { it.exists() }).map { it.toURI().toURL() }
            ExtensionManager.loaderFactory = { f, parent -> java.net.URLClassLoader((jars + f.toURI().toURL()).toTypedArray(), parent) }
            coil.Coil.setImageLoader(coil.ImageLoader.Builder(app).components { add(SyncNetImages(app)) }.crossfade(false).respectCacheHeaders(false).build())
            val dm = app.resources.configuration
            tv = dm.screenWidthDp >= 900 && dm.screenHeightDp <= 600
            dev.asfakul.yumeiro.ui.tv.TvMode.set(app, if (tv) dev.asfakul.yumeiro.ui.tv.TvMode.Pref.TV else dev.asfakul.yumeiro.ui.tv.TvMode.Pref.AUTO)
        }
        override fun after() {
            dev.asfakul.yumeiro.ui.tv.TvMode.set(ApplicationProvider.getApplicationContext<Application>(), dev.asfakul.yumeiro.ui.tv.TvMode.Pref.AUTO)
        }
    }
    val rule = createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(grant).around(rule)
    private val ctx get() = rule.activity.applicationContext
    private val out = File(System.getenv("YUMEIRO_REAL_OUT") ?: File(System.getProperty("user.dir"), "build/screenshots/real").path).apply { mkdirs() }
    private var prefix = ""
    private val log = StringBuilder()
    private var dark = true

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun pause(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            // Coil's crossfade and the app's animations run on the system clock, which Robolectric holds still.
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(200))
            rule.mainClock.advanceTimeBy(200); rule.waitForIdle(); Thread.sleep(100)
        }
    }
    private fun t(tag: String) = if (!tv) tag else when (tag) {
        "nav_tab_home", "nav_tab_music", "nav_tab_videos", "nav_tab_library" -> "tv_$tag"
        "topbar_settings_action" -> "tv_nav_settings"
        else -> tag
    }
    /** Taps the first on-screen node with [tag] (pagers keep off-screen copies of their pages). */
    private fun tap(tag0: String, index: Int = 0) {
        val tag = t(tag0)
        rule.waitUntil(20_000) { rule.mainClock.advanceTimeBy(100); exists(tag) }
        val w = rule.activity.resources.displayMetrics.widthPixels
        val nodes = rule.onAllNodesWithTag(tag, true)
        val all = nodes.fetchSemanticsNodes()
        val vis = all.indices.filter { all[it].boundsInRoot.left >= -1f && all[it].boundsInRoot.left < w }
        nodes[vis.getOrElse(index) { 0 }].performClick(); pause(800)
    }
    private fun firstTag(prefix: String): String? = rule.onAllNodes(SemanticsMatcher("prefix $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }, true)
        .fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.TestTag)
    /** Index (among on-screen song rows) of the Elektronomia "Sky High" track row (the song, not a video upload). */
    private fun songIndex(): Int {
        val w = rule.activity.resources.displayMetrics.widthPixels
        val nodes = rule.onAllNodesWithTag("youtube_song", false).fetchSemanticsNodes().filter { it.boundsInRoot.left >= -1f && it.boundsInRoot.left < w }
        fun text(n: androidx.compose.ui.semantics.SemanticsNode) = (n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") ?: "") + " " + (n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: "")
        log.append("songs: ").append(nodes.joinToString(" | ") { text(it).take(60) }).append("\n")
        return nodes.indexOfFirst { val t = text(it); t.contains("Sky High") && t.contains("Elektronomia") && t.contains("plays") }.coerceAtLeast(0)
    }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; pause(1_200) }
    /** Scrolls a lazy list down and back so every cell runs a fresh layout pass and draws its loaded image. */
    private fun nudge(list: String) {
        if (!exists(list)) return
        runCatching { rule.onAllNodesWithTag(list, true)[0].performTouchInput { swipeUp() } }; pause(1_200)
        runCatching { rule.onAllNodesWithTag(list, true)[0].performScrollToIndex(0) }; pause(1_500)
    }
    private fun type(tag: String, text: String) {
        rule.waitUntil(15_000) { exists(tag) }
        rule.onAllNodesWithTag(tag, true)[0].performTextInput(text); pause(300)
        rule.onAllNodesWithTag(tag, true)[0].performImeAction()
    }
    private fun tags(): String = rule.onAllNodes(SemanticsMatcher("any") { true }, true).fetchSemanticsNodes()
        .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }.distinct().joinToString(" ")

    /**
     * Saves the screen. Popups and sheets are separate windows (extra roots): each is drawn over the
     * first root behind a scrim. Under Robolectric a bottom sheet window captures blank, so the sheet's
     * content node is drawn on the bottom edge over a surface-coloured backing, the way the sheet sits.
     */
    private fun snap(name: String, sheet: String? = null, dialogs: Boolean = false, dialog: String? = null) {
        pause(400)
        val roots = rule.onAllNodes(isRoot())
        val count = roots.fetchSemanticsNodes().size
        val base = roots[0].captureToImage().asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(base)
        if (dialog != null) {
            val img = rule.onAllNodesWithTag(dialog, true)[0].captureToImage().asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, false)
            canvas.drawColor(android.graphics.Color.argb(140, 0, 0, 0))
            canvas.drawBitmap(img, (base.width - img.width) / 2f, (base.height - img.height) / 2f, null)
        } else if (sheet == null) for (i in 1 until count) {
            val layer = runCatching { roots[i].captureToImage().asAndroidBitmap() }.getOrNull() ?: continue
            // Only dialogs: the floating mini player and toasts are their own small windows too.
            if (!dialogs || layer.height > base.height * 0.85f || layer.height < base.height * 0.12f) continue
            val full = layer.width == base.width
            val x = if (full) 0f else (base.width - layer.width) / 2f
            val y = if (layer.height >= base.height - 4) 0f else if (full) (base.height - layer.height).toFloat() else (base.height - layer.height) / 2f
            if (!full || layer.height < base.height - 4) canvas.drawColor(android.graphics.Color.argb(120, 0, 0, 0))
            canvas.drawBitmap(layer.copy(android.graphics.Bitmap.Config.ARGB_8888, false), x, y, null)
        } else {
            val img = (runCatching { rule.onAllNodesWithTag(sheet, true)[0].captureToImage() }.getOrNull()
                ?: run { pause(2_000); rule.onAllNodesWithTag(sheet, false)[0].captureToImage() }).asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, false)
            canvas.drawColor(android.graphics.Color.argb(120, 0, 0, 0))
            val d = rule.activity.resources.displayMetrics.density
            val handle = 28 * d
            val left = (base.width - img.width) / 2f
            val top = base.height - img.height - handle
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) 0xFF16161A.toInt() else 0xFFFFFFFF.toInt() }
            canvas.drawRoundRect(left, top, left + img.width, base.height + 40 * d, 28 * d, 28 * d, paint)
            paint.color = if (dark) 0x55FFFFFF else 0x33000000
            canvas.drawRoundRect(base.width / 2f - 16 * d, top + 12 * d, base.width / 2f + 16 * d, top + 16 * d, 2 * d, 2 * d, paint)
            canvas.drawBitmap(img, left, top + handle, null)
        }
        File(out, "$prefix$name.png").outputStream().use { base.compress(android.graphics.Bitmap.CompressFormat.PNG, 92, it) }
    }

    private fun step(name: String, wait: Long = 4_000, sheet: String? = null, dialogs: Boolean = false, dialog: String? = null, popup: Boolean = false, body: () -> Unit) {
        runCatching { body(); pause(wait)
            if (popup) { if (exists("download_confirm")) snap(name, dialog = "download_confirm") else snap(name, sheet = "yt_download_sheet") } else snap(name, sheet, dialogs, dialog); log.append("$prefix$name: ").append(tags()).append("\n") }
            .onFailure { e ->
                log.append("$prefix$name FAIL $e\n")
                e.stackTrace.filter { it.className.startsWith("dev.asfakul") }.take(6).forEach { log.append("    at $it\n") }
                runCatching { log.append("  tags: ").append(tags()).append("\n") }
            }
        File(out, "${prefix}log.txt").writeText(log.toString())
    }

    /** Real anime and movie extensions for the Yumix feed (installed the same way the market does). */
    private fun installFeed() {
        rule.runOnUiThread { dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.setOnboarding(ctx, dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.Onboarding.SKIPPED) }
        runBlocking {
            FEED.forEach { (name, meta) ->
                if (!File(extDir, "$name.jar").exists()) return@forEach
                runCatching { ExtensionManager.install(ctx, RemoteExtension(
                    internalName = name, name = name, version = meta[0].toInt(), url = meta[1], authors = listOf(meta[2]), description = meta[3],
                    tvTypes = meta[4].split(","), language = "en", iconUrl = null, repoUrl = meta[5], repoName = meta[6],
                ), ExtensionReview.Mode.AUTO) }.onFailure { log.append("install $name FAIL $it\n") }
            }
        }
        pause(3_000)
    }

    private fun installIa() {
        rule.runOnUiThread { dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.setOnboarding(ctx, dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.Onboarding.SKIPPED) }
        runBlocking {
            ExtensionManager.install(ctx, RemoteExtension(
                internalName = "InternetArchiveProvider", name = "InternetArchiveProvider", version = 22,
                url = "https://raw.githubusercontent.com/Luna712/Luna712-CloudStream-Extensions/builds/InternetArchiveProvider.cs3",
                authors = listOf("Luna712"), description = "Watch content from the Internet Archive at archive.org",
                tvTypes = listOf("Others"), language = "en",
                iconUrl = "https://www.google.com/s2/favicons?domain=archive.org&sz=%size%",
                repoUrl = ExtensionManager.DEFAULT_REPO, repoName = "Luna712's CloudStream extension repository",
            ), ExtensionReview.Mode.AUTO)
        }
        pause(3_000)
    }

    /** Draws the film frame into the player (and a caption line), like a playing picture. */
    private fun paintFrame(caption: String?) {
        val f = frame ?: return
        rule.runOnUiThread {
            val pv = findPlayer(rule.activity.window.decorView) ?: return@runOnUiThread
            val content = pv.findViewById<androidx.media3.ui.AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame) ?: return@runOnUiThread
            content.setAspectRatio(16f / 9f)
            content.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            pv.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_shutter)?.visibility = android.view.View.GONE
            val subs = pv.subtitleView
            if (content.findViewWithTag<android.view.View>("real_frame") == null) {
                val at = (0 until content.childCount).firstOrNull { content.getChildAt(it) === subs } ?: content.childCount
                content.addView(android.widget.ImageView(rule.activity).apply {
                    tag = "real_frame"
                    setImageBitmap(android.graphics.BitmapFactory.decodeFile(f))
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                }, at, android.widget.FrameLayout.LayoutParams(-1, -1))
            }
            if (caption != null && subs != null) {
                subs.visibility = android.view.View.VISIBLE
                subs.setCues(listOf(androidx.media3.common.text.Cue.Builder().setText(caption).build()))
            }
        }
        repeat(4) { rule.mainClock.advanceTimeBy(250); rule.waitForIdle() }
    }
    private fun findPlayer(v: android.view.View): androidx.media3.ui.PlayerView? {
        if (v is androidx.media3.ui.PlayerView) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findPlayer(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun openFilm() {
        if (!exists(t("video_source_switch"))) tap("nav_tab_videos")
        tap("topbar_search_action")
        type("online_search_input", FILM_QUERY)
        pause(12_000); nudge("online_search_results")
    }
    /** Opens the exact public-domain item (search by its archive.org identifier, one result). */
    private fun playFilm() {
        rule.onAllNodesWithTag("online_search_input", true)[0].performTextClearance()
        type("online_search_input", FILM_ID); pause(10_000)
        rule.onAllNodes(hasTestTag("online_search_source_Internet Archive"), true).fetchSemanticsNodes().firstOrNull()?.let { tap("online_search_source_Internet Archive"); pause(800) }
        tap("online_poster", 0)
    }
    private fun closePlayer() {
        if (exists("watch_close")) runCatching { tap("watch_close") }
        rule.runOnUiThread {
            runCatching { androidx.lifecycle.ViewModelProvider(rule.activity)[dev.asfakul.yumeiro.ui.viewmodel.MainViewModel::class.java].closeVideoPlayer() }
            dev.asfakul.yumeiro.online.OnlineSession.end()
        }
        pause(1_500)
    }

    /** The full phone tour (also used for the light theme). */
    private fun phoneTour(p: String) {
        prefix = p
        installFeed()
        // Music first: the video player stays docked (or floating) once it is open.
        step("11_music_online", 20_000) { tap("nav_tab_music"); tap("music_source_switch"); tap("music_source_online") }
        step("11_music_online", 1_000) { nudge("youtube_music_list") }
        step("12_music_search", 12_000) { tap("topbar_search_action"); type("youtube_search", SONG_QUERY); pause(3_000); if (exists("ytmusic_suggestions_row")) tap("ytmusic_suggestions_row") }
        step("12_music_search", 1_000) { nudge("ytmusic_results") }
        step("14_now_playing", 15_000) {
            tap("youtube_song", songIndex()); pause(6_000)
            if (exists("now_playing_error_dismiss")) tap("now_playing_error_dismiss")
        }
        step("15_up_next", 3_000) { tap("now_playing_queue_toggle_button") }
        step("16_mini_player", 3_000) { tap("now_playing_queue_toggle_button"); tap("now_playing_collapse_button") }
        step("13_song_download", 4_000, popup = true) { rule.onAllNodesWithTag("youtube_song", true)[1].performTouchInput { longClick() } }
        step("13b_closed", 1_000) { back(); if (exists("youtube_search_close")) tap("youtube_search_close") }
        step("03_yumix", 25_000) { tap("nav_tab_videos"); tap("video_source_switch"); tap("video_source_online") }
        step("03_yumix", 1_000) { nudge("online_home_list") }
        step("03b_yumix_category", 6_000) { tap(if (exists("online_category_ANIME")) "online_category_ANIME" else "online_category_MOVIES") }
        step("03c_yumix_all", 2_000) { tap("online_category_all") }
        step("07_extensions", 4_000) { tap("online_extensions") }
        step("08_market_discover", 12_000) { back(); tap("online_market") }
        step("09_market_installed", 3_000) { tap("market_tab_installed") }
        step("10_market_repos", 3_000) { tap("market_tab_repos") }
        step("08b_market_search", 8_000) { tap("market_tab_discover"); tap("market_search_toggle"); rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction(), true)[0].performTextInput("anime") }
        step("04_search", 1_000) { back(); back(); installIa(); openFilm() }
        step("05_watch", 10_000) { playFilm(); pause(4_000); paintFrame(null) }
        step("05_watch_caption", 500) { paintFrame(CAPTION) }
        step("06_watch_download", 2_000, dialog = "download_confirm") { tap("online_watch_download"); paintFrame(null) }
        step("06b_closed", 1_000) { back(); closePlayer(); back(); back() }
        step("01_home", 4_000) { tap("nav_tab_home"); runCatching { rule.onNodeWithTag("home_list", true).performScrollToIndex(0) } }
        step("01_home", 1_000) { nudge("home_list") }
        step("02_home_shortcuts", 2_000) { tap("home_spaces_more"); pause(800) }
        step("02b_home_continue", 2_000) { runCatching { rule.onNodeWithTag("home_list", true).performScrollToNode(hasTestTag("home_continue_card")) } }
        step("17_settings", 3_000) { runCatching { rule.onNodeWithTag("home_list", true).performScrollToIndex(0) }; tap("topbar_settings_action") }
        step("18_sources_hub", 3_000) { runCatching { rule.onNodeWithTag("settings_home", true).performScrollToNode(hasTestTag("settings_cat_sources")) }; tap("settings_cat_sources") }
        for (i in 1..4) step("18_sources_tab_$i", 3_000) { tap("sources_tab_$i") }
    }

    @Test fun phoneDark() { dark = true; phoneTour("phone_dark_") }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun phoneLight() { dark = false; phoneTour("phone_light_") }

    /** Landscape: the watch page turns into the full-screen player. */
    @Test @Config(qualifiers = "w891dp-h411dp-land-night-xxhdpi")
    fun fullScreenDark() {
        prefix = "full_dark_"; installIa()
        step("search", 1_000) { openFilm() }
        step("player_caption", 10_000) { playFilm(); pause(4_000); paintFrame(CAPTION) }
    }

    @Test @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun fullScreenLight() {
        dark = false; prefix = "full_light_"; installIa()
        step("search", 1_000) { openFilm() }
        step("player_caption", 10_000) { playFilm(); pause(4_000); paintFrame(CAPTION) }
    }

    private fun tvTour(p: String) {
        prefix = p; installFeed()
        step("01_home", 3_000) { rule.waitUntil(15_000) { rule.mainClock.advanceTimeBy(100); exists("tv_nav_rail") } }
        step("03_yumix", 25_000) { tap("nav_tab_videos"); tap("video_source_switch"); tap("video_source_online") }
        step("03_yumix", 1_000) { nudge("online_home_list") }
        step("03b_yumix_category", 6_000) { tap(if (exists("online_category_ANIME")) "online_category_ANIME" else "online_category_MOVIES") }
        step("03c_yumix_all", 2_000) { tap("online_category_all") }
        step("04_search", 1_000) { installIa(); openFilm() }
        step("05_player", 10_000) { playFilm(); pause(4_000); paintFrame(CAPTION) }
        step("07_extensions", 4_000) { closePlayer(); back(); back(); if (!exists("online_extensions")) { tap("nav_tab_videos") }; tap("online_extensions") }
        step("08_market", 12_000) { back(); tap("online_market") }
        step("11_music_online", 20_000) { back(); tap("nav_tab_music"); tap("music_source_switch"); tap("music_source_online") }
        step("11_music_online", 1_000) { nudge("youtube_music_list") }
        // The music home is personalised and full of real artists; the shots use the NCS search.
        step("12_music_search", 12_000) { tap("topbar_search_action"); type("youtube_search", SONG_QUERY); pause(3_000); if (exists("ytmusic_suggestions_row")) tap("ytmusic_suggestions_row") }
        step("12_music_search", 1_000) { nudge("ytmusic_results") }
        step("17_settings", 3_000) { back(); if (exists("youtube_search_close")) tap("youtube_search_close") }
        step("17_settings", 3_000) { tap("topbar_settings_action") }
        step("01_home_after", 4_000) { back(); tap("nav_tab_home") }
    }

    @Test @Config(qualifiers = "w960dp-h540dp-land-night-xhdpi")
    fun tvDark() { dark = true; tvTour("tv_dark_") }

    @Test @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun tvLight() { dark = false; tvTour("tv_light_") }

    private companion object {
        /** Public domain (Internet Archive item gullivers-travels-1939_202312, Public Domain Mark 1.0). */
        const val FILM_QUERY = "Gulliver's Travels 1939"
        /** name to [version, url, author, description, types, repo url, repo name] */
        val FEED = listOf(
            "SoraAnime" to listOf("1", "https://raw.githubusercontent.com/Pendztagram/extcloud/builds/SoraAnime.cs3", "Pendztagram", "Anime catalogue from AniList", "Anime,AnimeMovie,OVA", "https://raw.githubusercontent.com/Pendztagram/extcloud/builds/repo.json", "ExtCloud"),
            "CineStream" to listOf("487", "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CineStream.cs3", "megix", "Movies, series, anime and Asian drama catalogues (TMDb, Kitsu, Simkl)", "TvSeries,Movie,AsianDrama,Anime", "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CS.json", "Megix Repo"),
        )
        const val FILM_ID = "gullivers-travels-1939_202312"
        const val CAPTION = "[orchestral music]"
        const val SONG_QUERY = "NCS Elektronomia Sky High"
    }
}

/** Fetches http(s) images on a worker thread and hands them back in the same request, so a still frame has its artwork. */
private class SyncNetImages(private val app: Application) : coil.intercept.Interceptor {
    private val http = okhttp3.OkHttpClient()
    override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
        val url = chain.request.data.toString()
        if (!url.startsWith("http")) return chain.proceed(chain.request)
        val bmp = cache[url] ?: run {
            var got: android.graphics.Bitmap? = null
            val th = Thread {
                got = runCatching {
                    val req = okhttp3.Request.Builder().url(url).header("User-Agent", "Mozilla/5.0")
                    chain.request.headers.forEach { (k, v) -> req.header(k, v) }
                    http.newCall(req.build()).execute().use { r ->
                        val bytes = r.body!!.bytes()
                        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                        var sample = 1; while (o.outWidth / (sample * 2) >= 720) sample *= 2
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                }.getOrNull()
            }
            th.start(); th.join(20_000)
            got?.also { cache[url] = it }
        } ?: return chain.proceed(chain.request)
        return coil.request.SuccessResult(android.graphics.drawable.BitmapDrawable(app.resources, bmp), chain.request.newBuilder().crossfade(false).build(), coil.decode.DataSource.MEMORY_CACHE)
    }
    companion object { val cache = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>() }
}
