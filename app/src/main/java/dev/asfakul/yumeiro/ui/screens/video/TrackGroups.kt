/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary

/**
 * Round 12: audio and subtitle tracks grouped by language. Only the language that's playing is
 * open; the others fold, and a group shows [PAGE] tracks at a time with "Show more", so a stream
 * with 60 subtitle tracks doesn't build 60 rows at once.
 */
object TrackGroups {
    const val PAGE = 10
    /** Lists this short (or with one language) are shown flat, without headers. */
    const val GROUP_FROM = 6

    data class Group(val key: String, val label: String, val indices: List<Int>)

    fun languageName(code: String?): String? = code?.takeIf { it.isNotBlank() && it != "und" }?.let { c ->
        runCatching { java.util.Locale.forLanguageTag(c).getDisplayLanguage(java.util.Locale.ENGLISH) }.getOrNull()
            ?.takeIf { it.isNotBlank() && !it.equals(c, true) } ?: c.uppercase()
    }

    /** Groups by language name, in first-seen order; unknown languages go last as "Other". Pure. */
    fun group(languages: List<String?>): List<Group> {
        val byKey = LinkedHashMap<String, MutableList<Int>>()
        languages.forEachIndexed { i, l -> byKey.getOrPut(languageName(l) ?: "") { ArrayList() }.add(i) }
        val out = byKey.filterKeys { it.isNotEmpty() }.map { (k, v) -> Group(k, k, v) }
        return out + listOfNotNull(byKey[""]?.let { Group("", if (out.isEmpty()) "Tracks" else "Other", it) })
    }

    fun shouldGroup(groups: List<Group>, total: Int) = groups.size > 1 && total >= GROUP_FROM

    /** The group that opens by itself: the one holding the playing track, else the first. */
    fun openKey(groups: List<Group>, selected: Int): String? =
        (groups.firstOrNull { selected in it.indices } ?: groups.firstOrNull())?.key

    fun visible(total: Int, playingAt: Int, pages: Int): Int =
        maxOf(PAGE * pages.coerceAtLeast(1), if (playingAt >= 0) playingAt + 1 else 0).coerceAtMost(total)
}

/**
 * Adds grouped (or flat) track rows to a LazyColumn. [toggled] holds keys the user flipped from
 * their default; [pages] counts "Show more" taps per group.
 */
fun LazyListScope.trackGroupItems(
    tag: String,
    languages: List<String?>,
    selected: Int,
    toggled: MutableCollection<String>,
    pages: MutableMap<String, Int>,
    row: @Composable (index: Int) -> Unit,
) {
    val groups = TrackGroups.group(languages)
    val grouped = TrackGroups.shouldGroup(groups, languages.size)
    val all = if (grouped) groups else listOf(TrackGroups.Group("all", "", languages.indices.toList()))
    val openKey = if (grouped) TrackGroups.openKey(groups, selected) else "all"
    all.forEach { g ->
        val open = !grouped || ((g.key == openKey) != (g.key in toggled))
        if (grouped) item(key = "${tag}_hdr_${g.key}") {
            val playing = selected in g.indices
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = if (open) "Hide ${g.label}" else "Show ${g.label}") { if (g.key in toggled) toggled.remove(g.key) else toggled.add(g.key) }
                    .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                    .padding(horizontal = 6.dp, vertical = 6.dp).testTag("${tag}_group"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Translate, null, tint = SpotifyGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(g.label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("  ${g.indices.size}" + if (playing && !open) " · playing" else "", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
        if (!open) return@forEach
        val shown = TrackGroups.visible(g.indices.size, g.indices.indexOf(selected), pages[g.key] ?: 1)
        g.indices.take(shown).forEach { i -> item(key = "${tag}_$i") { row(i) } }
        val more = g.indices.size - shown
        if (more > 0) item(key = "${tag}_more_${g.key}") {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                    .border(1.dp, SpotifyGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable { pages[g.key] = (pages[g.key] ?: 1) + 1 }.padding(10.dp).testTag("${tag}_more"),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ExpandMore, null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Show ${minOf(more, TrackGroups.PAGE)} more" + if (more > TrackGroups.PAGE) " of $more" else "", color = SpotifyGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
