/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** A scripted TDLib: [answer] maps each request to the objects TDLib would send back (answer + updates). */
class FakeTd(private val answer: FakeTd.(JsonObject) -> List<JsonObject>) : TdTransport {
    val sent = CopyOnWriteArrayList<JsonObject>()
    private val queue = LinkedBlockingQueue<String>()
    private var next = 0

    override fun createClientId(): Int = ++next
    override fun send(clientId: Int, request: String) {
        val o = TdJson.parseToJsonElement(request).jsonObject
        sent += o
        for (r in answer(this, o)) {
            val withId = JsonObject(r + ("@client_id" to JsonPrimitive(clientId)))
            queue.put(withId.toString())
        }
    }
    override fun receive(timeoutSeconds: Double): String? = queue.poll((timeoutSeconds * 1000).toLong(), TimeUnit.MILLISECONDS)
    override fun execute(request: String): String? = null

    fun push(clientId: Int, o: JsonObject) = queue.put(JsonObject(o + ("@client_id" to JsonPrimitive(clientId))).toString())

    fun sentTypes(): List<String> = sent.map { it.type() }

    companion object {
        fun ok(req: JsonObject): JsonObject = buildJsonObject { put("@type", "ok"); req["@extra"]?.let { put("@extra", it) } }
        fun error(req: JsonObject, code: Int, message: String): JsonObject =
            buildJsonObject { put("@type", "error"); put("code", code); put("message", message); req["@extra"]?.let { put("@extra", it) } }
        fun auth(type: String, build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}): JsonObject = buildJsonObject {
            put("@type", "updateAuthorizationState")
            put("authorization_state", tdObject(type, build))
        }
    }
}
