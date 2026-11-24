/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.novel

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** NOVEL-1 (owner item 26): one LNReader plugin from a repository index (`plugins.min.json`). */
data class NovelPlugin(val id: String, val name: String, val site: String, val lang: String, val version: String, val url: String, val iconUrl: String) {
    /** LNReader writes languages with a left-to-right mark in front (`\u200eالعربية`). */
    val language: String get() = lang.replace("\u200e", "").trim()

    /** ISO code of [language] for translation, or null when unknown / several. */
    val languageCode: String? get() = LANGS[language.lowercase()]

    fun toJson(): JsonObject = JsonObject(mapOf(
        "id" to JsonPrimitive(id), "name" to JsonPrimitive(name), "site" to JsonPrimitive(site), "lang" to JsonPrimitive(lang),
        "version" to JsonPrimitive(version), "url" to JsonPrimitive(url), "iconUrl" to JsonPrimitive(iconUrl),
    ))

    companion object {
        /** The language names LNReader's index uses. */
        private val LANGS = mapOf(
            "english" to "en", "español" to "es", "spanish" to "es", "português" to "pt", "portuguese" to "pt", "français" to "fr", "french" to "fr",
            "deutsch" to "de", "german" to "de", "italiano" to "it", "italian" to "it", "русский" to "ru", "russian" to "ru", "العربية" to "ar", "arabic" to "ar",
            "日本語" to "ja", "japanese" to "ja", "中文" to "zh", "chinese" to "zh", "简体中文" to "zh", "繁體中文" to "zh", "한국어" to "ko", "korean" to "ko",
            "bahasa indonesia" to "id", "indonesian" to "id", "tiếng việt" to "vi", "vietnamese" to "vi", "türkçe" to "tr", "turkish" to "tr",
            "ไทย" to "th", "thai" to "th", "polski" to "pl", "polish" to "pl", "українська" to "uk", "ukrainian" to "uk", "hindi" to "hi", "हिन्दी" to "hi",
        )

        fun from(o: JsonObject): NovelPlugin? {
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val id = s("id"); val url = s("url")
            if (id.isBlank() || !url.startsWith("http")) return null
            return NovelPlugin(id, s("name").ifBlank { id }, s("site"), s("lang"), s("version"), url, s("iconUrl"))
        }
    }
}

data class NovelItem(val pluginId: String, val name: String, val path: String, val cover: String?)

data class NovelChapter(val name: String, val path: String, val number: Double?, val released: String?)

data class NovelDetails(
    val pluginId: String,
    val path: String,
    val name: String,
    val cover: String?,
    val author: String?,
    val status: String?,
    val summary: String?,
    val genres: List<String>,
    /** Reading order (first chapter first). */
    val chapters: List<NovelChapter>,
    /** LNReader `totalPages`: more chapter pages behind `parsePage` when > 1. */
    val totalPages: Int = 1,
)

/** Pure mapping of plugin answers (JSON) to Yumeiro's models. */
object NovelParse {
    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    fun absolute(site: String, path: String?): String? {
        val p = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (p.startsWith("http://") || p.startsWith("https://")) return p
        if (p.startsWith("//")) return "https:$p"
        if (site.isBlank()) return null
        return site.trimEnd('/') + "/" + p.trimStart('/')
    }

    fun items(pluginId: String, site: String, json: JsonElement): List<NovelItem> =
        (json as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val path = o.s("path") ?: return@mapNotNull null
            NovelItem(pluginId, o.s("name") ?: path, path, absolute(site, o.s("cover")))
        }.distinctBy { it.path }

    fun chapters(json: JsonElement?): List<NovelChapter> =
        (json as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val path = o.s("path") ?: return@mapNotNull null
            NovelChapter(o.s("name") ?: path, path, (o["chapterNumber"] as? JsonPrimitive)?.doubleOrNull, o.s("releaseTime"))
        }

    fun details(pluginId: String, site: String, path: String, json: JsonElement): NovelDetails? {
        val o = json as? JsonObject ?: return null
        val genres = when (val g = o["genres"]) {
            is JsonPrimitive -> g.contentOrNull.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            is JsonArray -> g.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }
            else -> emptyList()
        }
        return NovelDetails(
            pluginId, path, o.s("name") ?: path, absolute(site, o.s("cover")), o.s("author"), o.s("status"), o.s("summary"),
            genres, readingOrder(chapters(o["chapters"])), (o["totalPages"] as? JsonPrimitive)?.intOrNull?.coerceAtLeast(1) ?: 1,
        )
    }

    /**
     * Plugins list chapters oldest-first or newest-first. When the chapter numbers say newest-first, flip it;
     * otherwise keep the plugin's order (numbers are often missing and the plugin's order is then the only truth).
     */
    fun readingOrder(list: List<NovelChapter>): List<NovelChapter> {
        val nums = list.mapNotNull { it.number }
        if (list.size < 2 || nums.size < list.size / 2) return list
        val first = list.firstNotNullOfOrNull { it.number } ?: return list
        val last = list.lastOrNull { it.number != null }?.number ?: return list
        return if (first > last) list.asReversed().toList() else list
    }
}
