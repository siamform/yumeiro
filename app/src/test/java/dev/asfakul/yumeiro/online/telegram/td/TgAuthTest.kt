/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** F-02 / ADR-052: sign-in states, input cleaning, error words, and the session's flow against a scripted TDLib. */
class TgAuthTest {
    private fun st(type: String, build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) = tdObject(type, build) as JsonObject

    @Test fun parsesEveryState() {
        assertEquals(TgAuthState.Starting, TgAuth.parse(null))
        assertEquals(TgAuthState.Starting, TgAuth.parse(st("authorizationStateWaitTdlibParameters")))
        assertEquals(TgAuthState.WaitPhone, TgAuth.parse(st("authorizationStateWaitPhoneNumber")))
        val code = TgAuth.parse(st("authorizationStateWaitCode") {
            put("code_info", tdObject("authenticationCodeInfo") {
                put("phone_number", "+99966")
                put("type", tdObject("authenticationCodeTypeTelegramMessage") { put("length", 5) })
                put("next_type", tdObject("authenticationCodeTypeSms") { put("length", 5) })
                put("timeout", 60)
            })
        })
        assertEquals(TgAuthState.WaitCode("+99966", "Telegram app", 5, "SMS", 60), code)
        assertEquals(TgAuthState.WaitCode("", "Telegram", 0, null, 0), TgAuth.parse(st("authorizationStateWaitCode")))
        assertEquals(TgAuthState.WaitPassword("cat", true, "a***@b.c"), TgAuth.parse(st("authorizationStateWaitPassword") {
            put("password_hint", "cat"); put("has_recovery_email_address", true); put("recovery_email_address_pattern", "a***@b.c")
        }))
        assertEquals(TgAuthState.WaitRegistration("Terms"), TgAuth.parse(st("authorizationStateWaitRegistration") {
            put("terms_of_service", tdObject("termsOfService") { put("text", tdObject("formattedText") { put("text", "Terms") }) })
        }))
        assertEquals(TgAuthState.Unsupported("email"), TgAuth.parse(st("authorizationStateWaitEmailAddress")))
        assertEquals(TgAuthState.Unsupported("email"), TgAuth.parse(st("authorizationStateWaitEmailCode")))
        assertEquals(TgAuthState.Unsupported("premium"), TgAuth.parse(st("authorizationStateWaitPremiumPurchase")))
        assertEquals(TgAuthState.Unsupported("qr"), TgAuth.parse(st("authorizationStateWaitOtherDeviceConfirmation")))
        assertEquals(TgAuthState.Ready, TgAuth.parse(st("authorizationStateReady")))
        assertEquals(TgAuthState.LoggingOut, TgAuth.parse(st("authorizationStateLoggingOut")))
        assertEquals(TgAuthState.Closed, TgAuth.parse(st("authorizationStateClosing")))
        assertEquals(TgAuthState.Closed, TgAuth.parse(st("authorizationStateClosed")))
        assertEquals(TgAuthState.Unsupported("authorizationStateNew"), TgAuth.parse(st("authorizationStateNew")))
    }

    @Test fun normalizesPhones() {
        assertEquals("+8801712345678", TgAuth.normalizePhone(" +880 1712-345678 "))
        assertEquals("+8801712345678", TgAuth.normalizePhone("00880 (1712) 345.678"))
        assertEquals("+9996612345", TgAuth.normalizePhone("9996612345"))
        assertNull(TgAuth.normalizePhone("+12345"))            // too short
        assertNull(TgAuth.normalizePhone("+1234567890123456")) // too long
        assertNull(TgAuth.normalizePhone("+880 17x2"))
        assertNull(TgAuth.normalizePhone("88+01712345678"))
        assertNull(TgAuth.normalizePhone(""))
    }

    @Test fun normalizesCodes() {
        assertEquals("12345", TgAuth.normalizeCode("Code: 12-345"))
        assertEquals("", TgAuth.normalizeCode("abc"))
    }

    @Test fun errorWordsAndFlood() {
        assertEquals(123, TgAuth.floodSeconds("FLOOD_WAIT_123"))
        assertEquals(30, TgAuth.floodSeconds("Too Many Requests: retry after 30"))
        assertNull(TgAuth.floodSeconds("PHONE_CODE_INVALID"))
        assertEquals("Too many tries. Telegram asks you to wait 3 min.", TgAuth.errorText(TdException(429, "Too Many Requests: retry after 150")))
        assertEquals("Too many tries. Telegram asks you to wait 2 h.", TgAuth.errorText(TdException(420, "FLOOD_WAIT_3601")))
        assertEquals("That code is wrong. Check it and try again.", TgAuth.errorText(TdException(400, "PHONE_CODE_INVALID")))
        assertEquals("Wrong password. Check it and try again.", TgAuth.errorText(TdException(400, "PASSWORD_HASH_INVALID")))
        assertEquals("Something went wrong. Try again.", TgAuth.errorText(RuntimeException()))
        assertEquals("Telegram says: ODD", TgAuth.errorText(TdException(400, "ODD")))
        assertEquals("45 seconds", TgAuth.waitWords(45))
    }

