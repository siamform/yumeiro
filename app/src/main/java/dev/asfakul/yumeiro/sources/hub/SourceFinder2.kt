/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.hub

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FIND-2 (owner item 26, Source Finding V2; ADR-066). Beside V1 (the E1-B fan-out in [YumeiroCatalogProvider], which
 * asks up to 12 sources at once and waits for all of them): a sweep after Zangetsu's play-time resolver
 * (lib/core/zmode/playback_resolver.dart, studied at 4aae490):
 *  - Remembered first: the source and page that played this title before are asked alone first, with no search.
 *  - Waves of [WAVE]: built-in parts, then the ranked sources three at a time. A wave in which a source found links
 *    gives the others [GRACE_MS] to add theirs, then the sweep ends; nothing after it is asked.
 *  - Equal patience ([BUDGET_MS]) for every source, more for the remembered one ([CHOSEN_MS]), so "the sweep said
 *    no, picking the source said yes" cannot happen because of an early cut.
 *  - A true reason per source ([Reason]); when nothing plays the user reads what actually happened ([detail]) and
 *    not "No playable sources were found" when, say, two sources were only slow.
 * Everything that decides is pure and unit-tested; [sweep] only runs it.
 */
object SourceFinder2 {
    const val WAVE = 3
    const val BUDGET_MS = 20_000L
    const val CHOSEN_MS = 25_000L
    const val GRACE_MS = 3_000L
    const val TOTAL_MS = 75_000L

    enum class Reason { FOUND, TIMED_OUT, BLOCKED, FAILED, NO_TITLE_MATCH, EPISODE_MISSING, NO_LINKS }

    data class Outcome(val source: String, val reason: Reason, val ms: Long)

    /** Thrown when nothing plays; its message is what the player shows (ExtensionException passes it through). */
    class NotFound(message: String) : dev.asfakul.yumeiro.online.ExtensionManager.ExtensionException(message)

    /** Pure: the order the sweep asks in. Built-ins form their own first wave; the remembered source leads the rest. */
    fun <T> waves(builtins: List<T>, ranked: List<T>, remembered: T?, size: Int = WAVE): List<List<T>> {
        val known = remembered?.takeIf { it in ranked }
        return buildList {
            if (builtins.isNotEmpty()) add(builtins)
            // The source that played this title before is asked alone (its page is known: no search).
            known?.let { add(listOf(it)) }
            ranked.filter { it != known }.chunked(size.coerceAtLeast(1)).forEach { add(it) }
        }
    }

    /** Pure: a failure's reason from what was thrown (null = the time budget ran out). */
    fun reasonOf(e: Throwable?): Reason {
        if (e == null || e is TimeoutCancellationException || e is java.net.SocketTimeoutException) return Reason.TIMED_OUT
        val m = generateSequence(e) { it.cause }.take(6).joinToString(" ") { it.message.orEmpty() }.lowercase()
        return if ("cloudflare" in m || "403" in m || "captcha" in m || "security check" in m || "blocked" in m) Reason.BLOCKED else Reason.FAILED
    }

    private fun names(o: List<Outcome>, r: Reason): String? {
        val n = o.filter { it.reason == r }.map { it.source }.distinct()
        return when (n.size) { 0 -> null; 1 -> n[0]; 2 -> "${n[0]} and ${n[1]}"; else -> "${n[0]} and ${n.size - 1} others" }
    }

    /** Pure (after Zangetsu's sweepFailureDetail): one true sentence about why nothing plays. */
    fun detail(o: List<Outcome>, sources: Int): String {
        if (sources == 0) return "Install a video source to play this title. Yumeiro finds it in your sources."
        names(o, Reason.TIMED_OUT)?.let { return "$it took too long to answer. Try again, or open the title in that source." }
        names(o, Reason.BLOCKED)?.let { return "$it is blocking this phone for now (site security). Try again later." }
        names(o, Reason.NO_LINKS)?.let { return "$it has this title but gave no playable links." }
        names(o, Reason.EPISODE_MISSING)?.let { return "$it has this title but not this episode yet." }
        names(o, Reason.FAILED)?.let { return "$it ran into an error. Try again in a moment." }
        return "None of your $sources sources has this title."
    }

