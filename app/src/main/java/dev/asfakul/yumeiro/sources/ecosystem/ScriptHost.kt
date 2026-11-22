/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.ecosystem

import android.content.Context
import android.util.Log
import dev.asfakul.yumeiro.sources.js.JsBridge
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * ADR-047: the host half of `assets/js/provider_runtime.js`, one instance per call (handles die
 * with the call). Network goes to [JsBridge] unchanged, so the SRC-2 private-network guard and the
 * 5 MB response cap apply to every ecosystem. DOM calls work on jsoup nodes by handle number.
 */
internal class ScriptHost(
    private val bridge: JsBridge,
    private val kv: KvStore,
    private val tag: String,
    private val deadline: Long = Long.MAX_VALUE,
) {
    private val nodes = HashMap<Int, Node>()
    private var next = 0
    var requests = 0
        private set

    private fun keep(n: Node): Int { next++; nodes[next] = n; return next }
    private fun keepAll(list: Iterable<Node>): String {
        val seen = HashSet<Node>()
        return JsonArray(list.filter { seen.add(it) }.map { JsonPrimitive(keep(it)) }).toString()
    }
    private fun node(e: JsonElement?): Node? = (e as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()?.let { nodes[it] }
    private fun elementsOf(e: JsonElement?): List<Element> = (e as? JsonArray).orEmpty().mapNotNull { node(it) as? Element }
    private fun JsonArray.s(i: Int): String = (getOrNull(i) as? JsonPrimitive)?.contentOrNull.orEmpty()

    fun handle(channel: String, raw: String): Any? = try {
        if (channel.startsWith("http_")) {
            requests++
            if (requests > MAX_REQUESTS) error("This source made too many requests in one call")
            if (System.currentTimeMillis() >= deadline) error("This call ran out of time")
            bridge.handle(channel, raw)
        } else {
            val a = RepoFormats.json.parseToJsonElement(raw).let { it as? JsonArray ?: JsonArray(listOf(it)) }
            dispatch(channel, a)
        }
    } catch (e: Throwable) {
        Log.w(TAG, "$tag $channel failed: ${e.message}")
        if (channel.startsWith("http_")) buildJsonObject { put("statusCode", 0); put("body", ""); put("headers", JsonObject(emptyMap())) }.toString() else ""
    }

    private fun dispatch(ch: String, a: JsonArray): Any? = when (ch) {
        "log" -> { if (dev.asfakul.yumeiro.BuildConfig.DEBUG) Log.d(TAG, "$tag: " + a.joinToString(" ") { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }.take(500)); "" }
        "dom_load" -> keep(Jsoup.parse(a.s(0))).toString()
        "dom_select" -> keepAll(elementsOf(a.getOrNull(0)).flatMap { e -> runCatching { e.select(a.s(1)) }.getOrDefault(emptyList()) })
        "dom_op" -> keepAll(op(a.s(0), elementsOf(a.getOrNull(1)), a.s(2)))
        "dom_attr" -> (node(a.getOrNull(0)) as? Element)?.let { e -> if (e.hasAttr(a.s(1))) JsonPrimitive(e.attr(a.s(1))) else JsonNull }?.toString() ?: "null"
        "dom_attrs" -> JsonObject((node(a.getOrNull(0)) as? Element)?.attributes()?.associate { it.key to JsonPrimitive(it.value) }.orEmpty()).toString()
        "dom_info" -> info(node(a.getOrNull(0))).toString()
        "dom_nodes" -> JsonArray(node(a.getOrNull(0))?.childNodes().orEmpty().filter { it is Element || it is TextNode }.map { n ->
            JsonObject(info(n) + ("h" to JsonPrimitive(keep(n))))
        }).toString()
        "dom_text" -> elementsOf(a.getOrNull(0)).joinToString("") { it.text() }
        "dom_html" -> (node(a.getOrNull(0)) as? Element)?.let { if (a.s(1) == "true") it.outerHtml() else it.html() }.orEmpty()
        "dom_is" -> elementsOf(a.getOrNull(0)).any { runCatching { it.`is`(a.s(1)) }.getOrDefault(false) }.toString()
        "dom_remove" -> { elementsOf(a.getOrNull(0)).forEach { it.remove() }; "" }
        "kv_get" -> kv.get(a.s(0)).orEmpty()
        "kv_set" -> { kv.set(a.s(0), a.s(1)); "" }
        "kv_delete" -> kv.delete(a.s(0)).toString()
        "kv_keys" -> JsonArray(kv.keys().map(::JsonPrimitive)).toString()
        "kv_clear" -> { kv.clear(); "" }
        // F-05-N (ADR-057): crypto-js shim, hex in / hex out (ScriptCrypto). A bad key or padding
        // answers "" and the shim throws, like crypto-js's own errors.
        "crypto_hash" -> ScriptCrypto.hash(a.s(0), a.s(1))
        "crypto_hmac" -> ScriptCrypto.hmac(a.s(0), a.s(1), a.s(2))
        "crypto_cipher" -> ScriptCrypto.cipher(a.s(0) == "enc", a.s(1), a.s(2), a.s(3), a.s(4), a.s(5), a.s(6))
        "crypto_evpkdf" -> ScriptCrypto.evpKdf(a.s(0), a.s(1), a.s(2).toIntOrNull() ?: 32, a.s(3).toIntOrNull() ?: 16)
        else -> { Log.w(TAG, "$tag: unsupported channel $ch"); "" }
    }

    private fun info(n: Node?): JsonObject = when (n) {
        is TextNode -> buildJsonObject { put("type", "text"); put("data", n.wholeText) }
        is org.jsoup.nodes.Document -> buildJsonObject { put("type", "root") }
        is Element -> buildJsonObject { put("type", "tag"); put("tag", n.tagName()) }
        else -> buildJsonObject { put("type", "other") }
    }

    /** cheerio traversal names on jsoup. An empty selector means "no filter". */
    private fun op(name: String, list: List<Element>, css: String): List<Node> {
        fun Element.ok() = css.isBlank() || runCatching { `is`(css) }.getOrDefault(false)
        return when (name) {
            "children" -> list.flatMap { it.children() }.filter { it.ok() }
            "parent" -> list.mapNotNull { it.parent() }.filter { it.ok() }
            "parents" -> list.flatMap { it.parents() }.filter { it.ok() }
            "closest" -> list.mapNotNull { e -> generateSequence(e) { it.parent() }.firstOrNull { css.isNotBlank() && it.ok() } }
            "next" -> list.mapNotNull { it.nextElementSibling() }.filter { it.ok() }
            "prev" -> list.mapNotNull { it.previousElementSibling() }.filter { it.ok() }
            "nextAll" -> list.flatMap { it.nextElementSiblings() }.filter { it.ok() }
            "prevAll" -> list.flatMap { it.previousElementSiblings() }.filter { it.ok() }
            "siblings" -> list.flatMap { it.siblingElements() }.filter { it.ok() }
            "filter" -> list.filter { css.isNotBlank() && it.ok() }
            "not" -> list.filter { !(css.isNotBlank() && it.ok()) }
            "has" -> list.filter { e -> css.isNotBlank() && runCatching { e.selectFirst(css) != null }.getOrDefault(false) }
            "clone" -> list.map { it.clone() }
            else -> emptyList()
        }
    }

    /** Per-provider store, capped at [MAX_KV_BYTES] in total so a script can't fill the disk. */
    internal class KvStore(context: Context, name: String) {
        private val p = context.getSharedPreferences("script_kv_$name", Context.MODE_PRIVATE)
        fun get(k: String): String? = p.getString(k, null)
        fun set(k: String, v: String) {
            val size = p.all.entries.sumOf { (key, value) -> if (key == k) 0 else key.length + value.toString().length } + k.length + v.length
            if (size > MAX_KV_BYTES) error("This source's storage is full")
            p.edit().putString(k, v).apply()
        }
        fun delete(k: String): Boolean = p.contains(k).also { p.edit().remove(k).apply() }
        fun keys(): List<String> = p.all.keys.toList()
        fun clear() = p.edit().clear().apply()
    }

    companion object {
        private const val TAG = "ScriptHost"
        /** Proposal F-05 §4: 30 requests per call. */
        const val MAX_REQUESTS = 30
        const val MAX_KV_BYTES = 64 * 1024
    }
}
