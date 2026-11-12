/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Instant notices over Firebase Cloud Messaging: announcements, app updates, maintenance alerts and
 * messages for one device, sent by the Yumeiro Cloud Worker (server/, `POST /v1/admin/notify`).
 *
 * - Only `firebase-messaging`: no Analytics, Auth or Firestore. The app is initialised by hand from
 *   BuildConfig; a build without FCM config (forks, F-Droid style builds) simply has push off.
 * - FID registration (firebase-messaging 25.1+): [FirebaseMessaging.register] + `onRegistered`; the
 *   deprecated token APIs are not used.
 * - Broadcasts go to topics the app subscribes to from the user's notification switches, so a
 *   switched-off kind is never even delivered. Direct messages need the Worker's device row (FID).
 * - Every message is data-only; the app builds the notification itself through [AppNotifier], so the
 *   inbox, channels, chime, the user's switches and the deep link all behave like other notices.
 */
object FcmPush {
    private const val TAG = "FcmPush"
    private const val PREFS = "fcm_push"
    private const val SECRET = "fcm_device" // "<id>.<token>" from the Worker, Keystore-sealed

    enum class Status(val label: String) {
        OFF("Off"),
        UNAVAILABLE("Not available in this build"),
        NO_PLAY_SERVICES("Needs Google Play services"),
        CONNECTING("Connecting…"),
        ON("On"),
        ERROR("Couldn't connect — will retry"),
    }

