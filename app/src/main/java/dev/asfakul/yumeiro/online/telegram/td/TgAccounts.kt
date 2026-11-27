/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import android.os.Build
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.security.SecretBox
import dev.asfakul.yumeiro.stream.local.LocalStreamServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.SecureRandom

/**
 * F-02 / ADR-052: the Telegram account of the open Yumeiro profile. Each profile has its own TDLib database
 * (`noBackupFilesDir/tg/<profile>/db`, never in backups) and media cache (`cacheDir/tg/<profile>`), and its own
 * random database key sealed by the Android Keystore. Switching profile closes the other profile's TDLib.
 */
object TgAccounts {
    private val hub by lazy { TdHub(JsonTdTransport) }
    private val lock = Mutex()
    private var session: TgSession? = null
    private var sessionProfile: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Device tests sign in on Telegram's test servers (no real account, no SMS). */
    @Volatile internal var useTestDc = false
    val server: LocalStreamServer get() = LocalStreamServer.shared

    /**
     * Shown at all: flag on and an engine published for this ABI. OWN-3: keys are no longer needed to show it; without
     * the build's keys the screen asks for the user's own (TgKeys).
     */
    fun enabled(context: Context): Boolean =
        FeatureFlags.on(context, FeatureFlags.Flag.TELEGRAM_LOGIN) && TdEngine.available()

    /** TG-PAGE-1: why sign-in can't be offered on this phone, or null when it can. */
    fun unavailableReason(context: Context): String? = when {
        !FeatureFlags.on(context, FeatureFlags.Flag.TELEGRAM_LOGIN) -> tr(R.string.telegram_sign_in_is_turned_off, "Telegram sign-in is turned off in this build.")
        !TdEngine.available() -> tr(R.string.telegram_sign_in_needs_telegrams_own, "Telegram sign-in needs Telegram's own library (TDLib), and there is no copy for this phone's processor (") +
            Build.SUPPORTED_ABIS.firstOrNull().orEmpty() + tr(R.string.public_channels_still_work, "). Public channels still work.")
        else -> null
    }

