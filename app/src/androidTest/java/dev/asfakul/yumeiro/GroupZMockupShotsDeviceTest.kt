/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.manga.MangaChapter
import dev.asfakul.yumeiro.sources.manga.MangaDetails
import dev.asfakul.yumeiro.sources.manga.MangaImage
import dev.asfakul.yumeiro.sources.manga.MangaItem
import dev.asfakul.yumeiro.sources.manga.MangaListPage
import dev.asfakul.yumeiro.sources.manga.MangaPageRef
import dev.asfakul.yumeiro.sources.manga.MangaProvider
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GROUP Z mockup captures: the real screens the owner listed (splash / loader, source page, website page,
 * Telegram page, search / filter / add patterns, manga, novel, settings, downloads), on the phone and the TV
 * layout. The app-level screens (home with the floating pill bar, feeds, library, player, music) come from
 * SocialShotsDeviceTest. Each test saves `z_<name>` and asserts the screen it claims is on screen, so a
 * mockup can never show a blank or wrong page. Not a feature test. Log tag YuGroupZ.
 */
@RunWith(AndroidJUnit4::class)
class GroupZMockupShotsDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val tv = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val form get() = if (tv) "tv" else "phone"

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag: String) { rule.onAllNodesWithTag(tag, true).onFirst().performClick(); rule.waitForIdle() }
    private fun waitTag(tag: String, ms: Long = 20_000) = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
    private fun pause(ms: Long) { val until = SystemClock.uptimeMillis() + ms; runCatching { rule.waitUntil(ms + 5_000) { SystemClock.uptimeMillis() > until } } }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { content() } } }
    }

    private fun snap(name: String, shows: Boolean) {
        rule.waitForIdle()
        FirstRun.shot("z_${form}_$name")
        Log.i("YuGroupZ", "Z_SHOT ${form}_$name ${if (shows) "ok" else "missing"}")
        assertTrue("mockup $name does not show what it claims", shows)
    }

    @Test fun a_splash() {
        show { dev.asfakul.yumeiro.ui.screens.splash.SplashScreen(onSplashFinished = {}) }
        pause(900)
        snap("splash", has("splash_screen_root"))
    }

    @Test fun b_sourcePages() {
        show { dev.asfakul.yumeiro.ui.screens.sources.SourcesHubScreen(onBack = {}) }
        assertTrue(waitTag("src_home")); pause(1_500)
        snap("source_overview", has("src_home"))
        click("src_page_aniyomi")
        assertTrue(waitTag("src_route_aniyomi")); pause(1_200)
        snap("source_aniyomi", has("src_route_aniyomi") && has("hub_tachiyomi_anime_add_plus"))
    }

    @Test fun c_websitePage() {
        inst.runOnMainSync {
            SavedSites.add(ctx, "https://archive.org/details/movies", "Internet Archive")
            SavedSites.add(ctx, "https://en.wikipedia.org/wiki/Animation", "Wikipedia")
        }
        try {
            show { dev.asfakul.yumeiro.ui.screens.sites.WebSitesScreen(onBack = {}, only = SavedSites.Kind.WEBSITE) }
            assertTrue(runCatching { rule.waitUntil(15_000) { hasText("Internet Archive") }; true }.getOrDefault(false))
            pause(1_000)
            snap("websites_list", hasText("Wikipedia"))
            rule.onAllNodesWithText("Wikipedia", substring = false, useUnmergedTree = true).onFirst().performClick()
            assertTrue(waitTag("browser_bar")); pause(8_000)
            snap("website_page", has("browser_bar"))
            click("browser_menu"); pause(800)
            snap("website_menu", has("browser_menu_nav") && has("browser_menu_tabs"))
        } finally {
            inst.runOnMainSync { listOf("https://archive.org/details/movies", "https://en.wikipedia.org/wiki/Animation").forEach { SavedSites.remove(ctx, it) } }
        }
    }

    @Test fun d_telegramPage() {
        show { dev.asfakul.yumeiro.ui.screens.sites.WebSitesScreen(onBack = {}, only = SavedSites.Kind.TELEGRAM) }
        assertTrue(waitTag("telegram_screen")); pause(1_500)
        snap("telegram", has("tg_add_open") && has("tg_user"))
    }

    private fun item(n: Int) = MangaItem("yu_test_z", "yuz://t/$n", listOf("Moonlit Atlas", "The Paper Fox", "Harbour of Stars", "Clockwork Rain", "Lantern Road", "Seventh Garden")[n % 6], null)
    private val provider = object : MangaProvider {
        override val id = "yu_test_z"
        override val name = "Sample source"
        override val lang = "en"
        override val ecosystem = "test"
        override val nsfw = false
        override val supportsLatest = true
        override val baseUrl: String? = null
        override suspend fun popular(page: Int) = MangaListPage((0 until 6).map { item(it) }, false)
        override suspend fun latest(page: Int) = MangaListPage((0 until 6).map { item(it) }, false)
        override suspend fun search(query: String, page: Int) = MangaListPage(listOf(item(0)), false)
        override suspend fun details(url: String) = MangaDetails(id, url, "Sample", null)
        override suspend fun chapters(url: String) = listOf(MangaChapter("$url/1", "Chapter 1", 1f))
        override suspend fun pages(chapterUrl: String) = emptyList<MangaPageRef>()
        override suspend fun image(page: MangaPageRef) = MangaImage(ByteArray(0), null)
    }

    @Test fun e_manga() {
        SourceHub.publishManga("yu_test_z_bridge", listOf(provider))
        try {
            show { dev.asfakul.yumeiro.ui.screens.manga.MangaHomeScreen(onBack = {}, onOpenTitle = {}, onOpenSources = {}) }
            assertTrue(waitTag("manga_home"))
            runCatching { rule.waitUntil(15_000) { hasText("Moonlit Atlas") } }
            pause(1_500)
            snap("manga", has("manga_home") && hasText("Moonlit Atlas"))
            click("manga_search_open"); pause(800)
            snap("manga_search_open", has("manga_search"))
        } finally { SourceHub.remove("yu_test_z_bridge") }
    }

    @Test fun f_novel() {
        show { dev.asfakul.yumeiro.ui.screens.novel.NovelHomeScreen(onBack = {}, onOpenNovel = {}, onOpenSources = {}) }
        assertTrue(waitTag("novel_home")); pause(4_000)
        snap("novel", has("novel_home"))
    }

    @Test fun g_settingsAndSearch() {
        show { dev.asfakul.yumeiro.ui.screens.settings.SettingsScreen(onBackClick = {}, onRescanClick = {}) }
        assertTrue(waitTag("settings_home")); pause(1_200)
        snap("settings", has("settings_search_open"))
        click("settings_search_open"); assertTrue(waitTag("settings_search"))
        rule.onAllNodesWithTag("settings_search", true).onFirst().performTextInput("telegram")
        runCatching { rule.waitUntil(5_000) { has("settings_cat_telegram") } }
        snap("search_pattern", has("settings_cat_telegram"))
    }

    @Test fun h_filterPattern() {
        DownloadSettings.init(ctx); OnlineDownloads.ensureLoaded(ctx)
        val before = OnlineDownloads.items.value
        val items = listOf("Moonlit Atlas" to "Episode 3", "Harbour of Stars" to "Episode 1").mapIndexed { i, (show, ep) ->
            OnlineDownloads.Item(
                "z$i", show, "Sample", "u-z$i", ep, null, emptyMap(), "", false, emptyMap(), "z-$i.mp4",
                status = OnlineDownloads.Status.DONE, bytes = 4096, total = 4096, kind = OnlineDownloads.Kind.EPISODE, folder = show, order = i,
                finishedAt = System.currentTimeMillis() - 60_000L * (i + 1), quality = "720p",
            )
        }
        items.forEach { OnlineDownloads.fileOf(ctx, it).writeBytes(ByteArray(4096)) }
        OnlineDownloads.setItemsForTest(ctx, items)
        try {
            show { dev.asfakul.yumeiro.ui.screens.downloads.DownloadsScreen(onBack = {}, onPlayVideo = {}, onBrowse = {}) }
            assertTrue(waitTag("downloads_filter")); pause(1_000)
            snap("downloads", has("downloads_screen"))
            click("downloads_filter"); assertTrue(waitTag("downloads_filter_group")); pause(900)
            snap("filter_pattern", has("downloads_filter_sheet") || has("downloads_filter_group"))
        } finally {
            items.forEach { runCatching { OnlineDownloads.fileOf(ctx, it).delete() } }
            OnlineDownloads.setItemsForTest(ctx, before)
        }
    }
}
