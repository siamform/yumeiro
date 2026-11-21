package dev.asfakul.yumeiro

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.telegram.td.TdEngine
import dev.asfakul.yumeiro.online.telegram.td.TgAccounts
import dev.asfakul.yumeiro.online.telegram.td.TgAuthState
import dev.asfakul.yumeiro.online.telegram.td.TgLibrary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-02 / ADR-052 on a real device: the pinned TDLib engine downloads, passes its hash check and loads; then a full
 * sign-in on Telegram's test servers (number 99966 2 XXXX, code 22222: Telegram's documented test accounts, no SMS,
 * no real account), the chat list loads, and log-out works.
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

    @Test fun signInOnTestServersListsChatsAndLogsOut(): Unit = runBlocking {
        FeatureFlags.set(ctx, FeatureFlags.Flag.TELEGRAM_LOGIN, true)
        assertTrue("Telegram keys missing from this build, or no engine pin", TgAccounts.enabled(ctx))
        TgAccounts.useTestDc = true
        val s = withTimeout(180_000) { TgAccounts.session(ctx) }
        requireNotNull(s) { "session did not start: " + TdEngine.state.value }
        val version = s.client.call("getOption") { put("name", "version") }["value"]?.toString()
        android.util.Log.i("F02", "tdlib version $version")

        var st = withTimeout(60_000) { s.state.first { it !is TgAuthState.Starting } }
        if (st is TgAuthState.Ready) { s.logOut(); st = withTimeout(60_000) { s.state.first { it is TgAuthState.WaitPhone } } }
        assertEquals(TgAuthState.WaitPhone, st)
        val phone = "+999662" + (1000..9999).random()
        assertTrue("phone: " + s.error.value, s.submitPhone(phone))
        st = withTimeout(60_000) { s.state.first { it is TgAuthState.WaitCode || it is TgAuthState.WaitRegistration } }
        if (st is TgAuthState.WaitCode) {
            assertTrue("code: " + s.error.value, s.submitCode("22222"))
            st = withTimeout(60_000) { s.state.first { it !is TgAuthState.WaitCode } }
        }
        if (st is TgAuthState.WaitRegistration) {
            assertTrue("register: " + s.error.value, s.register("Yumeiro", "Test"))
            st = withTimeout(60_000) { s.state.first { it !is TgAuthState.WaitRegistration } }
        }
        assertEquals("error: " + s.error.value, TgAuthState.Ready, st)

        val chats = withTimeout(60_000) { TgLibrary(s.client).chats() }
        android.util.Log.i("F02", "test account chats: " + chats.size)

        assertTrue("logout: " + s.error.value, s.logOut())
        withTimeout(60_000) { s.state.first { it is TgAuthState.Closed || it is TgAuthState.WaitPhone || it is TgAuthState.LoggingOut } }
    }
}
