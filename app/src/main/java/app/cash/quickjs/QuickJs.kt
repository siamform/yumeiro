/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package app.cash.quickjs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.Closeable

/**
 * The `app.cash.quickjs.QuickJs` API that Mihon hands to extensions (Keiyoushi lib 1.6 sources use
 * it to unpack obfuscated scripts), backed by the QuickJS engine Yumeiro already ships — so there
 * is one native QuickJS in the APK, not two. Calls are blocking, like the original, and run on the
 * caller's thread. Scripts are sandboxed: no Java objects are exposed to them.
 *
 * Results follow the original contract: String, Integer / Double, Boolean, null; arrays come back
 * as `Object[]`.
 */
class QuickJs private constructor(private val js: com.dokar.quickjs.QuickJs) : Closeable {

    /** Evaluates [script] and returns its result. */
    fun evaluate(script: String, fileName: String): Any? = convert(runBlocking(Dispatchers.Unconfined) {
        js.evaluate<Any?>(script, fileName, false)
    })

    fun evaluate(script: String): Any? = evaluate(script, "?")

    /** Compiles [sourceCode] to QuickJS bytecode for [execute]. */
    fun compile(sourceCode: String, fileName: String): ByteArray = js.compile(sourceCode, fileName, false)

    /** Runs bytecode from [compile]. */
    fun execute(bytecode: ByteArray): Any? = convert(runBlocking(Dispatchers.Unconfined) { js.evaluate<Any?>(bytecode) })

    override fun close() = js.close()

    private fun convert(v: Any?): Any? = when (v) {
        null, Unit -> null
        is List<*> -> v.map(::convert).toTypedArray()
        is Array<*> -> v.map(::convert).toTypedArray()
        is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else v.toDouble()
        is Float -> v.toDouble()
        else -> v
    }

    companion object {
        /** A new, isolated JavaScript runtime. Close it when done. */
        @JvmStatic
        fun create(): QuickJs = QuickJs(com.dokar.quickjs.QuickJs.create(Dispatchers.Unconfined))
    }
}