    private val _status = MutableStateFlow(Status.OFF)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun configured(): Boolean = FcmPlan.configured(BuildConfig.FCM_PROJECT_ID, BuildConfig.FCM_APP_ID, BuildConfig.FCM_API_KEY, BuildConfig.FCM_SENDER_ID)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Creates the default FirebaseApp from BuildConfig. Cheap, no network. Null when push isn't configured. */
    @Synchronized
    fun app(c: Context): FirebaseApp? {
        if (!configured()) return null
        FirebaseApp.getApps(c).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }?.let { return it }
        return runCatching {
            val o = FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FCM_PROJECT_ID)
                .setApplicationId(BuildConfig.FCM_APP_ID)
                .setApiKey(BuildConfig.FCM_API_KEY)
                .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
                .build()
            FirebaseApp.initializeApp(c.applicationContext, o)
        }.onFailure { Log.w(TAG, "init: ${it.javaClass.simpleName}") }.getOrNull()
    }

    /** App start: set up Firebase (so incoming messages can be handled) and schedule a sync. */
    fun start(c: Context) {
        if (!configured()) { _status.value = Status.UNAVAILABLE; return }
        NotifySettings.init(c)
        if (!NotifySettings.get(c, NotifySettings.Key.FCM)) { _status.value = Status.OFF; return }
        if (app(c) == null) { _status.value = Status.ERROR; return }
        if (_status.value == Status.OFF || _status.value == Status.UNAVAILABLE) {
            _status.value = if (prefs(c).getString("fid", null) != null) Status.ON else Status.CONNECTING
        }
        FcmSyncWorker.enqueue(c)
    }

    /** A notification switch changed: topics (or the whole registration) follow it. */
    /**
     * The Worker's id for this phone (never the secret half), so a user can share it with support and the
     * owner can send a message to just this device. Null until the first check-in succeeds.
     */
    fun supportId(): String? = OnlineSettings.secret(SECRET)?.substringBefore('.')?.takeIf { it.isNotBlank() }

    fun onSettingsChanged(c: Context) { if (configured()) FcmSyncWorker.enqueue(c) }

    internal fun onRegistered(c: Context, fid: String) {
        prefs(c).edit().putString("fid", fid).apply()
        FcmSyncWorker.enqueue(c)
    }

    internal fun onUnregistered(c: Context) {
        prefs(c).edit().remove("fid").remove("fp").remove("topics").apply()
        FcmSyncWorker.enqueue(c)
    }

    /** What the user's switches ask for right now. */
    fun wantedTopics(c: Context): Set<String> {
        NotifySettings.init(c)
        return FcmPlan.topics(
            channel = BuildConfig.PUSH_CHANNEL,
            announcements = NotifySettings.get(c, NotifySettings.Key.NEWS),
            service = NotifySettings.get(c, NotifySettings.Key.SERVICE),
            updates = true, // "a new version is out" is never switched off; preview builds get their own topic
        )
    }

    /** Blocking; run from [FcmSyncWorker]. Returns false for a retryable failure. */
    fun sync(c: Context): Boolean {
        if (!configured()) { _status.value = Status.UNAVAILABLE; return true }
        NotifySettings.init(c)
        OnlineSettings.init(c)
        val on = NotifySettings.get(c, NotifySettings.Key.FCM)
        if (!on) { turnOff(c); return true }
        if (!playServices(c)) { _status.value = Status.NO_PLAY_SERVICES; return true }
        app(c) ?: run { _status.value = Status.ERROR; return false }
        val p = prefs(c)
        val fm = FirebaseMessaging.getInstance()
        fm.isAutoInitEnabled = true
        val fid = runCatching {
            await(fm.register())
            await(FirebaseInstallations.getInstance().id)
        }.onFailure { Log.w(TAG, "register: ${it.javaClass.simpleName}") }.getOrNull()
        if (fid.isNullOrBlank()) { _status.value = Status.ERROR; return false }
        p.edit().putString("fid", fid).apply()

        // Topic diff: only changed subscriptions touch the network.
        val wanted = wantedTopics(c)
        val had = p.getStringSet("topics", emptySet()).orEmpty()
        val (add, drop) = FcmPlan.diff(had, wanted)
        var topicsOk = true
        val now = had.toMutableSet()
        for (t in add) runCatching { await(fm.subscribeToTopic(t)); now += t }.onFailure { topicsOk = false; Log.w(TAG, "subscribe $t") }
        for (t in drop) runCatching { await(fm.unsubscribeFromTopic(t)); now -= t }.onFailure { topicsOk = false; Log.w(TAG, "unsubscribe $t") }
        p.edit().putStringSet("topics", now).apply()

        val cloudOk = registerWithCloud(c, fid, now)
        _status.value = if (topicsOk && cloudOk) Status.ON else Status.ERROR
        return topicsOk && cloudOk
    }

    /** Tells the Worker this device's FID (for messages to one device) and its topics (counts only). */
    private fun registerWithCloud(c: Context, fid: String, topics: Set<String>): Boolean {
        val host = BuildConfig.CLOUD_HOST
        if (host.isBlank()) return true
        val p = prefs(c)
        val nowMs = System.currentTimeMillis()
        val creds = OnlineSettings.secret(SECRET)
        val fp = FcmPlan.fingerprint(fid, topics, BuildConfig.VERSION_CODE)
        if (creds != null && !FcmPlan.due(fp, p.getString("fp", null), nowMs, p.getLong("synced_at", 0L))) return true
        if (nowMs < p.getLong("retry_at", 0L)) return true // backing off; topics still work meanwhile
        val body = JSONObject().put("fid", fid).put("topics", JSONArray(topics.sorted()))
            .put("app_version", BuildConfig.VERSION_CODE).put("channel", BuildConfig.PUSH_CHANNEL).toString()
        val req = Request.Builder().url("https://$host/v1/fcm/devices")
            .post(body.toRequestBody("application/json".toMediaType()))
            .apply { if (creds != null) header("Authorization", "Bearer $creds") }
            .build()
        fun failed(): Boolean {
            val n = p.getInt("failures", 0) + 1
            p.edit().putInt("failures", n).putLong("retry_at", nowMs + CloudAlertsPlan.retryDelay(n)).apply()
            return false
        }
        return runCatching {
            val client = NetworkClient.get(c).newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
            client.newCall(req).execute().use { r ->
                if (r.code == 401 || r.code == 403) { OnlineSettings.setSecret(c, SECRET, null); p.edit().remove("fp").apply() }
                if (!r.isSuccessful) { Log.w(TAG, "cloud HTTP ${r.code}"); return failed() }
                val j = JSONObject(r.body.string())
                val id = j.optString("id")
                val token = j.optString("token").takeIf { it.isNotBlank() && it != "null" }
                if (token != null && id.isNotBlank()) OnlineSettings.setSecret(c, SECRET, "$id.$token")
                p.edit().putString("fp", fp).putLong("synced_at", nowMs).remove("failures").remove("retry_at").apply()
                true
            }
        }.getOrElse { Log.w(TAG, "cloud: ${it.javaClass.simpleName}"); failed() }
    }

    /** The user switched instant alerts off: leave all topics, drop the FID and the Worker's row. */
    private fun turnOff(c: Context) {
        val p = prefs(c)
        if (app(c) != null && (p.getString("fid", null) != null || p.getStringSet("topics", null)?.isNotEmpty() == true)) {
            runCatching {
                val fm = FirebaseMessaging.getInstance()
                p.getStringSet("topics", emptySet()).orEmpty().forEach { runCatching { await(fm.unsubscribeFromTopic(it)) } }
                fm.isAutoInitEnabled = false
                await(fm.unregister())
            }.onFailure { Log.w(TAG, "unregister: ${it.javaClass.simpleName}") }
        }
        val creds = OnlineSettings.secret(SECRET)
        if (creds != null && BuildConfig.CLOUD_HOST.isNotBlank()) runCatching {
            val req = Request.Builder().url("https://${BuildConfig.CLOUD_HOST}/v1/fcm/devices").delete()
                .header("Authorization", "Bearer $creds").build()
            NetworkClient.get(c).newBuilder().callTimeout(15, TimeUnit.SECONDS).build().newCall(req).execute().close()
        } // if this fails the Worker prunes the silent row after 45 days
        OnlineSettings.setSecret(c, SECRET, null)
        p.edit().clear().apply()
        _status.value = Status.OFF
    }

    private fun playServices(c: Context): Boolean = runCatching {
        com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(c) ==
            com.google.android.gms.common.ConnectionResult.SUCCESS
    }.getOrDefault(false)

    private fun <T> await(t: Task<T>): T = Tasks.await(t, 30, TimeUnit.SECONDS)

    /** Turns a received message into an inbox item + notification. Called by the messaging service. */
    internal fun handle(c: Context, data: Map<String, String>, fallbackTitle: String?, fallbackBody: String?, sentMs: Long) {
        val m = FcmPlan.parse(data, fallbackTitle, fallbackBody, sentMs) ?: run { Log.i(TAG, "ignored malformed message"); return }
        val nowS = System.currentTimeMillis() / 1000
        if (!FcmPlan.forThisApp(m, BuildConfig.VERSION_CODE, nowS)) { Log.i(TAG, "not for this version / expired: ${m.id}"); return }
        AppNotifier.post(c, Inbox.Item("fcm-${m.id}", m.kind, m.title, m.body, m.atMs, link = m.link),
            notificationId = ("fcm-" + m.id).hashCode())
    }
}

