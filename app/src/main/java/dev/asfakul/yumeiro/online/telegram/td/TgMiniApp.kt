/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import kotlinx.serialization.json.put

/**
 * DW-BOT-1 (owner list R21, item 19b): opens a Telegram bot's Mini App inside Yumeiro, the way the Telegram app does.
 *
 * Why: DiskWala gives a file's address only to its own app (its file API needs an AppiCrypt app-attestation
 * signature, which Yumeiro does not fake). The working route people use is a downloader bot's Mini App on Telegram.
 * A Mini App is a web page that Telegram opens with the user's signed launch data (`tgWebAppData`); TDLib makes
 * that address for a signed-in account (`getMainWebApp` / `getWebAppLinkUrl`). Yumeiro opens it in its own page
 * finder, so the page runs its own code (no copied keys, no copied code) and the video it gives is played here.
 *
 * Privacy: the bot receives what Telegram gives every Mini App (the account's id, name, language) and the link.
 * The user agrees once ([consented]); nothing is sent before that.
 */
object TgMiniApp {
    /** `t.me/<bot>?startapp=<start>` (main Mini App) or `t.me/<bot>/<app>?startapp=<start>` (named Mini App). */
    data class Link(val bot: String, val app: String?, val start: String)

    private val HOSTS = setOf("t.me", "telegram.me", "telegram.dog")

    /** Pure, unit-tested. Null for anything that is not a Mini App start link. */
    fun parse(url: String): Link? {
        val u = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return null
        val scheme = u.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return null
        val host = u.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host !in HOSTS) return null
        val parts = u.path.orEmpty().split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.size > 2) return null
        val bot = parts[0]
        if (!validName(bot) || !bot.lowercase().endsWith("bot")) return null
        val app = parts.getOrNull(1)?.takeIf { validName(it) }
        if (parts.size == 2 && app == null) return null
        val query = u.rawQuery.orEmpty().split('&').mapNotNull { kv ->
            val k = kv.substringBefore('='); val v = kv.substringAfter('=', "")
            if (k.isEmpty()) null else k to java.net.URLDecoder.decode(v, "UTF-8")
        }.toMap()
        val start = query["startapp"] ?: if (app != null) "" else return null
        if (start.length > 512) return null
        return Link(bot, app, start)
    }

    private fun validName(s: String) = s.length in 3..64 && s.all { it.isLetterOrDigit() || it == '_' }

    // ---- consent (one switch per profile) -------------------------------------------------------------------------
    private const val PREFS = "telegram_miniapp"
    private const val KEY_OK = "consented"

    fun consented(context: Context): Boolean =
        context.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context, PREFS), Context.MODE_PRIVATE).getBoolean(KEY_OK, false)

    fun setConsented(context: Context, on: Boolean) {
        context.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context, PREFS), Context.MODE_PRIVATE).edit().putBoolean(KEY_OK, on).apply()
    }

    /** What the user is agreeing to, in plain words (shown before the first Mini App opens). */
    val CONSENT_TEXT: String get() = tr(R.string.this_opens_a_telegram_bots_mini, "This opens a Telegram bot's mini app inside Yumeiro, as the Telegram app would. ") +
        tr(R.string.the_bot_is_run_by_a, "The bot is run by a third party, not by Yumeiro or DiskWala. Telegram gives it your Telegram name, ") +
        "user id and language, and Yumeiro gives it the link. The bot may show its own ads. You can turn this off " +
        "in Websites > Telegram > Bot mini apps."

    /** The launch address for [link] with the signed-in account, or a plain-words error. */
    sealed interface Opened {
        data class Url(val url: String) : Opened
        data class Error(val message: String) : Opened
    }

    suspend fun open(session: TgSession, link: Link): Opened {
        val client = session.client
        if (session.state.value != TgAuthState.Ready) return Opened.Error("Sign in to Telegram first.")
        return try {
            val chat = client.call("searchPublicChat") { put("username", link.bot) }
            val chatId = chat.long("id")
            val botId = chat.obj("type")?.takeIf { it.type() == "chatTypePrivate" }?.long("user_id") ?: 0L
            if (botId == 0L) return Opened.Error("@${link.bot} is not a bot.")
            val url = if (link.app != null) {
                client.call("searchWebApp") { put("bot_user_id", botId); put("web_app_short_name", link.app) }
                client.call("getWebAppLinkUrl") {
                    put("chat_id", chatId); put("bot_user_id", botId); put("web_app_short_name", link.app)
                    put("start_parameter", link.start); put("allow_write_access", false)
                    put("parameters", openParameters())
                }.str("url")
            } else {
                client.call("getMainWebApp") {
                    put("chat_id", chatId); put("bot_user_id", botId); put("start_parameter", link.start)
                    put("parameters", openParameters())
                }.str("url")
            }
            if (url.startsWith("https://")) Opened.Url(url) else Opened.Error("@${link.bot} has no mini app.")
        } catch (e: TdException) {
            Opened.Error(TgAuth.errorText(e))
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Opened.Error("Telegram didn't answer. Check the connection and try again.")
        }
    }

    private fun openParameters(): kotlinx.serialization.json.JsonElement = tdObject("webAppOpenParameters") {
        put("application_name", "Yumeiro")
        put("mode", tdObject("webAppOpenModeFullSize"))
    }

    /** The bot DiskWala links go to (owner list R21, 19b). The sky577bot page now sends DiskWala ids here itself. */
    const val DISKWALA_BOT = "flezennbot"

    fun diskWalaLink(shareId: String) = Link(DISKWALA_BOT, null, shareId)
}

