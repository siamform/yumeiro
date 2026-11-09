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
import dev.asfakul.yumeiro.cloud.CloudApi
import dev.asfakul.yumeiro.cloud.CloudCodes
import dev.asfakul.yumeiro.cloud.CloudCrypto
import dev.asfakul.yumeiro.cloud.CloudGate
import dev.asfakul.yumeiro.cloud.CloudKeys
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
        /** Online room through Yumeiro Cloud (end-to-end encrypted) instead of the local Wi-Fi. */
        val online: Boolean = false,
        /** Online: the invite link to share (holds the room key in its #fragment). */
        val invite: String = "",
        /** Online guests: whether the host is connected right now. */
        val hostHere: Boolean = true,
    )

    data class Chat(val from: String, val text: String, val mine: Boolean, val at: Long, val reaction: Boolean = false)

    data class Found(val name: String, val host: String, val port: Int)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()
    private val _found = MutableStateFlow<List<Found>>(emptyList())
    val found: StateFlow<List<Found>> = _found.asStateFlow()
    private val _chat = MutableStateFlow<List<Chat>>(emptyList())
    /** Online rooms only: chat lines and reactions, newest last (kept in memory, max 200). */
    val chat: StateFlow<List<Chat>> = _chat.asStateFlow()

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
        if (_status.value.online) {
            _status.value = _status.value.copy(state = next)
            if (RoomPlan.shouldSend(lastSentState, next, System.currentTimeMillis(), lastSentAt)) sendState(next)
            return
        }
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

    // region online (Yumeiro Cloud)

    private var socket: WebSocket? = null
    private var onlineRoom: CloudKeys.Room? = null
    private var onlineHostToken: String? = null
    private var onlineName: String = ""
    private var onlineJob: Job? = null
    private var reconnects = 0
    @Volatile private var serverOffset = 0L
    @Volatile private var lastSentAt = 0L
    @Volatile private var lastSentState: RoomState? = null
    private val incoming = MutableStateFlow<RoomState?>(null)

    /** Creates an online room. Anyone with the invite link can join, on any network. */
    fun hostOnline(context: Context, name: String) {
        leave(context)
        val app = context.applicationContext
        if (!CloudApi.enabled) { _status.value = Status(message = "Online rooms are off in this build"); return }
        if (!CloudGate.open()) { _status.value = Status(message = CloudGate.BUSY); return }
        _status.value = Status(Role.HOST, "Online room", online = true, message = "Creating the room…", state = hostState)
        onlineName = name.ifBlank { "Host" }.take(24)
        onlineJob = scope.launch {
            val created = runCatching {
                val req = Request.Builder().url(CloudApi.url("/v1/rooms")).post(ByteArray(0).toRequestBody(null)).build()
                CloudApi.client(app).newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw CloudApi.error(r.code, r.body.string())
                    JSONObject(r.body.string()).let { it.getString("id") to it.getString("host") }
                }
            }.getOrElse { e ->
                if (e is kotlinx.coroutines.CancellationException) return@launch
                if (e.message != "Too many rooms created from this network. Try again in a minute.") CloudGate.failed()
                _status.value = Status(message = if (e.message?.startsWith("Too many") == true) e.message else CloudGate.BUSY); return@launch
            }
            CloudGate.ok()
            if (!isActive) return@launch
            val secret = CloudCrypto.randomBytes(CloudKeys.ROOM_BYTES)
            onlineRoom = CloudKeys.Room(created.first, secret)
            onlineHostToken = created.second
            _status.value = _status.value.copy(code = created.first, invite = RoomPlan.inviteLink(created.first, secret), message = "Connecting…")
            connect(app)
            main.post { publishFromPlayer(); tick() } // the player may only be read on the main thread
        }
    }

    /** Joins from an invite link (https://…/r/ID#KEY, yumeiro://room/ID#KEY) or "ID KEY". False if it can't be read. */
    fun joinOnline(context: Context, invite: String, name: String): Boolean {
        val (id, secret) = RoomPlan.parseInvite(invite) ?: return false
        leave(context)
        val app = context.applicationContext
        if (!CloudApi.enabled) { _status.value = Status(message = "Online rooms are off in this build"); return true }
        if (!CloudGate.open()) { _status.value = Status(message = CloudGate.BUSY); return true }
        onlineRoom = CloudKeys.Room(id, secret)
        onlineName = name.ifBlank { "Guest" }.take(24)
        _status.value = Status(Role.GUEST, "Online room", code = id, online = true, invite = RoomPlan.inviteLink(id, secret), message = "Connecting…")
        onlineJob = scope.launch {
            launch {
                var applied = RoomState()
                incoming.collect { remote -> if (remote != null) applied = follow(app, remote, applied, serverOffset) }
            }
            connect(app)
        }
        return true
    }

    private fun connect(app: Context) {
        val room = onlineRoom ?: return
        val req = Request.Builder().url(CloudApi.ws("/v1/rooms/${room.id}/ws")).apply {
            onlineHostToken?.let { header("Authorization", "Bearer $it") }
        }.build()
        val client = CloudApi.client(app).newBuilder().callTimeout(0, TimeUnit.SECONDS).pingInterval(25, TimeUnit.SECONDS).build()
        val t0 = System.currentTimeMillis()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (socket !== webSocket) return
                runCatching { onFrame(webSocket, JSONObject(text), t0) }.onFailure { Log.w(TAG, "frame: ${it.javaClass.simpleName}") }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (socket === webSocket) dropped(app, if (reason == "closed" || reason == "expired") "The room has ended" else null)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket !== webSocket) return
                val code = response?.code
                val body = runCatching { response?.body?.string() }.getOrNull().orEmpty()
                when {
                    code == 404 -> dropped(app, "This room has ended or the link is wrong")
                    code == 429 && body.contains("room_full") -> dropped(app, "This room is full")
                    code == 403 -> dropped(app, "Couldn't rejoin as host")
                    // Server broken or Cloudflare's free quota used up: stop trying for a while. Playback is untouched.
                    code != null && (code == 429 || code >= 500) -> { CloudGate.failed(); dropped(app, CloudGate.BUSY) }
                    else -> dropped(app, null) // network blip: reconnect with backoff
                }
            }
        })
    }

    private fun onFrame(ws: WebSocket, o: JSONObject, t0: Long) {
        val room = onlineRoom ?: return
        when (o.optString("t")) {
            "hello" -> {
                reconnects = 0
                CloudGate.ok()
                serverOffset = o.optLong("now") - (t0 + System.currentTimeMillis()) / 2
                _status.value = _status.value.copy(connected = true, message = null, guests = (o.optInt("peers") - 1).coerceAtLeast(0), hostHere = o.optBoolean("host", true))
                if (_status.value.role == Role.HOST) { lastSentState = null; main.post { publishFromPlayer() } }
            }
            "state" -> if (_status.value.role == Role.GUEST) applyState(room, o.optString("c"))
            "peers" -> {
                _status.value = _status.value.copy(guests = (o.optInt("n") - 1).coerceAtLeast(0), hostHere = o.optBoolean("host", true))
                // The server keeps no state; a newcomer gets it from the host right away.
                if (_status.value.role == Role.HOST && o.has("joined")) { lastSentState = null; main.post { publishFromPlayer() } }
            }
            "msg" -> {
                val m = runCatching { JSONObject(CloudCrypto.openText(room.key, o.optString("c"), room.aad("msg"))) }.getOrNull() ?: return
                addChat(Chat(m.optString("n").take(24).ifBlank { "Someone" }, m.optString("x").take(RoomPlan.MAX_CHAT), false, System.currentTimeMillis(), m.optString("k") == "react"))
            }
            "closed" -> { socket = null; ws.close(1000, null); _status.value = _status.value.copy(connected = false, message = "The host ended the room") }
        }
    }

    private fun applyState(room: CloudKeys.Room, sealed: String) {
        val plain = runCatching { CloudCrypto.openText(room.key, sealed, room.aad("state")) }.getOrNull()
        if (plain == null) { _status.value = _status.value.copy(message = "This invite's key doesn't match the room"); return }
        val remote = RoomState.from(JSONObject(plain))
        _status.value = _status.value.copy(state = remote)
        incoming.value = remote
    }

    private fun dropped(app: Context, final: String?) {
        socket = null
        if (!_status.value.online) return
        if (final != null) { _status.value = _status.value.copy(connected = false, message = final); return }
        val attempt = ++reconnects
        if (attempt > RoomPlan.MAX_RECONNECTS) { _status.value = _status.value.copy(connected = false, message = "Lost the connection to the room"); return }
        _status.value = _status.value.copy(connected = false, message = "Reconnecting…")
        scope.launch {
            delay(RoomPlan.backoffMs(attempt))
            if (_status.value.online && socket == null && onlineRoom != null) connect(app)
        }
    }

    private fun sendState(state: RoomState) {
        val room = onlineRoom ?: return
        val ws = socket ?: return
        // Online rooms use the server clock, so every guest can extrapolate the same position.
        val wire = state.copy(at = state.at + serverOffset)
        val sealed = CloudCrypto.sealText(room.key, wire.toJson().toString(), room.aad("state"))
        if (ws.send(JSONObject().put("t", "state").put("c", sealed).toString())) { lastSentAt = System.currentTimeMillis(); lastSentState = state }
    }

    /** Sends a chat line (or a reaction emoji when [reaction]). False when not connected. */
    fun say(text: String, reaction: Boolean = false): Boolean {
        val room = onlineRoom ?: return false
        val ws = socket ?: return false
        val clean = text.trim().take(RoomPlan.MAX_CHAT)
        if (clean.isEmpty()) return false
        val body = JSONObject().put("k", if (reaction) "react" else "chat").put("x", clean).put("n", onlineName).toString()
        val ok = ws.send(JSONObject().put("t", "msg").put("c", CloudCrypto.sealText(room.key, body, room.aad("msg"))).toString())
        if (ok) addChat(Chat(onlineName, clean, true, System.currentTimeMillis(), reaction))
        return ok
    }

    private fun addChat(c: Chat) { _chat.value = (_chat.value + c).takeLast(200) }

    private fun closeOnline(endForAll: Boolean) {
        val ws = socket
        socket = null
        if (ws != null) {
            if (endForAll && _status.value.role == Role.HOST) ws.send("{\"t\":\"close\"}")
            ws.close(1000, null)
        }
        onlineJob?.cancel(); onlineJob = null
        onlineRoom = null; onlineHostToken = null
        incoming.value = null
        lastSentState = null; lastSentAt = 0; reconnects = 0
        _chat.value = emptyList()
    }

    // endregion

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
        closeOnline(endForAll = true)
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

