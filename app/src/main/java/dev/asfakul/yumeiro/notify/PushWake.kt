/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import java.util.concurrent.TimeUnit

/**
 * Pure scheduling logic for the push wake-ups (unit tested).
 *
 * There's no Yumeiro server, and there won't be one. The app asks the ntfy server behind its own
 * UnifiedPush address to deliver "wake" messages later (ntfy's free scheduled delivery, 10 s to
 * 3 days). A message every [STEP_MS] for the next [HORIZON_MS], plus one [BACKSTOP_MS] out in
 * case the phone is off for a day, keeps the chain alive. Each wake-up tops it up again.
 * Duplicates do no harm: the checks they trigger are throttled.
 */
object PushPlan {
    const val STEP_MS = 3L * 3_600_000
    const val HORIZON_MS = 24L * 3_600_000
    const val BACKSTOP_MS = 71L * 3_600_000 // just inside ntfy's 3-day limit
    const val REFILL_GAP_MS = 45L * 60_000
    const val MAX_PENDING = 16

    /** Where to publish: the endpoint without its query (`?up=1` is only for the distributor), https only. */
    fun publishUrl(endpoint: String?): String? {
        val u = endpoint?.trim()?.toHttpUrlOrNull() ?: return null
        if (!u.isHttps || u.pathSegments.none { it.isNotBlank() }) return null
        return u.newBuilder().query(null).fragment(null).build().toString()
    }

    /**
     * New delivery times so no gap within the horizon is longer than [STEP_MS], given what's
     * already pending. Returns absolute times (ms), ascending.
     */
    fun plan(now: Long, pending: Collection<Long>): List<Long> {
        val future = pending.filter { it > now }.sorted()
        val out = ArrayList<Long>()
        var cursor = now
        while (cursor + STEP_MS <= now + HORIZON_MS) {
            val covered = future.lastOrNull { it > cursor && it <= cursor + STEP_MS }
            cursor = covered ?: (cursor + STEP_MS).also { out += it }
        }
        if (future.none { it >= now + HORIZON_MS * 2 } && out.none { it >= now + HORIZON_MS * 2 }) out += now + BACKSTOP_MS
        return out.take((MAX_PENDING - future.size).coerceAtLeast(0))
    }

    /** Pending times still in the future (what's worth remembering). */
    fun prune(now: Long, pending: Collection<Long>): List<Long> = pending.filter { it > now }.sorted().takeLast(MAX_PENDING)

    fun refillDue(now: Long, lastRefill: Long): Boolean = now - lastRefill >= REFILL_GAP_MS || now < lastRefill

    /** ntfy echoes the message with its delivery `time` (seconds). Scheduled = time well past now. */
    fun acceptedAsScheduled(responseBody: String, nowMs: Long, delayMs: Long): Boolean {
        val t = runCatching { Json.parseToJsonElement(responseBody).jsonObject["time"]?.jsonPrimitive?.longOrNull }.getOrNull() ?: return false
        return t * 1000 >= nowMs + delayMs / 2
    }
}

/**
 * Optional UnifiedPush: alerts arrive on time even when Android holds back WorkManager.
 * Off by default; WorkManager stays the baseline either way. Free: ntfy (Apache-2.0/GPL) and its
 * public server, no account. Messages carry no data, and their text is ignored.
 */
object PushWake {
    private const val TAG = "PushWake"
    private const val PREFS = "push_wake"
    private const val SECRET = "push_endpoint"
    const val NTFY_PACKAGE = "io.heckel.ntfy"
    private const val MESSAGE_FOR_DISTRIBUTOR = "Yumeiro: wakes the new-episode and news checks"

    enum class Schedule { UNKNOWN, YES, NO }

