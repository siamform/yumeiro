/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.cache

import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.OnlineRow
import org.json.JSONArray
import org.json.JSONObject

/** JSON for the online models kept in [DataCache]. Pure; a bad entry reads as null (= reload). */
object OnlineCodec {
    private fun JSONObject.str(k: String): String? = if (isNull(k) || !has(k)) null else optString(k)
    private fun JSONObject.int(k: String): Int? = if (isNull(k) || !has(k)) null else optInt(k)
    private fun JSONObject.dbl(k: String): Double? = if (isNull(k) || !has(k)) null else optDouble(k).takeIf { !it.isNaN() }
    private fun map(o: JSONObject?): Map<String, String> = o?.keys()?.asSequence()?.associateWith { o.optString(it) }.orEmpty()
    private fun strings(a: JSONArray?): List<String> = a?.let { (0 until it.length()).map { i -> it.optString(i) } }.orEmpty()

    fun item(i: OnlineItem): JSONObject = JSONObject().put("n", i.name).put("u", i.url).put("a", i.api).put("p", i.posterUrl)
        .put("h", JSONObject(i.posterHeaders)).put("t", i.type).put("q", i.quality).put("s", i.score).put("d", JSONArray(i.dub.toList()))

    fun item(o: JSONObject): OnlineItem = OnlineItem(o.getString("n"), o.getString("u"), o.getString("a"), o.str("p"), map(o.optJSONObject("h")),
        o.str("t"), o.str("q"), o.dbl("s"), strings(o.optJSONArray("d")).toSet())

    fun items(list: List<OnlineItem>): String = JSONArray(list.map(::item)).toString()
    fun items(text: String): List<OnlineItem>? = runCatching { JSONArray(text).let { a -> (0 until a.length()).map { item(a.getJSONObject(it)) } } }.getOrNull()

    fun rows(rows: List<OnlineRow>): String = JSONArray(rows.map { r ->
        JSONObject().put("a", r.api).put("t", r.title).put("i", JSONArray(r.items.map(::item))).put("h", r.horizontal)
            .put("pn", r.pageName).put("pd", r.pageData).put("x", r.hasNext)
    }).toString()

    fun rows(text: String): List<OnlineRow>? = runCatching {
        val a = JSONArray(text)
        (0 until a.length()).map { k ->
            val o = a.getJSONObject(k)
            val its = o.getJSONArray("i")
            OnlineRow(o.getString("a"), o.optString("t"), (0 until its.length()).map { item(its.getJSONObject(it)) }, o.optBoolean("h"),
                o.str("pn"), o.str("pd"), o.optBoolean("x"))
        }
    }.getOrNull()

    private fun episode(e: OnlineEpisode) = JSONObject().put("d", e.data).put("n", e.name).put("s", e.season).put("e", e.episode)
        .put("p", e.posterUrl).put("x", e.description).put("r", e.runtimeMin)
    private fun episode(o: JSONObject) = OnlineEpisode(o.getString("d"), o.str("n"), o.int("s"), o.int("e"), o.str("p"), o.str("x"), o.int("r"))

    fun detail(d: OnlineDetail): String = JSONObject()
        .put("a", d.api).put("u", d.url).put("n", d.name).put("k", d.kind.name).put("t", d.type).put("p", d.posterUrl).put("b", d.backgroundUrl)
        .put("h", JSONObject(d.posterHeaders)).put("pl", d.plot).put("y", d.year).put("tg", JSONArray(d.tags)).put("dm", d.durationMin)
        .put("s", d.score).put("cr", d.contentRating).put("pd", d.playData)
        .put("ep", JSONObject().apply { d.episodes.forEach { (v, list) -> put(v, JSONArray(list.map(::episode))) } })
        .put("sn", JSONObject().apply { d.seasonNames.forEach { (k, v) -> put(k.toString(), v) } })
        .put("rc", JSONArray(d.recommendations.map(::item)))
        .put("mal", d.malId).put("al", d.aniListId).put("imdb", d.imdbId).put("tmdb", d.tmdbId).put("un", d.unsupportedReason)
        .toString()

    fun detail(text: String): OnlineDetail? = runCatching {
        val o = JSONObject(text)
        val ep = o.optJSONObject("ep") ?: JSONObject()
        val sn = o.optJSONObject("sn") ?: JSONObject()
        val rc = o.optJSONArray("rc") ?: JSONArray()
        OnlineDetail(
            o.getString("a"), o.getString("u"), o.getString("n"), runCatching { OnlineKind.valueOf(o.optString("k")) }.getOrDefault(OnlineKind.OTHER),
            o.optString("t"), o.str("p"), o.str("b"), map(o.optJSONObject("h")), o.str("pl"), o.int("y"), strings(o.optJSONArray("tg")), o.int("dm"),
            o.dbl("s"), o.str("cr"), o.str("pd"),
            ep.keys().asSequence().associateWith { v -> ep.getJSONArray(v).let { a -> (0 until a.length()).map { episode(a.getJSONObject(it)) } } },
            sn.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to sn.optString(k) } }.toMap(),
            (0 until rc.length()).map { item(rc.getJSONObject(it)) },
            o.int("mal"), o.int("al"), o.str("imdb"), o.int("tmdb"), o.str("un"),
        )
    }.getOrNull()
}
