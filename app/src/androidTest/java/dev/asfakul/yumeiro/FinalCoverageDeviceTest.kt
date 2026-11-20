/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.cache.DataCache
import dev.asfakul.yumeiro.cache.OnlineCodec
import dev.asfakul.yumeiro.notify.AppFeed
import dev.asfakul.yumeiro.notify.AppNotifier
import dev.asfakul.yumeiro.notify.Inbox
import dev.asfakul.yumeiro.player.equalizer.EqualizerManager
import dev.asfakul.yumeiro.security.IntegrityGuard
import dev.asfakul.yumeiro.stream.AdFilterDataSource
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import dev.asfakul.yumeiro.ui.screens.extras.SetupGuide
import dev.asfakul.yumeiro.watch.WatchTogether
import dev.asfakul.yumeiro.youtube.YtCookiePaste
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Final test (docs/MASTER-CHECKLIST-FINAL.md): rows that had no device class until now, each run on
 * the real Android runtime of every target (api33-lowram, api36, tv-api34, tv-api24).
 *
 *  a  H9   the welcome guide opens, "Explore" starts the tour, Back leaves it and the tabs come back
 *  b  M6   Equalizer page (nav-bar shortcut): master switch and a preset reach the real audio effect
 *  c  M9   Music mix page opens from its shortcut and shows its list
 *  d  N1   Watch together: hosting opens a room server on the device; the right code reads the state,
 *          a wrong code is refused (403); closing the room stops the server
 *  e  T5   HLS ad filter in the player's data-source chain: a VOD playlist served over HTTP comes out
 *          without its foreign ad break, and the ad segments are never requested
 *  f  X12  data cache on this device's disk: a value survives a memory clear, invalidation hides it,
 *          and the online codec round-trips an item through it
 *  g  N9/X13 edition and integrity: the signer digest read through OsCompat (the API 28+ or the
 *          pre-28 path, per device) matches the legacy signatures read independently; a debug build
 *          reports DEVELOPMENT, never UNOFFICIAL
 *  h  I9   app feed: a parsed feed item is posted to the inbox (inbox only, no notification) and
 *          can be removed again
 *  i  I10  YouTube account: a pasted cookies.txt is read on the device and gives a Cookie header
 *  j  F-14 Live TV failover: a channel with two links whose first link is dead plays from the second
 *          link on its own (served from a local server, so no internet is involved)
 *  k  F-15 Now/Next guide: XMLTV served over HTTP gives what is on now and next for the channel's
 *          tvg-id; on a metered network nothing is downloaded (the rule in ADR-040)
 */
