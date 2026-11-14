/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.drama

import dev.asfakul.yumeiro.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

    fun markDown(c: DramaCatalog, platform: String) { states[key(c, platform)] = Entry(State.DOWN, now()) }

    /** Probes [platform] with the first playable-looking title of [shows] (already fetched). */
    suspend fun check(c: DramaCatalog, platform: DramaPlatform, shows: List<DramaShow>): State {
        val names = listOf(platform.name, platform.slug)
        names.forEach { n -> states[key(c, n)]?.takeIf { now() - it.at < ttlMs }?.let { return it.state } }
        val lock = locks.getOrPut(key(c, platform.slug)) { Mutex() }
        return lock.withLock {
            states[key(c, platform.slug)]?.takeIf { now() - it.at < ttlMs }?.let { return@withLock it.state }
            if (shows.isEmpty()) return@withLock State.UNKNOWN
            // The shelf itself just loaded, so our network works: a studio that refuses, hangs or
            // 404s on its first two titles can't play for the user either. Only a local network
            // failure (no DNS / no route) leaves the shelf visible.
            var local = false
            var refusedHard = false
            var state = State.DOWN
            val results = coroutineScope {
                shows.take(2).map { sample ->
                    async {
                        withTimeoutOrNull(15_000) {
                            runCatching { c.streams(c.series(sample.url), 1).let { it.sources.isNotEmpty() && reach(it) } }
                        } ?: Result.failure(java.io.IOException("timeout"))
                    }
                }.awaitAll()
            }
            if (results.any { it.getOrNull() == true }) state = State.OK
            results.mapNotNull { it.exceptionOrNull() }.forEach { e ->
                if (localNetwork(e)) local = true
                if (refused(e)) refusedHard = true
            }
            if (state == State.DOWN && local && !refusedHard) state = State.UNKNOWN
            // Soft failures (timeouts) are rechecked after 10 minutes, refusals after 30.
            val at = when {
                state == State.UNKNOWN -> now() - ttlMs + 60_000L
                state == State.DOWN && !refusedHard -> now() - ttlMs + 10 * 60_000L
                else -> now()
            }
            val e = Entry(state, at)
            names.forEach { n -> states[key(c, n)] = e }
            state
        }
    }

    companion object {
        /** First source answers 2xx with media (not an HTML error page); first 2 KB only. */
        suspend fun defaultReach(st: DramaStreams): Boolean = withContext(Dispatchers.IO) {
            val url = st.sources.entries.maxByOrNull { it.key.toIntOrNull() ?: 0 }?.value ?: return@withContext false
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
            return Regex("""answered HTTP (4\d\d|5\d\d)""").containsMatchIn(m) || m.startsWith("No stream for episode") || m.contains("has no player") ||
                Regex("""\((4\d\d|5\d\d)\)""").containsMatchIn(m)
        }

        fun localNetwork(e: Throwable): Boolean = generateSequence(e) { it.cause }.any {
            it is java.net.UnknownHostException || it is java.net.ConnectException || it is java.net.NoRouteToHostException
        }

        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

        /** "The Lightbringer’s Return" and "The Lightbringer's Return (Dubbed)" → same key. */
        fun titleKey(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKD)
            .replace(Regex("""\((dubbed|dub|sub|subbed|english|en)\)""", RegexOption.IGNORE_CASE), "")
            .lowercase().filter { it.isLetterOrDigit() }
    }
}
