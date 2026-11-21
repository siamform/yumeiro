/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

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
    val server: LocalStreamServer by lazy { LocalStreamServer() }

    /** Shown at all: keys in this build, flag on, an engine published for this ABI. */
    fun enabled(context: Context): Boolean =
        BuildConfig.TG_API_ID.toIntOrNull() != null && BuildConfig.TG_API_HASH.isNotBlank() &&
            FeatureFlags.on(context, FeatureFlags.Flag.TELEGRAM_LOGIN) && TdEngine.available()

    /** The open profile's session, starting TDLib (and downloading it the first time). Null when it can't start. */
    suspend fun session(context: Context): TgSession? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!TdEngine.ensure(app, NetworkClient.get(app))) return@withContext null
        lock.withLock {
            val profile = Profiles.currentId(app)
            session?.let { if (sessionProfile == profile && !it.client.closed) return@withLock it }
            session?.close()
            session = null
            val key = databaseKey(app, profile) ?: return@withLock null
            val root = File(app.noBackupFilesDir, "tg/" + safe(profile) + if (useTestDc) "-test" else "")
            val params = TgParams(
                apiId = BuildConfig.TG_API_ID.toInt(), apiHash = BuildConfig.TG_API_HASH,
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
            TgSession(hub.newClient(), params, scope).also { session = it; sessionProfile = profile }
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
}
