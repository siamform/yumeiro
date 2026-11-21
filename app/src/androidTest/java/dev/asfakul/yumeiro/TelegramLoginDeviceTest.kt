package dev.asfakul.yumeiro

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.telegram.td.TdEngine
import dev.asfakul.yumeiro.online.telegram.td.TgAccounts
import dev.asfakul.yumeiro.online.telegram.td.TgAuthState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-02 / ADR-052 on a real device: the pinned TDLib engine downloads, passes its hash check and loads; then the
 * sign-in round trip on Telegram's test servers (see [phoneReachesCodeScreenAndTelegramChecksTheCode]).
 */
@RunWith(AndroidJUnit4::class)
class TelegramLoginDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @After fun tearDown(): Unit = runBlocking {
        TgAccounts.closeAll()
        TgAccounts.useTestDc = false
        FeatureFlags.set(ctx, FeatureFlags.Flag.TELEGRAM_LOGIN, false)
    }

    @Test fun engineDownloadsChecksAndLoads(): Unit = runBlocking {
        assertTrue("this build has no engine pin for " + android.os.Build.SUPPORTED_ABIS.toList(), TdEngine.available())
        val t0 = System.nanoTime()
        assertTrue("engine: " + TdEngine.state.value, withTimeout(180_000) { TdEngine.ensure(ctx, NetworkClient.get(ctx)) })
        android.util.Log.i("F02", "engine ready in " + (System.nanoTime() - t0) / 1_000_000 + " ms")
        assertTrue(TdEngine.isLoaded())
        assertEquals(TdEngine.State.Ready, TdEngine.state.value)
        // A second call is instant and changes nothing.
        assertTrue(TdEngine.ensure(ctx, NetworkClient.get(ctx)))
    }

    /**
     * The sign-in round trip on Telegram's test servers: number accepted, code screen shown, the code is sent and
     * Telegram's answer comes back as plain words while the code screen stays open for another try.
     *
     * A full automated sign-in is not possible any more: Telegram switched off the public 99966 test accounts, so
     * 22222 is always PHONE_CODE_INVALID (tdlib/td#3083, #3370; device run 44878617517 saw exactly that). The full
     * sign-in, chat list and log-out are owner manual test OMT-63 with a real account.
     */
    @Test fun phoneReachesCodeScreenAndTelegramChecksTheCode(): Unit = runBlocking {
        FeatureFlags.set(ctx, FeatureFlags.Flag.TELEGRAM_LOGIN, true)
        assertTrue("Telegram api_id missing from this build", BuildConfig.TG_API_ID.toIntOrNull() != null)
        assertTrue("Telegram api_hash missing from this build", BuildConfig.TG_API_HASH.isNotBlank())
        assertTrue("flag f02_telegram_login did not turn on", FeatureFlags.on(ctx, FeatureFlags.Flag.TELEGRAM_LOGIN))
        assertTrue("no engine pin for " + android.os.Build.SUPPORTED_ABIS.toList(), TdEngine.available())
        assertTrue(TgAccounts.enabled(ctx))
        TgAccounts.useTestDc = true
        val s = withTimeout(180_000) { TgAccounts.session(ctx) }
        requireNotNull(s) { "session did not start: " + TdEngine.state.value }
        val version = s.client.call("getOption") { put("name", "version") }["value"]?.toString()
        android.util.Log.i("F02", "tdlib version $version")

        // Every state and error seen, for the failure message (no phone numbers or codes in it).
        val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
        val watch = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            launch { s.state.collect { seen += "state=" + it.javaClass.simpleName } }
            launch { s.error.collect { seen += "error=" + it } }
            launch { s.busy.collect { seen += "busy=" + it } }
        }
        var st = withTimeout(60_000) { s.state.first { it !is TgAuthState.Starting } }
        if (st is TgAuthState.Ready) { s.logOut(); st = withTimeout(60_000) { s.state.first { it is TgAuthState.WaitPhone } } }
        assertEquals(TgAuthState.WaitPhone, st)
        val phone = "+999662" + (1000..9999).random()
        assertTrue("phone: " + s.error.value + " seen=" + seen, s.submitPhone(phone))
        st = withTimeout(60_000) { s.state.first { it is TgAuthState.WaitCode || it is TgAuthState.WaitRegistration } }
        assertTrue("expected the code screen, got $st seen=$seen", st is TgAuthState.WaitCode)
        val ok = s.submitCode("22222")
        android.util.Log.i("F02", "after code: ok=$ok seen=$seen")
        // Telegram answered (no hang, no crash, no busy left on), in words a person understands.
        val err = s.error.value
        assertTrue("no answer from Telegram for the code; seen=$seen", ok || err != null)
        if (!ok) assertTrue("raw or empty error \"$err\"; seen=$seen", err != null && err.isNotBlank() && !err.contains("_"))
        assertEquals("busy stayed on; seen=$seen", false, s.busy.value)
        if (!ok) assertTrue("code screen closed after a refused code: ${s.state.value}", s.state.value is TgAuthState.WaitCode)
        watch.cancel()
    }
}
