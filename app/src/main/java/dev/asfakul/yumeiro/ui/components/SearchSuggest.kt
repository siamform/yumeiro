/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.ui.res.stringResource
import dev.asfakul.yumeiro.R
import androidx.compose.material3.minimumInteractiveComponentSize
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import java.text.Normalizer
import dev.asfakul.yumeiro.ui.theme.yuRound

// Precompiled once (R15 audit): these ran a regex compile on every call.
private val RX_SEARCHSUGGEST_HOT_1 = Regex("\\p{M}+")
private val RX_SEARCHSUGGEST_HOT_2 = Regex("[^\\p{L}\\p{N}]+")

/** One typeahead suggestion. [detail] is a short second line ("Artist", "Hue · Anime"). */
data class Suggestion(val text: String, val kind: Kind, val detail: String? = null) {
    enum class Kind(val icon: ImageVector) {
        RECENT(Icons.Default.History),
        SONG(Icons.Default.MusicNote),
        ARTIST(Icons.Default.Person),
        ALBUM(Icons.Default.Album),
        VIDEO(Icons.Default.Movie),
        TITLE(Icons.Default.AutoAwesome),
        YOUTUBE(Icons.Default.SmartDisplay),
        QUERY(Icons.Default.Search),
    }
}

/** Ranking used by every search box: recent searches and local titles that match what is typed so far. */
object SearchSuggest {
    /** Lowercase, accents removed, punctuation collapsed — so "Pokémon" matches "pokemon". */
    fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(RX_SEARCHSUGGEST_HOT_1, "")
        .lowercase()
        .replace(RX_SEARCHSUGGEST_HOT_2, " ")
        .trim()

    /**
     * Score of [candidate] for [query]: 0 = starts with it, 1 = a word starts with it, 2 = contains it,
     * 3 = every typed word appears somewhere; null = no match.
     */
    fun score(query: String, candidate: String): Int? {
        val q = fold(query).ifEmpty { return null }
        val c = fold(candidate).ifEmpty { return null }
        if (c == q) return 0
        return when {
            c.startsWith(q) -> 0
            c.split(' ').any { it.startsWith(q) } || c.contains(" $q") -> 1
            c.contains(q) -> 2
            q.split(' ').all { w -> c.split(' ').any { it.startsWith(w) } } -> 3
            else -> null
        }
    }

    /**
     * Best [limit] suggestions for [query]. Recent searches win ties, shorter text next; duplicates
     * (same text, any kind) are dropped; a candidate equal to the query is skipped.
     */
    fun rank(query: String, candidates: List<Suggestion>, limit: Int = 8): List<Suggestion> {
        val q = fold(query)
        if (q.isEmpty()) return emptyList()
        return candidates.asSequence()
            .filter { it.text.isNotBlank() && fold(it.text) != q }
            .mapNotNull { s -> score(query, s.text)?.let { it to s } }
            .sortedWith(compareBy<Pair<Int, Suggestion>>({ it.first }, { if (it.second.kind == Suggestion.Kind.RECENT) 0 else 1 }, { it.second.text.length }))
            .map { it.second }
            .distinctBy { fold(it.text) }
            .take(limit)
            .toList()
    }
}

/**
 * SEARCH-ML-1 (owner list 24): how a list of searches is kept in one preference string.
 * It used to be the searches joined by line breaks, so a search with a line break in it came back
 * as several searches and lost its breaks. Now it is a JSON array (line breaks kept); a value saved
 * the old way still loads (it can never hold a line break inside one search).
 */
object SearchHistoryCodec {
    /** Same break everywhere (a pasted "\r\n" is one break), outer spaces off, inner text untouched. */
    fun clean(q: String): String = q.replace("\r\n", "\n").replace('\r', '\n').trim()

