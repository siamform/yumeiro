/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel.ai

import android.content.Context
import android.util.Log
import dev.asfakul.yumeiro.data.SafeJsonFile
import dev.asfakul.yumeiro.intel.Discovery
import dev.asfakul.yumeiro.intel.MediaCategory
import dev.asfakul.yumeiro.profiles.Profiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * B21: the assistant's conversations, per profile (`intel/chats.json` in the profile's folder, the last 20).
 * Incognito chats live in memory only and are gone when the app closes.
 */
object AssistantChats {
    private const val TAG = "AssistantChats"
    const val FILE = "intel/chats.json"
    private const val KEEP = 20
    private const val TURNS = 40

    data class Turn(val user: Boolean, val text: String, val answer: Assistant.Answer? = null, val at: Long = 0, val error: Boolean = false)
    data class Chat(val id: String, val title: String, val category: MediaCategory?, val turns: List<Turn>, val at: Long)

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()
    @Volatile private var loadedFor: String? = null
    private val lock = Any()

    fun ensure(c: Context) {
        val id = Profiles.currentId(c)
        if (loadedFor == id) return
        synchronized(lock) {
            if (loadedFor == id) return
            _chats.value = SafeJsonFile.read(Profiles.file(c, FILE)) { JSONObject(it) }.value?.optJSONArray("chats")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::decode) }
            }.orEmpty()
            loadedFor = id
        }
    }

    fun get(c: Context, id: String): Chat? { ensure(c); return _chats.value.firstOrNull { it.id == id } }

    /** Saves [chat] (newest first; the oldest beyond 20 are dropped). */
    fun put(c: Context, chat: Chat) {
        ensure(c)
        synchronized(lock) {
            _chats.value = (listOf(chat.copy(turns = chat.turns.takeLast(TURNS))) + _chats.value.filter { it.id != chat.id }).sortedByDescending { it.at }.take(KEEP)
        }
        save(c)
    }

    fun delete(c: Context, id: String) {
        ensure(c)
        synchronized(lock) { _chats.value = _chats.value.filter { it.id != id } }
        save(c)
    }

    fun clear(c: Context) { ensure(c); synchronized(lock) { _chats.value = emptyList() }; save(c) }

    /** Titles the assistant suggested in this profile's recent chats (not repeated without a reason). */
    fun recentlySuggested(c: Context, chats: Int = 3): List<String> {
        ensure(c)
        return _chats.value.take(chats).flatMap { ch -> ch.turns.mapNotNull { it.answer }.flatMap { a -> a.picks.map { it.title } } }.distinct().take(40)
    }

    fun unload() = synchronized(lock) { loadedFor = null; _chats.value = emptyList() }

    private fun save(c: Context) {
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        val text = JSONObject().put("v", 1).put("chats", JSONArray().apply { _chats.value.forEach { put(encode(it)) } }).toString()
        runCatching { SafeJsonFile.write(Profiles.file(c, FILE), text) }.onFailure { Log.w(TAG, "save failed: ${it.javaClass.simpleName}") }
    }

    // region JSON

    internal fun encode(ch: Chat): JSONObject = JSONObject().put("id", ch.id).put("t", ch.title).put("at", ch.at).apply { ch.category?.let { put("c", it.code) } }
        .put("turns", JSONArray().apply {
            ch.turns.forEach { t ->
                put(JSONObject().put("u", t.user).put("x", t.text).put("at", t.at).apply { if (t.error) put("e", true); t.answer?.let { put("a", encodeAnswer(it)) } })
            }
        })

    internal fun decode(o: JSONObject): Chat? {
        val id = o.optString("id").ifBlank { return null }
        val turns = o.optJSONArray("turns")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { t ->
                Turn(t.optBoolean("u"), t.optString("x"), t.optJSONObject("a")?.let(::decodeAnswer), t.optLong("at"), t.optBoolean("e"))
            }
        }.orEmpty()
        return Chat(id, o.optString("t"), MediaCategory.of(o.optString("c").ifBlank { null }), turns, o.optLong("at"))
    }

    private fun encodeAnswer(a: Assistant.Answer): JSONObject = JSONObject().put("r", a.reply).put("m", a.model).apply {
        if (a.clarify.isNotBlank()) put("q", a.clarify)
        a.research?.let { r -> put("rs", JSONObject().put("al", r.anilist).put("tm", r.tmdb).put("w", r.web).put("n", r.evidence).put("s", JSONArray(r.seeds)).put("g", JSONArray(r.tags))) }
        put("src", JSONArray().apply { a.sources.forEach { put(JSONObject().put("t", it.title).put("u", it.url)) } })
        put("s", JSONArray().apply {
            a.sections.forEach { s ->
                put(JSONObject().put("n", s.name).put("p", JSONArray().apply {
                    s.picks.forEach { p ->
                        put(JSONObject().put("t", p.title).put("v", p.verified).put("why", p.why).put("dif", p.differences).put("rec", p.reception)
                            .put("dis", p.discovery).put("conf", p.confidence).put("tags", JSONArray(p.tags)).apply {
                                p.year?.let { put("y", it) }; p.category?.let { put("c", it.code) }; p.note?.let { put("note", it) }
                                p.rec?.let { put("r", Discovery.encodeRec(it)) }
                            })
                    }
                }))
            }
        })
    }

    private fun decodeAnswer(o: JSONObject): Assistant.Answer {
        fun strs(a: JSONArray?) = a?.let { x -> (0 until x.length()).map { x.optString(it) } }.orEmpty()
        val sections = o.optJSONArray("s")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { s ->
                Assistant.Section(s.optString("n"), s.optJSONArray("p")?.let { p ->
                    (0 until p.length()).mapNotNull { p.optJSONObject(it) }.map { x ->
                        val cat = MediaCategory.of(x.optString("c").ifBlank { null })
                        Assistant.Pick(x.optString("t"), x.optInt("y").takeIf { it > 0 }, cat, strs(x.optJSONArray("tags")), x.optString("why"), x.optString("dif"),
                            x.optString("rec"), x.optString("dis"), x.optString("conf"), x.optBoolean("v"),
                            x.optJSONObject("r")?.let { r -> cat?.let { Discovery.decodeRec(it, r) } }, x.optString("note").ifBlank { null })
                    }
                }.orEmpty())
            }
        }.orEmpty()
        val rs = o.optJSONObject("rs")?.let { r -> Assistant.Research(r.optBoolean("al"), r.optBoolean("tm"), r.optBoolean("w"), r.optInt("n"), strs(r.optJSONArray("s")), strs(r.optJSONArray("g"))) }
        val src = o.optJSONArray("src")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { AiSource(it.optString("t"), it.optString("u")) } }.orEmpty()
        return Assistant.Answer(o.optString("r"), o.optString("q"), sections, src, rs, o.optString("m"))
    }

    // endregion
}
