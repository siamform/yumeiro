/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.anticipation

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * B23 (ADR-B23-1): what the Anticipation Engine keeps across restarts, per profile. Only facts that stay true:
 * - [PrepData.series]: when each series was last watched (the 7-day cleanup and "active series first");
 * - [PrepData.misses]: episodes no source had (not searched again for [AnticipationPolicy.MISS_MS]);
 * - [PrepData.matches]: which other extension has the series and at which address (no repeated searches).
 * Never links (they expire), never history, downloads or bookmarks (those live in their own stores, untouched).
 */
@Serializable
data class PrepData(
    val series: Map<String, Long> = emptyMap(),
    val misses: Map<String, Miss> = emptyMap(),
    val matches: Map<String, Match> = emptyMap(),
) {
    @Serializable data class Miss(val series: String, val at: Long)
    @Serializable data class Match(val series: String, val api: String, val url: String, val at: Long)

    fun touch(series: String, now: Long): PrepData = copy(series = (this.series + (series to now)).newest(MAX_SERIES) { it })

    fun miss(series: String, episode: String, now: Long): PrepData =
        copy(misses = (misses + (episode to Miss(series, now))).newest(MAX_MISSES) { it.at })

    fun isMiss(episode: String, now: Long): Boolean = misses[episode]?.let { now - it.at in 0 until AnticipationPolicy.MISS_MS } == true

    fun match(series: String, api: String, url: String, now: Long): PrepData =
        copy(matches = (matches + ("$series\n$api" to Match(series, api, url, now))).newest(MAX_MATCHES) { it.at })

    fun matchesFor(series: String): List<Match> = matches.values.filter { it.series == series }.sortedByDescending { it.at }

    /** Drops series not watched for 7 days with their misses and matches, and misses that are no longer fresh. */
    fun cleaned(now: Long): PrepData {
        val keep = series.filterValues { now - it < AnticipationPolicy.INACTIVE_MS }
        return PrepData(
            series = keep,
            misses = misses.filter { (_, m) -> m.series in keep && now - m.at in 0 until AnticipationPolicy.MISS_MS },
            matches = matches.filter { (_, m) -> m.series in keep },
        )
    }

    companion object {
        const val MAX_SERIES = 100
        const val MAX_MISSES = 200
        const val MAX_MATCHES = 100
        private fun <V> Map<String, V>.newest(max: Int, at: (V) -> Long): Map<String, V> =
            if (size <= max) this else entries.sortedByDescending { at(it.value) }.take(max).associate { it.key to it.value }
    }
}

/** The file side of [PrepData]: one small JSON file per profile, written whole (temp file, then rename). */
object PrepStore {
    const val FILE = "anticipation.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = Any()
    private var profile: String? = null
    private var data = PrepData()

    fun read(file: File): PrepData = runCatching { if (file.isFile) json.decodeFromString(PrepData.serializer(), file.readText()) else PrepData() }.getOrDefault(PrepData())

    fun write(file: File, d: PrepData) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "$FILE.tmp")
            tmp.writeText(json.encodeToString(PrepData.serializer(), d))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    private fun fileOf(context: Context) = dev.asfakul.yumeiro.profiles.Profiles.file(context, FILE)

    /** The open profile's data (read once per profile). Disk: call off the main thread. */
    fun get(context: Context): PrepData = synchronized(lock) {
        val id = dev.asfakul.yumeiro.profiles.Profiles.currentId(context)
        if (profile != id) { profile = id; data = read(fileOf(context)) }
        data
    }

    /** Changes the open profile's data and saves it. Disk: call off the main thread. */
    fun update(context: Context, change: (PrepData) -> PrepData) = synchronized(lock) {
        val before = get(context)
        val after = change(before)
        if (after != before) { data = after; write(fileOf(context), after) }
    }

    /** Startup and daily: the 7-day cleanup for the open profile. */
    fun cleanup(context: Context, now: Long = System.currentTimeMillis()) = update(context) { it.cleaned(now) }

    internal fun resetForTest() = synchronized(lock) { profile = null; data = PrepData() }
}
