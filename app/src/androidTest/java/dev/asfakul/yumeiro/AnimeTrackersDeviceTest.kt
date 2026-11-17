/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.online.anime.AnimeSync
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.Collections

/**
 * G11 IG-2 / IG-3 (emulator): the tracker updates sent when an online anime episode reaches 85%.
 *
 * The app's shared OkHttp client is swapped (reflection on `NetworkClient.client`, as in
 * OnlineSwitchesEffectDeviceTest) for one whose interceptor plays MyAnimeList, Kitsu, Trakt and Simkl
 * and records every call as "METHOD host/path bearer". Real tracker code runs end to end.
 *
 *  a. Expired MAL and Kitsu tokens (401) are refreshed once and the update is sent with the new token;
 *     Trakt and Simkl still get the episode. Before the fix: Kitsu threw, Trakt and Simkl were skipped.
 *  b. A refused Kitsu refresh signs Kitsu out; Trakt and Simkl still get the episode exactly once over
 *     repeated progress ticks.
 *  c. A tracker that keeps failing (Simkl offline) is tried at most 3 times, and does not repeat the
 *     others.
 */
@RunWith(AndroidJUnit4::class)
class AnimeTrackersDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun log(m: String) = Log.i("YuAnimeTrk", m)
    private val seen = Collections.synchronizedList(ArrayList<String>())
    private val field = NetworkClient::class.java.getDeclaredField("client").apply { isAccessible = true }
    private var original: Any? = null
    private val run = System.currentTimeMillis()

    @Volatile private var kitsuRefreshRefused = false
    @Volatile private var simklOffline = false

    private val keys = listOf(
        "mal_token", "mal_refresh", "mal_expires", "mal_user", "mal_client",
        "kitsu_token", "kitsu_refresh", "kitsu_expires", "kitsu_uid", "kitsu_user",
        "trakt_token", "trakt_refresh", "trakt_expires", "trakt_user",
        "simkl_token", "simkl_refresh", "simkl_expires", "simkl_user",
        "anilist_token", "anilist_user",
    )
    private val saved = HashMap<String, String?>()

    private val fake = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val r = chain.request()
        val url = r.url
        val auth = r.header("Authorization")?.removePrefix("Bearer ") ?: "-"
        val line = "${r.method} ${url.host}${url.encodedPath} $auth"
        seen += line
        val path = url.encodedPath
        val (code, body) = when (url.host) {
            "myanimelist.net" -> if (path == "/v1/oauth2/token")
                200 to """{"token_type":"Bearer","expires_in":2678400,"access_token":"mal-new","refresh_token":"malr-new"}""" else 404 to ""
            "api.myanimelist.net" -> when {
                auth != "mal-new" -> 401 to """{"error":"invalid_token"}"""
                r.method == "GET" -> 200 to """{"id":21,"num_episodes":12,"my_list_status":{"num_episodes_watched":0}}"""
                r.method == "PATCH" -> 200 to """{"num_episodes_watched":1}"""
                else -> 404 to ""
            }
            "kitsu.app" -> when {
                path == "/api/oauth/token" -> if (kitsuRefreshRefused) 401 to """{"error":"invalid_grant"}"""
                    else 200 to """{"access_token":"kitsu-new","refresh_token":"kr-new","expires_in":2591963,"created_at":${System.currentTimeMillis() / 1000},"token_type":"bearer"}"""
                auth != "kitsu-new" -> 401 to """{"errors":[{"title":"Invalid token","status":"401"}]}"""
                path.endsWith("/mappings") -> 200 to """{"data":[{"id":"9"}],"included":[{"id":"777","type":"anime"}]}"""
                path.endsWith("/library-entries") && r.method == "GET" -> 200 to """{"data":[]}"""
                path.endsWith("/library-entries") && r.method == "POST" -> 201 to """{"data":{"id":"55"}}"""
                else -> 404 to ""
            }
            "api.trakt.tv" -> when {
                path.startsWith("/search/show") -> 200 to """[{"type":"show","score":1,"show":{"title":"Yumeiro Test Anime","year":null,"ids":{"trakt":5}}}]"""
                path == "/sync/history" -> 201 to """{"added":{"episodes":1}}"""
                else -> 404 to ""
            }
            "api.simkl.com" -> when {
                simklOffline -> throw IOException("test: simkl offline")
                path == "/sync/history" -> 201 to """{"added":{"movies":0,"shows":0,"episodes":1}}"""
                else -> 404 to ""
            }
            else -> 404 to ""
        }
        Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }).build()

    @Before fun setUp() {
        NetworkClient.get(ctx)
        original = field.get(NetworkClient)
        field.set(NetworkClient, fake)
        OnlineSettings.init(ctx)
        keys.forEach { saved[it] = OnlineSettings.secret(it); OnlineSettings.setSecret(ctx, it, null) }
        assertFalse("incognito must be off for tracker updates", dev.asfakul.yumeiro.security.Privacy.incognito.value)
    }

    @After fun tearDown() {
        field.set(NetworkClient, original)
        keys.forEach { OnlineSettings.setSecret(ctx, it, saved[it]) }
    }

    private fun plant(vararg kv: Pair<String, String>) = kv.forEach { (k, v) -> OnlineSettings.setSecret(ctx, k, v) }
    private fun signInTraktSimkl() = plant("trakt_token" to "trakt-ok", "trakt_user" to "t", "simkl_token" to "simkl-ok", "simkl_user" to "s")

    private fun detail(case: String) = OnlineDetail(
        api = "yu-test-trk", url = "u-trk-$run-$case", name = "Yumeiro Test Anime", kind = OnlineKind.ANIME, type = "Anime",
        posterUrl = null, backgroundUrl = null, posterHeaders = emptyMap(), plot = null, year = null, tags = emptyList(),
        durationMin = null, score = null, contentRating = null, playData = null, episodes = emptyMap(), seasonNames = emptyMap(),
        recommendations = emptyList(), malId = 21, aniListId = 22,
    )
    private val ep = OnlineEpisode("d1", null, 1, 1, null, null, null)
    private fun tick(d: OnlineDetail) = AnimeSync.onProgress(ctx, d, ep, 0, 90_000L, 100_000L)
    private fun count(prefix: String) = synchronized(seen) { seen.count { it.startsWith(prefix) } }
    private fun has(prefix: String) = count(prefix) > 0
    private fun waitFor(ms: Long = 10_000, ok: () -> Boolean): Boolean {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) { if (ok()) return true; Thread.sleep(100) }
        return ok()
    }
    private fun dump(case: String) = synchronized(seen) { seen.forEach { log("ANIME_TRK_CALL $case $it") } }

    @Test fun a_expiredTokensRefreshAndOthersStillUpdate() {
        plant(
            "mal_token" to "mal-old", "mal_refresh" to "malr-old", "mal_user" to "m", "mal_client" to "test-client",
            "kitsu_token" to "kitsu-old", "kitsu_refresh" to "kr-old", "kitsu_uid" to "42", "kitsu_user" to "k",
        )
        signInTraktSimkl()
        tick(detail("a"))
        val all = waitFor {
            has("POST api.trakt.tv/sync/history") && has("POST api.simkl.com/sync/history") &&
                has("PATCH api.myanimelist.net/v2/anime/21/my_list_status mal-new") && has("POST kitsu.app/api/edge/library-entries kitsu-new")
        }
        dump("a")
        val trakt = count("POST api.trakt.tv/sync/history"); val simkl = count("POST api.simkl.com/sync/history")
        val malNew = has("PATCH api.myanimelist.net/v2/anime/21/my_list_status mal-new")
        val kitsuNew = has("POST kitsu.app/api/edge/library-entries kitsu-new")
        log("ANIME_TRK_CASE a trakt=$trakt simkl=$simkl malPatchNew=$malNew kitsuPostNew=$kitsuNew " +
            "malToken=${OnlineSettings.secret("mal_token")} malRefresh=${OnlineSettings.secret("mal_refresh")} kitsuToken=${OnlineSettings.secret("kitsu_token")}")
        assertTrue("Trakt history posted although Kitsu's token had expired", trakt >= 1)
        assertTrue("Simkl history posted although Kitsu's token had expired", simkl >= 1)
        assertTrue("MAL update sent with the refreshed token", malNew)
        assertTrue("Kitsu update sent with the refreshed token", kitsuNew)
        assertEquals("mal-new", OnlineSettings.secret("mal_token"))
        assertEquals("MAL's rotated refresh token is kept", "malr-new", OnlineSettings.secret("mal_refresh"))
        assertEquals("kitsu-new", OnlineSettings.secret("kitsu_token"))
        assertTrue(all)
        log("ANIME_TRK_RESULT PASS a")
    }

    @Test fun b_refusedRefreshSignsOutAndOthersUpdateOnce() {
        kitsuRefreshRefused = true
        plant("kitsu_token" to "kitsu-old", "kitsu_refresh" to "kr-old", "kitsu_uid" to "42", "kitsu_user" to "k")
        signInTraktSimkl()
        val d = detail("b")
        tick(d)
        waitFor { has("POST api.trakt.tv/sync/history") && has("POST api.simkl.com/sync/history") && has("POST kitsu.app/api/oauth/token") }
        repeat(3) { Thread.sleep(700); tick(d) } // the player reports progress about every 5 s
        Thread.sleep(2_000)
        dump("b")
        val trakt = count("POST api.trakt.tv/sync/history"); val simkl = count("POST api.simkl.com/sync/history")
        val kitsuCalls = count("GET kitsu.app") + count("POST kitsu.app")
        val signedIn = dev.asfakul.yumeiro.tracking.Kitsu.signedIn
        log("ANIME_TRK_CASE b trakt=$trakt simkl=$simkl kitsuCalls=$kitsuCalls kitsuSignedIn=$signedIn")
        assertEquals("Trakt gets the episode once", 1, trakt)
        assertEquals("Simkl gets the episode once", 1, simkl)
        assertFalse("a refused refresh signs Kitsu out so the card asks to sign in again", signedIn)
        assertTrue("Kitsu is not retried after sign-out (calls=$kitsuCalls)", kitsuCalls <= 3)
        log("ANIME_TRK_RESULT PASS b")
    }

    @Test fun c_failingTrackerRetriedAtMostThreeTimes() {
        simklOffline = true
        signInTraktSimkl()
        val d = detail("c")
        repeat(6) { tick(d); Thread.sleep(1_200) }
        Thread.sleep(1_500)
        dump("c")
        val trakt = count("POST api.trakt.tv/sync/history"); val simkl = count("POST api.simkl.com/sync/history")
        log("ANIME_TRK_CASE c trakt=$trakt simklAttempts=$simkl")
        assertEquals("Trakt gets the episode once", 1, trakt)
        assertEquals("an offline tracker is tried 3 times, not on every tick", 3, simkl)
        log("ANIME_TRK_RESULT PASS c")
    }
}
