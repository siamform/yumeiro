/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.online.anime.AnimeSync
import dev.asfakul.yumeiro.online.anime.AniList
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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * IG-8 (G11): after an AniList / MyAnimeList sign-in redirect the app chose its message from
 * "is any token saved?". For someone already signed in, a failed new sign-in (AniList rejected the
 * token, the link was stale) still said "Signed in to AniList as <old user>": a false success.
 * Here the user is signed in as "OldUser"; a rejected redirect must say it failed, and an accepted
 * one must name the new account.
 */
@RunWith(AndroidJUnit4::class)
class AnimeSignInMessageDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val netField = NetworkClient::class.java.getDeclaredField("client").apply { isAccessible = true }
    private var original: Any? = null
    private val keys = listOf("anilist_token", "anilist_user", "anilist_client")
    private val saved = HashMap<String, String?>()
    @Volatile private var viewerOk = false

    private val fake = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val r = chain.request()
        val (code, body) = if (r.url.host == "graphql.anilist.co" && viewerOk) 200 to """{"data":{"Viewer":{"name":"NewUser"}}}"""
            else 401 to """{"errors":[{"message":"Invalid token","status":401}]}"""
        Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }).build()

    @Before fun setUp() {
        OnlineSettings.init(ctx)
        NetworkClient.get(ctx)
        original = netField.get(NetworkClient)
        netField.set(NetworkClient, fake)
        keys.forEach { saved[it] = OnlineSettings.secret(it) }
        OnlineSettings.setSecret(ctx, "anilist_client", "12345")
        OnlineSettings.setSecret(ctx, "anilist_token", "old-token-0123456789abcdef")
        OnlineSettings.setSecret(ctx, "anilist_user", "OldUser")
    }

    @After fun tearDown() {
        netField.set(NetworkClient, original)
        keys.forEach { OnlineSettings.setSecret(ctx, it, saved[it]) }
    }

    private fun redirect(): String {
        val q = ArrayBlockingQueue<String>(1)
        AniList.loginUrl(ctx) // starts the 10-minute sign-in window, as tapping "Sign in" does
        val uri = Uri.parse("yumeiro://anilist-login#access_token=new-token-abcdef0123456789&token_type=Bearer")
        assertTrue(AnimeSync.handleRedirect(ctx, uri) { q.offer(it) })
        return q.poll(30, TimeUnit.SECONDS) ?: error("no message within 30 s")
    }

    @Test fun rejectedReSignInIsNotReportedAsSuccess() {
        viewerOk = false
        val msg = redirect()
        Log.i(TAG, "SIGNIN_CASE rejected msg=\"$msg\" token=${OnlineSettings.secret("anilist_token")?.take(9)}")
        assertFalse("false success: $msg", msg.startsWith("Signed in"))
        assertTrue(msg.contains("failed"))
        assertEquals("old-token-0123456789abcdef", OnlineSettings.secret("anilist_token"))
        Log.i(TAG, "SIGNIN_RESULT PASS rejected")
    }

    @Test fun acceptedSignInNamesTheNewAccount() {
        viewerOk = true
        val msg = redirect()
        Log.i(TAG, "SIGNIN_CASE accepted msg=\"$msg\"")
        assertEquals("Signed in to AniList as NewUser", msg)
        Log.i(TAG, "SIGNIN_RESULT PASS accepted")
    }

    private companion object { const val TAG = "YuSignInTest" }
}
