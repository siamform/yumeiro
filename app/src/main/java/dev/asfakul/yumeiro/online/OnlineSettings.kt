/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferences for online videos. */
object OnlineSettings {
    private var prefs: SharedPreferences? = null
    /** Account tokens and API keys. Kept in their own file so backups can exclude them. */
    private var secrets: SharedPreferences? = null

    private val _adult = MutableStateFlow(false)
    /** Show 18+ extensions and titles (off by default; turning on needs an age confirmation). */
    val adult: StateFlow<Boolean> = _adult.asStateFlow()

    private val _adultPrivate = MutableStateFlow(true)
    /**
     * Keep 18+ activity private: watched 18+ titles are still saved (resume works, search finds
     * them) but never appear in feeds, suggestions, Continue watching or Home.
     */
    val adultPrivate: StateFlow<Boolean> = _adultPrivate.asStateFlow()

    private val _skipIntro = MutableStateFlow(true)
    /** Offer "Skip intro / outro" on anime (AniSkip). */
    val skipIntro: StateFlow<Boolean> = _skipIntro.asStateFlow()

    private val _autoSkip = MutableStateFlow(false)
    /** Skip intros/outros without asking. */
    val autoSkip: StateFlow<Boolean> = _autoSkip.asStateFlow()

    private val _subtitleLang = MutableStateFlow("en")
    /** Preferred language for online subtitle search (ISO 639-1). */
    val subtitleLang: StateFlow<String> = _subtitleLang.asStateFlow()

    private val _openSubtitlesKey = MutableStateFlow("")
    /** Optional OpenSubtitles.com API key (adds OpenSubtitles results to subtitle search). */
    val openSubtitlesKey: StateFlow<String> = _openSubtitlesKey.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("online_settings", Context.MODE_PRIVATE).also { prefs = it }
        val s = context.applicationContext.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE).also { secrets = it }
        migrateSecrets(p, s)
        _adult.value = p.getBoolean("adult", false)
        _adultPrivate.value = p.getBoolean("adult_private", true)
        _skipIntro.value = p.getBoolean("skip_intro", true)
        _autoSkip.value = p.getBoolean("auto_skip", false)
        _subtitleLang.value = p.getString("subtitle_lang", null) ?: java.util.Locale.getDefault().language.ifBlank { "en" }
        _openSubtitlesKey.value = readSecret("os_key").orEmpty()
    }

    fun setAdult(context: Context, v: Boolean) { init(context); _adult.value = v; prefs?.edit()?.putBoolean("adult", v)?.apply(); OnlineRepository.clearCaches() }
    fun setAdultPrivate(context: Context, v: Boolean) { init(context); _adultPrivate.value = v; prefs?.edit()?.putBoolean("adult_private", v)?.apply() }
    fun setSkipIntro(context: Context, v: Boolean) { init(context); _skipIntro.value = v; prefs?.edit()?.putBoolean("skip_intro", v)?.apply() }
    fun setAutoSkip(context: Context, v: Boolean) { init(context); _autoSkip.value = v; prefs?.edit()?.putBoolean("auto_skip", v)?.apply() }
    fun setSubtitleLang(context: Context, v: String) { init(context); _subtitleLang.value = v; prefs?.edit()?.putString("subtitle_lang", v)?.apply() }
    fun setOpenSubtitlesKey(context: Context, v: String) { init(context); _openSubtitlesKey.value = v.trim(); writeSecret("os_key", v.trim()) }

    /** Small secrets store for sync accounts (tokens). */
    fun secret(key: String): String? = readSecret("secret:$key")
    fun setSecret(context: Context, key: String, value: String?) {
        init(context)
        writeSecret("secret:$key", value)
    }

    /** Session-only copies, used when the Keystore can't encrypt. Secrets are never written in plaintext. */
    private val volatileSecrets = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun readSecret(name: String): String? {
        volatileSecrets[name]?.let { return it }
        val stored = secrets?.getString(name, null) ?: return null
        if (dev.asfakul.yumeiro.security.SecretBox.isSealed(stored)) return dev.asfakul.yumeiro.security.SecretBox.open(stored)
        // Legacy plaintext from older versions: encrypt it in place (or drop it from disk if the Keystore fails).
        writeSecret(name, stored)
        return stored
    }

    private fun writeSecret(name: String, value: String?) {
        val p = secrets ?: return
        if (value.isNullOrBlank()) { volatileSecrets.remove(name); p.edit().remove(name).apply(); return }
        val sealed = dev.asfakul.yumeiro.security.SecretBox.seal(value)
        if (sealed != null) { volatileSecrets.remove(name); p.edit().putString(name, sealed).apply() }
        else { volatileSecrets[name] = value; p.edit().remove(name).apply() }
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTests() { prefs = null; secrets = null; volatileSecrets.clear() }

    /** Same name as in res/xml/backup_rules.xml and data_extraction_rules.xml. */
    private const val SECRETS_FILE = "online_secrets"

    /**
     * One-time move of tokens/keys written by older versions into [SECRETS_FILE], encrypted.
     * Also encrypts any plaintext left in [SECRETS_FILE] by an interrupted migration.
     */
    private fun migrateSecrets(from: SharedPreferences, to: SharedPreferences) {
        val legacy = from.all.filter { (k, v) -> v is String && (k.startsWith("secret:") || k == "os_key") }
        legacy.forEach { (k, v) -> if (!to.contains(k)) writeSecret(k, v as String) }
        if (legacy.isNotEmpty()) from.edit().apply { legacy.keys.forEach(::remove) }.apply()
        to.all.forEach { (k, v) -> if (v is String && !dev.asfakul.yumeiro.security.SecretBox.isSealed(v)) writeSecret(k, v) }
    }
}
