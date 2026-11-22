/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import kotlinx.serialization.json.JsonObject

/**
 * F-02 / ADR-052: what the Telegram sign-in screen shows, derived only from TDLib's `authorizationState*` objects
 * (pure, unit tested in TgAuthTest). TDLib owns the real state; Yumeiro never guesses a step.
 */
sealed interface TgAuthState {
    /** TDLib is starting or waiting for its parameters (sent automatically by [TgSession]). */
    data object Starting : TgAuthState
    data object WaitPhone : TgAuthState
    /** [kind] is how the code was sent ("Telegram app", "SMS", "call"); [next] is what "Send again" switches to. */
    data class WaitCode(val phone: String, val kind: String, val length: Int, val next: String?, val timeoutSeconds: Int) : TgAuthState
    data class WaitPassword(val hint: String, val hasRecoveryEmail: Boolean, val recoveryPattern: String) : TgAuthState
    /** A new number with no Telegram account yet: Yumeiro asks for a first name (Telegram's own rule). */
    data class WaitRegistration(val termsText: String) : TgAuthState
    /**
     * A step Yumeiro does not run itself (email set-up, premium purchase, QR on another device): the user finishes it
     * in an official Telegram app once, then signs in here.
     */
    data class Unsupported(val step: String) : TgAuthState
    data object Ready : TgAuthState
    data object LoggingOut : TgAuthState
    data object Closed : TgAuthState
}

object TgAuth {
    fun parse(state: JsonObject?): TgAuthState {
        if (state == null) return TgAuthState.Starting
        return when (state.type()) {
            "authorizationStateWaitTdlibParameters" -> TgAuthState.Starting
            "authorizationStateWaitPhoneNumber" -> TgAuthState.WaitPhone
            "authorizationStateWaitCode" -> {
                val info = state.obj("code_info")
                val type = info?.obj("type")
                TgAuthState.WaitCode(
                    phone = info?.str("phone_number").orEmpty(),
                    kind = codeKind(type?.type().orEmpty()),
                    length = type?.int("length") ?: 0,
                    next = info?.obj("next_type")?.type()?.let(::codeKind),
                    timeoutSeconds = info?.int("timeout") ?: 0,
                )
            }
            "authorizationStateWaitPassword" -> TgAuthState.WaitPassword(
                hint = state.str("password_hint"),
                hasRecoveryEmail = state.bool("has_recovery_email_address"),
                recoveryPattern = state.str("recovery_email_address_pattern"),
            )
            "authorizationStateWaitRegistration" -> TgAuthState.WaitRegistration(
                termsText = state.obj("terms_of_service")?.obj("text")?.str("text").orEmpty(),
            )
            "authorizationStateWaitEmailAddress", "authorizationStateWaitEmailCode" -> TgAuthState.Unsupported("email")
            "authorizationStateWaitPremiumPurchase" -> TgAuthState.Unsupported("premium")
            "authorizationStateWaitOtherDeviceConfirmation" -> TgAuthState.Unsupported("qr")
            "authorizationStateReady" -> TgAuthState.Ready
            "authorizationStateLoggingOut" -> TgAuthState.LoggingOut
            "authorizationStateClosing", "authorizationStateClosed" -> TgAuthState.Closed
            else -> TgAuthState.Unsupported(state.type())
        }
    }

    private fun codeKind(type: String): String = when (type) {
        "authenticationCodeTypeTelegramMessage" -> "Telegram app"
        "authenticationCodeTypeSms", "authenticationCodeTypeSmsWord", "authenticationCodeTypeSmsPhrase" -> "SMS"
        "authenticationCodeTypeCall", "authenticationCodeTypeFlashCall", "authenticationCodeTypeMissedCall" -> "call"
        "authenticationCodeTypeFragment" -> "Fragment"
        "authenticationCodeTypeFirebaseAndroid", "authenticationCodeTypeFirebaseIos" -> "SMS"
        else -> "Telegram"
    }

    /**
     * The number as TDLib wants it: digits with a leading +, spaces / dashes / brackets dropped. Null when it can't be
     * a phone number (E.164: 7 to 15 digits). A leading "00" is the international prefix and becomes "+".
     */
    fun normalizePhone(input: String): String? {
        val t = input.trim()
        val digits = StringBuilder()
        for (ch in t) {
            when {
                ch in '0'..'9' -> digits.append(ch)
                ch == '+' && digits.isEmpty() -> Unit
                ch == ' ' || ch == '-' || ch == '(' || ch == ')' || ch == '.' -> Unit
                else -> return null
            }
        }
        var d = digits.toString()
        if (!t.startsWith("+") && d.startsWith("00")) d = d.substring(2)
        if (d.length !in 7..15) return null
        return "+$d"
    }

    /** The code with everything but digits removed (people paste "12-345" or "Code: 12345"). */
    fun normalizeCode(input: String): String = input.filter { it in '0'..'9' }

    /** Plain words for TDLib's sign-in errors (codes from core.telegram.org/api/auth). */
    fun errorText(e: Throwable): String {
        val msg = (e as? TdException)?.message ?: e.message.orEmpty()
        val wait = floodSeconds(msg)
        return when {
            wait != null -> "Too many tries. Telegram asks you to wait ${waitWords(wait)}."
            "PHONE_NUMBER_INVALID" in msg -> "That phone number isn't valid. Include the country code, like +880…"
            "PHONE_NUMBER_BANNED" in msg -> "Telegram has banned this phone number."
            "PHONE_NUMBER_FLOOD" in msg -> "Telegram is blocking sign-in for this number for now. Try again later."
            "PHONE_CODE_INVALID" in msg -> "That code is wrong. Check it and try again."
            "PHONE_CODE_EXPIRED" in msg -> "That code has expired. Tap \"Send again\"."
            "PHONE_CODE_EMPTY" in msg -> "Type the code Telegram sent you."
            "PASSWORD_HASH_INVALID" in msg -> "Wrong password. Check it and try again."
            "FIRSTNAME_INVALID" in msg -> "Type a first name."
            "API_ID_INVALID" in msg || "API_ID_PUBLISHED_FLOOD" in msg -> "This Yumeiro build can't sign in to Telegram. Update Yumeiro."
            "Telegram is closed" in msg -> "Telegram stopped. Open it again."
            e is kotlinx.coroutines.TimeoutCancellationException -> "Telegram didn't answer. Check your connection and try again."
            msg.isBlank() -> "Something went wrong. Try again."
            else -> "Telegram says: $msg"
        }
    }

    /** "FLOOD_WAIT_123" or "Too Many Requests: retry after 123" → 123. */
    fun floodSeconds(message: String): Int? {
        val i = message.indexOf("FLOOD_WAIT_")
        if (i >= 0) return message.substring(i + 11).takeWhile { it.isDigit() }.toIntOrNull()
        val j = message.indexOf("retry after ")
        if (j >= 0) return message.substring(j + 12).takeWhile { it.isDigit() }.toIntOrNull()
        return null
    }

    fun waitWords(seconds: Int): String = when {
        seconds < 60 -> "$seconds seconds"
        seconds < 3600 -> "${(seconds + 59) / 60} min"
        else -> "${(seconds + 3599) / 3600} h"
    }
}
