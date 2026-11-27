/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.novel

import dev.asfakul.yumeiro.sources.js.QuickJsGuard
import android.content.Context
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.FunctionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * NOVEL-1 (owner item 26): one QuickJS context running one LNReader novel plugin (CommonJS), unmodified, on top of
 * the cheerio bundle + require() harness Zangetsu uses (assets/js/lnreader_*.js). Requests go through Kotlin
 * ([fetch]: OkHttp with Yumeiro's DNS, proxy and browser-check passes). One engine per plugin, so a slow or broken
 * source never holds up another; calls on one engine are serialised and stopped after [CALL_TIMEOUT_MS].
 */
class NovelEngine(
    private val context: Context,
    val plugin: NovelPlugin,
    private val code: String,
    private val client: OkHttpClient,
) {
    private val mutex = Mutex()
    private var js: QuickJs? = null
    @Volatile private var result: String? = null
    @Volatile private var failure: String? = null
    @Volatile var site: String = plugin.site
        private set
    /** B7: a request of this source ended on another site (the site moved or was taken over); null = none since [clearAway]. */
    @Volatile var awayHost: String? = null
        private set
    fun clearAway() { awayHost = null }
    /** B7: the plugin's own cover-image headers (`imageRequestInit.headers`), empty when it gives none. */
    @Volatile private var imageInit: Map<String, String> = emptyMap()

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun runtime(): QuickJs {
        js?.let { return it }
        val q = QuickJsGuard.create(Dispatchers.IO.limitedParallelism(1))
        q.memoryLimit = 96L * 1024 * 1024
        q.maxStackSize = 2L * 1024 * 1024
        q.evaluationTimeoutMillis = LOAD_TIMEOUT_MS
        q.defineBinding("__ktFetch", FunctionBinding<String> { args -> fetch(args.getOrNull(0)?.toString().orEmpty(), args.getOrNull(1)?.toString() ?: "{}") })
        q.defineBinding("__result", FunctionBinding<Any?> { args -> result = args.getOrNull(0)?.toString(); null })
        q.defineBinding("__error", FunctionBinding<Any?> { args -> failure = args.getOrNull(0)?.toString() ?: "error"; null })
        val assets = context.applicationContext.assets
        q.evaluate<Any?>(assets.open("js/lnreader_cheerio.js").bufferedReader().use { it.readText() }, "cheerio.js", false)
        q.evaluate<Any?>(assets.open("js/lnreader_harness.js").bufferedReader().use { it.readText() }, "harness.js", false)
        q.evaluate<Any?>("__loadPlugin(${JsonPrimitive(plugin.id)}, ${JsonPrimitive(code)})", "plugin.js", false)
        runCatching { q.evaluate<String>("__pluginInfo(${JsonPrimitive(plugin.id)})", "info.js", false) }.getOrNull()?.let { info ->
            runCatching { JSONObject(info).optString("site") }.getOrNull()?.takeIf { it.startsWith("http") }?.let { site = it }
            runCatching { NovelImageHeaders.fromInfo(JSONObject(info)) }.getOrNull()?.let { imageInit = it }
        }
        NovelImageHeaders.remember(plugin.id, NovelImageHeaders.forCovers(site, imageInit))
        q.evaluationTimeoutMillis = CALL_TIMEOUT_MS
        js = q
        return q
    }

    /** `plugin.method(...args)` with promises awaited; the plugin's JSON answer. */
    suspend fun call(method: String, args: List<JsonElement>): JsonElement = mutex.withLock {
        val q = try { runtime() } catch (e: QuickJsInterruptedException) {
            close(); throw IllegalStateException(STOPPED, e)
        }
        try {
            result = null; failure = null
            val a = JsonPrimitive(JsonArray(args).toString())
            q.evaluate<Any?>(
                "__callPlugin(${JsonPrimitive(plugin.id)}, ${JsonPrimitive(method)}, $a).then(__result, function (e) { __error(String((e && e.message) || e)); })",
                "call.js", false,
            )
            var spins = 0
            while (result == null && failure == null && spins++ < 200) q.evaluate<Any?>("0", "tick.js", false)
            failure?.let { throw IllegalStateException(friendly(it)) }
            kotlinx.serialization.json.Json.parseToJsonElement(result ?: "null")
        } catch (e: QuickJsInterruptedException) {
            close(); throw IllegalStateException(STOPPED, e)
        }
    }

    /** The synchronous request the harness asks for; runs on this engine's own JS thread (never the main one). */
    private fun fetch(url: String, initJson: String): String {
        val init = runCatching { JSONObject(initJson) }.getOrDefault(JSONObject())
        val method = init.optString("method", "GET").uppercase()
        val b = Request.Builder().url(url)
        var hasUa = false
        init.optJSONObject("headers")?.let { h ->
            for (k in h.keys()) {
                val v = h.optString(k)
                if (k.equals("user-agent", true)) hasUa = true
                runCatching { b.header(k, v) }
            }
        }
        if (!hasUa) b.header("User-Agent", dev.asfakul.yumeiro.online.web.BrowserCheck.userAgent())
        val body = init.opt("body")?.takeIf { it != JSONObject.NULL }?.toString()
        if (method != "GET" && method != "HEAD") {
            val type = init.optJSONObject("headers")?.let { h -> h.keys().asSequence().firstOrNull { it.equals("content-type", true) }?.let { h.optString(it) } }
            b.method(method, (body ?: "").toRequestBody((type ?: "application/x-www-form-urlencoded").toMediaTypeOrNull()))
        }
        val out = JSONObject()
        try {
            client.newCall(b.build()).execute().use { r ->
                out.put("status", r.code)
                out.put("url", r.request.url.toString())
                NovelSiteCheck.awayHost(url, r.request.url.toString(), site)?.let { awayHost = it }
                val hs = JSONObject()
                for (name in r.headers.names()) hs.put(name, r.headers(name).joinToString(", "))
                out.put("headers", hs)
                val src = r.body.source()
                src.request(MAX_BODY)
                out.put("body", src.buffer.readUtf8(minOf(src.buffer.size, MAX_BODY)))
            }
        } catch (e: java.io.IOException) {
            out.put("status", 0); out.put("url", url); out.put("headers", JSONObject()); out.put("body", "")
        }
        return out.toString()
    }

    fun close() { runCatching { js?.let(QuickJsGuard::close) }; js = null }

    companion object {
        const val LOAD_TIMEOUT_MS = 25_000L
        const val CALL_TIMEOUT_MS = 60_000L
        const val MAX_BODY = 8L * 1024 * 1024
        const val STOPPED = "This source took too long and was stopped"

        /** Plugin errors are written for developers; say what the reader can do. */
        fun friendly(raw: String): String {
            val l = raw.lowercase()
            return when {
                "security check" in l || "cloudflare" in l || "captcha" in l -> "The site asks for a check. Open it in the browser once, then try again."
                "unknown module" in l -> "This source needs a part Yumeiro doesn't have yet (${raw.substringAfter("module:").trim().take(40)})."
                "unknown method" in l -> "This source can't do that."
                else -> raw.lineSequence().first().take(160)
            }
        }
    }
}

/** Reads a JSON string field. */
internal fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
