/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.text

/**
 * R15: the few patterns that depend on a runtime value (an episode number, an artist name, a query
 * key) are compiled once per distinct value and kept in a small LRU, instead of on every call.
 * Every literal pattern in the app is a file- or object-level constant; `RegexGuardTest` fails the
 * build if a `Regex(...)` is constructed inside a function again.
 *
 * Thread-safe; bounded ([MAX] entries) so it can never grow with user input.
 */
object RegexCache {
    private const val MAX = 128

    private val map = object : LinkedHashMap<String, Regex>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex>?): Boolean = size > MAX
    }

    fun get(pattern: String, vararg options: RegexOption): Regex {
        val key = if (options.isEmpty()) pattern else options.joinToString(",", postfix = "|") { it.name } + pattern
        synchronized(map) { map[key]?.let { return it } }
        val rx = if (options.isEmpty()) Regex(pattern) else Regex(pattern, options.toSet())
        synchronized(map) { map[key] = rx }
        return rx
    }

    /** For tests and memory pressure. */
    fun clear() = synchronized(map) { map.clear() }

    internal fun size(): Int = synchronized(map) { map.size }
}