    // ── Remembered matches (title → source + page), prefs "yu_find2", newest 300 ─────────────────────────────────
    private const val PREFS = "yu_find2"
    private const val MAX = 300
    private val memory = ConcurrentHashMap<String, Pair<String, String>>()
    @Volatile private var ctx: Context? = null

    /** Loads the saved matches. Call off the main thread. */
    fun init(c: Context) {
        ctx = c.applicationContext
        runCatching {
            ctx!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                (v as? String)?.split('\u0001')?.takeIf { it.size == 2 }?.let { memory.putIfAbsent(k, it[0] to it[1]) }
            }
        }
    }

    fun recall(titleKey: String): Pair<String, String>? = memory[titleKey]

    fun remember(titleKey: String, source: String, pageUrl: String) {
        if (memory[titleKey] == (source to pageUrl)) return
        if (memory.size >= MAX) memory.keys.firstOrNull()?.let { memory.remove(it) }
        memory[titleKey] = source to pageUrl
        ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(titleKey, "$source\u0001$pageUrl")?.apply()
    }

    fun forget(titleKey: String) {
        memory.remove(titleKey)
        ctx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.remove(titleKey)?.apply()
    }

    internal fun resetForTests() { memory.clear(); ctx = null }

    /** What one source ask returns: its reason, and the page that matched (to remember). */
    data class Answer(val reason: Reason, val pageUrl: String? = null)

    /**
     * Runs the sweep. [ask] asks one source (it streams links itself); [name] names it; [isChosen] gets [CHOSEN_MS].
     * Returns the outcomes; the caller decides found / not found. Measured numbers go to [onMeasure].
     */
    suspend fun <T> sweep(
        waves: List<List<T>>, name: (T) -> String, isChosen: (T) -> Boolean,
        ask: suspend (T) -> Answer, onFound: (T, String?) -> Unit,
        budgetMs: Long = BUDGET_MS, chosenMs: Long = CHOSEN_MS, graceMs: Long = GRACE_MS, totalMs: Long = TOTAL_MS,
    ): List<Outcome> {
        val outcomes = java.util.Collections.synchronizedList(ArrayList<Outcome>())
        withTimeoutOrNull(totalMs) {
            for (wave in waves) {
                val found = AtomicBoolean(false)
                val firstHit = CompletableDeferred<Unit>()
                coroutineScope {
                    val jobs = wave.map { s ->
                        async {
                            val t0 = System.currentTimeMillis()
                            val limit = if (isChosen(s)) chosenMs else budgetMs
                            var err: Throwable? = null
                            val a = withTimeoutOrNull(limit) {
                                try { ask(s) } catch (e: Throwable) {
                                    if (e is CancellationException && e !is TimeoutCancellationException) throw e
                                    err = e; null
                                }
                            }
                            val reason = a?.reason ?: reasonOf(err)
                            outcomes += Outcome(name(s), reason, System.currentTimeMillis() - t0)
                            if (reason == Reason.FOUND) { found.set(true); onFound(s, a?.pageUrl); firstHit.complete(Unit) }
                        }
                    }
                    // A hit: the rest of this wave gets [graceMs] to add alternatives, then it is cut.
                    val cutter = launch {
                        firstHit.await()
                        kotlinx.coroutines.delay(graceMs)
                        jobs.forEach { it.cancel() }
                    }
                    runCatching { jobs.awaitAll() }
                    cutter.cancel()
                }
                // A source cut by the grace was still answering; it is not counted against it.
                if (found.get()) break
            }
        }
        return outcomes.toList()
    }
}
