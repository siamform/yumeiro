/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.drama

import dev.asfakul.yumeiro.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

// Precompiled once (never during composition, scrolling or per call): see docs/STABILITY-R15.md.
// R15: only 4xx (and 500/501) mean "refused". 502/503/504/429 are the host being busy (ONShort
// answers 15 s bursts of 502): those never hide a studio, they are retried by OnShortGate.
private val RX_DRAMAHEALTH_1 = Regex("""answered HTTP (4(?!29)\d\d|50[01])""")
private val RX_DRAMAHEALTH_BUSY = Regex("""HTTP (429|502|503|504)\b""")
private val RX_DRAMAHEALTH_2 = Regex("""\((4(?!29)\d\d|50[01])\)""")

/**
 * Round 2: which drama studios can really play right now.
 *
 * An index such as ONShort keeps listing a studio's titles after the studio stops answering it
 * (on 2026-11-14 ShortMax, FlexTV and nine others answered "invalid or missing access code"; the
 * site's own web player failed too). Tapping such a title can never play, so a studio is probed
 * once (first title, episode 1) and hidden while it is down. Only a refusal from the server
 * counts as down; a network error on our side does not hide anything. Results last 30 minutes,
 * one probe per studio at a time.
 */
class DramaHealth(
    private val ttlMs: Long = 30 * 60_000L,
    private val now: () -> Long = System::currentTimeMillis,
    /** Asks the video server itself: an index can hand out signed links its CDN refuses (DramaBite: 403). */
    private val reach: suspend (DramaStreams) -> Boolean = ::defaultReach,
    /** Budget per probe sample; the probe runs in the background, so a slow site still finishes. */
    private val probeMs: Long = 90_000L,
) {
    enum class State { OK, DOWN, UNKNOWN }

    private data class Entry(val state: State, val at: Long)
    private val states = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun key(c: DramaCatalog, platform: String) = "${c.id}|${norm(platform)}"

    fun isDown(c: DramaCatalog, platform: String?): Boolean {
        if (platform.isNullOrBlank()) return false
        val e = states[key(c, platform)] ?: return false
        return e.state == State.DOWN && now() - e.at < ttlMs
    }

    /** False only when the video server clearly refuses; a slow answer is left to the player. */
    suspend fun playable(st: DramaStreams): Boolean =
        st.sources.isNotEmpty() && (withTimeoutOrNull(10_000) { runCatching { reach(st) }.getOrElse { localNetwork(it) } } ?: true)

    /** Titles whose own episode was refused (a studio can serve some titles and not others). */
    private val deadTitles = ConcurrentHashMap<String, Long>()
    fun markTitleDown(url: String) { deadTitles[url] = now() }
    fun isTitleDown(url: String): Boolean = deadTitles[url]?.let { now() - it < ttlMs } == true

    fun markDown(c: DramaCatalog, platform: String) { states[key(c, platform)] = Entry(State.DOWN, now()) }

    /**
     * R15 system design: studio health is a background service, not part of loading a shelf.
     *  - A probe runs in [scope] with its own generous budget ([probeMs] per sample), so a slow
     *    site can finish the probe even though the shelf call that asked for it timed out.
     *  - The shelf waits at most [waitMs] for the verdict; while a probe is still running the last
     *    saved verdict ([saved], persisted by [persist]) is used, else the shelf is shown.
     *  - [prewarm] probes every studio at app start, so the verdicts exist before Yumix opens.
     */
    // R16: every probe call goes through OnShortGate's background lane, behind any user request.
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO + GateLane(background = true))
    private val running = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<State>>()
    /** Verdicts from the last run (key → state, time); used while a new probe is still running. */
    @Volatile var saved: Map<String, Pair<State, Long>> = emptyMap()
    /** Called with every finished verdict (key, state, time) to keep it across restarts. */
    var persist: ((String, State, Long) -> Unit)? = null

    suspend fun check(c: DramaCatalog, platform: DramaPlatform, shows: List<DramaShow>, waitMs: Long = 12_000L): State {
        val names = listOf(platform.name, platform.slug)
        names.forEach { n -> states[key(c, n)]?.takeIf { now() - it.at < ttlMs }?.let { return it.state } }
        val k = key(c, platform.slug)
        saved[k]?.takeIf { now() - it.second < ttlMs && it.first != State.UNKNOWN }?.let { (st, at) ->
            names.forEach { n -> states[key(c, n)] = Entry(st, at) }
            return st
        }
        if (shows.isEmpty()) return State.UNKNOWN
        // R15: lazy + putIfAbsent, never computeIfAbsent: a probe that finishes at once removes
        // itself from [running] inside the map update ("Recursive update" in DramaHealthTest).
        val fresh = scope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) { probe(c, platform, shows) }
        val job = running.putIfAbsent(k, fresh)?.also { fresh.cancel() }
            ?: fresh.also { d -> d.invokeOnCompletion { running.remove(k, d) }; d.start() }
        return withTimeoutOrNull(waitMs) { job.await() }
            ?: saved[k]?.takeIf { now() - it.second < 24 * 60 * 60_000L }?.first
            ?: State.UNKNOWN
    }

    /** Probes every studio of [c] in the background (app start); verdicts land in [states]. */
    fun prewarm(c: DramaCatalog): kotlinx.coroutines.Job = scope.launch {
        c.platforms.forEach { p ->
            launch {
                runCatching {
                    if (states[key(c, p.slug)]?.let { now() - it.at < ttlMs } == true) return@runCatching
                    check(c, p, c.shelf(p, 1), waitMs = Long.MAX_VALUE)
                }
            }
        }
    }

    private suspend fun probe(c: DramaCatalog, platform: DramaPlatform, shows: List<DramaShow>): State {
        val names = listOf(platform.name, platform.slug)
        val lock = locks.getOrPut(key(c, platform.slug)) { Mutex() }
        return lock.withLock {
            states[key(c, platform.slug)]?.takeIf { now() - it.at < ttlMs }?.let { return@withLock it.state }
            // The shelf itself just loaded, so our network works: a studio that refuses, hangs or
            // 404s on its first two titles can't play for the user either. Only a local network
            // failure (no DNS / no route) leaves the shelf visible.
            var local = false
            var refusedHard = false
            var state = State.DOWN
            val results = coroutineScope {
                shows.take(2).map { sample ->
                    async {
                        // The series page answered but episode 1 never did within the whole budget:
                        // that is the studio hanging (ShortMax, 2026-11-14), not our queue.
                        var pageOk = false
                        withTimeoutOrNull(probeMs) {
                            runCatching { c.streams(c.series(sample.url).also { pageOk = true }, 1).let { it.sources.isNotEmpty() && reach(it) } }
                        } ?: Result.failure(if (pageOk) EpisodeHung() else ProbeBudget())
                    }
                }.awaitAll()
            }
            if (results.any { it.getOrNull() == true }) state = State.OK
            results.mapNotNull { it.exceptionOrNull() }.forEach { e ->
                if (localNetwork(e)) local = true
                if (refused(e)) refusedHard = true
            }
            // R15: only a clear refusal, or every sample hanging while the host was not busy,
            // hides a studio. A busy host (502 burst) used to hide it for 10 minutes, which is why
            // dramas "often do not appear in the feed at all".
            // Busy = this studio's own samples said so (HTTP 429/5xx). Our probe budget running
            // out (calls queued behind other studios) is inconclusive, never "hung".
            val errors = results.mapNotNull { it.exceptionOrNull() }
            val busy = errors.any { busy(it) || it is ProbeBudget }
            val hung = results.isNotEmpty() && results.all { r -> r.exceptionOrNull()?.let { it !is ProbeBudget && hung(it) } == true }
            if (state == State.DOWN && !refusedHard && !(hung && !busy && !local)) state = State.UNKNOWN
            val at = when {
                state == State.UNKNOWN -> now() - ttlMs + 60_000L
                // Every sample hung while the shelf itself answered: hidden, rechecked in 5 minutes.
                state == State.DOWN && !refusedHard -> now() - ttlMs + 5 * 60_000L
                else -> now()
            }
            val e = Entry(state, at)
            names.forEach { n -> states[key(c, n)] = e }
            if (state != State.UNKNOWN) persist?.invoke(key(c, platform.slug), state, at)
            state
        }
    }

    /** The series page answered, episode 1 didn't within the whole probe budget. */
    class EpisodeHung : java.io.InterruptedIOException("episode never answered")

    /** The probe's own time budget ran out (not an answer from the studio). */
    class ProbeBudget : java.io.IOException("health probe budget used up")

    companion object {
        /** First source answers 2xx with media (not an HTML error page); first 2 KB only. */
        suspend fun defaultReach(st: DramaStreams): Boolean = withContext(Dispatchers.IO) {
            val url = st.sources.entries.maxByOrNull { DramaStreams.qualityOf(it.key) }?.value ?: return@withContext false
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36")
                .header("Range", "bytes=0-2047")
                .apply { st.referer?.let { header("Referer", it) } }
                .build()
            NetworkClient.get().newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Stream answered HTTP ${r.code}")
                val head = r.body.source().let { src -> src.request(64); src.buffer.snapshot().utf8().take(64) }
                !head.trimStart().startsWith("<")
            }
        }


        /** The server answered and said no (HTTP 4xx / 5xx, or no stream in a valid answer). */
        fun refused(e: Throwable): Boolean {
            val m = e.message.orEmpty()
            return RX_DRAMAHEALTH_1.containsMatchIn(m) || m.startsWith("No stream for episode") || m.contains("has no player") ||
                RX_DRAMAHEALTH_2.containsMatchIn(m)
        }

        /** The host is overloaded (HTTP 429 / 502 / 503 / 504): retry later, never hide. */
        fun busy(e: Throwable): Boolean = RX_DRAMAHEALTH_BUSY.containsMatchIn(e.message.orEmpty())

        /** No answer in time (a hung studio such as ShortMax on 2026-11-14). */
        fun hung(e: Throwable): Boolean = generateSequence(e) { it.cause }.any {
            it is java.io.InterruptedIOException || it is kotlinx.coroutines.TimeoutCancellationException || it.message == "timeout"
        } || e.message.orEmpty().contains("Timed out waiting")

        fun localNetwork(e: Throwable): Boolean = generateSequence(e) { it.cause }.any {
            it is java.net.UnknownHostException || it is java.net.ConnectException || it is java.net.NoRouteToHostException
        }

        // R15: precompiled (titleKey runs for every drama row).
        private val DUB_SUFFIX = Regex("""\((dubbed|dub|sub|subbed|english|en)\)""", RegexOption.IGNORE_CASE)

        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

        /** "The Lightbringer’s Return" and "The Lightbringer's Return (Dubbed)" → same key. */
        fun titleKey(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKD)
            .replace(DUB_SUFFIX, "")
            .lowercase().filter { it.isLetterOrDigit() }
    }
}
