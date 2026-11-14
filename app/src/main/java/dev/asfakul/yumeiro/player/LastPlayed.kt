/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import dev.asfakul.yumeiro.data.model.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Round 13: Home → Continue ("Jump back in") shows the item really played last, at the position it
 * was left, for songs from any source (device, YouTube Music, servers) and for YouTube videos,
 * which the device and online histories don't time-stamp.
 */
object LastPlayed {
    private const val PREFS = "yu_last_played"

    data class Audio(val track: AudioTrack, val positionMs: Long, val durationMs: Long, val at: Long) {
        val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    private val _audio = MutableStateFlow<Audio?>(null)
    val audio: StateFlow<Audio?> = _audio.asStateFlow()
    /** YouTube video id → when it was last opened. */
    private val _ytAt = MutableStateFlow<Map<String, Long>>(emptyMap())
    val ytAt: StateFlow<Map<String, Long>> = _ytAt.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _audio.value = runCatching { decodeAudio(p.getString("audio", null)) }.getOrNull()
        _ytAt.value = runCatching { JSONObject(p.getString("yt", null) ?: "{}").let { o -> o.keys().asSequence().associateWith { o.getLong(it) } } }.getOrDefault(emptyMap())
    }

    fun saveAudio(context: Context, track: AudioTrack, positionMs: Long, durationMs: Long) {
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        init(context)
        val a = Audio(track, positionMs.coerceAtLeast(0L), durationMs.coerceAtLeast(0L), System.currentTimeMillis())
        _audio.value = a
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("audio", encodeAudio(a)).apply()
    }

    fun markYouTube(context: Context, id: String) {
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        init(context)
        val next = (_ytAt.value + (id to System.currentTimeMillis())).entries.sortedByDescending { it.value }.take(60).associate { it.key to it.value }
        _ytAt.value = next
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("yt", JSONObject(next as Map<*, *>).toString()).apply()
    }

    fun clear(context: Context) {
        _audio.value = null; _ytAt.value = emptyMap()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    internal fun encodeAudio(a: Audio): String = JSONObject()
        .put("id", a.track.id).put("t", a.track.title).put("ar", a.track.artist).put("al", a.track.album)
        .put("d", a.track.durationMs).put("u", a.track.uri).put("art", a.track.albumArtUri ?: "").put("aid", a.track.albumId)
        .put("f", a.track.folderName).put("fp", a.track.folderPath)
        .put("pos", a.positionMs).put("dur", a.durationMs).put("at", a.at).toString()

    internal fun decodeAudio(raw: String?): Audio? {
        if (raw.isNullOrBlank()) return null
        val o = JSONObject(raw)
        val t = AudioTrack(
            id = o.getString("id"), title = o.optString("t"), artist = o.optString("ar"), album = o.optString("al"),
            durationMs = o.optLong("d"), uri = o.optString("u"), albumArtUri = o.optString("art").ifBlank { null },
            albumId = o.optLong("aid"), folderName = o.optString("f", "Music"), folderPath = o.optString("fp"),
        )
        if (t.uri.isBlank()) return null
        return Audio(t, o.optLong("pos"), o.optLong("dur"), o.optLong("at"))
    }
}
