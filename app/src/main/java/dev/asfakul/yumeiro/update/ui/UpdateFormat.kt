/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.update.ui

import dev.asfakul.yumeiro.update.UpdateState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal sealed interface NoteLine {
    data class Heading(val text: String) : NoteLine
    data class Bullet(val text: String) : NoteLine
    data class Paragraph(val text: String) : NoteLine
}

private val LINK = Regex("""\[([^\]]+)]\([^)]+\)""")
private val COMMIT_SUFFIX = Regex("""\s*\(([0-9a-f]{7,40})\)\s*(—.*)?$""")
private val HTML_COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

/** Very small Markdown subset parser for release notes. */
internal fun parseNotes(markdown: String): List<NoteLine> =
    markdown.replace(HTML_COMMENT, "")
        .lines()
        .map { it.trim() }
        .filter {
            it.isNotEmpty() && !it.startsWith("---") && !it.startsWith("**Full Changelog") &&
                !it.startsWith("**Install") && !it.startsWith("**SHA-256") &&
                !Regex("""^#+\s*Yumeiro v""").containsMatchIn(it)
        }
        .map { raw ->
            val clean = { s: String -> s.replace(LINK, "$1").replace("**", "").replace("`", "").trim() }
            when {
                raw.startsWith("#") -> NoteLine.Heading(clean(raw.trimStart('#')))
                raw.startsWith("- ") || raw.startsWith("* ") ->
                    NoteLine.Bullet(clean(raw.substring(2)).replace(COMMIT_SUFFIX, ""))
                else -> NoteLine.Paragraph(clean(raw))
            }
        }

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

internal fun eta(s: UpdateState.Downloading): String {
    if (s.bytesPerSecond <= 0 || s.totalBytes <= 0) return "calculating…"
    val secs = ((s.totalBytes - s.downloadedBytes) / s.bytesPerSecond).coerceAtLeast(0)
    return if (secs >= 60) "${secs / 60} min ${secs % 60} s left" else "$secs s left"
}

internal fun formatDate(iso: String): String = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(parser.parse(iso)!!)
}.getOrDefault("Latest")

internal fun formatRelative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    if (epochMillis <= 0) return "Never"
    val diff = (now - epochMillis) / 1000
    return when {
        diff < 60 -> "Just now"
        diff < 3600 -> "${diff / 60} min ago"
        diff < 86_400 -> "${diff / 3600} h ago"
        else -> "${diff / 86_400} d ago"
    }
}
