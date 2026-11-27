/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.security.SecretBox

/**
 * OWN-3 (owner 2026-11-22): sign in to Telegram with the user's own API ID and API hash from my.telegram.org,
 * like Hikari and other third-party Telegram clients (Telegram's terms ask every client for its own keys). The
 * build's keys are used when the user set none; a build without keys (F-Droid, forks) can sign in only with the
 * user's. The hash is stored sealed by the Android Keystore (SecretBox), never in backups or logs.
 */
object TgKeys {
    private const val PREFS = "telegram_keys"
    data class Keys(val apiId: Int, val apiHash: String)

    /** Null when both look right, else what to fix. */
    fun problem(apiId: String, apiHash: String): String? {
        val id = apiId.trim()
        val hash = apiHash.trim()
        if (id.isEmpty() || id.length > 10 || !id.all { it.isDigit() } || id.toLongOrNull()?.let { it in 1..Int.MAX_VALUE } != true) return tr(R.string.the_api_id_is_a_number, "The API ID is a number, like 1234567.")
        if (hash.length != 32 || !hash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return tr(R.string.the_api_hash_has_32_letters, "The API hash has 32 letters and digits (0-9, a-f).")
        return null
    }

    fun build(): Keys? = BuildConfig.TG_API_ID.toIntOrNull()?.takeIf { BuildConfig.TG_API_HASH.isNotBlank() }?.let { Keys(it, BuildConfig.TG_API_HASH) }

    fun own(context: Context): Keys? {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = p.getInt("api_id", 0).takeIf { it > 0 } ?: return null
        val hash = p.getString("api_hash", null)?.let { SecretBox.open(it) } ?: return null
        return Keys(id, hash)
    }

    fun hasOwn(context: Context): Boolean = own(context) != null

    /** The keys sign-in uses: the user's, else the build's; null = the user must enter keys first. */
    fun effective(context: Context): Keys? = own(context) ?: build()

    /** Saves the user's keys; returns an error text or null. */
    fun save(context: Context, apiId: String, apiHash: String): String? {
        problem(apiId, apiHash)?.let { return it }
        val sealed = SecretBox.seal(apiHash.trim().lowercase()) ?: return tr(R.string.this_phone_could_not_store_the, "This phone could not store the key safely.")
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("api_id", apiId.trim().toInt()).putString("api_hash", sealed).commit()
        return null
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
