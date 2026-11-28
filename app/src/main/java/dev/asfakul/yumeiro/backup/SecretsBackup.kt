/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

import android.content.Context
import dev.asfakul.yumeiro.online.OnlineSettings
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.security.SecretBox
import org.json.JSONObject

/**
 * B20 S7: the "Keys and sign-ins" section, only inside a password-locked backup.
 *
 * Secrets on the phone are sealed with its Keystore, which never leaves it, so a backup carries them opened, inside
 * the password encryption, and a restore seals each one again with the new phone's Keystore while staging (nothing
 * opened is written to disk). AI keys stay on their own profile id; a locked profile other than the open one keeps
 * its AI key out of the backup, like the rest of its data.
 */
internal object SecretsBackup {
    const val ENTRY = "secrets.json"
    const val STAGED = "secrets.sealed.json"
    private const val SECRETS_PREFS = "online_secrets"
    /** Plain preferences that belong with a sign-in (the YouTube account's "since" date and mode). */
    private val PLAIN_PREFS = setOf("yt_account")
    private val FIXED = setOf("os_key", "subdl_key", "subsource_key")
    private const val MAX_VALUE = 64 * 1024

    /** Only names Yumeiro writes are restored. */
    internal fun nameOk(n: String) = n in FIXED || (n.startsWith("secret:") && n.length in 8..96 && n.drop(7).all { it.isLetterOrDigit() || it in "_.:-" })

    /** "secret:ai_key_p<id>" → id; null for other secrets. */
    internal fun aiProfileOf(n: String): String? = n.takeIf { it.startsWith("secret:ai_key_p") }?.removePrefix("secret:ai_key_p")

    /** What goes in the backup: `{"secrets": {name: value}, "prefs": {file: typed values}}`. */
    fun collect(c: Context): JSONObject {
        val locked = Profiles.lockedOthers(c)
        val secrets = JSONObject()
        OnlineSettings.allSecretsForBackup(c).forEach { (n, v) ->
            if (!nameOk(n) || aiProfileOf(n)?.let { it in locked } == true) return@forEach
            secrets.put(n, v)
        }
        val prefs = JSONObject()
        PLAIN_PREFS.forEach { f -> c.getSharedPreferences(f, Context.MODE_PRIVATE).all.takeIf { it.isNotEmpty() }?.let { prefs.put(f, PrefCodec.encode(it)) } }
        return JSONObject().put("secrets", secrets).put("prefs", prefs)
    }

    /**
     * Staging: every value sealed with [seal] (this phone's Keystore). Returns the staged JSON and how many
     * values couldn't be sealed (they are dropped, never written opened).
     */
    fun reseal(o: JSONObject, seal: (String) -> String? = SecretBox::seal): Pair<JSONObject, Int> {
        val out = JSONObject(); var failed = 0
        o.optJSONObject("secrets")?.let { s ->
            s.keys().forEach { n ->
                val v = s.optString(n)
                if (!nameOk(n) || v.isBlank() || v.length > MAX_VALUE) return@forEach
                val sealed = seal(v)?.takeIf { SecretBox.isSealed(it) }
                if (sealed == null) failed++ else out.put(n, sealed)
            }
        }
        val prefs = JSONObject()
        o.optJSONObject("prefs")?.let { p -> p.keys().forEach { f -> if (f in PLAIN_PREFS) p.optJSONObject(f)?.let { prefs.put(f, it) } } }
        return JSONObject().put("secrets", out).put("prefs", prefs) to failed
    }

    /** At the next launch (before anything has read them): sealed values into the secrets file. Returns the count. */
    fun apply(c: Context, staged: JSONObject): Int {
        var n = 0
        staged.optJSONObject("secrets")?.let { s ->
            val e = c.getSharedPreferences(SECRETS_PREFS, Context.MODE_PRIVATE).edit()
            s.keys().forEach { k -> val v = s.optString(k); if (nameOk(k) && SecretBox.isSealed(v)) { e.putString(k, v); n++ } }
            e.commit()
        }
        staged.optJSONObject("prefs")?.let { p ->
            p.keys().forEach { f -> if (f in PLAIN_PREFS) p.optJSONObject(f)?.let { PrefCodec.apply(c.getSharedPreferences(f, Context.MODE_PRIVATE), PrefCodec.decode(it)) } }
        }
        return n
    }
}