    private const val HINT_PREFS = "telegram_account_hint"
    /** TG-PAGE-1: last state TDLib reported for [Profiles.currentId]: signed in or not. A hint for the page only. */
    fun signedInHint(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(HINT_PREFS, Context.MODE_PRIVATE).getBoolean(safe(Profiles.currentId(context.applicationContext)), false)
    private fun setSignedInHint(context: Context, profile: String, on: Boolean) {
        context.getSharedPreferences(HINT_PREFS, Context.MODE_PRIVATE).edit().putBoolean(safe(profile), on).apply()
        if (!on) context.getSharedPreferences(ME_PREFS, Context.MODE_PRIVATE).edit().remove(safe(profile) + ".name").remove(safe(profile) + ".photo").apply()
    }

    /** TG-ME-1: the signed-in user's name and local photo for the open profile, as last read from TDLib. */
    data class MeHint(val name: String, val photoPath: String?)
    private const val ME_PREFS = "telegram_me_hint"
    /** Read off the main thread (SharedPreferences). Null until the account page has read the user once. */
    fun meHint(context: Context): MeHint? {
        val app = context.applicationContext
        val key = safe(Profiles.currentId(app))
        val prefs = app.getSharedPreferences(ME_PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString("$key.name", null)?.takeIf { it.isNotBlank() } ?: return null
        return MeHint(name, prefs.getString("$key.photo", null)?.takeIf { File(it).isFile })
    }
    fun rememberMe(context: Context, name: String, photoPath: String?) {
        val app = context.applicationContext
        val key = safe(Profiles.currentId(app))
        app.getSharedPreferences(ME_PREFS, Context.MODE_PRIVATE).edit().putString("$key.name", name).putString("$key.photo", photoPath).apply()
    }

    /** OWN-3: sign-in needs keys first (no build keys and none typed). */
    fun needsKeys(context: Context): Boolean = TgKeys.effective(context) == null

    /** OWN-3: new keys: close TDLib and drop every profile's local Telegram data (it belongs to the old keys). */
    suspend fun keysChanged(context: Context) = withContext(Dispatchers.IO) {
        closeAll()
        val app = context.applicationContext
        File(app.noBackupFilesDir, "tg").deleteRecursively()
        File(app.cacheDir, "tg").deleteRecursively()
    }

    /** The open profile's session, starting TDLib (and downloading it the first time). Null when it can't start. */
    suspend fun session(context: Context): TgSession? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!TdEngine.ensure(app, NetworkClient.get(app))) return@withContext null
        lock.withLock {
            val profile = Profiles.currentId(app)
            session?.let { if (sessionProfile == profile && !it.client.closed) return@withLock it }
            session?.close()
            session = null
            val keys = TgKeys.effective(app) ?: return@withLock null
            val key = databaseKey(app, profile) ?: return@withLock null
            val root = File(app.noBackupFilesDir, "tg/" + safe(profile) + if (useTestDc) "-test" else "")
            val params = TgParams(
                apiId = keys.apiId, apiHash = keys.apiHash,
                databaseDir = File(root, "db").absolutePath,
                filesDir = File(app.cacheDir, "tg/" + safe(profile) + if (useTestDc) "-test" else "").absolutePath,
                encryptionKey = key, useTestDc = useTestDc,
                language = java.util.Locale.getDefault().language,
                deviceModel = (Build.MANUFACTURER + " " + Build.MODEL).trim(),
                systemVersion = "Android " + Build.VERSION.RELEASE,
                appVersion = "Yumeiro " + BuildConfig.VERSION_NAME,
            )
            // Errors only in TDLib's own log (logcat); never message text or numbers.
            runCatching { hub.execute(buildJsonObject { put("@type", "setLogVerbosityLevel"); put("new_verbosity_level", 1) }) }
            TgSession(hub.newClient(), params, scope).also { s ->
                session = s; sessionProfile = profile
                // TG-PAGE-1: remember whether this profile is signed in, so the Telegram page can say so without
                // starting TDLib (it downloads and loads a native library). Only TDLib's own state sets it.
                scope.launch {
                    s.state.collect { st ->
                        when (st) {
                            TgAuthState.Ready -> setSignedInHint(app, profile, true)
                            TgAuthState.WaitPhone, TgAuthState.LoggingOut -> setSignedInHint(app, profile, false)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    /** Closes TDLib for the open session (profile switch, low memory); the account stays signed in on disk. */
    suspend fun closeAll() = lock.withLock { session?.close(); session = null; sessionProfile = null }

    /**
     * 32 random bytes per profile, stored sealed. If the sealed key can't be opened (restored to a new phone, Keystore
     * reset), the old database can't be read either: it is deleted and the user signs in again.
     */
    private fun databaseKey(context: Context, profile: String): ByteArray? {
        val prefs = context.getSharedPreferences(Profiles.prefsName(context, "telegram_account"), Context.MODE_PRIVATE)
        val name = if (useTestDc) "db_key_test" else "db_key"
        prefs.getString(name, null)?.let { sealed ->
            SecretBox.open(sealed)?.let { b64 -> return java.util.Base64.getDecoder().decode(b64) }
            File(context.noBackupFilesDir, "tg/" + safe(profile) + if (useTestDc) "-test" else "").deleteRecursively()
        }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val sealed = SecretBox.seal(java.util.Base64.getEncoder().encodeToString(key)) ?: return null
        prefs.edit().putString(name, sealed).commit()
        return key
    }

    private fun safe(id: String) = id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifEmpty { "default" }

    /** Plays [video] through the local server; returns the URL to hand to the player. */
    fun streamUrl(session: TgSession, video: TgVideo): String = synchronized(published) {
        val src = TdFileSource(session.client, video.fileId, video.size, video.mimeType)
        val url = server.publish(src)
        published.addLast(url to src)
        // Keep the last few (player + its next item); older ones stop downloading and disappear.
        while (published.size > 3) { val (u, s) = published.removeFirst(); server.unpublish(u); s.close() }
        url
    }

    private val published = ArrayDeque<Pair<String, TdFileSource>>()
    private val downloads = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * A link for Yumeiro's downloader (owner add 2026-11-21: Telegram downloads). It stays published for the app's
     * life, apart from the play links, so playing other videos never cuts a download. After an app restart the old
     * link is gone and the download asks to be started again from the chat.
     */
    fun downloadUrl(session: TgSession, video: TgVideo): String {
        val key = "${session.client.id}:${video.fileId}"
        return downloads.getOrPut(key) { server.publish(TdFileSource(session.client, video.fileId, video.size, video.mimeType)) } +
            "/" + fileName(video)
    }

    /** "name.ext" for a download link, safe in a URL path; the extension decides the saved file's type. */
    internal fun fileName(video: TgVideo): String {
        val ext = video.name.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) }
            ?: extFor(video.mimeType)
        val base = video.name.substringBeforeLast('.').filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(60).ifEmpty { "telegram" }
        return "$base.$ext"
    }

    internal fun extFor(mime: String): String = when (mime.lowercase()) {
        "video/x-matroska" -> "mkv"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "audio/mpeg" -> "mp3"
        "audio/mp4", "audio/x-m4a", "audio/aac" -> "m4a"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/ogg", "audio/opus" -> "ogg"
        "audio/wav", "audio/x-wav" -> "wav"
        else -> "mp4"
    }
}
