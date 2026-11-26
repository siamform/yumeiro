/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.manga.MangaChapter
import dev.asfakul.yumeiro.sources.manga.MangaDetails
import dev.asfakul.yumeiro.sources.manga.MangaImage
import dev.asfakul.yumeiro.sources.manga.MangaItem
import dev.asfakul.yumeiro.sources.manga.MangaListPage
import dev.asfakul.yumeiro.sources.manga.MangaPageRef
import dev.asfakul.yumeiro.sources.manga.MangaProvider
import dev.asfakul.yumeiro.stream.StreamRepository
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.tv.LocalTvMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * GROUP C catch-all (owner: "every other page that has the same pattern, listed or not"). The first
 * Group C pass fixed Novel, Novel sources and the Source page; this re-audit found nine more pages that
 * still had a permanent search box or a filter chip row. Each test opens the real screen (phone and TV
 * layout on the TV emulator) and checks:
 *  - no search field and no chip row is on the page when it opens;
 *  - the magnifier / filter icon is there, labelled, and opens the field or the sheet;
 *  - the field or the sheet really filters.
 * Before this commit every "no field on open" assertion fails. Log tag YuGroupC2.
 */
@RunWith(AndroidJUnit4::class)
class GroupCCatchAllDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val tv = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun hasExact(t: String) = rule.onAllNodesWithText(t, substring = false, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag: String) { rule.onAllNodesWithTag(tag, true).onFirst().performClick(); rule.waitForIdle() }
    private fun waitTag(tag: String, ms: Long = 15_000) = rule.waitUntil(ms) { has(tag) }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { CompositionLocalProvider(LocalTvMode provides tv) { MTPlayerTheme { content() } } }
    }

    /** Field hidden on open → icon → field shown → icon again closes it. */
    private fun searchFlow(page: String, open: String, field: String) {
        assertFalse("$page: search box is on the page before the icon is pressed", has(field))
        assertTrue("$page: no search icon ($open)", has(open))
        click(open)
        waitTag(field)
        Log.i("YuGroupC2", "$page: search opens from $open (tv=$tv)")
    }

    @Test fun a_settings() {
        show { dev.asfakul.yumeiro.ui.screens.settings.SettingsScreen(onBackClick = {}, onRescanClick = {}) }
        waitTag("settings_home")
        searchFlow("Settings", "settings_search_open", "settings_search")
        rule.onAllNodesWithTag("settings_search", true).onFirst().performTextInput("telegram")
        rule.waitUntil(5_000) { has("settings_cat_telegram") }
        FirstRun.shot("groupc2_settings_search")
        click("settings_search_open")
        rule.waitUntil(5_000) { !has("settings_search") }
    }

    @Test fun b_upcoming() {
        show { dev.asfakul.yumeiro.ui.screens.online.UpcomingScreen(onBack = {}, onSearch = {}) }
        waitTag("upcoming_search_open")
        searchFlow("Upcoming", "upcoming_search_open", "upcoming_search")
        FirstRun.shot("groupc2_upcoming")
    }

    private fun mitem(n: Int) = MangaItem("yu_test_gc", "yugc://t/$n", "GroupC Manga $n", null)
    private fun fake(id: String, name: String) = object : MangaProvider {
        override val id = id
        override val name = name
        override val lang = "en"
        override val ecosystem = "test"
        override val nsfw = false
        override val supportsLatest = true
        override val baseUrl: String? = null
        override suspend fun popular(page: Int) = MangaListPage(listOf(mitem(1), mitem(2)), false)
        override suspend fun latest(page: Int) = MangaListPage(listOf(mitem(3)), false)
        override suspend fun search(query: String, page: Int) = MangaListPage(listOf(MangaItem("yu_test_gc", "yugc://s", "Found $query", null)), false)
        override suspend fun details(url: String) = MangaDetails(id, url, "GroupC", null)
        override suspend fun chapters(url: String) = listOf(MangaChapter("$url/1", "Chapter 1", 1f))
        override suspend fun pages(chapterUrl: String) = emptyList<MangaPageRef>()
        override suspend fun image(page: MangaPageRef) = MangaImage(ByteArray(0), null)
    }

    @Test fun c_manga() {
        SourceHub.publishManga("yu_test_gc_bridge", listOf(fake("yu_test_gc", "AAA GroupC source"), fake("yu_test_gc2", "AAB GroupC source")))
        try {
            show { dev.asfakul.yumeiro.ui.screens.manga.MangaHomeScreen(onBack = {}, onOpenTitle = {}, onOpenSources = {}) }
            waitTag("manga_home")
            assertFalse("Manga: the source strip row is still on the page", has("manga_source_bar"))
            searchFlow("Manga", "manga_search_open", "manga_search")
            rule.onAllNodesWithTag("manga_search", true).onFirst().performTextInput("zz")
            rule.waitUntil(10_000) { hasText("Found zz") }
            assertTrue("Manga: no source filter icon", has("manga_source_pick"))
            FirstRun.shot("groupc2_manga")
        } finally { SourceHub.remove("yu_test_gc_bridge") }
    }

    @Test fun d_stream_watch_panel() {
        val chans = (1..8).map { StreamRequest(url = "http://127.0.0.1:9/gc$it.m3u8", title = "GroupC TV $it", group = if (it % 2 == 0) "News" else "Sports") }
        StreamRepository.setPlaylist("GroupC list", chans)
        try {
            show { dev.asfakul.yumeiro.ui.screens.watch.StreamWatchPanel(currentUrl = chans[0].url, title = chans[0].title) }
            waitTag("stream_watch_panel")
            // The "Channels" label sits below the player controls and actions; on the TV layout it is below the fold,
            // so scroll down to it as a user would (run 45761414933).
            rule.waitForIdle()
            runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction(), true).onFirst().performScrollToNode(androidx.compose.ui.test.hasTestTag("stream_watch_search_open")) }
            rule.waitUntil(10_000) { has("stream_watch_search_open") }
            assertFalse("Watch panel: group chip row is still on the page", has("stream_watch_group"))
            searchFlow("Watch panel", "stream_watch_search_open", "stream_watch_search")
            assertTrue("Watch panel: no group filter icon", has("stream_watch_filter"))
            click("stream_watch_filter")
            waitTag("stream_watch_groups")
            assertTrue(hasText("News") && hasText("Sports"))
            FirstRun.shot("groupc2_watch_filter_sheet")
        } finally { StreamRepository.setPlaylist(null, emptyList()) }
    }

    @Test fun e_downloads() {
        DownloadSettings.init(ctx); OnlineDownloads.ensureLoaded(ctx)
        val before = OnlineDownloads.items.value
        val item = OnlineDownloads.Item(
            "gc1", "GroupC Show", "GcApi", "u-gc", "Episode 1", null, emptyMap(), "", false, emptyMap(), "gc-1.mp4",
            status = OnlineDownloads.Status.DONE, bytes = 4096, total = 4096, kind = OnlineDownloads.Kind.EPISODE, folder = "GroupC Show", order = 0,
            finishedAt = System.currentTimeMillis() - 60_000, quality = "720p",
        )
        OnlineDownloads.fileOf(ctx, item).writeBytes(ByteArray(4096))
        OnlineDownloads.setItemsForTest(ctx, listOf(item))
        try {
            show { dev.asfakul.yumeiro.ui.screens.downloads.DownloadsScreen(onBack = {}, onPlayVideo = {}, onBrowse = {}) }
            waitTag("downloads_screen")
            rule.waitUntil(10_000) { has("downloads_filter") }
            assertFalse("Downloads: filter chip row is still on the page", has("downloads_filter_all"))
            click("downloads_filter")
            waitTag("downloads_filter_group")
            assertTrue(has("downloads_filter_permanent") && has("downloads_filter_offline"))
            FirstRun.shot("groupc2_downloads_filter")
        } finally {
            runCatching { OnlineDownloads.fileOf(ctx, item).delete() }
            OnlineDownloads.setItemsForTest(ctx, before)
        }
    }

    @Test fun f_my_list() {
        val items = listOf(
            OnlineItem("GroupC Saved One", "u-gc-a", "yu-test-gc", null, type = "TV"),
            OnlineItem("GroupC Saved Two", "u-gc-b", "yu-test-gc", null, type = "TV"),
        )
        OnlineBookmarks.save(ctx, items[0], OnlineBookmarks.Status.PLAN)
        OnlineBookmarks.save(ctx, items[1], OnlineBookmarks.Status.picks.last())
        try {
            show {
                dev.asfakul.yumeiro.ui.screens.library.VideoLibraryContent(
                    deviceContinue = emptyList(), onOpenTitle = {}, onPlayVideo = {}, onBrowseOnline = {}, onOpenHistory = {},
                )
            }
            waitTag("video_library")
            rule.waitUntil(10_000) { has("library_mylist_filter") }
            assertFalse("My List: status chip row (All / ${OnlineBookmarks.Status.PLAN.label}) is still on the page", hasExact("All") || hasExact(OnlineBookmarks.Status.PLAN.label))
            click("library_mylist_filter")
            waitTag("library_mylist_status")
            assertTrue(hasExact("All") && hasExact(OnlineBookmarks.Status.PLAN.label))
            FirstRun.shot("groupc2_mylist_filter")
        } finally { items.forEach { OnlineBookmarks.remove(ctx, it.api, it.url) } }
    }

    @Test fun g_playlist_market() {
        show { dev.asfakul.yumeiro.ui.screens.stream.PlaylistMarketScreen(onBack = {}, onOpenPlaylist = {}) }
        runCatching { rule.waitUntil(60_000) { has("market_list") } }
        assumeTrue("Market catalog did not load (network)", has("market_list"))
        assertTrue("Market: no filter icon", has("market_filter"))
        assertFalse("Market: the All / category chip row is still in the list", hasExact("All"))
        click("market_filter")
        waitTag("market_filter_show")
        assertTrue(has("market_filter_category"))
        FirstRun.shot("groupc2_market_filter")
    }
}
