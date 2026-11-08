/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.js

import android.content.Context
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.FunctionBinding
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object JsJson {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    fun JsonObject.arr(k: String): List<JsonElement> = (this[k] as? JsonArray).orEmpty()
}

/** Per-source preferences, with defaults taken from the source's getSourcePreferences(). */
class JsPreferences(context: Context, sourceId: String) {
    private val p = context.getSharedPreferences("js_source_$sourceId", Context.MODE_PRIVATE)
    @Volatile var defaults: Map<String, String> = emptyMap()

    fun get(key: String): Any = p.all[key]?.toString() ?: defaults[key].orEmpty().let { v ->
        when (v) { "true" -> true; "false" -> false; else -> if (v.startsWith("[")) v else v }
    }
    fun getString(key: String, def: String): String = p.getString(key, null) ?: def
    fun setString(key: String, value: String) = p.edit().putString(key, value).apply()

    companion object {
        /** Reads default values out of a getSourcePreferences() result. */
        fun defaultsOf(prefs: JsonArray): Map<String, String> = prefs.mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val key = (o["key"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val list = o["listPreference"] as? JsonObject
            val edit = o["editTextPreference"] as? JsonObject
            val check = (o["checkBoxPreference"] ?: o["switchPreferenceCompat"]) as? JsonObject
            val multi = o["multiSelectListPreference"] as? JsonObject
            val v = when {
                list != null -> {
                    val idx = (list["valueIndex"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
                    ((list["entryValues"] as? JsonArray)?.getOrNull(idx) as? JsonPrimitive)?.contentOrNull
                }
                edit != null -> (edit["value"] as? JsonPrimitive)?.contentOrNull
                check != null -> (check["value"] as? JsonPrimitive)?.contentOrNull
                multi != null -> (multi["values"] as? JsonArray)?.toString()
                else -> null
            } ?: return@mapNotNull null
            key to v
        }.toMap()
    }
}

/**
 * One QuickJS context running one Mangayomi JavaScript source. Calls are serialised; each call
 * returns the JSON the source produced.
 */
class JsEngine(private val context: Context, val sourceId: String, private val sourceJson: String, private val code: String) {
    private val mutex = Mutex()
    private var js: QuickJs? = null
    private val prefs = JsPreferences(context, sourceId)
    @Volatile private var result: String? = null
    @Volatile private var failure: String? = null
    private val bridge by lazy { JsBridge(context, NetworkHelper(context).client, prefs) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun runtime(): QuickJs {
        js?.let { return it }
        val q = QuickJs.create(Dispatchers.IO.limitedParallelism(1))
        q.memoryLimit = 64L * 1024 * 1024
        q.maxStackSize = 2L * 1024 * 1024
        q.defineBinding("sendMessage", FunctionBinding<Any?> { args ->
            bridge.handle(args.getOrNull(0)?.toString().orEmpty(), args.getOrNull(1)?.toString() ?: "[]")
        })
        q.defineBinding("__result", FunctionBinding<Any?> { args -> result = args.getOrNull(0)?.toString(); null })
        q.defineBinding("__error", FunctionBinding<Any?> { args -> failure = args.getOrNull(0)?.toString() ?: "error"; null })
        val runtime = context.assets.open("js/mangayomi_runtime.js").bufferedReader().use { it.readText() }
            .replace("__SOURCE__", sourceJson)
        q.evaluate<Any?>(runtime, "runtime.js", false)
        q.evaluate<Any?>(code + "\nvar extention = new DefaultExtension();", "source.js", false)
        runCatching { q.evaluate<String>("JSON.stringify(extention.getSourcePreferences() || [])", "prefs.js", false) }.getOrNull()
            ?.let { prefs.defaults = JsPreferences.defaultsOf(JsJson.json.parseToJsonElement(it) as JsonArray) }
        js = q
        return q
    }

    /** Calls `extention.<expr>` (awaiting promises) and parses its JSON result. */
    suspend fun call(expr: String): JsonElement = mutex.withLock {
        val q = runtime()
        try {
            result = null; failure = null
            q.evaluate<Any?>("__call(() => extention.$expr).then(__result, e => __error(String((e && e.stack) || e)))", "call.js", false)
            // Promise callbacks run as pending jobs; give the job queue a few turns if needed.
            var spins = 0
            while (result == null && failure == null && spins++ < 50) q.evaluate<Any?>("0", "tick.js", false)
            failure?.let { throw IllegalStateException(it.lineSequence().first()) }
            JsJson.json.parseToJsonElement(result ?: "null")
        } finally {
            bridge.reset()
        }
    }

    suspend fun headers(url: String): Map<String, String> = runCatching {
        (call("getHeaders(${JsonPrimitive(url)})") as? JsonObject)?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() }
    }.getOrNull().orEmpty()

    fun close() { js?.close(); js = null }
}
