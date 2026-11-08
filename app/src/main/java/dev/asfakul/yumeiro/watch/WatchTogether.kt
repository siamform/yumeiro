/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.watch

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.Player
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineSession
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Watch together on the same Wi-Fi, with no server: one phone hosts a tiny HTTP endpoint on the
 * local network (advertised with Android NSD / mDNS) and the others follow its title, episode,
 * play/pause and position. Each device streams from the source itself, so nothing is relayed.
 */
object WatchTogether {
    private const val TAG = "WatchTogether"
    const val SERVICE_TYPE = "_yumeiro-room._tcp."

    enum class Role { NONE, HOST, GUEST }

    data class RoomState(
        val api: String = "",
        val url: String = "",
        val version: String = "",
        val index: Int = -1,
        val title: String = "",
        val positionMs: Long = 0,
        val playing: Boolean = false,
        /** Host clock when [positionMs] was sampled. */
        val at: Long = 0,
        val seq: Long = 0,
    ) {
        fun toJson(): JSONObject = JSONObject().put("api", api).put("url", url).put("version", version).put("index", index)
            .put("title", title).put("positionMs", positionMs).put("playing", playing).put("at", at).put("seq", seq)

        /** Position "now" on the host clock, extrapolated while playing. */
        fun positionAt(hostNow: Long): Long = if (playing) positionMs + (hostNow - at).coerceAtLeast(0) else positionMs

        companion object {
            fun from(o: JSONObject) = RoomState(o.optString("api"), o.optString("url"), o.optString("version"), o.optInt("index", -1),
                o.optString("title"), o.optLong("positionMs"), o.optBoolean("playing"), o.optLong("at"), o.optLong("seq"))
        }
    }

    data class Status(
        val role: Role = Role.NONE,
        val roomName: String = "",
        val code: String = "",
        val address: String = "",
        val guests: Int = 0,
        val connected: Boolean = false,
        val message: String? = null,
        val state: RoomState = RoomState(),
    )

    data class Found(val name: String, val host: String, val port: Int)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()
    private val _found = MutableStateFlow<List<Found>>(emptyList())
    val found: StateFlow<List<Found>> = _found.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private var server: RoomServer? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var guestJob: Job? = null
    private var multicast: WifiManager.MulticastLock? = null
    @Volatile private var player: Player? = null
    @Volatile private var hostState = RoomState()
    private val seen = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // region host

