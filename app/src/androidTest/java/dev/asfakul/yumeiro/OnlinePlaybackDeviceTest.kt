/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.LinkEvent
import dev.asfakul.yumeiro.online.OnlinePlayback
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real-device checks for online playback (run on an emulator by the "Device check" workflow).
 * Screenshots are written to the app's external files dir under shots/ and pulled as artifacts.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class OnlinePlaybackDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun grant() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { ui.grantRuntimePermission(ctx.packageName, it) } }
        PlaybackDiagnostics.reset("")
    }

    private fun shot(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(ctx.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    /** Waits while letting Compose run (Thread.sleep would freeze the test clock). */
    private fun waitForPlayback(name: String, timeoutMs: Long = 75_000): PlaybackDiagnostics.Snapshot {
        val start = System.currentTimeMillis()
        var shotTaken = false
        runCatching {
            rule.waitUntil(timeoutMs) {
                if (!shotTaken && System.currentTimeMillis() - start > 8_000) { shotTaken = true; runCatching { shot("${name}_8s") } }
                val s = PlaybackDiagnostics.state.value
                s.firstFrame || (s.error != null && s.state == "IDLE")
            }
        }
        val settleUntil = System.currentTimeMillis() + 3_000
        runCatching { rule.waitUntil(5_000) { System.currentTimeMillis() > settleUntil } }
        shot("${name}_final")
        return PlaybackDiagnostics.state.value.also { Log.i("MTDeviceTest", "$name -> $it") }
    }

    private fun reachable(url: String): Boolean = runCatching {
        okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url(url).header("Range", "bytes=0-1023").header("User-Agent", com.lagradost.cloudstream3.USER_AGENT).build())
            .execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private fun installArchive() = runBlocking {
        ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
        if (ExtensionManager.provider("Internet Archive") == null) {
            val remote = ExtensionSources.all(ctx, force = true).items.first { it.internalName == "InternetArchiveProvider" }
            ExtensionManager.install(ctx, remote, dev.asfakul.yumeiro.online.ExtensionReview.Mode.AUTO) // unattended: no review dialog to tap
        }
        OnlineRepository.clearCaches()
        assertNotNull("extension loaded", ExtensionManager.provider("Internet Archive"))
    }

    @Test fun a_directMp4Plays() {
        rule.runOnUiThread {
            IncomingMedia.open(ctx, StreamRequest(url = "https://archive.org/download/f20110625mad/f20110625mad.mp4", title = "Direct MP4"), remember = false)
        }
        val s = waitForPlayback("direct_mp4")
        assertTrue("direct mp4: $s", s.firstFrame)
    }

    @Test fun b_extensionLinkPlays() {
        installArchive()
        val api = ExtensionManager.provider("Internet Archive")!!
        val (title, link, subs) = runBlocking {
            // Archive's featured list changes daily and may lead with restricted (403) TV recordings,
            // so search public-domain films as well and skip links the host refuses.
            // Known public-domain films come first; the live home list (often podcasts today) is a fallback.
            val urls = listOf("https://archive.org/details/his_girl_friday", "https://archive.org/details/f20110625mad") +
                runCatching { OnlineRepository.home(api, force = true).flatMap { it.items }.map { it.url } }.getOrDefault(emptyList()).take(15)
            for (url in urls) {
                val d = runCatching { OnlineRepository.load(api, url) }.getOrNull() ?: continue
                val data = d.playData ?: d.episodes.values.firstOrNull()?.firstOrNull()?.data ?: continue
                val events = OnlineRepository.links(api, data).toList()
                Log.i("MTDeviceTest", "links for ${d.name}: ${events.joinToString { it.toString().take(200) }}")
                val links = OnlineRepository.sortLinks(events.filterIsInstance<LinkEvent.Link>().map { it.link })
                val ok = links.firstOrNull { !it.audioOnly && reachable(it.url) }
                if (ok != null) { playableTitle = d.name; playableUrl = url; return@runBlocking Triple(d.name, ok, events.filterIsInstance<LinkEvent.Subtitle>().map { it.subtitle }) }
            }
            error("no links")
        }
        rule.runOnUiThread { OnlinePlayback.play(ctx, title, link, subs) }
        val s = waitForPlayback("extension_link")
        assertTrue("extension link: $s / $link", s.firstFrame)
    }

    @Test fun c_uiFlowPlays() {
        installArchive()
        // A fresh install opens on the welcome guide over the tabs (api33/api36 run 44401023896:
        // SetupGuide.WelcomePage on screen, the source menu never reachable). Get past it as a user does.
        FirstRun.toTabs(rule)
        // TV shows a side rail (tv_nav_tab_videos) instead of the tab bar (tv-api34 run 44403632079
        // waited 30 s for nav_tab_videos); FirstRun maps the tag and clicks as the remote's OK would.
        rule.waitUntil(30_000) { FirstRun.hasNav(rule, "nav_tab_videos") }
        FirstRun.clickTag(rule, FirstRun.navTag(rule, "nav_tab_videos"))
        // The source choice (Yumix · YouTube · Device · Live TV) sits in the menu under the page title.
        fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
        rule.waitUntil(20_000) { has("video_source_online") || has("video_source_switch") }
        if (!has("video_source_online")) FirstRun.clickTag(rule, "video_source_switch")
        rule.waitUntil(15_000) { has("video_source_online") }
        FirstRun.clickTag(rule, "video_source_online")
        // The home opens with the banner (hero) and shelves.
        rule.waitUntil(60_000) {
            rule.onAllNodesWithTag("online_hero_play", true).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("online_poster", true).fetchSemanticsNodes().isNotEmpty()
        }
        shot("ui_online_home")
        // Archive's featured and search lists change daily (podcasts, restricted TV), so open the
        // title the link test proved playable through the hook extensions use to open titles.
        val url = playableUrl ?: "https://archive.org/details/his_girl_friday"
        rule.runOnUiThread { com.lagradost.cloudstream3.utils.AppContextUtils.loadResult(url, "Internet Archive", playableTitle ?: "His Girl Friday") }
        val failed = hasTestTag("online_detail_error") or hasTestTag("online_resolve_error") or hasTestTag("online_detail_unsupported")
        // Movies start straight away; series open the episode list.
        rule.waitUntil(90_000) {
            PlaybackDiagnostics.state.value.uri.isNotEmpty() ||
                rule.onAllNodes(hasTestTag("online_detail_play") or failed, true).fetchSemanticsNodes().isNotEmpty()
        }
        shot("ui_after_open")
        if (rule.onAllNodesWithTag("online_detail_play", true).fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithTag("online_detail_play", true).performClick()
        // Phase 2: the time row shows 00:00, never "--:--", while the video opens. Sample the
        // player's texts until the first frame (or 20 s) and fail on any "--:--".
        val seen = LinkedHashSet<String>()
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 20_000 && !PlaybackDiagnostics.state.value.firstFrame) {
            runCatching {
                rule.onAllNodes(androidx.compose.ui.test.isRoot(), true).fetchSemanticsNodes().forEach { root ->
                    fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
                        n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }
                            ?.forEach { t -> if (':' in t.text && t.text.length <= 12) seen += t.text }
                        n.children.forEach { walk(it) }
                    }
                    walk(root)
                }
            }
            Thread.sleep(150)
        }
        Log.i("MTDeviceTest", "time labels while opening: $seen")
        assertTrue("time row showed --:-- while opening: $seen", seen.none { "--:--" in it })
        val s = waitForPlayback("ui_flow")
        assertTrue("ui flow: $s", s.firstFrame)
    }

    /**
     * Phase 2: rotation works, and a source the user picks by hand that turns out dead must rotate
     * again on its own. Play a real title, add a dead source to its list, pick it through the same
     * call the source panel uses, and require playback to come back on a working source.
     */
    @Test fun d_manualPickOfADeadSourceRotatesAgain() {
        installArchive()
        val api = ExtensionManager.provider("Internet Archive")!!
        val url = playableUrl ?: "https://archive.org/details/his_girl_friday"
        val detail = runBlocking { OnlineRepository.load(api, url) }
        rule.runOnUiThread { dev.asfakul.yumeiro.online.OnlineSession.play(ctx, detail) }
        val first = waitForPlayback("dead_pick_first", 120_000)
        assertTrue("title did not play first: $first", first.firstFrame)
        rule.waitUntil(60_000) { dev.asfakul.yumeiro.online.OnlineSession.state.value?.linksDone == true }
        val dead = "http://127.0.0.1:9/yumeiro-dead-source.mp4"
        val deadIndex = dev.asfakul.yumeiro.online.OnlineSession.addLinkForTest(
            dev.asfakul.yumeiro.online.OnlineLink("Dead source", "test", dead, null, 720, isHls = false, isDash = false, headers = emptyMap()),
        )
        assertTrue("dead link not added", deadIndex >= 0)
        val uris = LinkedHashSet<String>()
        PlaybackDiagnostics.reset("")
        rule.runOnUiThread { dev.asfakul.yumeiro.online.OnlineSession.switchSource(ctx, deadIndex, 5_000) }
        // The pick must really be tried (a manual pick wins), then rotation must move off it.
        rule.waitUntil(30_000) { PlaybackDiagnostics.state.value.uri.also { if (it.isNotEmpty()) uris += it }.contains("yumeiro-dead-source") }
        val ok = runCatching {
            rule.waitUntil(120_000) {
                val d = PlaybackDiagnostics.state.value
                if (d.uri.isNotEmpty()) uris += d.uri
                val st = dev.asfakul.yumeiro.online.OnlineSession.state.value
                d.firstFrame && !d.uri.contains("yumeiro-dead-source") && st?.linkIndex != deadIndex
            }
        }.isSuccess
        val st = dev.asfakul.yumeiro.online.OnlineSession.state.value
        Log.i("MTDeviceTest", "dead pick: rotated=$ok linkIndex=${st?.linkIndex} dead=$deadIndex failed=${st?.failed?.map { it.takeLast(40) }} uris=${uris.map { it.take(90) }}")
        shot("dead_pick_final")
        assertTrue("a dead manual pick did not rotate to a working source: uris=$uris state=${st?.phase}/${st?.message}", ok)
        assertTrue("dead source not marked failed", st?.failed?.contains(dead) == true)
    }

    companion object {
        /** A title the link test found playable, reused by the UI flow. */
        @Volatile var playableTitle: String? = null
        @Volatile var playableUrl: String? = null
    }
}
