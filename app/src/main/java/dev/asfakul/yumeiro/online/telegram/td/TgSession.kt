/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What TDLib needs to open one account database (`setTdlibParameters`). */
class TgParams(
    val apiId: Int,
    val apiHash: String,
    val databaseDir: String,
    val filesDir: String,
    /** 32 random bytes per profile, wrapped by the Android Keystore (TgAccounts); never logged. */
    val encryptionKey: ByteArray,
    val useTestDc: Boolean = false,
    val language: String = "en",
    val deviceModel: String = "Android",
    val systemVersion: String = "",
    val appVersion: String = "",
) {
    fun request(): JsonObject = buildJsonObject {
        put("@type", "setTdlibParameters")
        put("use_test_dc", useTestDc)
        put("database_directory", databaseDir)
        put("files_directory", filesDir)
        put("database_encryption_key", java.util.Base64.getEncoder().encodeToString(encryptionKey))
        put("use_file_database", true)
        put("use_chat_info_database", true)
        put("use_message_database", true)
        put("use_secret_chats", false)
        put("api_id", apiId)
        put("api_hash", apiHash)
        put("system_language_code", language.ifBlank { "en" })
        put("device_model", deviceModel.ifBlank { "Android" })
        put("system_version", systemVersion)
        put("application_version", appVersion.ifBlank { "Yumeiro" })
    }
}

/**
 * F-02 / ADR-052: one signed-in (or signing-in) Telegram account. [state] follows TDLib; every action is guarded by
 * [busy] so a double tap sends one request; errors become plain words in [error]. Back on the code or password
 * step goes to [backToPhone] (TDLib accepts a new number in those states), and closing the screen never logs out.
 */
class TgSession(val client: TdClient, private val params: TgParams, private val scope: CoroutineScope) {
    val state: StateFlow<TgAuthState> = client.auth.map(TgAuth::parse).stateIn(scope, SharingStarted.Eagerly, TgAuthState.Starting)
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    /** The user chose "Change number" on the code / password step; the screen shows the phone field again. */
    private val _editingPhone = MutableStateFlow(false)
    val editingPhone: StateFlow<Boolean> = _editingPhone.asStateFlow()
    private var paramsSent = false
    private val watcher: Job

    init {
        watcher = scope.launch {
            client.auth.collect { s ->
                if (s?.type() == "authorizationStateWaitTdlibParameters" && !paramsSent) {
                    paramsSent = true
                    runCatching { client.call("setTdlibParameters") { params.request().forEach { (k, v) -> if (k != "@type") put(k, v) } } }
                        .onFailure { _error.value = TgAuth.errorText(it) }
                }
                // A new code or a finished sign-in ends an old "Change number" view.
                if (s?.type() == "authorizationStateWaitCode" || s?.type() == "authorizationStateReady") _editingPhone.value = false
            }
        }
        // TDLib sends nothing until the first request reaches it.
        client.send("getOption") { put("name", "version") }
    }

    fun clearError() { _error.value = null }

    fun backToPhone() { _editingPhone.value = true; _error.value = null }

    /** Back on the "Change number" view: the code TDLib already sent is still valid. */
    fun cancelPhoneEdit() { _editingPhone.value = false; _error.value = null }

    suspend fun submitPhone(input: String): Boolean {
        val phone = TgAuth.normalizePhone(input)
            ?: return fail("That phone number isn't valid. Include the country code, like +880…")
        return act("phone") {
            client.call("setAuthenticationPhoneNumber") {
                put("phone_number", phone)
                put("settings", tdObject("phoneNumberAuthenticationSettings") {
                    put("allow_flash_call", false); put("allow_missed_call", false)
                    put("is_current_phone_number", false); put("has_unknown_phone_number", false)
                    put("allow_sms_retriever_api", false)
                })
            }
            _editingPhone.value = false
        }
    }

    suspend fun submitCode(input: String): Boolean {
        val code = TgAuth.normalizeCode(input)
        if (code.isEmpty()) return fail("Type the code Telegram sent you.")
        return act("code") { client.call("checkAuthenticationCode") { put("code", code) } }
    }

    suspend fun resendCode(): Boolean = act("resend") {
        client.call("resendAuthenticationCode") { put("reason", tdObject("resendCodeReasonUserRequest")) }
    }

    suspend fun submitPassword(password: String): Boolean {
        if (password.isEmpty()) return fail("Type your Telegram password (two-step verification).")
        return act("password") { client.call("checkAuthenticationPassword") { put("password", password) } }
    }

    suspend fun register(firstName: String, lastName: String): Boolean {
        if (firstName.isBlank()) return fail("Type a first name.")
        return act("register") {
            client.call("registerUser") { put("first_name", firstName.trim()); put("last_name", lastName.trim()); put("disable_notification", true) }
        }
    }

    suspend fun logOut(): Boolean = act("logout") { client.call("logOut", timeoutMs = 60_000) }

    /** Closes TDLib (screen gone for good, profile switch). The account stays signed in on disk. */
    fun close() {
        watcher.cancel()
        client.send("close")
    }

    private fun fail(text: String): Boolean { _error.value = text; return false }

    private val gate = Mutex()
    @Volatile private var running: String? = null

    /**
     * One request at a time. A second tap on the same step while it runs is ignored (double tap). Another
     * step waits its turn: TDLib shows the next step before it answers the last request (device run
     * 44876643230), so a code typed in that gap must not be dropped.
     */
    private suspend fun act(kind: String, block: suspend () -> Unit): Boolean {
        if (running == kind) return false
        return gate.withLock {
            running = kind
            _busy.value = true
            _error.value = null
            try {
                block(); true
            } catch (e: TimeoutCancellationException) {
                _error.value = TgAuth.errorText(e); false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _error.value = TgAuth.errorText(e); false
            } finally {
                running = null
                _busy.value = false
            }
        }
    }
}
