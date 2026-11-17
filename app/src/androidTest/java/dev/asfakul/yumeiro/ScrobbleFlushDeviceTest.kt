/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.os.Looper
import android.os.StrictMode
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.scrobble.Scrobbler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * IG-4 (G11): right after Last.fm sign-in, Settings › Tracking calls `Scrobbler.flush` in
 * `onSuccess`, on the main thread. Before the fix the blocking upload ran there: a release build
 * threw NetworkOnMainThreadException (swallowed as "offline", so waiting listens were not sent) and
 * a debug build froze the screen for the upload. Here one listen waits in the queue, Last.fm answers
 * from a local server, and `flush` is called on the main thread under the release policy
 * (penaltyDeathOnNetwork). Pass: the queue is sent (pending 0) and no request ran on main.
 */
@RunWith(AndroidJUnit4::class)
class ScrobbleFlushDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private lateinit var server: MockWebServer
    private val threads = Collections.synchronizedList(mutableListOf<String>())
    private var oldPolicy: StrictMode.ThreadPolicy? = null
    private val field = NetworkClient::class.java.getDeclaredField("client").apply { isAccessible = true }
    private var original: Any? = null
    private val prefs get() = ctx.getSharedPreferences("scrobble", Context.MODE_PRIVATE)
    private var savedQueue: String? = null
    private var savedEnabled = true
    private var savedSession: String? = null
    private var savedUser: String? = null

    @Before fun setUp() {
        OnlineSettings.init(ctx)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.url.encodedPath.startsWith("/2.0")) MockResponse.Builder().code(200)
                    .body("""{"scrobbles":{"@attr":{"accepted":1,"ignored":0},"scrobble":[]}}""").build()
                else MockResponse.Builder().code(404).build()
        }
        server.start()
        NetworkClient.get(ctx)
        original = field.get(NetworkClient)
        field.set(NetworkClient, OkHttpClient.Builder().addInterceptor { chain ->
            val main = Looper.myLooper() == Looper.getMainLooper()
            threads += (if (main) "main:" else "bg:") + Thread.currentThread().name
            val to = chain.request().url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
            chain.proceed(chain.request().newBuilder().url(to).build())
        }.build())
        savedQueue = prefs.getString("queue", null)
        savedEnabled = prefs.getBoolean("enabled", true)
        savedSession = OnlineSettings.secret("lastfm_session"); savedUser = OnlineSettings.secret("lastfm_user")
        Scrobbler.init(ctx)
        Scrobbler.setEnabled(ctx, true)
        OnlineSettings.setSecret(ctx, "lastfm_session", "sk-ig4"); OnlineSettings.setSecret(ctx, "lastfm_user", "ig4")
        val play = JSONObject().put("a", "Artist IG4").put("t", "Track IG4").put("al", "").put("d", 200)
            .put("s", System.currentTimeMillis() / 1000 - 600)
        prefs.edit().putString("queue", JSONArray().put(play).toString()).commit()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            oldPolicy = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeathOnNetwork().build())
        }
    }

    @After fun tearDown() {
        oldPolicy?.let { p -> InstrumentationRegistry.getInstrumentation().runOnMainSync { StrictMode.setThreadPolicy(p) } }
        field.set(NetworkClient, original)
        prefs.edit().apply { if (savedQueue == null) remove("queue") else putString("queue", savedQueue) }.commit()
        Scrobbler.setEnabled(ctx, savedEnabled)
        OnlineSettings.setSecret(ctx, "lastfm_session", savedSession); OnlineSettings.setSecret(ctx, "lastfm_user", savedUser)
        runCatching { server.close() }
    }

    @Test fun flushCalledOnMainSendsTheQueueOffMain() {
        // Called on the main thread, exactly like the Tracking card's onSuccess { … Scrobbler.flush(context) }.
        val result = runBlocking(Dispatchers.Main) { runCatching { withTimeout(20_000) { Scrobbler.flush(ctx) } } }
        val left = JSONArray(prefs.getString("queue", "[]")).length()
        val line = "SCROBBLE_FLUSH error=${result.exceptionOrNull()?.javaClass?.simpleName} pending=${Scrobbler.pending.value} " +
            "queueLeft=$left requests=${server.requestCount} threads=$threads"
        Log.i("YuScrobbleTest", line)
        assertTrue(line, result.isSuccess)
        assertTrue(line, threads.isNotEmpty() && threads.none { it.startsWith("main:") })
        assertEquals(line, 0, left)
        assertEquals(line, 0, Scrobbler.pending.value)
        Log.i("YuScrobbleTest", "SCROBBLE_FLUSH_RESULT PASS")
    }
}