    private fun script(phoneGate: CountDownLatch? = null): FakeTd = FakeTd { req ->
        when (req.type()) {
            "getOption" -> listOf(FakeTd.auth("authorizationStateWaitTdlibParameters"))
            "setTdlibParameters" -> listOf(FakeTd.ok(req), FakeTd.auth("authorizationStateWaitPhoneNumber"))
            "setAuthenticationPhoneNumber" -> {
                phoneGate?.await(5, TimeUnit.SECONDS)
                listOf(FakeTd.ok(req), FakeTd.auth("authorizationStateWaitCode") {
                    put("code_info", tdObject("authenticationCodeInfo") {
                        put("phone_number", req.str("phone_number")); put("type", tdObject("authenticationCodeTypeSms") { put("length", 5) })
                    })
                })
            }
            "checkAuthenticationCode" ->
                if (req.str("code") == "22222") listOf(FakeTd.ok(req), FakeTd.auth("authorizationStateReady"))
                else listOf(FakeTd.error(req, 400, "PHONE_CODE_INVALID"))
            else -> listOf(FakeTd.ok(req))
        }
    }

    private fun params() = TgParams(1, "hash", "/tmp/db", "/tmp/files", ByteArray(32) { it.toByte() }, useTestDc = true)

    @Test fun signsInThroughPhoneAndCode() = runBlocking {
        val td = script()
        val hub = TdHub(td, pollSeconds = 0.05)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val s = TgSession(hub.newClient(), params(), scope)
            withTimeout(5_000) { s.state.first { it == TgAuthState.WaitPhone } }
            // The parameters went out once, with the key as base64 and the test DC on.
            val p = td.sent.single { it.type() == "setTdlibParameters" }
            assertEquals("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=", p.str("database_encryption_key"))
            assertTrue(p.bool("use_test_dc"))

            assertFalse(s.submitPhone("12"))
            assertEquals("That phone number isn't valid. Include the country code, like +880…", s.error.value)
            assertTrue(s.submitPhone("+999 66 1 2345"))
            val code = withTimeout(5_000) { s.state.first { it is TgAuthState.WaitCode } } as TgAuthState.WaitCode
            assertEquals("+9996612345", code.phone)
            assertEquals("SMS", code.kind)

            assertFalse(s.submitCode("11111"))
            assertEquals("That code is wrong. Check it and try again.", s.error.value)
            assertTrue(s.submitCode("22-222"))
            withTimeout(5_000) { s.state.first { it == TgAuthState.Ready } }
            assertNull(s.error.value)
        } finally { scope.cancel() }
    }

    @Test fun doubleTapSendsOneRequest() = runBlocking {
        val gate = CountDownLatch(1)
        val td = script(gate)
        val hub = TdHub(td, pollSeconds = 0.05)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val s = TgSession(hub.newClient(), params(), scope)
            withTimeout(5_000) { s.state.first { it == TgAuthState.WaitPhone } }
            val first = async(Dispatchers.IO) { s.submitPhone("+9996612345") }
            withTimeout(5_000) { s.busy.first { it } }
            assertFalse(s.submitPhone("+9996612345"))
            gate.countDown()
            assertTrue(first.await())
            assertEquals(1, td.sentTypes().count { it == "setAuthenticationPhoneNumber" })
            assertFalse(s.busy.value)
        } finally { scope.cancel() }
    }

    @Test fun changeNumberAndBack() = runBlocking {
        val td = script()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val s = TgSession(TdHub(td, 0.05).newClient(), params(), scope)
            withTimeout(5_000) { s.state.first { it == TgAuthState.WaitPhone } }
            s.submitPhone("+9996612345")
            withTimeout(5_000) { s.state.first { it is TgAuthState.WaitCode } }
            s.backToPhone(); assertTrue(s.editingPhone.value)
            s.cancelPhoneEdit(); assertFalse(s.editingPhone.value)
            s.backToPhone()
            assertTrue(s.submitPhone("+9996654321"))
            assertFalse(s.editingPhone.value)
        } finally { scope.cancel() }
    }

    @Test fun closedClientFailsCallsInsteadOfHanging() = runBlocking {
        val td = FakeTd { req -> if (req.type() == "close") listOf(FakeTd.auth("authorizationStateClosed")) else emptyList() }
        val c = TdHub(td, 0.05).newClient()
        val pending = async(Dispatchers.IO) { runCatching { c.call("getMe", timeoutMs = 5_000) } }
        Thread.sleep(100)
        c.send("close")
        val r = withTimeout(5_000) { pending.await() }
        assertTrue(r.exceptionOrNull() is TdException)
        assertTrue(c.closed)
        Unit
    }

    @Test fun keepsTheFileStateWithMoreBytes() {
        val c = TdHub(FakeTd { emptyList() }, 0.05).newClient()
        fun file(prefix: Long) = buildJsonObject {
            put("@type", "file"); put("id", 7); put("size", 1000)
            put("local", tdObject("localFile") { put("download_offset", 0); put("downloaded_prefix_size", prefix) })
        }
        c.remember(file(500))
        c.remember(file(100))
        assertEquals(500L, TdClient.downloadedPrefix(c.file(7)!!))
    }
}
