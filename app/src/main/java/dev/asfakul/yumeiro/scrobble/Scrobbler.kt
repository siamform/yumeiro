/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.scrobble

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dev.asfakul.yumeiro.data.model.PlaybackState
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.security.Privacy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Glue between the music player and Last.fm. Watches player snapshots through [ScrobbleTracker],
 * sends "now playing" best effort, and queues scrobbles on disk so listens made offline (or while
 * Last.fm is down) are sent later, oldest first, in batches of 50. Nothing is recorded while
 * incognito is on or when scrobbling is switched off.
 */
object Scrobbler {
    private const val TAG = "Scrobbler"
    private const val MAX_QUEUE = 500
    private var prefs: SharedPreferences? = null
    private val tracker = ScrobbleTracker()
    private val lock = Mutex()
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _pending = MutableStateFlow(0)
    /** Scrobbles waiting to be sent. */
    val pending: StateFlow<Int> = _pending.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("scrobble", Context.MODE_PRIVATE).also { prefs = it }
        _enabled.value = p.getBoolean("enabled", true)
        _pending.value = runCatching { JSONArray(p.getString("queue", "[]")).length() }.getOrDefault(0)
    }

    fun setEnabled(context: Context, v: Boolean) { init(context); _enabled.value = v; prefs?.edit()?.putBoolean("enabled", v)?.apply() }

    private fun active() = _enabled.value && LastFm.signedIn && !Privacy.incognito.value

    /** Starts watching [state] in [scope] (the player's own scope, so it stops with the player). */
    fun watch(context: Context, state: StateFlow<PlaybackState>, scope: CoroutineScope) {
        init(context)
        val app = context.applicationContext
        scope.launch {
            state.collect { s ->
                val t = s.currentTrack
                val events = tracker.feed(
                    t?.let { "${s.currentQueueIndex}:${it.id}" }, t?.artist.orEmpty(), t?.title.orEmpty(), t?.album.orEmpty(),
                    s.isPlaying, s.currentPositionMs, s.durationMs.takeIf { it > 0 } ?: (t?.durationMs ?: 0L), System.currentTimeMillis(),
                )
                if (events.isEmpty() || !active()) return@collect
                launch(Dispatchers.IO) {
                    for (e in events) when (e) {
                        is ScrobbleTracker.Event.NowPlaying -> runCatching { LastFm.nowPlaying(NetworkClient.get(app), e.play) }
                            .onFailure { Log.d(TAG, "now playing: ${it.message}") }
                        is ScrobbleTracker.Event.Scrobble -> { enqueue(e.play); flush(app) }
                    }
                }
            }
        }
    }

    internal fun encode(p: ScrobblePlay) = JSONObject().put("a", p.artist).put("t", p.track).put("al", p.album).put("d", p.durationSec).put("s", p.startedAtSec)
    internal fun decode(o: JSONObject) = ScrobblePlay(o.optString("a"), o.optString("t"), o.optString("al"), o.optInt("d"), o.optLong("s"))

    private fun readQueue(): MutableList<ScrobblePlay> = runCatching {
        val a = JSONArray(prefs?.getString("queue", "[]") ?: "[]")
        MutableList(a.length()) { decode(a.getJSONObject(it)) }
    }.getOrElse { mutableListOf() }

    private fun writeQueue(q: List<ScrobblePlay>) {
        prefs?.edit()?.putString("queue", JSONArray().apply { q.forEach { put(encode(it)) } }.toString())?.apply()
        _pending.value = q.size
    }

    private suspend fun enqueue(p: ScrobblePlay) = lock.withLock { writeQueue((readQueue() + p).takeLast(MAX_QUEUE)) }

    /** Sends queued scrobbles. Safe to call any time (app start, network back, after a listen). */
    suspend fun flush(context: Context) {
        init(context)
        if (!active()) return
        lock.withLock {
            var q = readQueue()
            // Last.fm rejects timestamps older than 14 days; drop them instead of retrying forever.
            val cutoff = System.currentTimeMillis() / 1000 - 14 * 86_400
            q = q.filter { it.startedAtSec >= cutoff }.toMutableList()
            while (q.isNotEmpty()) {
                val batch = q.take(LastFm.MAX_BATCH)
                try {
                    LastFm.scrobble(NetworkClient.get(context), batch)
                } catch (e: LastFm.ApiException) {
                    if (e.badSession) { LastFm.signOut(context); break }
                    if (e.transient) break
                    Log.w(TAG, "dropping batch: ${e.code} ${e.message}")
                } catch (e: Exception) {
                    Log.d(TAG, "offline, keeping ${q.size}: ${e.message}"); break
                }
                q = q.drop(batch.size).toMutableList()
            }
            writeQueue(q)
        }
    }
}