    data class State(
        val enabled: Boolean = false,
        val distributor: String? = null,
        val hasEndpoint: Boolean = false,
        val endpointHost: String? = null,
        val schedule: Schedule = Schedule.UNKNOWN,
        val lastWakeAt: Long = 0L,
        val pending: Int = 0,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    @Volatile private var loaded = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(c: Context) {
        if (loaded) return
        OnlineSettings.init(c.applicationContext)
        reload(c)
        loaded = true
    }

    private fun reload(c: Context) {
        val p = prefs(c)
        val ep = OnlineSettings.secret(SECRET)
        _state.value = State(
            enabled = p.getBoolean("enabled", false),
            distributor = runCatching { UnifiedPush.getAckDistributor(c) }.getOrNull(),
            hasEndpoint = !ep.isNullOrBlank(),
            endpointHost = ep?.toHttpUrlOrNull()?.host,
            schedule = runCatching { Schedule.valueOf(p.getString("schedule", null) ?: "UNKNOWN") }.getOrDefault(Schedule.UNKNOWN),
            lastWakeAt = p.getLong("last_wake_at", 0L),
            pending = PushPlan.prune(System.currentTimeMillis(), pending(c)).size,
            error = p.getString("error", null),
        )
    }

    fun distributors(c: Context): List<String> = runCatching { UnifiedPush.getDistributors(c) }.getOrDefault(emptyList())

    fun appLabel(c: Context, pkg: String): String = runCatching {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Turns push on with [distributor] (a package from [distributors]). */
    fun enable(c: Context, distributor: String) {
        init(c)
        prefs(c).edit().putBoolean("enabled", true).remove("error").putString("schedule", Schedule.UNKNOWN.name).apply()
        runCatching {
            UnifiedPush.saveDistributor(c, distributor)
            UnifiedPush.register(c, messageForDistributor = MESSAGE_FOR_DISTRIBUTOR)
        }.onFailure { setError(c, "Couldn't reach ${appLabel(c, distributor)}: ${it.message}") }
        reload(c)
    }

    fun disable(c: Context) {
        init(c)
        runCatching { UnifiedPush.unregister(c) }
        runCatching { UnifiedPush.removeDistributor(c) }
        OnlineSettings.setSecret(c, SECRET, null)
        prefs(c).edit().clear().apply()
        reload(c)
    }

    // region callbacks from PushWakeService

    internal fun onNewEndpoint(c: Context, url: String) {
        init(c)
        val old = OnlineSettings.secret(SECRET)
        OnlineSettings.setSecret(c, SECRET, url)
        // A new address means the old scheduled messages go nowhere: start over.
        if (old != url) prefs(c).edit().remove("pending").putLong("refill_at", 0L).putString("schedule", Schedule.UNKNOWN.name).apply()
        prefs(c).edit().remove("error").apply()
        reload(c)
        PushWakeWorker.enqueue(c, refill = true, wake = false)
    }

    internal fun onMessage(c: Context) {
        init(c)
        val now = System.currentTimeMillis()
        prefs(c).edit().putLong("last_wake_at", now).putStringSet("pending", PushPlan.prune(now, pending(c)).map { it.toString() }.toSet()).apply()
        reload(c)
        PushWakeWorker.enqueue(c, refill = true, wake = true)
    }

    internal fun onFailed(c: Context, reason: FailedReason) {
        setError(c, when (reason) {
            FailedReason.NETWORK -> "The push app couldn't reach its server. It will retry when you're online"
            FailedReason.ACTION_REQUIRED -> "Open the push app once and finish its setup, then try again"
            else -> "The push app refused the registration ($reason)"
        })
    }

    internal fun onUnregistered(c: Context) {
        OnlineSettings.setSecret(c, SECRET, null)
        prefs(c).edit().putBoolean("enabled", false).remove("pending").apply()
        reload(c)
    }

    // endregion

    private fun setError(c: Context, msg: String) { prefs(c).edit().putString("error", msg).apply(); reload(c) }

    private fun pending(c: Context): List<Long> = prefs(c).getStringSet("pending", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }

    @Volatile private var registeredThisRun = false

    /** App came to the foreground: refresh the registration once per process (UnifiedPush advice), then top up. */
    fun onForeground(c: Context) {
        init(c)
        if (!_state.value.enabled) return
        if (!registeredThisRun && runCatching { UnifiedPush.getAckDistributor(c) }.getOrNull() != null) {
            registeredThisRun = runCatching { UnifiedPush.register(c, messageForDistributor = MESSAGE_FOR_DISTRIBUTOR) }.isSuccess
        }
        refillIfDue(c)
    }

    /** The app came to the foreground or a check ran: top up the chain if it's been a while. */
    fun refillIfDue(c: Context) {
        init(c)
        if (!_state.value.enabled || !_state.value.hasEndpoint) return
        if (PushPlan.refillDue(System.currentTimeMillis(), prefs(c).getLong("refill_at", 0L))) PushWakeWorker.enqueue(c, refill = true, wake = false)
    }

    private val client by lazy {
        NetworkClient.get().newBuilder().apply { interceptors().clear(); networkInterceptors().clear() }
            .followRedirects(false).callTimeout(20, TimeUnit.SECONDS).build()
    }

    private fun publish(url: String, delayMs: Long): Pair<Int, String> {
        val b = Request.Builder().url(url)
            .header("Firebase", "no")
            .post("wake".toRequestBody("text/plain".toMediaType()))
        if (delayMs > 0) b.header("In", "${delayMs / 1000}s")
        return client.newCall(b.build()).execute().use { it.code to it.body.string().take(4_000) }
    }

    /** Publishes the missing wake-ups. Blocking. Returns false on a network error (retry later). */
    internal fun refill(c: Context): Boolean {
        init(c)
        val url = PushPlan.publishUrl(OnlineSettings.secret(SECRET)) ?: return true
        val p = prefs(c)
        if (p.getString("schedule", null) == Schedule.NO.name) return true
        val now = System.currentTimeMillis()
        val pending = PushPlan.prune(now, pending(c)).toMutableList()
        for (at in PushPlan.plan(now, pending)) {
            val delay = at - now
            val (code, body) = runCatching { publish(url, delay) }.getOrElse { Log.w(TAG, "publish", it); return false }
            if (code !in 200..299 || !PushPlan.acceptedAsScheduled(body, now, delay)) {
                // Not an ntfy server (or scheduling is off there): delivery would be instant, so stop.
                Log.w(TAG, "scheduling not accepted (HTTP $code)")
                if (code == 429) p.edit().putString("error", "The push server's daily limit was reached. Wake-ups resume tomorrow").apply()
                else p.edit().putString("schedule", Schedule.NO.name).apply()
                break
            }
            pending += at
            p.edit().putString("schedule", Schedule.YES.name).apply()
        }
        p.edit().putStringSet("pending", PushPlan.prune(now, pending).map { it.toString() }.toSet()).putLong("refill_at", now).apply()
        reload(c)
        return true
    }

    /** Sends one wake-up right now through the push server, to prove the whole path works. */
    suspend fun sendTest(c: Context): String? = withContext(Dispatchers.IO) {
        init(c)
        val url = PushPlan.publishUrl(OnlineSettings.secret(SECRET)) ?: return@withContext "No push address yet. Open the push app and wait a moment"
        runCatching { publish(url, 0) }.fold(
            onSuccess = { (code, _) -> if (code in 200..299) null else "The push server answered HTTP $code" },
            onFailure = { "Couldn't reach ${url.toHttpUrlOrNull()?.host ?: "the push server"}: ${it.message}" },
        )
    }

    /** Low-key notification for the few seconds a wake-up runs on Android 9-11 (expedited work). */
    internal fun foregroundInfo(c: Context): ForegroundInfo {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm?.getNotificationChannel(SYNC_CHANNEL) == null) runCatching {
            nm?.createNotificationChannel(NotificationChannel(SYNC_CHANNEL, "Background checks", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown briefly while Yumeiro checks for new episodes"; setShowBadge(false)
            })
        }
        val n = NotificationCompat.Builder(c, SYNC_CHANNEL).setSmallIcon(R.drawable.ic_stat_yumeiro)
            .setContentTitle("Checking for new episodes").setPriority(NotificationCompat.PRIORITY_MIN).setOngoing(true).setSilent(true).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(SYNC_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(SYNC_ID, n)
    }

    private const val SYNC_CHANNEL = "yumeiro_sync"
    private const val SYNC_ID = 0x7140
}

/** Receives UnifiedPush events (bound by the connector library from inside this app only). */
class PushWakeService : PushService() {
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) = PushWake.onNewEndpoint(applicationContext, endpoint.url)
    override fun onMessage(message: PushMessage, instance: String) = PushWake.onMessage(applicationContext) // content ignored by design
    override fun onRegistrationFailed(reason: FailedReason, instance: String) = PushWake.onFailed(applicationContext, reason)
    override fun onUnregistered(instance: String) = PushWake.onUnregistered(applicationContext)
}

/**
 * Runs after a push: tops up the scheduled wake-ups, then (for a real wake-up) starts the usual
 * feed and new-episode checks right away as expedited work.
 */
class PushWakeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        private const val WORK = "push_wake"
        fun enqueue(c: Context, refill: Boolean, wake: Boolean) {
            val wm = runCatching { WorkManager.getInstance(c.applicationContext) }.getOrNull() ?: return
            val k = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            runCatching {
                if (wake) BackgroundWork.wake(c)
                if (refill) wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<PushWakeWorker>().setConstraints(k).setInputData(workDataOf("refill" to true)).build())
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = PushWake.foregroundInfo(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!PushWake.refill(applicationContext) && runAttemptCount < 3) Result.retry() else Result.success()
    }
}
