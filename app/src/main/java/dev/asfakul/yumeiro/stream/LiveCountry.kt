/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import java.util.Locale

/**
 * Live TV country filter (2.1.0). A channel's country comes from its `tvg-country` attribute (first of an
 * `A;B` list) or, when that is missing, from the iptv-org `tvg-id` form "Name.cc@Feed" ("BTV.bd@SD" → BD).
 * Only two-letter ISO 3166 codes count; "INT", "UNDEFINED" and the like are ignored. "UK" is read as GB.
 */
object LiveCountry {
    private val ID_SUFFIX = Regex("""\.([A-Za-z]{2})(?:@.*)?$""")

    fun of(tvgCountry: String?, tvgId: String?): String? {
        tvgCountry?.split(';', ',')?.firstOrNull()?.trim()?.let { code(it) }?.let { return it }
        val m = tvgId?.trim()?.let { ID_SUFFIX.find(it) } ?: return null
        return code(m.groupValues[1])
    }

    private fun code(raw: String): String? {
        val c = raw.uppercase(Locale.ROOT)
        if (c.length != 2 || !c.all { it in 'A'..'Z' }) return null
        val iso = if (c == "UK") "GB" else c
        return iso.takeIf { it in ISO }
    }

    private val ISO: Set<String> by lazy { Locale.getISOCountries().toHashSet() }

    /** "BD" → "Bangladesh" in the device language; the code itself when the name is unknown. */
    fun name(code: String, locale: Locale = Locale.getDefault()): String =
        Locale("", code).getDisplayCountry(locale).takeIf { it.isNotBlank() && it != code } ?: code
}
