/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

/**
 * F-01 / F-16: the Dub or Sub chip on a poster was tapped; the detail page that opens next for that title starts on
 * the tapped version (one shot, in memory only). Titles without that version keep the normal default.
 */
object VersionHint {
    @Volatile private var pending: Pair<String, String>? = null

    /** "dub" / "sub" for a chip label, null when the chip is not about Sub / Dub (a language, Dual, Multi). */
    fun wantFor(label: String): String? = when {
        label.contains("dub", ignoreCase = true) -> "dub"
        label.contains("sub", ignoreCase = true) -> "sub"
        else -> null
    }

    fun set(url: String, want: String) { pending = url to want }

    /** The hint for [url], cleared once read; another title's hint is left alone. */
    @Synchronized fun take(url: String): String? {
        val p = pending ?: return null
        if (p.first != url) return null
        pending = null
        return p.second
    }

    fun pick(versions: List<String>, want: String?, fallback: String): String =
        want?.let { w -> versions.firstOrNull { it.contains(w, ignoreCase = true) } } ?: fallback
}
