/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.telegram.TelegramPublic
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.Resolution
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.StreamResolver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B16 with REAL public Telegram posts (no fixtures): every kind of public video found on 2026-11-28 in real channels
 * (square H.264 Main, 1080p High, 360p, no audio track, an album item, a round video, a channel reached by its old
 * name) is opened as a link and must reach a first frame in Yumeiro's player. Posts whose video Telegram keeps off
 * the web preview ("Media is too big": 18 of 20 posts on @movietrailers, 17 of 20 on @videos) must not dead-end in
 * "only in its own app": they play with the account, or say how to sign in. Log tag YuGroupB16.
 */
@RunWith(AndroidJUnit4::class)
class GroupB16TelegramRealDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupB16", s)

    @Before fun setUp() { TelegramPublic.base = "https://t.me"; TelegramPublic.clearCache() }
    @After fun tearDown() { IncomingMedia.consume() }

    private data class Sample(val link: String, val kind: String)
    private val public = listOf(
        Sample("https://t.me/telegram/441", "square 1920x1920 H.264 Main"),
        Sample("https://t.me/durov/554", "no audio track"),
        Sample("https://t.me/Otaku_Insight/8441", "album item (?single)"),
        Sample("https://t.me/movietrailers/1821", "round video"),
        Sample("https://t.me/trailers/1859", "1080p, channel by its other name"),
        Sample("https://t.me/nytimes/3598", "360x240"),
        Sample("https://t.me/SpaceX/10493", "1080p High"),
        Sample("https://t.me/videos/4606", "720p, 2 min"),
        Sample("https://t.me/euronews/25432", "HE-AAC audio"),
    )
    private val withheld = listOf("https://t.me/movietrailers/1817", "https://t.me/videos/4601")

    private fun playOnce(req: StreamRequest): PlaybackDiagnostics.Snapshot {
        PlaybackDiagnostics.reset("")
        rule.runOnUiThread { IncomingMedia.open(ctx, req) }
        // First frame only: a stale CDN token gives one early 403 that the player's refresher recovers from.
        runCatching { rule.waitUntil(45_000) { PlaybackDiagnostics.state.value.firstFrame } }
        val s = PlaybackDiagnostics.state.value
        rule.runOnUiThread {
            IncomingMedia.consume()
            androidx.lifecycle.ViewModelProvider(rule.activity)[dev.asfakul.yumeiro.ui.viewmodel.MainViewModel::class.java].closeVideoPlayer()
        }
        rule.runOnUiThread { dev.asfakul.yumeiro.player.PlaybackSession.releaseNow() }
        rule.waitForIdle(); Thread.sleep(800)
        return s
    }

    @Test fun a_realPublicPostsPlay() {
        FirstRun.toTabs(rule)
        val played = ArrayList<String>(); val failed = ArrayList<String>(); val gone = ArrayList<String>()
        for (s in public) {
            val r = runBlocking { StreamResolver.resolve(ctx, StreamRequest(s.link)) }
            if (r is Resolution.Failed && r.message.startsWith("Post ") && r.message.contains("has no video")) { gone += s.link; log("GB16 gone ${s.link}"); continue }
            if (r !is Resolution.Playable) { failed += "${s.link} (${s.kind}): $r"; log("GB16 FAIL resolve ${s.link} $r"); continue }
            val snap = playOnce(r.request)
            log("GB16 ${s.link} [${s.kind}] firstFrame=${snap.firstFrame} error=${snap.error}")
            if (snap.firstFrame) played += s.link else failed += "${s.link} (${s.kind}): ${snap.error}"
            if (played.size == 1) FirstRun.shot("gb16_a_first")
        }
        log("GB16 played=${played.size} failed=$failed gone=$gone")
        assertTrue("real public posts that did not play: $failed", failed.isEmpty())
        assertTrue("too few samples still online (${played.size})", played.size >= 6)
        log("GB16_RESULT_A PASS played=${played.size}")
    }

    @Test fun b_withheldPostLinksAreNotADeadEnd() {
        FirstRun.toTabs(rule)
        val signedIn = dev.asfakul.yumeiro.online.telegram.td.TgAccounts.signedInHint(ctx)
        for (link in withheld) {
            val r = runBlocking { StreamResolver.resolve(ctx, StreamRequest(link)) }
            log("GB16 withheld $link signedIn=$signedIn -> $r")
            if (r is Resolution.Failed && r.message.contains("has no video")) continue // the post was deleted
            val unavailable = dev.asfakul.yumeiro.online.telegram.td.TgAccounts.unavailableReason(ctx)
            when {
                r is Resolution.Playable -> assertTrue("played without the account path: $r", signedIn)
                unavailable != null -> assertTrue("no reason given: $r", r is Resolution.Failed && r.message.contains(unavailable))
                else -> assertTrue(
                    "a withheld post still dead-ends in 'only in its own app' instead of asking to sign in: $r",
                    r is Resolution.Failed && r.title == StreamResolver.TELEGRAM_SIGN_IN_TITLE &&
                        r.message.contains(dev.asfakul.yumeiro.online.telegram.TelegramAccountPlay.Words.NEEDS_SIGN_IN),
                )
            }
        }
        log("GB16_RESULT_B PASS")
    }
}
