/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.TvType

/** Tells 18+ extensions and titles apart, so their activity can be kept private. */
object AdultContent {
    fun isAdultType(type: String?): Boolean = type != null && (type.equals("NSFW", true) || type == "18+")

    /** An extension that only serves 18+ content (CloudStream marks these with TvType.NSFW). */
    fun isAdultApi(api: String): Boolean = runCatching {
        val p = ExtensionManager.provider(api) ?: return false
        val types = p.supportedTypes
        (types.isNotEmpty() && types.all { it == TvType.NSFW }) || ExtensionManager.extensionOf(p)?.let { ext -> ext.tvTypes.any { it.equals("NSFW", true) } } == true
    }.getOrDefault(false)

    fun isAdult(api: String, type: String?): Boolean = isAdultType(type) || isAdultApi(api)

    /**
     * NBUG-11 (ADR-023): every loaded extension → is it 18+-only. One pass over the loaded list, so a
     * screen does not rebuild the provider list once per title. Missing name = not loaded.
     */
    fun loadedAdultApis(): Map<String, Boolean> = runCatching {
        ExtensionManager.providers().associate { p ->
            val types = p.supportedTypes
            p.name to ((types.isNotEmpty() && types.all { it == TvType.NSFW }) ||
                ExtensionManager.extensionOf(p)?.let { ext -> ext.tvTypes.any { it.equals("NSFW", true) } } == true)
        }
    }.getOrDefault(emptyMap())

    /**
     * NBUG-11 (ADR-023): what is known about an online title without a stored 18+ mark: its extension
     * if loaded ([loaded]), else Continue watching's mark for the same title; null = unknown.
     */
    fun knownAdult(api: String?, url: String?, loaded: Map<String, Boolean>): Boolean? {
        if (api.isNullOrBlank()) return null
        loaded[api]?.let { return it }
        return if (OnlineHistory.entries.value.any { it.api == api && it.url == url && OnlineHistory.isAdult(it) }) true else null
    }

    /**
     * NBUG-11 (ADR-023): true if a counted online play must stay out of feeds while 18+ is hidden.
     * Stored mark first (an extension now known as 18+-only still wins); unknown stays out (fail closed).
     */
    fun hidePlay(adult: Boolean?, api: String?, url: String?, loaded: Map<String, Boolean>): Boolean {
        if (adult == true) return true
        if (!api.isNullOrBlank() && loaded[api] == true) return true
        if (adult == false) return false
        return knownAdult(api, url, loaded) ?: true
    }

    /** True when 18+ items must stay out of feeds (18+ off, or on but private). */
    fun hideInFeeds(): Boolean = !OnlineSettings.adult.value || OnlineSettings.adultPrivate.value
}