@RunWith(AndroidJUnit4::class)
class FinalCoverageDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun log(s: String) = Log.i("YuFinalCoverage", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(FirstRun.navTag(rule, tag), true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun waitTag(tag: String, ms: Long = 30_000) {
        val ok = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
        assertTrue("no '$tag' after ${ms}ms · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun waitGone(tag: String, ms: Long = 10_000) {
        val ok = runCatching { rule.waitUntil(ms) { !has(tag) }; true }.getOrDefault(false)
        assertTrue("'$tag' still shown after ${ms}ms · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun tap(tag: String) = FirstRun.tapVisible(rule, tag)
    private fun back() { FirstRun.dismissForeignDialog(rule); FirstRun.shell("input keyevent 4") }

    /** Opens [shortcut] from the nav bar (or the TV rail), runs [block], then removes the shortcut. */
    private fun viaShortcut(shortcut: NavPrefs.Shortcut, root: String, block: () -> Unit) {
        try {
            inst.runOnMainSync { NavPrefs.setShortcut(ctx, shortcut, true) }
            waitTag("nav_shortcut_${shortcut.id}", 15_000)
            tap("nav_shortcut_${shortcut.id}")
            waitTag(root)
            block()
            back(); waitGone(root)
        } finally {
            inst.runOnMainSync { NavPrefs.setShortcut(ctx, shortcut, false) }
        }
    }

    @Before fun setUp() {
        runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        FirstRun.toTabs(rule)
    }

    @Test fun a_h9_welcomeGuideExploreTourAndBack() {
        // FINAL-36 (run 44665104133, tv-api24): the class has no fixed method order, so an earlier method's
        // start had already skipped the welcome (SKIPPED), and the test expected EXPLORED right after tapping
        // Explore. The app records EXPLORED only when the tour reaches its last stop; leaving it early is
        // SKIPPED (ExploreTour.finish, its KDoc). Start from a first-run state and check that design.
        inst.runOnMainSync { SetupGuide.setOnboarding(ctx, SetupGuide.Onboarding.NONE) }
        inst.runOnMainSync { SetupGuide.show() }
        waitTag("setup_guide")
        waitTag("setup_explore")
        assertTrue("welcome guide has no Skip", has("setup_skip"))
        tap("setup_explore")
        waitGone("setup_guide", 15_000)
        // "Explore" hands over to the tour; Back leaves it as a user would.
        val tour = runCatching { rule.waitUntil(10_000) { has("explore_tour") }; true }.getOrDefault(false)
        log("explore tour shown=$tour onboarding=${SetupGuide.onboarding.value}")
        assertTrue("Explore did not open the tour", tour)
        assertEquals("no choice is recorded while the tour runs", SetupGuide.Onboarding.NONE, SetupGuide.onboarding.value)
        var backs = 0
        while (has("explore_tour") && backs < 6) { back(); SystemClock.sleep(800); backs++ }
        waitGone("explore_tour", 10_000)
        // Left before the last stop: skipped, remembered, and the welcome does not come back on the next start.
        assertEquals(SetupGuide.Onboarding.SKIPPED, SetupGuide.onboarding.value)
        assertFalse("the guide would open again on the next start", SetupGuide.shouldAutoShow(ctx))
        assertTrue("tabs did not come back · ${FirstRun.onScreen(rule)}", FirstRun.hasNav(rule, "nav_tab_home"))
    }

    @Test fun b_m6_equalizerReachesTheRealAudioEffect() {
        val eq = EqualizerManager.getInstance(ctx)
        val session = (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
        assertTrue("no audio session id", session > 0)
        eq.attachAudioSession(session)
        // The effect objects are built on the "yu-audio-fx" thread; wait until the bands come from the device.
        val bandsReady = runCatching { rule.waitUntil(15_000) { eq.equalizerState.value.bands.isNotEmpty() && fxEqualizer(eq) != null }; true }.getOrDefault(false)
        val st = eq.equalizerState.value
        log("equalizer: supported=${st.isSupportedOnDevice} bands=${st.bands.size} ready=$bandsReady")
        assertTrue("audio effects are not available on this device image", st.isSupportedOnDevice && bandsReady)
        viaShortcut(NavPrefs.Shortcut.EQUALIZER, "equalizer_screen_root") {
            waitTag("equalizer_master_switch")
            if (!eq.equalizerState.value.isEnabled) tap("equalizer_master_switch")
            rule.waitUntil(5_000) { eq.equalizerState.value.isEnabled }
            FirstRun.scrollTo(rule, "preset_chip_Rock")
            tap("preset_chip_Rock")
            rule.waitUntil(5_000) { eq.equalizerState.value.currentPreset == "Rock" }
            // The preset reached the platform Equalizer bound to the session, not just the UI state.
            val ok = runCatching {
                rule.waitUntil(10_000) {
                    val fx = fxEqualizer(eq) ?: return@waitUntil false
                    fx.enabled && (0 until fx.numberOfBands).any { fx.getBandLevel(it.toShort()).toInt() != 0 }
                }; true
            }.getOrDefault(false)
            val fx = fxEqualizer(eq)
            log("platform equalizer enabled=${fx?.enabled} levels=${fx?.let { f -> (0 until f.numberOfBands).map { f.getBandLevel(it.toShort()) } }}")
            assertTrue("Rock preset did not reach the platform equalizer", ok)
            tap("equalizer_reset_button")
            rule.waitUntil(5_000) { eq.equalizerState.value.bands.all { it.levelMb.toInt() == 0 } }
        }
        inst.runOnMainSync { eq.setEnabled(false) }
        eq.releaseAudioEffects()
    }

    /** The manager's own platform Equalizer (private field), read on its effect thread's object. */
    private fun fxEqualizer(eq: EqualizerManager): android.media.audiofx.Equalizer? = runCatching {
        val f = EqualizerManager::class.java.getDeclaredField("equalizer"); f.isAccessible = true
        f.get(eq) as android.media.audiofx.Equalizer?
    }.getOrNull()

    @Test fun c_m9_musicMixOpensFromItsShortcut() {
        viaShortcut(NavPrefs.Shortcut.MIX, "music_mix") {
            log("music mix: on screen")
            assertTrue("music mix list missing · ${FirstRun.onScreen(rule)}", has("music_mix"))
        }
    }

    @Test fun d_n1_watchTogetherHostsARoomOnTheDevice() {
        try {
            viaShortcut(NavPrefs.Shortcut.WATCH_TOGETHER, "watch_together") {
                waitTag("wt_host")
                tap("wt_host")
                rule.waitUntil(10_000) { WatchTogether.status.value.role == WatchTogether.Role.HOST }
                val s = WatchTogether.status.value
                val port = s.address.substringAfterLast(':').toIntOrNull()
                log("room '${s.roomName}' code=${s.code.length} digits address=${s.address}")
                assertEquals(4, s.code.length)
                assertNotNull("no port in '${s.address}'", port)
                val right = get("http://127.0.0.1:$port/state", s.code)
                assertEquals(200, right.first)
                assertTrue("state is not JSON: ${right.second.take(80)}", org.json.JSONObject(right.second).has("seq"))
                assertEquals(403, get("http://127.0.0.1:$port/state", if (s.code == "1000") "1001" else "1000").first)
                inst.runOnMainSync { WatchTogether.leave(ctx) }
                rule.waitUntil(5_000) { WatchTogether.status.value.role == WatchTogether.Role.NONE }
                assertTrue("room server still answers after Close room", runCatching { get("http://127.0.0.1:$port/state", s.code) }.isFailure)
            }
        } finally { inst.runOnMainSync { WatchTogether.leave(ctx) } }
    }

    private fun get(url: String, code: String): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 3_000; c.readTimeout = 3_000
        c.setRequestProperty("x-room-code", code); c.setRequestProperty("x-guest", "final-test")
        return try {
            val status = c.responseCode
            status to ((if (status < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty())
        } finally { c.disconnect() }
    }

    @OptIn(UnstableApi::class)
    @Test fun e_t5_hlsAdBreakIsCutBeforeThePlayerSeesIt() {
        val requests = CopyOnWriteArrayList<String>()
        val server = MockWebServer()
        fun seg(uri: String, d: Double = 10.0) = "#EXTINF:$d,\n$uri\n"
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                requests += request.url.encodedPath
                return if (request.url.encodedPath == "/hls/ep1/index.m3u8") {
                    val first = (1..5).joinToString("") { seg("a$it.ts") }
                    val ad = "#EXT-X-DISCONTINUITY\n" + seg("https://tracker.adnet.example/x/1.ts", 5.0) + seg("https://tracker.adnet.example/x/2.ts", 5.0) + "#EXT-X-DISCONTINUITY\n"
                    val main = (1..20).joinToString("") { seg("seg$it.ts") }
                    MockResponse.Builder().addHeader("Content-Type", "application/vnd.apple.mpegurl")
                        .body("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n#EXT-X-PLAYLIST-TYPE:VOD\n$first$ad$main#EXT-X-ENDLIST\n").build()
                } else MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        try {
            val ds = AdFilterDataSource.Factory(DefaultHttpDataSource.Factory()).createDataSource()
            val text = try {
                ds.open(DataSpec(Uri.parse(server.url("/hls/ep1/index.m3u8").toString())))
                val out = ByteArrayOutputStream(); val buf = ByteArray(4096)
                while (true) { val n = ds.read(buf, 0, buf.size); if (n == C.RESULT_END_OF_INPUT) break; out.write(buf, 0, n) }
                out.toString("UTF-8")
            } finally { ds.close() }
            log("playlist after filter: ${text.lines().count { it.endsWith(".ts") }} segments; requests=$requests")
            assertFalse("ad break still in the playlist", text.contains("adnet"))
            assertEquals("content segments lost", 25, text.lines().count { it.endsWith(".ts") })
            assertTrue(text.trimEnd().endsWith("#EXT-X-ENDLIST"))
            assertEquals(listOf("/hls/ep1/index.m3u8"), requests.toList())
        } finally { server.close() }
    }

    @Test fun f_x12_dataCacheOnDiskSurvivesAMemoryClear() {
        DataCache.init(ctx)
        val ns = "final_cov"; val key = "k-${System.nanoTime()}"
        val item = dev.asfakul.yumeiro.online.OnlineItem("Final coverage", "https://example.com/t/1", "TestApi", "https://example.com/p.jpg", emptyMap())
        val value = OnlineCodec.items(listOf(item))
        try {
            DataCache.write(ns, key, value)
            DataCache.clearMemory()
            val file = DataCache.fileFor(ns, key)
            assertTrue("no cache file on disk for $key", file?.isFile == true)
            val back = DataCache.read(ns, key, maxAgeMs = 60_000)
            assertEquals("disk read differs", value, back)
            val decoded = OnlineCodec.items(back!!)
            assertEquals(listOf(item.name to item.url), decoded?.map { it.name to it.url })
            DataCache.invalidateAll()
            DataCache.awaitClearForTests()
            assertNull("invalidated entry still read", DataCache.read(ns, key, maxAgeMs = 60_000))
        } finally { DataCache.remove(ns, key) }
    }

    @Suppress("DEPRECATION")
    @Test fun g_n9_x13_signerDigestAndIntegrityStatus() {
        val pm = ctx.packageManager
        val viaCompat = OsCompat.signerDigests(pm.getPackageInfo(ctx.packageName, OsCompat.SIGNING_FLAGS))
        // Independent read: the legacy signatures list, available on every API level.
        val legacy = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty().filterNotNull().map { s ->
            java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }
        log("API ${Build.VERSION.SDK_INT}: compat=${viaCompat.size} legacy=${legacy.size} same=${viaCompat.toSet() == legacy.toSet()}")
        assertTrue("no signer digest on API ${Build.VERSION.SDK_INT}", viaCompat.isNotEmpty())
        assertTrue("digest is not 64 hex chars", viaCompat.all { it.matches(Regex("[0-9a-f]{64}")) })
        assertEquals("OsCompat and the legacy signatures disagree", legacy.toSet(), viaCompat.toSet())
        val status = IntegrityGuard.status(ctx)
        log("integrity=$status edition=${Edition.name} tv=${Edition.isTv}")
        if (BuildConfig.DEBUG) assertEquals(IntegrityGuard.Status.DEVELOPMENT, status)
        assertFalse("this build reports itself as unofficial", IntegrityGuard.isUnofficial(ctx))
        assertEquals(if (BuildConfig.IS_TV_APP) "Yumeiro TV" else "Yumeiro", Edition.label)
    }

    @Test fun h_i9_appFeedItemReachesTheInboxQuietly() {
        val id = "final-cov-${System.currentTimeMillis()}"
        val json = """{"items":[{"id":"$id","kind":"news","title":"Final coverage","body":"Feed item on the device","at":${System.currentTimeMillis()},"link":"market"}]}"""
        val items = AppFeed.parse(json)
        assertEquals(listOf(id), items.map { it.id })
        val it0 = items[0]
        Inbox.ensureLoaded(ctx)
        try {
            AppNotifier.post(ctx, Inbox.Item("feed-${it0.id}", it0.kind, it0.title, it0.body, it0.at, link = it0.link), inboxOnly = true)
            rule.waitUntil(10_000) { Inbox.items.value.any { it.id == "feed-$id" } }
            val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
            assertTrue("an inbox-only item raised a notification", nm.activeNotifications.none { it.id == "feed-$id".hashCode() })
        } finally {
            Inbox.remove(ctx, "feed-$id")
            rule.waitUntil(10_000) { Inbox.items.value.none { it.id == "feed-$id" } }
        }
    }

    @Test fun i_i10_pastedCookiesTxtGivesACookieHeader() {
        val far = System.currentTimeMillis() / 1000 + 86_400 * 30
        val txt = "# Netscape HTTP Cookie File\n" +
            ".youtube.com\tTRUE\t/\tTRUE\t$far\tSAPISID\tabc123\n" +
            ".youtube.com\tTRUE\t/\tFALSE\t$far\tSID\tsid789\n" +
            ".youtube.com\tTRUE\t/\tTRUE\t$far\t__Secure-3PAPISID\tdef456\n" +
            ".example.com\tTRUE\t/\tFALSE\t$far\tOTHER\tx\n"
        val r = YtCookiePaste.parse(txt)
        log("cookie paste: $r".replace(Regex("=[^;]+"), "=…"))
        assertTrue("cookies.txt was not read: $r", r is YtCookiePaste.Result.Ok)
        r as YtCookiePaste.Result.Ok
        assertEquals(YtCookiePaste.Format.NETSCAPE, r.format)
        assertTrue(r.cookie.contains("SAPISID=abc123"))
        assertFalse("a non-YouTube cookie was kept", r.cookie.contains("OTHER="))
        assertTrue(YtCookiePaste.parse("not a cookie") is YtCookiePaste.Result.Error)
    }

    @Test fun j_f14_deadLinkFailsOverToTheChannelsOtherLink() {
        val run = (System.currentTimeMillis() % 100_000).toString()
        val clip = inst.context.assets.open("yumeiro_test_clip.mp4").use { it.readBytes() }
        val requests = CopyOnWriteArrayList<String>()
        val server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                requests += request.url.encodedPath
                return if (request.url.encodedPath == "/live-$run.mp4")
                    MockResponse.Builder().addHeader("Content-Type", "video/mp4").body(okio.Buffer().write(clip)).build()
                else MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        val name = "YuFailover $run"
        val dead = dev.asfakul.yumeiro.stream.StreamRequest(server.url("/dead-$run.mp4").toString(), name, group = "YuFinal")
        val live = dev.asfakul.yumeiro.stream.StreamRequest(server.url("/live-$run.mp4").toString(), name, group = "YuFinal")
        try {
            assertTrue("F-14 flag is off", FeatureFlags.on(ctx, FeatureFlags.Flag.LIVE_FAILOVER))
            inst.runOnMainSync {
                dev.asfakul.yumeiro.ui.screens.watch.StreamWatch.resetAutoSwitch()
                dev.asfakul.yumeiro.stream.StreamRepository.setPlaylist("YuFinal $run", listOf(dead, live))
                dev.asfakul.yumeiro.ui.screens.watch.StreamWatch.play(ctx, dead)
            }
            val ok = runCatching {
                rule.waitUntil(120_000) { PlaybackDiagnostics.state.value.let { it.firstFrame && it.uri.contains("live-$run") } }; true
            }.getOrDefault(false)
            val st = PlaybackDiagnostics.state.value
            log("F14 ok=$ok state=${st.state} uri=${st.uri.substringAfterLast('/')} firstFrame=${st.firstFrame} requests=$requests")
            if (!ok) FirstRun.shot("f14_failover")
            assertTrue("the dead link was not tried first: $requests", requests.firstOrNull() == "/dead-$run.mp4")
            assertTrue("did not fail over to the channel's second link ($st)", ok)
        } finally {
            inst.runOnMainSync { dev.asfakul.yumeiro.stream.StreamRepository.setPlaylist(null, emptyList()) }
            runCatching { back() }
            server.close()
        }
    }

    @Test fun k_f15_nowAndNextFromTheXmltvGuide() {
        val now = System.currentTimeMillis()
        val fmt = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        fun t(ms: Long) = fmt.format(java.util.Date(ms)) + " +0000"
        val xml = """<?xml version="1.0" encoding="UTF-8"?><tv>
            |<channel id="yu.news"><display-name>Yu News</display-name></channel>
            |<programme channel="yu.news" start="${t(now - 30 * 60_000)}" stop="${t(now + 30 * 60_000)}"><title>Yu Morning</title></programme>
            |<programme channel="yu.news" start="${t(now + 30 * 60_000)}" stop="${t(now + 90 * 60_000)}"><title>Yu Noon</title></programme>
            |</tv>""".trimMargin()
        val hits = AtomicCounter()
        val server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                hits.inc(); return MockResponse.Builder().addHeader("Content-Type", "application/xml").body(xml).build()
            }
        }
        server.start()
        try {
            assertTrue("F-15 flag is off", FeatureFlags.on(ctx, FeatureFlags.Flag.LIVE_GUIDE))
            val url = server.url("/guide-${System.nanoTime()}.xml").toString()
            val metered = ctx.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered == true
            val guide = kotlinx.coroutines.runBlocking { dev.asfakul.yumeiro.stream.EpgGuide.guide(ctx, url) }
            log("F15 metered=$metered guide=${guide != null} requests=${hits.n}")
            if (metered) {
                assertNull("a guide was downloaded on a metered network", guide)
                assertEquals("the guide was requested on a metered network", 0, hits.n)
            } else {
                assertNotNull("no guide from $url", guide)
                val ch = dev.asfakul.yumeiro.stream.StreamRequest("http://127.0.0.1/yu-news.m3u8", "Yu News", tvgId = "yu.news")
                val (on, next) = guide!!.nowNext(ch, now)
                assertEquals("Yu Morning", on?.title)
                assertEquals("Yu Noon", next?.title)
                // By name when the playlist has no tvg-id.
                assertEquals("Yu Morning", guide.nowNext(ch.copy(tvgId = null), now).first?.title)
                // A second ask within the cache time comes from the cache, not the network.
                kotlinx.coroutines.runBlocking { dev.asfakul.yumeiro.stream.EpgGuide.guide(ctx, url) }
                assertEquals("the guide was downloaded twice", 1, hits.n)
            }
        } finally { server.close() }
    }

    private class AtomicCounter { @Volatile var n = 0; @Synchronized fun inc() { n++ } }

    @After fun tearDown() { runCatching { inst.runOnMainSync { WatchTogether.leave(ctx) } } }
}
