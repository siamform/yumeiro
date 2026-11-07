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

    /** True when 18+ items must stay out of feeds (18+ off, or on but private). */
    fun hideInFeeds(): Boolean = !OnlineSettings.adult.value || OnlineSettings.adultPrivate.value
}
