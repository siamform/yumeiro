/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groups

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.g11.FakeHttp
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.online.subtitles.SubDL
import dev.asfakul.yumeiro.tracking.Trakt
import dev.asfakul.yumeiro.ui.screens.settings.SetupGuides
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * B18 (Trakt) and B19 (SubDL on the Tracking page). The Trakt rules come from developer.trakt.tv, read on
 * 2026-11-28: OAuth routes on auth.trakt.tv, a refresh's redirect must match the app's own, history answers
 * report `added` / `not_found`. The SubDL rules come from subdl.com/api-doc (api_key on /api/v1/me and
 * /api/v1/subtitles; a bad key is HTTP 403 `{"status":false,"error":"Not Authorized"}`, the real answer seen
 * on 2026-11-28).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupB18B19TraktSubdlTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() { OnlineSettings.resetForTests(); OnlineSettings.init(ctx) }

    private fun signedIn(expiresInMs: Long = 3L * 24 * 3600 * 1000) {
        OnlineSettings.setSecret(ctx, "trakt_token", "b18-access-token-0123")
        OnlineSettings.setSecret(ctx, "trakt_refresh", "b18-refresh-token-0123")
        OnlineSettings.setSecret(ctx, "trakt_expires", (System.currentTimeMillis() + expiresInMs).toString())
        OnlineSettings.setSecret(ctx, "trakt_user", "b18")
    }

    // region B18 Trakt

    @Test fun signInGoesToTheAuthHostAndNamesTheApp() {
        val http = FakeHttp { """{"device_code":"d","user_code":"ABCD1234","verification_url":"https://trakt.tv/activate","expires_in":600,"interval":5}""" }
        val code = Trakt.deviceCode(http.client)
        assertEquals("ABCD1234", code.userCode)
        val s = http.seen.single()
        assertEquals("https://auth.trakt.tv/oauth/device/code", s.url)
        assertFalse("no client secret on a device code", JSONObject(s.body).has("client_secret"))
    }

    @Test fun historyUsesTheTmdbIdWithoutATitleSearch() {
        signedIn()
        val http = FakeHttp { s -> if (s.url.endsWith("/sync/history")) 201 to """{"added":{"movies":0,"episodes":1},"not_found":{"shows":[]}}""" else 404 }
        val r = Trakt.watched(ctx, http.client, "Solo Leveling", 2024, 2, 5, tmdb = 127532, imdb = "tt21209876")
        assertEquals(Trakt.Sent.ADDED, r)
        assertTrue("no title search when ids are known: ${http.seen.map { it.url }}", http.seen.none { "/search/" in it.url })
        val body = JSONObject(http.seen.single { it.url.endsWith("/sync/history") }.body)
        val show = body.getJSONArray("shows").getJSONObject(0)
        assertEquals(127532, show.getJSONObject("ids").getInt("tmdb"))
        assertEquals("tt21209876", show.getJSONObject("ids").getString("imdb"))
        val season = show.getJSONArray("seasons").getJSONObject(0)
        assertEquals(2, season.getInt("number"))
        assertEquals(5, season.getJSONArray("episodes").getJSONObject(0).getInt("number"))
    }

    @Test fun aMovieWithOnlyAnImdbIdIsSentByIt() {
        signedIn()
        val http = FakeHttp { s -> if (s.url.endsWith("/sync/history")) 201 to """{"added":{"movies":1,"episodes":0}}""" else 404 }
        assertEquals(Trakt.Sent.ADDED, Trakt.watched(ctx, http.client, "Dune: Part Two", 2024, null, null, imdb = "tt15239678"))
        val m = JSONObject(http.seen.last().body).getJSONArray("movies").getJSONObject(0).getJSONObject("ids")
        assertEquals("tt15239678", m.getString("imdb")); assertFalse(m.has("tmdb"))
    }

    @Test fun aTitleTraktDoesNotKnowIsNotLoggedAsSent() {
        signedIn()
        // No ids and an empty search: nothing is posted.
        val none = FakeHttp { s -> if ("/search/" in s.url) "[]" else 404 }
        assertEquals(Trakt.Sent.NOT_FOUND, Trakt.watched(ctx, none.client, "A Title Nobody Has", null, 1, 1))
        assertTrue(none.seen.none { it.url.endsWith("/sync/history") })
        // Trakt answered 201 but put the id under not_found (e.g. a wrong TMDB id from a source).
        val notFound = FakeHttp { 201 to """{"added":{"movies":0,"episodes":0},"existing":{"movies":0,"episodes":0},"not_found":{"movies":[{"ids":{"tmdb":999999999}}]}}""" }
        assertEquals(Trakt.Sent.NOT_FOUND, Trakt.watched(ctx, notFound.client, "Wrong", null, null, null, tmdb = 999999999))
        // Already in history counts as landed.
        val existing = FakeHttp { 201 to """{"added":{"movies":0,"episodes":0},"existing":{"movies":0,"episodes":1}}""" }
        assertEquals(Trakt.Sent.ADDED, Trakt.watched(ctx, existing.client, "Frieren", 2023, 1, 1, tmdb = 209867))
    }

    @Test fun signedOutIsReportedNotSwallowed() {
        assertEquals(Trakt.Sent.SIGNED_OUT, Trakt.watched(ctx, FakeHttp { 500 }.client, "Frieren", 2023, 1, 1, tmdb = 209867))
    }

    @Test fun aRefreshRejectedForItsRedirectRetriesBeforeSigningOut() {
        signedIn(expiresInMs = 60_000) // inside the one-day refresh window
        val http = FakeHttp { s ->
            if (s.url == "https://auth.trakt.tv/oauth/token") {
                val redirect = JSONObject(s.body).optString("redirect_uri")
                if (redirect == Trakt.REDIRECT_LOCAL) 200 to """{"access_token":"b18-new-access","refresh_token":"b18-new-refresh","expires_in":604800,"created_at":${System.currentTimeMillis() / 1000}}"""
                else 401 to """{"error":"invalid_grant","error_description":"redirect_uri mismatch"}"""
            } else 404
        }
        val token = Trakt.validToken(ctx, http.client)
        assertEquals("b18-new-access", token)
        assertTrue(Trakt.signedIn)
        assertEquals(OnlineSettings.secret("trakt_refresh"), "b18-new-refresh")
        val tried = http.seen.filter { it.url.endsWith("/oauth/token") }.map { JSONObject(it.body).optString("redirect_uri") }
        assertEquals(listOf(Trakt.REDIRECT, Trakt.REDIRECT_LOCAL), tried)
    }

    @Test fun aRefreshEveryRedirectRejectsSignsOut() {
        signedIn(expiresInMs = 60_000)
        val http = FakeHttp { 401 to """{"error":"invalid_grant"}""" }
        assertNull(Trakt.validToken(ctx, http.client))
        assertFalse(Trakt.signedIn)
        assertEquals(3, http.seen.count { it.url == "https://auth.trakt.tv/oauth/token" })
        // The last try carries no redirect at all.
        assertFalse(JSONObject(http.seen.last().body).has("redirect_uri"))
    }

    @Test fun aRefreshDuringAnOutageKeepsTheSignIn() {
        signedIn(expiresInMs = 60_000)
        assertEquals("b18-access-token-0123", Trakt.validToken(ctx, FakeHttp { IOException("offline") }.client))
        assertTrue(Trakt.signedIn)
    }

    @Test fun theTraktGuideUsesLinksThatExistAndARedirectTraktAccepts() {
        val links = SetupGuides.trakt.steps.mapNotNull { it.link }
        assertTrue(links.toString(), "https://trakt.tv/join" in links && "https://developer.trakt.tv/apps/new" in links)
        assertFalse("trakt.tv/auth/join is a 404 since the new site", links.any { "auth/join" in it })
        assertEquals("http://localhost", SetupGuides.TRAKT_REDIRECT)
        assertTrue(SetupGuides.trakt.tips.any { "16 people" in it })
    }

    // endregion

    // region B19 SubDL

    @Test fun aKeySubdlRefusesIsNotSaved() {
        val http = FakeHttp { 403 to """{"status":false,"error":"Not Authorized"}""" }
        assertEquals(SubDL.KeyCheck.Rejected, SubDL.link(ctx, http.client, "not-a-real-key-123"))
        assertFalse(SubDL.available()); assertNull(SubDL.account())
        assertTrue(http.seen.single().url.startsWith("https://api.subdl.com/api/v1/me?api_key="))
    }

    @Test fun aGoodKeyIsSavedWithTheAccountName() {
        val http = FakeHttp { """{"status":true,"user":{"username":"siamform","email":"x@example.com"}}""" }
        val r = SubDL.link(ctx, http.client, "  b19-key-0123456789  ")
        assertEquals(SubDL.KeyCheck.Valid("siamform"), r)
        assertTrue(SubDL.available())
        assertEquals("b19-key-0123456789", OnlineSettings.subdlKey.value)
        assertEquals("siamform", SubDL.account())
        SubDL.unlink(ctx)
        assertFalse(SubDL.available()); assertNull(SubDL.account())
    }

    @Test fun withoutTheAccountEndpointOneRealSearchDecides() {
        val ok = FakeHttp { s -> if ("/me" in s.url) 404 to "" else """{"status":true,"results":[{"imdb_id":"tt0133093","name":"The Matrix"}],"subtitles":[]}""" }
        assertEquals(SubDL.KeyCheck.Valid(null), SubDL.checkKey(ok.client, "b19-key-0123456789"))
        assertTrue(ok.seen.last().url.contains("imdb_id=tt0133093"))
        val bad = FakeHttp { s -> if ("/me" in s.url) 404 to "" else 403 to """{"status":false,"statusCode":403,"error":"not_authorized","message":"Not Authorized"}""" }
        assertEquals(SubDL.KeyCheck.Rejected, SubDL.checkKey(bad.client, "b19-key-0123456789"))
    }

    @Test fun anOutageOrLimitIsNotCalledABadKey() {
        assertTrue(SubDL.checkKey(FakeHttp { IOException("offline") }.client, "b19-key-0123456789") is SubDL.KeyCheck.Unreachable)
        assertTrue(SubDL.checkKey(FakeHttp { 429 }.client, "b19-key-0123456789") is SubDL.KeyCheck.Unreachable)
        assertTrue(SubDL.checkKey(FakeHttp { 503 }.client, "b19-key-0123456789") is SubDL.KeyCheck.Unreachable)
        // Obviously not a key: no request at all.
        val none = FakeHttp { 200 }
        assertEquals(SubDL.KeyCheck.Rejected, SubDL.checkKey(none.client, "ab"))
        assertTrue(none.seen.isEmpty())
    }

    @Test fun accountNamesAreReadOrMasked() {
        assertEquals("neo", SubDL.parseAccount("""{"status":true,"username":"neo"}"""))
        assertEquals("ne•••@example.com", SubDL.parseAccount("""{"status":true,"data":{"email":"neo@example.com"}}"""))
        assertNull(SubDL.parseAccount("""{"status":true}"""))
    }

    @Test fun subdlHasItsOwnGuideWithRealLinks() {
        assertTrue(SetupGuides.subdl in SetupGuides.all)
        val links = SetupGuides.subdl.steps.mapNotNull { it.link }
        assertEquals(listOf("https://subdl.com/panel/register", "https://subdl.com/panel/api"), links)
    }

    // endregion
}
