package eu.kanade.tachiyomi.network

import android.content.Context
import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small JavaScript evaluator extensions use for packed/obfuscated players. Backed by QuickJS
 * (sandboxed, no Java access from scripts). Arrays come back as Array<String>, matching the
 * original Tachiyomi/Aniyomi contract.
 */
@Suppress("UNUSED_PARAMETER")
class JavaScriptEngine(context: Context) {
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.IO) {
        val q = QuickJs.create(Dispatchers.IO.limitedParallelism(1))
        try {
            val result: Any? = q.evaluate<Any?>(script, "extension.js", false)
            when (result) {
                is List<*> -> result.map { it?.toString() ?: "" }.toTypedArray()
                is Array<*> -> result.map { it?.toString() ?: "" }.toTypedArray()
                null, Unit -> null
                is CharSequence -> result.toString()
                is Number, is Boolean -> result
                else -> result.toString()
            } as T
        } finally {
            q.close()
        }
    }
}
