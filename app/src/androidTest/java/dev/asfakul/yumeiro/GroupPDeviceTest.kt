/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.online.EpisodeStatus
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.telegram.TelegramSeen
import dev.asfakul.yumeiro.online.web.SavedSites
import dev.asfakul.yumeiro.online.web.SiteHistory
import dev.asfakul.yumeiro.online.web.SiteLogins
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.ui.components.RecentSearches
import dev.asfakul.yumeiro.ui.screens.online.SearchHistory
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.File

/**
 * Group P (beta chapter) on a real phone / TV, log tag YuGroupP.
 *  a (B19, GH-47): a second profile sees none of the first one's searches, saved and visited sites, site logins,
 *     "seen" marks or downloads; the first profile still has all of them.
 *  b (B9): "Clear watch and search history" leaves no history: not in the leftover .tmp copy (read back at the next
 *     start), the Play Link / Live TV / website recents, the seen marks, nor the saved searches in the cache folder.
 *  c (B13): Song info for a YouTube Music song shows its length and the size of the audio it streams.
 *  d (B7): the Novel page shows its banner with real covers, on several real LNReader sources (network).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupPDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupP", s)

    @After fun tearDown() {
        runCatching { Profiles.select(ctx, Profiles.DEFAULT) }
    }

    private fun texts(under: String): List<String> = rule.onAllNodes(
        hasAnyAncestor(hasTestTag(under)) and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), true,
    ).fetchSemanticsNodes().flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
    private fun allText(): List<String> = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), true)
        .fetchSemanticsNodes().flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

    /** Forgets what a store loaded (its own test seam, or its `loaded` flag), so it reads the open profile again. */
    private fun unload(store: Any, vararg sets: String) {
        runCatching { store.javaClass.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(store, false) }
        sets.forEach { n -> runCatching { (store.javaClass.getDeclaredField(n).apply { isAccessible = true }.get(store) as MutableCollection<*>).clear() } }
    }
    private fun reloadStores() {
        SavedSites.resetForTest(); SavedSites.init(ctx)
        SiteHistory.resetForTest(); SiteHistory.init(ctx)
        unload(SiteLogins, "cache")
        OnlineDownloads.unloadForTests(); OnlineDownloads.ensureLoaded(ctx)
    }

    private fun item(id: String) = OnlineDownloads.Item(
        id = id, title = "YuP download", api = "YuP", titleUrl = "https://yup.invalid/t", episodeLabel = null, posterUrl = null,
        posterHeaders = emptyMap(), url = "https://yup.invalid/v.mp4", isHls = false, headers = emptyMap(), fileName = "yup.mp4",
        status = OnlineDownloads.Status.DONE,
    )

    /** What profile [label] can see of the seeded data. */
    private fun seen(label: String): Map<String, Boolean> = mapOf(
        "search_history" to ("yup alpha" in SearchHistory.load(ctx)),
        "recent_searches" to ("yup novel" in RecentSearches.load(ctx, "novel")),
        "saved_sites" to SavedSites.sites.value.any { "yup-saved" in it.url },
        "site_history" to SiteHistory.visits.value.any { "yup-visit" in it.url },
        "site_logins" to SiteLogins.has(ctx, "yup-login.example"),
        "telegram_seen" to (TelegramSeen.get(ctx, "yupchannel") == 41),
        "episode_seen" to (EpisodeStatus.Seen.get(ctx, "YuP", "https://yup.invalid/t", "sub") == 7),
        "downloads" to OnlineDownloads.items.value.any { it.title == "YuP download" },
    ).also { log("GROUPP_B19 $label sees $it") }

    @Test fun a_b19_profilesKeepTheirOwnData() {
        Profiles.resetForTests(ctx)
        Profiles.select(ctx, Profiles.DEFAULT)
        reloadStores()
        // The first profile's data, through the app's own stores.
        SearchHistory.add(ctx, "yup alpha")
        RecentSearches.add(ctx, "novel", "yup novel")
        SavedSites.add(ctx, "https://yup-saved.example/home")
        SiteHistory.visit(ctx, "https://yup-visit.example/page", "YuP visit")
        SiteLogins.add(ctx, "yup-login.example")
        TelegramSeen.mark(ctx, "yupchannel", 41)
        EpisodeStatus.Seen.put(ctx, "YuP", "https://yup.invalid/t", "sub", 7)
        OnlineDownloads.setItemsForTest(ctx, listOf(item("yup-dl-1")))
        OnlineDownloads.replace("yup-dl-1") { it }
        OnlineDownloads.flushForTests()
        Thread.sleep(300) // apply() writes
        val mine = seen("default")
        assertTrue("B19: the first profile has its data $mine", mine.values.all { it })

        val other = Profiles.add(ctx, "YuP Two", dev.asfakul.yumeiro.ui.theme.AccentTone.values().last())
        Profiles.select(ctx, other.id)
        reloadStores()
        val theirs = seen("second")
        val leaked = theirs.filterValues { it }.keys
        log("GROUPP_B19 leaked=$leaked")

        Profiles.select(ctx, Profiles.DEFAULT)
        reloadStores()
        val back = seen("default-again")
        // Clean up the second profile (its own data goes with it) and the seeds.
        Profiles.remove(ctx, other.id)
        SearchHistory.clear(ctx); RecentSearches.clearAll(ctx); SavedSites.resetForTest(); SiteHistory.clear(ctx)
        SiteLogins.remove(ctx, "yup-login.example"); TelegramSeen.forget(ctx, "yupchannel")
        OnlineDownloads.setItemsForTest(ctx, emptyList()); OnlineDownloads.replace("none") { it }; OnlineDownloads.flushForTests()
        log("GROUPP_B19_RESULT a ${if (leaked.isEmpty() && back.values.all { it }) "PASS" else "FAIL"}")
        assertEquals("B19: the second profile sees none of the first one's data", emptySet<String>(), leaked)
        assertTrue("B19: the first profile keeps its data $back", back.values.all { it })
    }

    @Test fun b_b9_clearHistoryLeavesNothing() {
        Profiles.resetForTests(ctx)
        val files = ctx.filesDir
        // A watch history that the app kept as a .tmp copy (a write that the app did not get to finish).
        val history = JSONObject().put("version", 1).put("titles", JSONArray().put(JSONObject()
            .put("api", "YuP").put("url", "https://yup.invalid/h").put("name", "YuP history title").put("version", "")
            .put("position", 60_000).put("duration", 1_200_000).put("at", System.currentTimeMillis())))
            .put("progress", JSONObject())
        File(files, "online_history.json").delete()
        File(files, "online_history.json.tmp").writeText(history.toString())
        File(files, "online_history.json.bad").writeText("{ broken")
        // Recents kept elsewhere.
        fun prefs(n: String) = ctx.getSharedPreferences(n, Context.MODE_PRIVATE)
        prefs("mt_streams").edit().putString("history", "[{\"url\":\"https://yup.invalid/s.m3u8\"}]").putString("live_history", "[\"yup\"]").commit()
        prefs("live_tv").edit().putString("country_recent", "BD,IN").commit()
        prefs("yu_sites_recent").edit().putString("visits", "[{\"url\":\"https://yup-visit.example/\",\"title\":\"x\",\"at\":1}]").commit()
        prefs("episode_seen").edit().putInt("YuP|https://yup.invalid/t|sub", 7).commit()
        prefs("online").edit().putString("online_search_history", "[\"yup alpha\"]").commit()
        prefs("search_recent").edit().putString("novel", "[\"yup novel\"]").commit()
        // A search kept for a quick reopen, in the cache folder.
        dev.asfakul.yumeiro.cache.DataCache.init(ctx)
        dev.asfakul.yumeiro.cache.DataCache.write("search", "yup|search|q", "[]")
        val cacheDir = File(ctx.cacheDir, "data_cache/search")
        log("GROUPP_B9 seeded cacheFiles=${cacheDir.listFiles()?.size}")

        dev.asfakul.yumeiro.backup.ResetCenter.applyNow(ctx, setOf("history"))
        dev.asfakul.yumeiro.cache.DataCache.awaitClearForTests()

        OnlineHistory.resetForTests(); OnlineHistory.ensureLoaded(ctx)
        val left = buildList {
            if (OnlineHistory.entries.value.isNotEmpty()) add("watch history came back (${OnlineHistory.entries.value.map { it.name }})")
            listOf("online_history.json", "online_history.json.tmp", "online_history.json.bad").forEach { if (File(files, it).exists()) add("file $it") }
            if (prefs("mt_streams").contains("history")) add("Play Link history")
            if (prefs("mt_streams").contains("live_history")) add("Live history")
            if (prefs("live_tv").contains("country_recent")) add("Live TV countries")
            if (prefs("yu_sites_recent").contains("visits")) add("visited websites")
            if (prefs("episode_seen").all.isNotEmpty()) add("seen marks")
            if (prefs("online").contains("online_search_history")) add("Yumix searches")
            if (prefs("search_recent").all.isNotEmpty()) add("recent searches")
            if (dev.asfakul.yumeiro.cache.DataCache.read("search", "yup|search|q", Long.MAX_VALUE) != null) add("saved search (memory)")
            if ((cacheDir.listFiles()?.size ?: 0) > 0) add("saved search files")
        }
        log("GROUPP_B9 left=$left")
        log("GROUPP_B9_RESULT b ${if (left.isEmpty()) "PASS" else "FAIL"}")
        OnlineHistory.resetForTests()
        assertEquals("B9: history left after Clear history", emptyList<String>(), left)
    }

    @Test fun c_b13_streamedSongShowsLengthAndSize() {
        val id = "yuPb13song"
        val info = StreamInfo(0, dev.asfakul.yumeiro.youtube.YouTube.watchUrl(id), dev.asfakul.yumeiro.youtube.YouTube.watchUrl(id), StreamType.AUDIO_STREAM, id, "YuP B13 song", 0)
        info.duration = 205 // 3:25
        val itag = ItagItem.getItag(140).apply { contentLength = 3_311_616L }
        info.audioStreams = listOf(
            AudioStream.Builder().setId("140").setContent("https://invalid.example/yumeiro-test/b13", true)
                .setMediaFormat(MediaFormat.M4A).setAverageBitrate(128).setItagItem(itag).build(),
        )
        dev.asfakul.yumeiro.youtube.YouTube.seedForTests(id, info)
        // As the YouTube Music player hands a song to Song info: no length, no size, a stream address.
        val track = AudioTrack(id = "yt:$id", title = "YuP B13 song", artist = "YuP", album = "YouTube Music", durationMs = 0L,
            uri = dev.asfakul.yumeiro.youtube.YouTubeAudioDataSource.uriFor(id))
        rule.setContent { MTPlayerTheme { dev.asfakul.yumeiro.ui.components.SongDetailsDialog(track) {} } }
        runCatching { rule.waitUntil(20_000) { allText().none { it.startsWith("Reading") } && allText().any { it == "Duration" } } }
        val shown = allText()
        log("GROUPP_B13 c texts=$shown")
        fun after(label: String) = shown.indexOf(label).takeIf { it >= 0 }?.let { shown.getOrNull(it + 1) }
        val duration = after("Duration"); val size = after("File size")
        log("GROUPP_B13 c duration=$duration size=$size")
        log("GROUPP_B13_RESULT c ${if (duration == "3:25" && size?.contains("MB") == true) "PASS" else "FAIL"}")
        assertEquals("B13: the length of a streamed song", "3:25", duration)
        assertTrue("B13: the size of a streamed song's audio, got $size", size?.contains("MB") == true && size.contains("streamed"))
    }

    @Test fun d_b7_novelBannersOnRealSources() {
        val wanted = listOf("royalroad", "scribblehub", "novelbin", "novelfull", "novelfire", "FWN.com", "readnovelfull", "webnovel", "libread", "mtlnovel", "wuxiaworld", "ranobes")
        val repo = "https://raw.githubusercontent.com/LNReader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json"
        val sources = NovelSourcesSetup.install(ctx, repo, wanted) { log(it) }
        assertTrue("B7: no novel source could be installed", sources.isNotEmpty())
        var current by mutableStateOf<String?>(null)
        rule.setContent {
            MTPlayerTheme {
                current?.let { id ->
                    androidx.compose.runtime.key(id) {
                        dev.asfakul.yumeiro.ui.screens.novel.NovelHomeScreen(onBack = {}, onOpenNovel = {}, onOpenSources = {})
                    }
                }
            }
        }
        val results = LinkedHashMap<String, String>()
        for (id in sources) {
            ctx.getSharedPreferences("novel_reader", Context.MODE_PRIVATE).edit().putString("source", id).commit()
            rule.runOnIdle { current = null }; rule.waitForIdle()
            rule.runOnIdle { current = id }
            val decided = runCatching { rule.waitUntil(60_000) {
                listOf("novel_banner", "novel_browse_error", "novel_browse_empty").any { t -> rule.onAllNodesWithTag(t, true).fetchSemanticsNodes().isNotEmpty() } ||
                    rule.onAllNodesWithTag("online_poster", true).fetchSemanticsNodes().isNotEmpty()
            }; true }.getOrDefault(false)
            val banner = rule.onAllNodesWithTag("novel_banner", true).fetchSemanticsNodes().isNotEmpty()
            val error = rule.onAllNodesWithTag("novel_browse_error", true).fetchSemanticsNodes().isNotEmpty()
            if (!banner) {
                results[id] = when { error -> "source-error"; !decided -> "timeout"; else -> "NO-BANNER" }
                log("GROUPP_B7 d $id ${results[id]} texts=${allText().take(12)}")
                continue
            }
            val inBanner = { tag: String -> rule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag("novel_banner")), true).fetchSemanticsNodes().size }
            runCatching { rule.waitUntil(30_000) { inBanner(dev.asfakul.yumeiro.ui.screens.online.PosterImageMode.LOADING_TAG) == 0 } }
            val loading = inBanner(dev.asfakul.yumeiro.ui.screens.online.PosterImageMode.LOADING_TAG)
            val fallback = inBanner(dev.asfakul.yumeiro.ui.screens.online.PosterImageMode.FALLBACK_TAG)
            results[id] = if (loading == 0 && fallback == 0) "OK" else "BROKEN-IMAGE(fallback=$fallback loading=$loading)"
            val headers = dev.asfakul.yumeiro.sources.novel.NovelSources.plugin(id)?.let { runCatching { NovelSourcesSetup.headersOf(id) }.getOrNull() }
            log("GROUPP_B7 d $id ${results[id]} headers=${headers?.keys}")
        }
        rule.runOnIdle { current = null }
        val ok = results.filterValues { it == "OK" }.keys
        val bad = results.filterValues { it == "NO-BANNER" || it.startsWith("BROKEN") }
        log("GROUPP_B7 d results=$results")
        log("GROUPP_B7_RESULT d ${if (bad.isEmpty() && ok.size >= 4) "PASS" else "FAIL"} ok=${ok.size} bad=$bad")
        assertTrue("B7: banners without covers or missing: $bad (all: $results)", bad.isEmpty())
        assertTrue("B7: too few sources answered to judge (${ok.size} OK): $results", ok.size >= 4)
    }
}