/** Pure, unit-tested parts of [FcmPush]. */
object FcmPlan {
    const val TOPIC_ANNOUNCEMENTS = "announcements"
    const val TOPIC_UPDATES = "updates"
    const val TOPIC_MAINTENANCE = "maintenance"
    const val MAX_TITLE = 80
    const val MAX_BODY = 600
    /**
     * Weekly check-in (the Worker prunes rows silent for 45 days). At 200k phones that's ~29k Worker
     * requests and D1 writes a day, leaving most of the free 100k/day for airing alerts. Topics need
     * no check-in at all, so broadcasts never depend on this.
     */
    const val REFRESH_MS = 7L * 24 * 3600 * 1000

    fun due(fp: String, oldFp: String?, now: Long, lastSync: Long): Boolean =
        fp != oldFp || now - lastSync >= REFRESH_MS || now < lastSync

    /** In-app actions a notice may open (same set the inbox understands), besides https links. */
    val ACTIONS = setOf("update", "extensions", "market", "streams", "support", "downloads", "inbox")

    fun configured(projectId: String, appId: String, apiKey: String, senderId: String): Boolean =
        projectId.isNotBlank() && apiKey.isNotBlank() && senderId.isNotBlank() && appId.count { it == ':' } >= 3

    /** Release builds use the plain topics; preview/debug get their own, so tests never reach users. */
    fun topic(channel: String, base: String): String = if (channel == "release" || channel.isBlank()) base else "$channel-$base"

    fun topics(channel: String, announcements: Boolean, service: Boolean, updates: Boolean): Set<String> = buildSet {
        if (announcements) add(topic(channel, TOPIC_ANNOUNCEMENTS))
        if (service) add(topic(channel, TOPIC_MAINTENANCE))
        if (updates) add(topic(channel, TOPIC_UPDATES))
    }

