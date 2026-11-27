/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import dev.asfakul.yumeiro.online.telegram.td.TgAccounts
import dev.asfakul.yumeiro.online.telegram.td.TgAuthState
import dev.asfakul.yumeiro.online.telegram.td.TgLibrary
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * GROUP F (owner items 4 and 6): a public channel post whose video Telegram keeps out of its web preview ("Media is
 * too big") plays with the user's own signed-in account, inside Yumeiro's player. Without an account the page asks
 * the user to sign in here; it never sends them to the Telegram app first.
 */
object TelegramAccountPlay {
    sealed interface Result {
        data class Ok(val request: StreamRequest) : Result
        /** Sign-in works on this phone, but nobody is signed in. */
        data object NeedsSignIn : Result
        /** Sign-in can't run here (no TDLib for this processor, or turned off). */
        data class Unavailable(val reason: String) : Result
        data class Failed(val message: String) : Result
    }

    object Words {
        val NEEDS_SIGN_IN: String get() = tr(R.string.telegram_keeps_this_video_for_signed, "Telegram keeps this video for signed-in users. Sign in once and it plays here in Yumeiro.")
        val WHERE_SIGN_IN: String get() = tr(R.string.open_telegram_in_yumeiro_live_tv, "Open Telegram in Yumeiro (Live TV › Tools › Telegram) and tap the account button.")
        val ROW_SIGN_IN: String get() = tr(R.string.plays_after_you_sign_in, "Plays after you sign in")
        val ROW_ACCOUNT: String get() = tr(R.string.plays_with_your_account, "Plays with your account")
        val NO_VIDEO: String get() = tr(R.string.your_account_cant_open_a_video, "Your account can't open a video in this post. It may have been deleted.")
        fun withheld(n: Int, signedIn: Boolean) = if (signedIn) tr(R.string.with_your_account, "%1\$s with your account", n) else tr(R.string.need_sign_in, "%1\$s need sign-in", n)
    }

    /** What the row and the card say for a withheld post (pure). */
    fun rowNote(signedIn: Boolean) = if (signedIn) Words.ROW_ACCOUNT else Words.ROW_SIGN_IN

    suspend fun request(context: Context, channel: String, id: Int, title: String, thumb: String?): Result {
        TgAccounts.unavailableReason(context)?.let { return Result.Unavailable(it) }
        if (!TgAccounts.signedInHint(context)) return Result.NeedsSignIn
        val s = TgAccounts.session(context) ?: return Result.Failed(tr(R.string.telegram_didnt_start_try_again, "Telegram didn't start. Try again."))
        val st = withTimeoutOrNull(20_000) { s.state.first { it !is TgAuthState.Starting } }
        if (st != TgAuthState.Ready) return Result.NeedsSignIn
        val video = runCatching { TgLibrary(s.client).postVideo(channel, id) }
            .getOrElse { return Result.Failed(dev.asfakul.yumeiro.online.telegram.td.TgAuth.errorText(it)) }
            ?: return Result.Failed(Words.NO_VIDEO)
        return Result.Ok(
            StreamRequest(
                url = TgAccounts.streamUrl(s, video), title = title, format = StreamFormat.PROGRESSIVE,
                // Like the account's own chat videos: a local TDLib stream, so no web-preview refresher (no pageUrl/group).
                logoUrl = thumb,
            ),
        )
    }
}
