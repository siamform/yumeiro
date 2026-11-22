/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.ecosystem

import android.content.Context
import android.content.pm.PackageManager
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.FunctionBinding
import dev.asfakul.yumeiro.DeviceClass
import eu.kanade.tachiyomi.network.NetworkHelper
import dev.asfakul.yumeiro.sources.js.JsBridge
import dev.asfakul.yumeiro.sources.js.JsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import java.io.File

/**
 * ADR-047: the one script runtime every JavaScript ecosystem adapter shares (Vega, Nuvio since
 * ADR-057; SkyStream plugs into the same call when its adapter ships). Rules from proposal F-05 §4:
 * - a fresh QuickJS per call, closed afterwards, so nothing leaks between titles or providers;
 * - memory 48 MB (32 MB on low-end phones and TV), stack 1 MB, evaluation limit per evaluate and
 *   a whole-call budget;
 * - at most 4 calls at once (2 on low-end and TV), on a background dispatcher;
 * - network only through [JsBridge] (SRC-2 private-network guard, 5 MB cap), 30 requests a call.
 */
object ScriptRuntime {
    const val CALL_BUDGET_MS = 25_000L
    const val EVAL_TIMEOUT_MS = 20_000L
    /** One network request; well under the call budget so a hung host can't hold a call open. */
    const val REQUEST_TIMEOUT_MS = 12_000L
    private const val MAX_SPINS = 400

    @Volatile private var runtimeJs: String? = null
    @Volatile private var gate: Semaphore? = null
    @Volatile private var http: okhttp3.OkHttpClient? = null

    /** One client for every script call (a new [NetworkHelper] per call would open a second disk cache on the same folder). */
    private fun client(context: Context): okhttp3.OkHttpClient = http ?: synchronized(this) {
        http ?: NetworkHelper(context).client.newBuilder()
            .callTimeout(REQUEST_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build().also { http = it }
    }

    private fun constrained(context: Context): Boolean =
        DeviceClass.lowEnd || context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    private fun gate(context: Context): Semaphore = gate ?: synchronized(this) {
        gate ?: Semaphore(if (constrained(context)) 2 else 4).also { gate = it }
    }

    /** One module of a plugin: its name (`posts`) and code file. */
    data class Module(val name: String, val file: File)

    /**
     * Loads [modules] as CommonJS modules, calls `require(entry)[fn](args)`, awaits it and returns
     * the JSON result. [storeName] keys the provider's own small key/value store.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun call(
        context: Context,
        storeName: String,
        modules: List<Module>,
        entry: String,
        fn: String,
        argsJson: String,
        /** F-05-N (ADR-057): `__invoke` passes one args object (Vega); `__invokePositional` spreads a JSON array (Nuvio). */
        invoker: String = "__invoke",
        /** F-05-N: globals set before the modules load, name to JSON value (e.g. `SCRAPER_ID`). */
        globals: Map<String, String> = emptyMap(),
    ): JsonElement {
        require(invoker == "__invoke" || invoker == "__invokePositional") { "Unknown invoker" }
        val app = context.applicationContext
        return gate(app).withPermit {
            withTimeout(CALL_BUDGET_MS) {
                withContext(Dispatchers.IO) {
                    val bridge = JsBridge(app, client(app), JsPreferences(app, "script_$storeName"))
                    // QuickJS can only stop running JavaScript, not a blocking host call, so the
                    // budget is enforced in both places: each evaluate gets what is left of it, and
                    // the host refuses requests once it is spent.
                    val deadline = System.currentTimeMillis() + CALL_BUDGET_MS - 500
                    val host = ScriptHost(bridge, ScriptHost.KvStore(app, storeName), storeName, deadline)
                    var result: String? = null
                    var failure: String? = null
                    val q = QuickJs.create(Dispatchers.IO.limitedParallelism(1))
                    fun left() = (deadline - System.currentTimeMillis()).coerceIn(1L, EVAL_TIMEOUT_MS)
                    try {
                        q.memoryLimit = (if (constrained(app)) 32L else 48L) * 1024 * 1024
                        q.maxStackSize = 1L * 1024 * 1024
                        q.defineBinding("sendMessage", FunctionBinding<Any?> { args ->
                            host.handle(args.getOrNull(0)?.toString().orEmpty(), args.getOrNull(1)?.toString() ?: "[]")
                        })
                        q.defineBinding("__result", FunctionBinding<Any?> { args -> result = args.getOrNull(0)?.toString(); null })
                        q.defineBinding("__error", FunctionBinding<Any?> { args -> failure = args.getOrNull(0)?.toString() ?: "error"; null })
                        q.evaluationTimeoutMillis = left()
                        q.evaluate<Any?>(runtime(app), "provider_runtime.js", false)
                        if (globals.isNotEmpty()) {
                            val set = globals.entries.joinToString("\n") { (k, v) -> "globalThis[${quote(k)}] = JSON.parse(${quote(v)});" }
                            q.evaluate<Any?>(set, "globals.js", false)
                        }
                        for (m in modules) {
                            val code = m.file.readText()
                            q.evaluationTimeoutMillis = left()
                            q.evaluate<Any?>("__defineModule(${quote(m.name)}, function (module, exports, require) {\n$code\n});", "${m.name}.js", false)
                        }
                        q.evaluationTimeoutMillis = left()
                        q.evaluate<Any?>("$invoker(${quote(entry)}, ${quote(fn)}, ${quote(argsJson)})", "call.js", false)
                        var spins = 0
                        while (result == null && failure == null && spins++ < MAX_SPINS) {
                            if (System.currentTimeMillis() >= deadline) throw ScriptException(STOPPED)
                            q.evaluationTimeoutMillis = left()
                            q.evaluate<Any?>("0", "tick.js", false)
                        }
                        failure?.let { throw ScriptException(it.lineSequence().first().take(300)) }
                        RepoFormats.json.parseToJsonElement(result ?: throw ScriptException("The source didn't answer"))
                    } catch (e: QuickJsInterruptedException) {
                        throw ScriptException(STOPPED, e)
                    } finally {
                        runCatching { q.close() }
                    }
                }
            }
        }
    }

    private fun runtime(context: Context): String = runtimeJs
        ?: context.assets.open("js/provider_runtime.js").bufferedReader().use { it.readText() }.also { runtimeJs = it }

    /** A JS string literal for [s] (JSON string escaping is valid JS). */
    internal fun quote(s: String): String = kotlinx.serialization.json.JsonPrimitive(s).toString()

    class ScriptException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

    const val STOPPED = "This source took too long and was stopped"
}