    fun diff(had: Set<String>, wanted: Set<String>): Pair<Set<String>, Set<String>> = (wanted - had) to (had - wanted)

    fun fingerprint(fid: String, topics: Set<String>, version: Int): String =
        MessageDigest.getInstance("SHA-256").digest("f1|$fid|${topics.sorted().joinToString(",")}|$version".toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }

    data class Message(
        val id: String, val kind: Inbox.Kind, val title: String, val body: String, val link: String?,
        val atMs: Long, val minVersion: Int?, val maxVersion: Int?, val expiresS: Long?,
    )

    private val ID = Regex("^[A-Za-z0-9_-]{1,64}$")

    /** Only https links and known in-app actions; anything else is dropped (the notice still shows). */
    fun safeLink(raw: String?): String? {
        val l = raw?.trim().orEmpty()
        if (l.isEmpty() || l.length > 500) return null
        if (l in ACTIONS) return l
        val u = runCatching { java.net.URI(l) }.getOrNull() ?: return null
        return if (u.scheme.equals("https", true) && !u.host.isNullOrBlank() && u.userInfo == null) l else null
    }

    fun kindOf(k: String?): Inbox.Kind? = when (k?.lowercase()) {
        "announcement" -> Inbox.Kind.NEWS
        "update" -> Inbox.Kind.UPDATE
        "maintenance" -> Inbox.Kind.ALERT
        "direct" -> Inbox.Kind.ALERT
        else -> null
    }

    /**
     * Reads the Worker's data payload (`y=1`). Messages typed in the Firebase console (no `y`) still
     * work as announcements from their notification title/body.
     */
    fun parse(data: Map<String, String>, fallbackTitle: String?, fallbackBody: String?, sentMs: Long): Message? {
        val ours = data["y"] == "1"
        val kind = if (ours) kindOf(data["kind"]) ?: return null else Inbox.Kind.NEWS
        val title = (data["title"] ?: fallbackTitle).orEmpty().trim().take(MAX_TITLE)
        val body = (data["body"] ?: fallbackBody).orEmpty().trim().take(MAX_BODY)
        if (title.isEmpty()) return null
        val id = data["id"]?.takeIf { ID.matches(it) } ?: if (ours) return null else "console-${(title + body).hashCode().toUInt()}-${sentMs / 60_000}"
        val link = safeLink(data["link"]) ?: if (kind == Inbox.Kind.UPDATE) "update" else null
        val at = data["sent"]?.toLongOrNull()?.times(1000)?.takeIf { it > 0 } ?: sentMs.takeIf { it > 0 } ?: System.currentTimeMillis()
        return Message(id, kind, title, body, link, at, data["min_v"]?.toIntOrNull(), data["max_v"]?.toIntOrNull(), data["exp"]?.toLongOrNull())
    }

    fun forThisApp(m: Message, versionCode: Int, nowS: Long): Boolean =
        (m.minVersion == null || versionCode >= m.minVersion) &&
            (m.maxVersion == null || versionCode <= m.maxVersion) &&
            (m.expiresS == null || nowS < m.expiresS)
}

/** Receives FCM registrations and messages (bound by Google Play services, never by other apps). */
class YumeiroMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) = FcmPush.onRegistered(applicationContext, installationId)
    override fun onUnregistered(installationId: String) = FcmPush.onUnregistered(applicationContext)
    override fun onMessageReceived(message: RemoteMessage) {
        FcmPush.app(applicationContext) ?: return
        FcmPush.handle(applicationContext, message.data, message.notification?.title, message.notification?.body, message.sentTime)
    }
    override fun onDeletedMessages() { Log.i("FcmPush", "FCM dropped pending messages (device was offline too long)") }
}

/** Registration, topic changes and the Worker check-in, with network constraint and back-off. */
class FcmSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val WORK = "fcm_sync"
        fun enqueue(c: Context) {
            val wm = runCatching { WorkManager.getInstance(c.applicationContext) }.getOrNull() ?: return
            val k = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            runCatching {
                wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<FcmSyncWorker>().setConstraints(k)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ok = runCatching { FcmPush.sync(applicationContext) }.onFailure { Log.w("FcmPush", "sync", it) }.getOrDefault(false)
        if (ok || runAttemptCount >= 5) Result.success() else Result.retry()
    }
}
