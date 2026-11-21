/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.MainAPI
import dev.asfakul.yumeiro.sources.SourceHub

/**
 * F-21: one label for every extension row (market, installed list, "Show videos from" sheet), so two
 * extensions with the same name from different repositories or ecosystems can be told apart:
 * `HDHub4u` `HI` / `3 sources • CloudStream • Phisher Repo`. Pure parts are unit-tested.
 */
object ExtensionLabels {
    data class Label(val title: String, val lang: String?, val line: String)

    fun build(title: String, language: String?, sources: Int?, ecosystem: String?, repo: String?, extra: List<String> = emptyList()): Label {
        val parts = buildList {
            sources?.takeIf { it > 0 }?.let { add(if (it == 1) "1 source" else "$it sources") }
            ecosystem?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            repo?.trim()?.takeIf { it.isNotEmpty() && !it.equals(ecosystem?.trim(), true) }?.let(::add)
            extra.map { it.trim() }.filter { it.isNotEmpty() }.forEach(::add)
        }
        return Label(title.trim(), langCode(language), parts.joinToString(" • "))
    }

    /** "hi", "en-US", "pt_BR" → HI, EN, PT; "multi"/"mul" → MULTI; anything else → null. */
    fun langCode(language: String?): String? {
        val l = language?.trim()?.lowercase().orEmpty()
        if (l == "mul" || l == "multi" || l == "all") return "MULTI"
        val base = l.substringBefore('-').substringBefore('_')
        return base.takeIf { it.length in 2..3 && it.all { c -> c in 'a'..'z' } }?.uppercase()
    }

    /** Label for a provider shown by name (filter sheet): its extension's language, source count and repo. */
    fun forApi(api: MainAPI): Label {
        val ext = runCatching { ExtensionManager.extensionOf(api) }.getOrNull()
        val count = ext?.let { e -> runCatching { ExtensionManager.state.value.info(e.key).providers.size }.getOrNull() }
        val eco = runCatching { SourceHub.ecosystemOf(api).label }.getOrNull()
        val lang = runCatching { api.lang }.getOrNull()?.takeIf { it.isNotBlank() } ?: ext?.language
        return build(api.name, lang, count, eco, ext?.repoName)
    }
}