/** Pure rules for online rooms, unit-tested. */
object RoomPlan {
    const val MAX_CHAT = 500
    const val MAX_RECONNECTS = 12
    /** Heartbeat so drifting guests stay in step even when nothing changes (cheap: incoming frames bill 20:1). */
    const val HEARTBEAT_MS = 30_000L
    private val ID = Regex("^[A-Za-z0-9]{8}$")

    fun inviteLink(id: String, secret: ByteArray): String =
        "https://${dev.asfakul.yumeiro.BuildConfig.CLOUD_HOST}/r/$id#${CloudCodes.encode(secret)}"

    /** Accepts the https invite, the yumeiro://room/ deep link, or "ID KEY" typed by hand. */
    fun parseInvite(text: String): Pair<String, ByteArray>? {
        var t = text.trim()
        val marker = listOf("/r/", "room/").firstOrNull { t.contains(it) }
        if (marker != null) t = t.substringAfter(marker)
        val parts = t.split('#', ' ', '\n', '\t').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val id = parts[0].trim('/').uppercase()
        if (!ID.matches(id)) return null
        val secret = CloudCodes.decode(parts.drop(1).joinToString(""), CloudKeys.ROOM_BYTES) ?: return null
        return id to secret
    }

    /** Host side: send only on real changes (title, episode, play/pause, a seek) plus a heartbeat. */
    fun shouldSend(last: WatchTogether.RoomState?, next: WatchTogether.RoomState, now: Long, lastAt: Long): Boolean {
        if (last == null) return true
        if (last.api != next.api || last.url != next.url || last.index != next.index || last.playing != next.playing || last.version != next.version) return true
        if (kotlin.math.abs(last.positionAt(next.at) - next.positionMs) > 2_000) return true
        return now - lastAt >= HEARTBEAT_MS
    }

    fun backoffMs(attempt: Int): Long = (1_000L shl (attempt - 1).coerceIn(0, 5)).coerceAtMost(30_000)
}

/** An invite opened from outside (yumeiro://room/ID#KEY). The NavHost opens Watch together; the screen offers to join. */
object RoomNav {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()
    fun open(invite: String) { _pending.value = invite }
    fun consumed() { _pending.value = null }
}
