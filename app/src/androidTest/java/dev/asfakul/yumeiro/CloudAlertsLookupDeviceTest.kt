/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.notify.CloudAlerts
import dev.asfakul.yumeiro.notify.PushWake
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineSettings
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * IG-6 (G11): airing alerts follow a saved anime through its AniList id, looked up once and cached.
 * A failed lookup (offline, AniList 429/5xx) was cached as 0 ("not anime") for good, so the show
 * never got airing alerts again. Here AniList fails on the first check-in and works on the second;
 * the cloud server is played by the same fake client. Pass: the failure is not cached and the second
 * check-in follows the show (its id is in the registration sent to the server).
 */
@RunWith(AndroidJUnit4::class)
class CloudAlertsLookupDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun log(m: String) = Log.i("YuCloudTest", m)
    private val netField = NetworkClient::class.java.getDeclaredField("client").apply { isAccessible = true }
    private var original: Any? = null
    private val stateField = PushWake::class.java.getDeclaredField("_state").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST") private val pushState get() = stateField.get(PushWake) as MutableStateFlow<PushWake.State>
    private var savedState: PushWake.State? = null
    private val prefs get() = ctx.getSharedPreferences("cloud_alerts", Context.MODE_PRIVATE)
    private var savedPrefs: Map<String, *> = emptyMap<String, Any>()
    private val secretKeys = listOf("push_endpoint", "cloud_device")
    private val savedSecrets = HashMap<String, String?>()
    private val run = System.currentTimeMillis()
    private val item = OnlineItem("Yumeiro Test Airing Show", "u-cloud-$run", "yu-test-cloud", null, type = "TV")
    private val registrations = Collections.synchronizedList(ArrayList<String>())
    @Volatile private var aniListDown = true

    private val fake = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val r = chain.request()
        val sent = r.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
        val (code, body) = when (r.url.host) {
            "graphql.anilist.co" -> when {
                aniListDown -> 500 to """{"errors":[{"message":"Internal Server Error","status":500}]}"""
                sent.contains("nextAiringEpisode") -> 200 to """{"data":{"Page":{"media":[{"id":4242,"nextAiringEpisode":null}]}}}"""
                else -> 200 to """{"data":{"Page":{"media":[{"id":4242,"idMal":77,"episodes":12}]}}}"""
            }
            else -> if (r.url.encodedPath == "/v1/devices" && r.method == "POST") {
                registrations += sent
                200 to """{"id":"dev-ig6","token":"tok-ig6","follows":0,"airing":0}"""
            } else 404 to ""
        }
        Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }).build()

    @Before fun setUp() {
        OnlineSettings.init(ctx)
        PushWake.init(ctx)
        NetworkClient.get(ctx)
        original = netField.get(NetworkClient)
        netField.set(NetworkClient, fake)
        savedState = pushState.value
        savedPrefs = prefs.all.toMap()
        secretKeys.forEach { savedSecrets[it] = OnlineSettings.secret(it) }
        prefs.edit().clear().commit()
        OnlineSettings.setSecret(ctx, "cloud_device", null)
        OnlineSettings.setSecret(ctx, "push_endpoint", "https://ntfy.example.org/upIG6test")
        pushState.value = PushWake.State(enabled = true, hasEndpoint = true, endpointHost = "ntfy.example.org")
        OnlineBookmarks.save(ctx, item)
    }

    @After fun tearDown() {
        netField.set(NetworkClient, original)
        OnlineBookmarks.remove(ctx, item.api, item.url)
        savedState?.let { pushState.value = it }
        secretKeys.forEach { OnlineSettings.setSecret(ctx, it, savedSecrets[it]) }
        prefs.edit().clear().apply {
            savedPrefs.forEach { (k, v) ->
                when (v) { is String -> putString(k, v); is Long -> putLong(k, v); is Int -> putInt(k, v); is Boolean -> putBoolean(k, v) }
            }
        }.commit()
    }

    private fun cached(): String? = runCatching { JSONObject(prefs.getString("ids", "{}")!!) }.getOrNull()
        ?.takeIf { it.has("${item.api}|${item.url}") }?.get("${item.api}|${item.url}")?.toString()

    @Test fun aFailedLookupIsNotCachedAsNotAnime() {
        CloudAlerts.sync(ctx) // AniList down
        val afterFail = cached()
        log("CLOUD_CASE first sync (AniList 500): cached=$afterFail registrations=${registrations.size}")
        assertFalse("a failed lookup must not be cached as 0 (not anime): cached=$afterFail", afterFail == "0")

        aniListDown = false
        // The first check-in registered; a changed follow list is due again at once (new fingerprint).
        CloudAlerts.sync(ctx)
        val afterOk = cached()
        val followed = registrations.any { JSONObject(it).optJSONArray("anilist")?.toString()?.contains("4242") == true }
        log("CLOUD_CASE second sync (AniList ok): cached=$afterOk followed=$followed registrations=${registrations.size} last=${registrations.lastOrNull()}")
        assertTrue("the show is looked up again and cached with its id", afterOk == "4242")
        assertTrue("the next check-in follows the show", followed)
        log("CLOUD_RESULT PASS")
    }
}
