/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources

import com.lagradost.cloudstream3.MainAPI
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.sources.manga.MangaProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One registry for every source that is not a CloudStream `.cs3` plugin. Each bridge (Aniyomi,
 * Mihon, Stremio, JavaScript, home servers) publishes its sources under its own key:
 *
 *  - video sources as CloudStream [MainAPI]s, so Home, Search, details, player, downloads,
 *    bookmarks and history work for them unchanged;
 *  - manga / comic sources as [MangaProvider]s for the reader.
 *
 * Bridges never see each other; one failing bridge only empties its own key.
 */
object SourceHub {
    /** Which ecosystem a source comes from. Shown as a small badge. */
    enum class Ecosystem(val label: String) {
        CLOUDSTREAM("CloudStream"), ANIYOMI("Aniyomi"), MIHON("Mihon"), STREMIO("Stremio"),
        JAVASCRIPT("JavaScript"), JELLYFIN("Jellyfin"), EMBY("Emby"), PLEX("Plex"), DLNA("DLNA"),
        SMB("SMB share"), WEBDAV("WebDAV"), YUMEIRO("Yumeiro"),
    }

    data class State(
        val video: Map<String, List<MainAPI>> = emptyMap(),
        val manga: Map<String, List<MangaProvider>> = emptyMap(),
        val generation: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Ecosystem of every bridged MainAPI, by provider name. */
    private val ecosystems = java.util.concurrent.ConcurrentHashMap<String, Ecosystem>()

    fun publishVideo(bridge: String, ecosystem: Ecosystem, apis: List<MainAPI>) {
        apis.forEach { ecosystems[it.name] = ecosystem }
        _state.update { it.copy(video = it.video + (bridge to apis), generation = it.generation + 1) }
        runCatching { ExtensionManager.bumpGeneration() }
    }

    fun publishManga(bridge: String, providers: List<MangaProvider>) {
        _state.update { it.copy(manga = it.manga + (bridge to providers), generation = it.generation + 1) }
    }

    fun remove(bridge: String) {
        _state.update { it.copy(video = it.video - bridge, manga = it.manga - bridge, generation = it.generation + 1) }
        runCatching { ExtensionManager.bumpGeneration() }
    }

    fun providers(): List<MainAPI> = _state.value.video.values.flatten()

    fun mangaProviders(): List<MangaProvider> = _state.value.manga.values.flatten().distinctBy { it.id }

    fun manga(id: String): MangaProvider? = mangaProviders().firstOrNull { it.id == id }

    /** Ecosystem of [api]; CloudStream when it isn't bridged. */
    fun ecosystemOf(api: MainAPI): Ecosystem = ecosystems[api.name] ?: Ecosystem.CLOUDSTREAM

    /** Unique provider name: adds the language or a counter when two sources share a name. */
    fun uniqueName(base: String, lang: String?, taken: Set<String>): String {
        if (base !in taken) return base
        val withLang = if (!lang.isNullOrBlank()) "$base (${lang.uppercase()})" else base
        if (withLang !in taken) return withLang
        var i = 2
        while ("$withLang $i" in taken) i++
        return "$withLang $i"
    }
}
