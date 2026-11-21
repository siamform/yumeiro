/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * F-02 / ADR-052: the four calls TDLib's JSON interface offers. The real one is [JsonTdTransport]; tests use a fake.
 * [receive] is global (one queue for every client, each answer carries `@client_id`) and must never run on two
 * threads at once, so only [TdHub]'s single thread calls it.
 */
interface TdTransport {
    fun createClientId(): Int
    fun send(clientId: Int, request: String)
    fun receive(timeoutSeconds: Double): String?
    fun execute(request: String): String?
}

/** TDLib itself, after [TdEngine] has loaded the library. */
object JsonTdTransport : TdTransport {
    override fun createClientId(): Int = org.drinkless.tdlib.JsonClient.createClientId()
    override fun send(clientId: Int, request: String) = org.drinkless.tdlib.JsonClient.send(clientId, request)
    override fun receive(timeoutSeconds: Double): String? = org.drinkless.tdlib.JsonClient.receive(timeoutSeconds)
    override fun execute(request: String): String? = org.drinkless.tdlib.JsonClient.execute(request)
}

/** A TDLib `error` object answered to a request. */
class TdException(val code: Int, override val message: String) : Exception(message)

internal val TdJson = Json { ignoreUnknownKeys = true; isLenient = false }

fun JsonObject.type(): String = this["@type"]?.jsonPrimitive?.contentOrNull.orEmpty()
fun JsonObject.str(key: String): String = this[key]?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
fun JsonObject.int(key: String): Int = this[key]?.let { (it as? JsonPrimitive)?.intOrNull } ?: 0
fun JsonObject.long(key: String): Long = this[key]?.let { (it as? JsonPrimitive)?.let { p -> p.longOrNull ?: p.contentOrNull?.toLongOrNull() } } ?: 0L
fun JsonObject.bool(key: String): Boolean = this[key]?.let { (it as? JsonPrimitive)?.contentOrNull } == "true"
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/**
 * One receive thread for every TDLib client in the process (TDLib's rule, see [TdTransport.receive]). It starts with
 * the first client and stops when the last one is closed, so a user who never signs in to Telegram has no thread.
 */
class TdHub(private val transport: TdTransport, private val pollSeconds: Double = 1.0) {
    private val clients = ConcurrentHashMap<Int, TdClient>()
    private var thread: Thread? = null

    fun newClient(): TdClient {
        val id = transport.createClientId()
        val c = TdClient(transport, id) { gone -> clients.remove(gone) }
        clients[id] = c
        ensureThread()
        return c
    }

    fun execute(request: JsonObject): JsonObject? =
        transport.execute(request.toString())?.let { runCatching { TdJson.parseToJsonElement(it).jsonObject }.getOrNull() }

    @Synchronized
    private fun ensureThread() {
        if (thread?.isAlive == true) return
        thread = Thread({ loop() }, "yumeiro-tdlib").apply { isDaemon = true; start() }
    }

    private fun loop() {
        while (true) {
            if (clients.isEmpty()) {
                synchronized(this) { if (clients.isEmpty()) { thread = null; return } }
            }
            val raw = try { transport.receive(pollSeconds) } catch (e: Throwable) { null } ?: continue
            val o = runCatching { TdJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: continue
            val id = o["@client_id"]?.jsonPrimitive?.intOrNull ?: continue
            clients[id]?.deliver(o)
        }
    }
}

/**
 * One TDLib instance (one Telegram account database). Requests are matched to answers by `@extra`; updates go to
 * [updates]; the latest authorization state and every file's latest state are kept so late collectors and blocking
 * readers ([TdFileSource]) never miss them.
 */
class TdClient internal constructor(
    private val transport: TdTransport,
    val id: Int,
    private val onClosed: (Int) -> Unit,
) {
    private val seq = AtomicLong()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val _updates = MutableSharedFlow<JsonObject>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val updates: SharedFlow<JsonObject> = _updates.asSharedFlow()
    private val _auth = MutableStateFlow<JsonObject?>(null)
    /** The last `authorizationState*` object TDLib reported (null before the first one). */
    val auth: StateFlow<JsonObject?> = _auth.asStateFlow()
    private val files = ConcurrentHashMap<Int, JsonObject>()
    /** Lock + condition for readers that wait on a file's downloaded bytes (notified on every `updateFile`). */
    val fileLock = Object()
    @Volatile var closed = false
        private set

    fun file(fileId: Int): JsonObject? = files[fileId]

    fun send(type: String, build: JsonObjectBuilder.() -> Unit = {}) {
        if (closed) return
        transport.send(id, buildJsonObject { put("@type", type); build() }.toString())
    }

    /** Sends a request and waits for its answer; a TDLib `error` becomes [TdException]. */
    suspend fun call(type: String, timeoutMs: Long = 30_000, build: JsonObjectBuilder.() -> Unit = {}): JsonObject {
        if (closed) throw TdException(500, "Telegram is closed")
        val extra = "y" + seq.incrementAndGet()
        val wait = CompletableDeferred<JsonObject>()
        pending[extra] = wait
        try {
            transport.send(id, buildJsonObject { put("@type", type); build(); put("@extra", extra) }.toString())
            val answer = withTimeout(timeoutMs) { wait.await() }
            if (answer.type() == "error") throw TdException(answer.int("code"), answer.str("message"))
            return answer
        } finally {
            pending.remove(extra)
        }
    }

    internal fun deliver(o: JsonObject) {
        val extra = o["@extra"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (extra != null) { pending.remove(extra)?.complete(o); return }
        when (o.type()) {
            "updateAuthorizationState" -> {
                val s = o.obj("authorization_state")
                _auth.value = s
                if (s?.type() == "authorizationStateClosed") close()
            }
            "updateFile" -> o.obj("file")?.let { f ->
                files[f.int("id")] = f
                synchronized(fileLock) { fileLock.notifyAll() }
            }
        }
        _updates.tryEmit(o)
    }

    /** Remembers a file object that came inside an answer (messages carry their files). */
    fun remember(file: JsonObject) {
        val fid = file.int("id")
        if (fid == 0) return
        val old = files[fid]
        // An answer can be older than an updateFile already seen: keep the one with more bytes.
        if (old == null || downloadedPrefix(file) >= downloadedPrefix(old)) files[fid] = file
    }

    private fun close() {
        if (closed) return
        closed = true
        pending.values.forEach { it.complete(buildJsonObject { put("@type", "error"); put("code", 500); put("message", "Telegram is closed") }) }
        pending.clear()
        synchronized(fileLock) { fileLock.notifyAll() }
        onClosed(id)
    }

    companion object {
        fun downloadedPrefix(file: JsonObject): Long {
            val local = file.obj("local") ?: return 0L
            return local.long("download_offset").takeIf { it == 0L }?.let { local.long("downloaded_prefix_size") } ?: 0L
        }
    }
}

/** `{"@type": t, ...}` helper for nested objects. */
fun tdObject(type: String, build: JsonObjectBuilder.() -> Unit = {}): JsonElement = buildJsonObject { put("@type", type); build() }
