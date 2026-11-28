/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.tracking.Trakt
import dev.asfakul.yumeiro.ui.components.AppMessageHost
import dev.asfakul.yumeiro.ui.screens.settings.SetupGuides
import dev.asfakul.yumeiro.ui.screens.settings.TrackingScreen
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B18 (Trakt page and sign-in) and B19 (SubDL on the Tracking page), against the REAL services:
 * a) the Tracking page has no inline "how to" cards; every account has a "?" that opens its steps;
 * b) every setup link the app shows answers (trakt.tv/auth/join answered 404 on 2026-11-28);
 * c) real Trakt device sign-in with the built-in Client ID and no client secret: a code is issued and the first
 *    poll is "pending" (400), not "invalid client" (401). Approving the code needs a person (MANUAL M-TRAKT-SIGNIN);
 * d) SubDL card: a made-up key is checked with the real SubDL and refused inside the form, and is not saved.
 * Written so it compiles on the code before the fix (fail-before branch ab/b18-before). Log tag YuGroupB18.
 */
@RunWith(AndroidJUnit4::class)
class GroupB18B19DeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupB18", s)
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun text(tag: String): String = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().joinToString(" ") { n ->
        n.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text }
    }
    private fun wait(what: String, ms: Long = 20_000, cond: () -> Boolean) {
        val ok = runCatching { compose.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("gb18_fail_" + what.filter { it.isLetterOrDigit() }.take(30))
        assertTrue("timed out: $what", ok)
    }
    private fun tap(tag: String) {
        runCatching { compose.onNodeWithTag(tag, true).performScrollTo() }
        compose.waitForIdle()
        compose.onNodeWithTag(tag, true).performClick()
        compose.waitForIdle()
    }

    @Before fun setUp() {
        OnlineSettings.init(ctx)
        OnlineSettings.setSubdlKey(ctx, "")
        OnlineSettings.setSecret(ctx, "subdl_user", null)
    }

    private fun showTracking() {
        compose.setContent { Box(Modifier.fillMaxSize()) { TrackingScreen(onBack = {}); AppMessageHost() } }
        wait("tracking screen") { has("tracking_screen") }
    }

    @Test fun a_trackingPageUsesQuestionMarksNotInlineGuides() {
        showTracking()
        val trackers = listOf("anilist", "mal", "kitsu", "trakt", "simkl", "subdl", "lastfm")
        val missingCard = trackers.filterNot { t -> runCatching { compose.onNodeWithTag("tracker_$t", true).performScrollTo() }; has("tracker_$t") }
        log("GB18 cards missing=$missingCard")
        val inline = listOf("anilist", "myanimelist", "kitsu", "trakt", "simkl", "lastfm", "subdl").filter { has("guide_$it") }
        val stepHint = compose.onAllNodesWithText("Step by step", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size
        log("GB18 inline guide cards=$inline stepByStepHints=$stepHint")
        FirstRun.shot("gb18_tracking_page")
        val noHelp = trackers.filter { t -> runCatching { compose.onNodeWithTag("tracker_${t}_help", true).performScrollTo() }; !has("tracker_${t}_help") }
        log("GB18 trackers without ? = $noHelp")
        assertTrue("every account has a card (SubDL included): missing $missingCard", missingCard.isEmpty())
        assertTrue("no inline how-to cards on the page: $inline", inline.isEmpty() && stepHint == 0)
        assertTrue("every account has a ? help: missing $noHelp", noHelp.isEmpty())

        tap("tracker_trakt_help")
        wait("Trakt help dialog") { has("tracker_trakt_help_dialog") && has("guide_trakt_body") }
        FirstRun.shot("gb18_trakt_help")
        log("GB18 trakt help shown")
        assertTrue("the Trakt steps are in the dialog", compose.onAllNodesWithText("trakt.tv/activate", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        tap("tracker_trakt_help_ok")
        wait("help closed") { !has("tracker_trakt_help_dialog") }
        log("GB18_RESULT_A PASS")
    }

    @Test fun b_everySetupLinkAnswers() {
        val client = NetworkClient.get(ctx)
        val links = (SetupGuides.all + SetupGuides.simkl + SetupGuides.lastfm).flatMap { g -> g.steps.mapNotNull { s -> s.link?.let { g.id to it } } }.distinct()
        val broken = ArrayList<String>()
        for ((id, url) in links) {
            val code = runCatching {
                client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Yumeiro-linkcheck").build()).execute().use { it.code }
            }.getOrElse { -1 }
            log("GB18 link $id $url -> $code")
            // -1: this emulator couldn't reach the host at all; that's the network, not a wrong link.
            if (code >= 400) broken += "$id $url ($code)"
        }
        assertTrue("setup links that don't open: $broken", broken.isEmpty())
        log("GB18_RESULT_B PASS links=${links.size}")
    }

    @Test fun c_realTraktSignInNeedsNoClientSecret() {
        if (BuildConfig.TRAKT_CLIENT_ID.isBlank()) { log("GB18 UNVERIFIED-TRAKT no built-in client id in this build"); return }
        val client = NetworkClient.get(ctx)
        val code = Trakt.deviceCode(client)
        assertTrue("a user code was issued", code.userCode.length in 4..12)
        assertTrue(code.verificationUrl, code.verificationUrl.contains("trakt.tv"))
        // One poll exactly as the app sends it: code + client_id, no client_secret.
        val body = JSONObject().put("code", code.deviceCode).put("client_id", BuildConfig.TRAKT_CLIENT_ID).toString().toRequestBody("application/json".toMediaType())
        val status = client.newCall(Request.Builder().url("https://auth.trakt.tv/oauth/device/token").post(body)
            .header("trakt-api-version", "2").header("trakt-api-key", BuildConfig.TRAKT_CLIENT_ID).build()).execute().use { it.code }
        log("GB18 trakt device code issued, first poll HTTP $status (400 = waiting for approval)")
        assertEquals("first poll is 'pending', not a client error", 400, status)
        log("GB18_RESULT_C PASS")
    }

    @Test fun d_subdlCardChecksTheKeyWithSubdl() {
        showTracking()
        wait("SubDL card") { runCatching { compose.onNodeWithTag("tracker_subdl", true).performScrollTo() }; has("tracker_subdl_signin") }
        tap("tracker_subdl_signin")
        wait("SubDL key form") { has("subdl_key_field") }
        compose.onNodeWithTag("subdl_key_field", true).performTextInput("yumeiro-b19-not-a-real-key-000")
        runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
        tap("subdl_key_save")
        wait("SubDL's answer in the form", 45_000) { has("subdl_key_error") }
        val err = text("subdl_key_error")
        FirstRun.shot("gb18_subdl_refused")
        log("GB18 subdl error='$err' saved=${OnlineSettings.subdlKey.value.isNotBlank()}")
        assertTrue("SubDL refused the made-up key: '$err'", err.contains("didn't accept", true))
        assertTrue("a refused key is not saved", OnlineSettings.subdlKey.value.isBlank())
        log("GB18_RESULT_D PASS")
    }
}
