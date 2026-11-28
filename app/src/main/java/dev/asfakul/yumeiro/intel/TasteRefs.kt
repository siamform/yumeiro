/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel

import dev.asfakul.yumeiro.online.OnlineDetail

/** B20: one place that turns each kind of title into a [TasteSignals.Ref] (same key from every screen). Pure. */
object TasteRefs {
    fun onlineKey(api: String, url: String) = "o|$api|$url"
    fun mangaKey(provider: String, url: String) = "m|$provider|$url"
    fun novelKey(plugin: String, path: String) = "n|$plugin|$path"

    /** An online title page; null for live TV and pages with no category. */
    fun online(d: OnlineDetail): TasteSignals.Ref? {
        val cat = MediaCategory.of(d) ?: return null
        val catalog = dev.asfakul.yumeiro.sources.hub.YumeiroCatalog.refOf(d.url)
        return TasteSignals.Ref(
            key = onlineKey(d.api, d.url), category = cat, title = d.name, cover = d.posterUrl, year = d.year,
            ids = TasteSignals.Ids(
                anilist = d.aniListId, mal = d.malId, imdb = d.imdbId?.takeIf { it.startsWith("tt") },
                tmdb = d.tmdbId ?: catalogTmdb(catalog), tmdbKind = catalogKind(catalog),
            ),
            sourceTags = d.tags, adult = dev.asfakul.yumeiro.online.AdultContent.isAdult(d.api, d.type),
        )
    }

    /** A history / My List row (no title page loaded): category from the source's type. */
    fun online(api: String, url: String, name: String, cover: String?, type: String?): TasteSignals.Ref? {
        val cat = MediaCategory.ofType(type) ?: return null
        return TasteSignals.Ref(onlineKey(api, url), cat, name, cover, adult = dev.asfakul.yumeiro.online.AdultContent.isAdult(api, type))
    }

    fun manga(provider: String, url: String, title: String, cover: String?, genres: List<String> = emptyList()) =
        TasteSignals.Ref(mangaKey(provider, url), MediaCategory.MANGA, title, cover, sourceTags = genres)

    fun novel(plugin: String, path: String, name: String, cover: String?, genres: List<String> = emptyList()) =
        TasteSignals.Ref(novelKey(plugin, path), MediaCategory.NOVEL, name, cover, sourceTags = genres)

    private fun catalogTmdb(ref: dev.asfakul.yumeiro.sources.hub.YumeiroCatalog.Ref?): Int? = ref?.id?.toInt()
    private fun catalogKind(ref: dev.asfakul.yumeiro.sources.hub.YumeiroCatalog.Ref?): String? = ref?.kind
}
