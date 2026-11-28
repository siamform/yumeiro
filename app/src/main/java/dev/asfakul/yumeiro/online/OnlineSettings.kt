/*
 * Yumeiro — Copyright (C) 2026 siamform
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
    @Volatile private var prefs: SharedPreferences? = null
    /** Account tokens and API keys. Kept in their own file so backups can exclude them. */
    @Volatile private var secrets: SharedPreferences? = null
    /** FINAL-8: set last, so a caller that sees it also sees [prefs], [secrets] and the loaded values. */
    @Volatile private var ready = false
    /** Test hook: runs inside [init] right after [prefs] is set (where the FINAL-8 race was). */
    @androidx.annotation.VisibleForTesting @Volatile internal var initPauseForTests: (() -> Unit)? = null

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

    private val _subdlKey = MutableStateFlow("")
    /** Optional free SubDL API key (adds SubDL results to subtitle search). Stored encrypted. */
    val subdlKey: StateFlow<String> = _subdlKey.asStateFlow()

    private val _subsourceKey = MutableStateFlow("")
    /** Optional free SubSource API key (adds SubSource results to subtitle search). Stored encrypted. */
    val subsourceKey: StateFlow<String> = _subsourceKey.asStateFlow()

    @Volatile private var adultPrefs: SharedPreferences? = null

    /**
     * FINAL-8: one init at a time. It used to return as soon as [prefs] was set; a second thread (the app's
     * start-up init racing an AniList / MAL sign-in link that cold-started the app) then wrote its token while
     * [secrets] was still null, and `writeSecret` dropped it silently (AnimeSignInMessageDeviceTest, tv-api24 P1,
     * device-check 44603655334). Callers now wait for the running init to finish.
     */
    fun init(context: Context) {
        if (ready) return
        synchronized(this) { if (!ready) initLocked(context) }
    }

    private fun initLocked(context: Context) {
        val p = context.applicationContext.getSharedPreferences("online_settings", Context.MODE_PRIVATE).also { prefs = it }
        initPauseForTests?.invoke()
        val s = context.applicationContext.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE).also { secrets = it }
        migrateSecrets(p, s)
        // PR-1: the adult switches belong to the profile (a new profile starts with adult content off).
        val a = context.applicationContext.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context, "online_settings"), Context.MODE_PRIVATE).also { adultPrefs = it }
        _adult.value = a.getBoolean("adult", false)
        _adultPrivate.value = a.getBoolean("adult_private", true)
        _skipIntro.value = p.getBoolean("skip_intro", true)
        _autoSkip.value = p.getBoolean("auto_skip", false)
        _subtitleLang.value = p.getString("subtitle_lang", null) ?: java.util.Locale.getDefault().language.ifBlank { "en" }
        _openSubtitlesKey.value = readSecret("os_key").orEmpty()
        _subdlKey.value = readSecret("subdl_key").orEmpty()
        _subsourceKey.value = readSecret("subsource_key").orEmpty()
        ready = true
    }

    fun setAdult(context: Context, v: Boolean) { init(context); _adult.value = v; adultPrefs?.edit()?.putBoolean("adult", v)?.apply(); OnlineRepository.clearCaches() }
    fun setAdultPrivate(context: Context, v: Boolean) { init(context); _adultPrivate.value = v; adultPrefs?.edit()?.putBoolean("adult_private", v)?.apply() }
    fun setSkipIntro(context: Context, v: Boolean) { init(context); _skipIntro.value = v; prefs?.edit()?.putBoolean("skip_intro", v)?.apply() }
    fun setAutoSkip(context: Context, v: Boolean) { init(context); _autoSkip.value = v; prefs?.edit()?.putBoolean("auto_skip", v)?.apply() }
    fun setSubtitleLang(context: Context, v: String) { init(context); _subtitleLang.value = v; prefs?.edit()?.putString("subtitle_lang", v)?.apply() }
    fun setOpenSubtitlesKey(context: Context, v: String) { init(context); _openSubtitlesKey.value = v.trim(); writeSecret("os_key", v.trim()) }
    fun setSubdlKey(context: Context, v: String) { init(context); _subdlKey.value = v.trim(); writeSecret("subdl_key", v.trim()) }
    fun setSubsourceKey(context: Context, v: String) { init(context); _subsourceKey.value = v.trim(); writeSecret("subsource_key", v.trim()) }

    /** Small secrets store for sync accounts (tokens). */
    fun secret(key: String): String? = readSecret("secret:$key")
    /** Drops a stored secret (a rejected token). Needs [init] to have run, as reading the secret did. */
    fun clearSecret(key: String) = writeSecret("secret:$key", null)
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

    /**
     * B20 S7: every stored secret, opened, for a password-locked backup (never anything else). Values that can't be
     * opened on this device are left out.
     */
    internal fun allSecretsForBackup(context: Context): Map<String, String> {
        init(context)
        val names = (secrets?.all?.keys.orEmpty() + volatileSecrets.keys).toSortedSet()
        return names.mapNotNull { n -> readSecret(n)?.takeIf { it.isNotBlank() }?.let { n to it } }.toMap()
    }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTests() { ready = false; prefs = null; secrets = null; volatileSecrets.clear() }

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
