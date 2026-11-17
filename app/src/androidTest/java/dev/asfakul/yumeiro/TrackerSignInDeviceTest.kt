/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.Looper
import android.os.StrictMode
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.tracking.Simkl
import dev.asfakul.yumeiro.tracking.Trakt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * IG-1 (G11): Settings › Tracking starts the Trakt and Simkl sign-in from the screen's coroutine
 * scope (main thread) and called `awaitToken` without leaving it; `awaitToken` polls with blocking
 * OkHttp `execute()`. A release build's main thread kills network calls (NetworkOnMainThreadException),
 * so the sign-in failed at the first poll; a debug build (DevStrictMode only logs) froze the screen on
 * every poll. Here the trackers answer from a local server (pending once, then the token), the call is
 * started on the main thread under the release policy (penaltyDeathOnNetwork), and every request's
 * thread is recorded. Pass: signed in, and no request ran on the main thread.
 */
@RunWith(AndroidJUnit4::class)
class TrackerSignInDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private lateinit var server: MockWebServer
    private val polls = AtomicInteger()
    private val threads = Collections.synchronizedList(mutableListOf<String>())
    private var oldPolicy: StrictMode.ThreadPolicy? = null

    @Before fun setUp() {
        OnlineSettings.init(ctx)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                return when {
                    path.endsWith("/oauth/device/token") || path.endsWith("/oauth2/token") ->
                        if (polls.incrementAndGet() == 1) {
                            MockResponse.Builder().code(400).body("{\"error\":\"authorization_pending\"}").build()
                        } else {
                            MockResponse.Builder().code(200).body(
                                "{\"access_token\":\"t-ig1\",\"refresh_token\":\"r-ig1\",\"expires_in\":604800,\"created_at\":${System.currentTimeMillis() / 1000},\"scope\":\"media:read media:write\"}",
                            ).build()
                        }
                    path.endsWith("/users/settings") ->
                        MockResponse.Builder().code(200).body("{\"user\":{\"username\":\"ig1-user\",\"name\":\"ig1-user\"}}").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        // Release main-thread rule: a network call on the main thread throws.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            oldPolicy = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeathOnNetwork().build())
        }
    }

    @After fun tearDown() {
        oldPolicy?.let { p -> InstrumentationRegistry.getInstrumentation().runOnMainSync { StrictMode.setThreadPolicy(p) } }
        Trakt.signOut(ctx)
        Simkl.signOut(ctx)
        runCatching { server.close() }
    }

    /** Every tracker host goes to the local server; the calling thread is recorded. */
    private fun client(): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val main = Looper.myLooper() == Looper.getMainLooper()
        threads += (if (main) "main:" else "bg:") + Thread.currentThread().name
        val to = chain.request().url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
        chain.proceed(chain.request().newBuilder().url(to).build())
    }.build()

    private fun signIn(name: String, block: suspend () -> String) {
        // Started on the main thread, exactly like the Tracking card's scope.launch { … }.
        val result = runBlocking(Dispatchers.Main) { runCatching { withTimeout(40_000) { block() } } }
        val line = "TRACKER_SIGNIN $name result=${result.getOrNull()} error=${result.exceptionOrNull()?.javaClass?.simpleName}:${result.exceptionOrNull()?.message} polls=${polls.get()} threads=$threads"
        Log.i("YuTrackerTest", line)
        assertTrue(line, result.isSuccess)
        assertEquals(line, "ig1-user", result.getOrNull())
        assertTrue(line, threads.isNotEmpty() && threads.none { it.startsWith("main:") })
        Log.i("YuTrackerTest", "TRACKER_SIGNIN_RESULT PASS $name")
    }

    @Test fun traktSignInPollsOffTheMainThread() = signIn("trakt") {
        Trakt.awaitToken(ctx, client(), Trakt.DeviceCode("dev-ig1", "ABCD1234", "https://trakt.tv/activate", 1, 60))
    }

    @Test fun simklSignInPollsOffTheMainThread() = signIn("simkl") {
        Simkl.awaitToken(ctx, client(), Simkl.DeviceCode("dev-ig1", "ABCD1234", "https://simkl.com/pin", 5, 60))
    }
}