/** Installs real LNReader plugins for [GroupPDeviceTest] (network). */
internal object NovelSourcesSetup {
    fun install(ctx: Context, repo: String, wanted: List<String>, log: (String) -> Unit): List<String> = runBlocking {
        val ns = dev.asfakul.yumeiro.sources.novel.NovelSources
        ns.ensureLoaded(ctx)
        val added = ns.addRepo(ctx, repo)
        log("GROUPP_B7 repo=${added.isSuccess} ${added.exceptionOrNull()?.message.orEmpty()}")
        val available = runCatching { ns.available(ctx) }.getOrDefault(emptyList())
        wanted.mapNotNull { w -> available.firstOrNull { it.id.equals(w, true) } }.mapNotNull { p ->
            val r = if (ns.plugin(p.id) != null) Result.success(Unit) else ns.install(ctx, p)
            log("GROUPP_B7 install ${p.id} ${r.isSuccess} ${r.exceptionOrNull()?.message.orEmpty()}")
            p.id.takeIf { r.isSuccess }
        }
    }

    /** The cover headers the app uses for [id] (after the fix); reflection so this file builds before it too. */
    fun headersOf(id: String): Map<*, *>? = runCatching {
        val k = Class.forName("dev.asfakul.yumeiro.sources.novel.NovelImageHeaders")
        val o = k.getField("INSTANCE").get(null)
        k.getMethod("of", String::class.java).invoke(o, id) as Map<*, *>
    }.getOrNull()
}