    /** For one-line places (a pill): every line of [q], joined with " / ", so no line is cut off. */
    fun oneLine(q: String): String = clean(q).lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" / ")

    fun encode(list: List<String>): String = org.json.JSONArray().apply { list.forEach { put(it) } }.toString()

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        // encode() always writes [] or ["…; a list saved the old way may start with "[" too (a search
        // like "[oshi no ko]"), and org.json would read that leniently, so only these two are JSON.
        if (raw == "[]" || raw.startsWith("[\"")) runCatching {
            val a = org.json.JSONArray(raw)
            return (0 until a.length()).mapNotNull { i -> a.opt(i) as? String }.filter { it.isNotBlank() }
        }
        return raw.split('\n').filter { it.isNotBlank() } // saved before SEARCH-ML-1
    }

    /** [q] first, the same search (any case) dropped further down, at most [max]. */
    fun push(list: List<String>, q: String, max: Int): List<String> =
        (listOf(q) + list.filterNot { it.equals(q, true) }).take(max)
}

/** Recent searches per search box ("library", "hues", "youtube", "ytmusic"), kept on the device. */
object RecentSearches {
    private const val PREFS = "search_recent"
    private fun prefs(c: Context) = c.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(c, PREFS), Context.MODE_PRIVATE)
    fun load(c: Context, scope: String): List<String> = SearchHistoryCodec.decode(prefs(c).getString(scope, ""))
    fun add(c: Context, scope: String, q: String) {
        val t = SearchHistoryCodec.clean(q).takeIf { it.length >= 2 } ?: return
        prefs(c).edit().putString(scope, SearchHistoryCodec.encode(SearchHistoryCodec.push(load(c, scope), t, 15))).apply()
    }
    fun clear(c: Context, scope: String) = prefs(c).edit().remove(scope).apply()
    /** Forgets every recent search in every search box. */
    fun clearAll(c: Context) = prefs(c).edit().clear().apply()
    fun asSuggestions(c: Context, scope: String) = load(c, scope).map { Suggestion(it, Suggestion.Kind.RECENT) }
}

/** [text] with the part matching [query] in bold accent. */
@Composable
private fun highlighted(text: String, query: String, accent: Color): AnnotatedString = remember(text, query, accent) {
    val i = text.indexOf(query.trim(), ignoreCase = true)
    buildAnnotatedString {
        if (i < 0 || query.isBlank()) { append(text); return@buildAnnotatedString }
        append(text.substring(0, i))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(text.substring(i, i + query.trim().length)) }
        append(text.substring(i + query.trim().length))
    }
}

/**
 * Typeahead list shown under a search box while typing. Tap a row to search it; tap the arrow to
 * put it in the box and keep typing.
 */
@Composable
fun SuggestionList(
    query: String,
    suggestions: List<Suggestion>,
    onPick: (Suggestion) -> Unit,
    onFill: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "search_suggestions",
) {
    if (suggestions.isEmpty()) return
    val p = LocalMtPalette.current
    val shape = yuRound(22)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .background(p.surfaceContainerHigh.copy(alpha = 0.96f))
            .border(1.dp, p.accent.copy(alpha = 0.22f), shape)
            .padding(vertical = 6.dp)
            .testTag(tag),
    ) {
        suggestions.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(s) }.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp).testTag(tag + "_row"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(p.accent.copy(alpha = if (s.kind == Suggestion.Kind.RECENT) 0.08f else 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(s.kind.icon, null, tint = if (s.kind == Suggestion.Kind.RECENT) p.textTertiary else p.accent, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // SEARCH-ML-1: a recent search shows whole, line breaks included; other rows stay one line.
                    Text(highlighted(s.text, query, p.accent), color = p.textPrimary, fontSize = 14.sp, maxLines = if (s.kind == Suggestion.Kind.RECENT) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                    s.detail?.let { Text(it, color = p.textTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Box(
                    Modifier.minimumInteractiveComponentSize().size(36.dp).clip(CircleShape).clickable { onFill(s) }.testTag(tag + "_fill"),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.NorthWest, stringResource(R.string.fill_search_box), tint = p.textTertiary, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}