    fun host(context: Context, name: String) {
        leave(context)
        val app = context.applicationContext
        val code = (1000 + SecureRandom().nextInt(9000)).toString()
        val srv = RoomServer(code).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true) }
        server = srv
        val ip = localIp() ?: "?"
        _status.value = Status(Role.HOST, name.ifBlank { "Yumeiro room" }, code, "$ip:${srv.listeningPort}", state = hostState)
        val info = NsdServiceInfo().apply { serviceName = name.ifBlank { "Yumeiro room" }; serviceType = SERVICE_TYPE; port = srv.listeningPort }
        val nsd = app.getSystemService(NsdManager::class.java)
        val reg = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { _status.value = _status.value.copy(roomName = info.serviceName) }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) { Log.w(TAG, "register $errorCode") }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registration = reg
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, reg) }.onFailure { Log.w(TAG, "nsd", it) }
        publishFromPlayer()
    }

    private fun publishFromPlayer() {
        if (_status.value.role != Role.HOST) return
        val s = OnlineSession.state.value
        val p = player
        val next = RoomState(
            api = s?.api.orEmpty(), url = s?.detail?.url.orEmpty(), version = s?.version.orEmpty(), index = s?.index ?: -1,
            title = s?.title.orEmpty(), positionMs = p?.currentPosition ?: 0, playing = p?.isPlaying == true,
            at = System.currentTimeMillis(), seq = hostState.seq + 1,
        )
        hostState = next
        _status.value = _status.value.copy(state = next, guests = seen.count { System.currentTimeMillis() - it.value < 5_000 })
    }

    private class RoomServer(val code: String) : NanoHTTPD(0) {
        override fun serve(session: IHTTPSession): Response {
            if (session.headers["x-room-code"] != code) return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "wrong code")
            return when (session.uri) {
                "/state" -> {
                    session.headers["x-guest"]?.let { seen[it.take(40)] = System.currentTimeMillis() }
                    val body = hostState.toJson().put("now", System.currentTimeMillis()).toString()
                    newFixedLengthResponse(Response.Status.OK, "application/json", body)
                }
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "")
            }
        }
    }

    // endregion

    // region guest

    fun discover(context: Context) {
        stopDiscovery(context)
        val app = context.applicationContext
        val nsd = app.getSystemService(NsdManager::class.java)
        _found.value = emptyList()
        multicast = (app.getSystemService(Context.WIFI_SERVICE) as WifiManager).createMulticastLock("yumeiro-room").apply { setReferenceCounted(false); acquire() }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Log.w(TAG, "discover $errorCode") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) { _found.value = _found.value.filterNot { it.name == info.serviceName } }
            override fun onServiceFound(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                runCatching {
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host?.hostAddress ?: return
                            _found.value = (_found.value.filterNot { it.name == info.serviceName } + Found(info.serviceName, host, info.port))
                        }
                    })
                }
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }.onFailure { Log.w(TAG, "discover", it) }
    }

    fun stopDiscovery(context: Context) {
        discovery?.let { d -> runCatching { context.applicationContext.getSystemService(NsdManager::class.java).stopServiceDiscovery(d) } }
        discovery = null
        runCatching { multicast?.release() }
        multicast = null
    }

    /** Joins `host:port` with the 4-digit code the host shows. */
    fun join(context: Context, address: String, code: String, name: String = address) {
        leave(context)
        val app = context.applicationContext
        val base = "http://" + address.removePrefix("http://").trim().removeSuffix("/")
        val guestId = java.util.UUID.randomUUID().toString()
        _status.value = Status(Role.GUEST, name, code, address, message = "Connecting…")
        val client = NetworkClient.get(app).newBuilder().callTimeout(4, TimeUnit.SECONDS).build()
        guestJob = scope.launch {
            var applied = RoomState()
            var failures = 0
            while (isActive) {
                val t0 = System.currentTimeMillis()
                val result = runCatching {
                    client.newCall(Request.Builder().url("$base/state").header("x-room-code", code.trim()).header("x-guest", guestId).build()).execute().use { r ->
                        if (r.code == 403) error("Wrong room code")
                        if (!r.isSuccessful) error("Room answered HTTP ${r.code}")
                        JSONObject(r.body.string())
                    }
                }
                val t1 = System.currentTimeMillis()
                result.onSuccess { o ->
                    failures = 0
                    val remote = RoomState.from(o)
                    // Host clock offset estimated from the round trip midpoint.
                    val offset = o.optLong("now") - (t0 + t1) / 2
                    _status.value = _status.value.copy(connected = true, message = null, state = remote)
                    applied = follow(app, remote, applied, offset)
                }.onFailure { e ->
                    failures++
                    _status.value = _status.value.copy(connected = false, message = e.message ?: "Can't reach the room")
                    if (e.message == "Wrong room code") { leave(app); return@launch }
                    if (failures > 30) { _status.value = _status.value.copy(message = "The room closed"); return@launch }
                }
                delay(1_000)
            }
        }
    }

    private suspend fun follow(context: Context, remote: RoomState, applied: RoomState, offset: Long): RoomState {
        if (remote.api.isBlank() || remote.url.isBlank()) return applied
        val cur = OnlineSession.state.value
        val sameTitle = cur != null && cur.api == remote.api && cur.detail.url == remote.url && cur.index == remote.index
        if (!sameTitle) {
            if (applied.api == remote.api && applied.url == remote.url && applied.index == remote.index) return applied // already opening
            val api = ExtensionManager.provider(remote.api)
            if (api == null) { _status.value = _status.value.copy(message = "Install \"${remote.api}\" to follow this room"); return applied }
            val detail = runCatching { OnlineRepository.load(api, remote.url) }.getOrElse {
                _status.value = _status.value.copy(message = "Couldn't open ${remote.title}"); return applied
            }
            val target = remote.positionAt(System.currentTimeMillis() + offset)
            withContext(Dispatchers.Main) { OnlineSession.play(context, detail, remote.version.ifBlank { detail.versions.firstOrNull().orEmpty() }, remote.index, target) }
            return remote
        }
        val p = player ?: return remote
        withContext(Dispatchers.Main) {
            val target = remote.positionAt(System.currentTimeMillis() + offset)
            if (abs(p.currentPosition - target) > 2_500) p.seekTo(target)
            if (p.playWhenReady != remote.playing) p.playWhenReady = remote.playing
        }
        return remote
    }

    // endregion

    /** Called by the video player while it is on screen. */
    fun bind(p: Player?) {
        player = p
        if (p != null && _status.value.role == Role.HOST) {
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { if (player === p) publishFromPlayer() }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) { if (player === p) publishFromPlayer() }
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { if (player === p) publishFromPlayer() }
            })
            tick()
        }
    }

    private var ticking = false
    private fun tick() {
        if (ticking) return
        ticking = true
        main.post(object : Runnable {
            override fun run() {
                if (_status.value.role != Role.HOST) { ticking = false; return }
                publishFromPlayer()
                main.postDelayed(this, 3_000)
            }
        })
    }

    fun leave(context: Context) {
        guestJob?.cancel(); guestJob = null
        server?.let { runCatching { it.stop() } }; server = null
        registration?.let { r -> runCatching { context.applicationContext.getSystemService(NsdManager::class.java).unregisterService(r) } }
        registration = null
        seen.clear()
        hostState = RoomState()
        _status.value = Status()
    }

    private fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()
}
