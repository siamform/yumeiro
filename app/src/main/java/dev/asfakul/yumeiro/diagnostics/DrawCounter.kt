package dev.asfakul.yumeiro.diagnostics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * FREEZE-4: how often big screens recompose. The 2.0.0 freeze dump showed Home recomputing while Live
 * TV played, and only a real phone can say which input changed. Screens call [hit] from a SideEffect
 * (one map lookup and an increment on the main thread); [MemoryWatch] drains the counts into its
 * 30-second samples, so a Freeze / crash report shows "draws home=…" for the time before it.
 */
object DrawCounter {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    fun hit(key: String) { counts.getOrPut(key) { AtomicInteger() }.incrementAndGet() }

    /** "draws home=42 online=3" since the last drain (keys sorted), or "" when nothing drew. */
    fun drain(): String {
        val parts = counts.keys.sorted().mapNotNull { k -> counts[k]?.getAndSet(0)?.takeIf { it > 0 }?.let { "$k=$it" } }
        return if (parts.isEmpty()) "" else "draws " + parts.joinToString(" ")
    }

    internal fun resetForTests() = counts.clear()
}
