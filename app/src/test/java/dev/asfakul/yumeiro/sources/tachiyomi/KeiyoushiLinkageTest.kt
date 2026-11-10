/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.tachiyomi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Every class, method and field that the 1554 Keiyoushi (extensions-lib 1.6) extensions link
 * against must exist in Yumeiro, with the same JVM signature — otherwise the extension fails with
 * NoSuchMethodError at runtime. The fixture was extracted from the extensions' own class files.
 * Known gap: ca.mpreg.imagedecoder (4 extensions), left out of the fixture on purpose.
 */
class KeiyoushiLinkageTest {
    private val loader = javaClass.classLoader!!

    private fun cls(internal: String): Class<*>? = runCatching { Class.forName(internal.replace('/', '.'), false, loader) }.getOrNull()

    private fun desc(c: Class<*>): String = when {
        c.isArray -> "[" + desc(c.componentType!!)
        c == Void.TYPE -> "V"; c == Integer.TYPE -> "I"; c == java.lang.Long.TYPE -> "J"; c == java.lang.Boolean.TYPE -> "Z"
        c == java.lang.Float.TYPE -> "F"; c == java.lang.Double.TYPE -> "D"; c == java.lang.Byte.TYPE -> "B"
        c == Character.TYPE -> "C"; c == java.lang.Short.TYPE -> "S"
        else -> "L" + c.name.replace('.', '/') + ";"
    }

    private fun desc(m: Method) = m.parameterTypes.joinToString("", "(", ")") { desc(it) } + desc(m.returnType)
    private fun desc(k: Constructor<*>) = k.parameterTypes.joinToString("", "(", ")") { desc(it) } + "V"

    private fun hierarchy(c: Class<*>): Sequence<Class<*>> = sequence {
        val seen = HashSet<Class<*>>()
        val queue = ArrayDeque(listOf(c))
        while (queue.isNotEmpty()) {
            val x = queue.removeFirst()
            if (!seen.add(x)) continue
            yield(x)
            x.superclass?.let(queue::add)
            queue.addAll(x.interfaces)
        }
        if (c.isInterface) yield(Any::class.java)
    }

    private fun has(owner: Class<*>, name: String, d: String, field: Boolean): Boolean = runCatching {
        if (field) return@runCatching hierarchy(owner).any { k -> k.declaredFields.any { it.name == name && desc(it.type) == d.substringAfter(')') } }
        if (name == "<init>") return@runCatching owner.declaredConstructors.any { desc(it) == d }
        if (name == "<clinit>") return@runCatching true
        hierarchy(owner).any { k -> k.declaredMethods.any { it.name == name && desc(it) == d } }
    }.getOrDefault(false)

    @Test fun everyReferenceResolves() {
        val root = Json.parseToJsonElement(loader.getResourceAsStream("keiyoushi-lib16-refs.json")!!.reader().readText()).jsonObject
        val missing = mutableListOf<String>()
        for (c in root["classes"]!!.jsonArray) {
            val n = c.jsonPrimitive.content
            if (cls(n) == null) missing += "class $n"
        }
        for (r in root["refs"]!!.jsonArray) {
            val (owner, name, d, kind) = r.jsonArray.map { it.jsonPrimitive.content }
            val k = cls(owner) ?: continue // already reported above
            if (!has(k, name, d, kind == "F")) missing += "$owner.$name$d"
        }
        assertTrue("Missing for Keiyoushi extensions (${missing.size}):\n" + missing.joinToString("\n"), missing.isEmpty())
    }
}
