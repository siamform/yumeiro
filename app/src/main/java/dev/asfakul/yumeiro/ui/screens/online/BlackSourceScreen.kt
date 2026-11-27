/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.blacksource.BlackSource
import dev.asfakul.yumeiro.ui.components.DialogConfirmButton
import dev.asfakul.yumeiro.ui.components.SubPageHeader
import dev.asfakul.yumeiro.ui.components.TsukiDialog
import dev.asfakul.yumeiro.ui.theme.DarkOutline
import dev.asfakul.yumeiro.ui.theme.GlassFill
import dev.asfakul.yumeiro.ui.theme.OnAccent
import dev.asfakul.yumeiro.ui.theme.SpotifyGreen
import dev.asfakul.yumeiro.ui.theme.TextPrimary
import dev.asfakul.yumeiro.ui.theme.TextSecondary
import dev.asfakul.yumeiro.ui.theme.TextTertiary
import dev.asfakul.yumeiro.ui.theme.yuRound
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private sealed interface BsPhase {
    data object Idle : BsPhase
    data object Loading : BsPhase
    data class Error(val busy: Boolean) : BsPhase
    data class Done(val titles: List<BlackSource.Title>) : BsPhase
}

/**
 * F14 BlackSource: genre / tags / demographic search (AniList through Yumeiro Cloud), then the titles in the user's
 * own sources. Opt-in with an alert about the extra time; "Turn off" in the header undoes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlackSourceScreen(
    onBack: () -> Unit,
    onOpenTitle: (OnlineItem) -> Unit,
    onSearchWords: (String) -> Unit,
    providers: () -> List<MainAPI>,
) {
    val context = LocalContext.current
    var optedIn by remember { mutableStateOf(BlackSource.optedIn(context)) }
    var words by rememberSaveable { mutableStateOf("") }
    var tagsText by rememberSaveable { mutableStateOf("") }
    var genres by rememberSaveable { mutableStateOf(listOf<String>()) }
    var demographic by rememberSaveable { mutableStateOf<String?>(null) }
    // B9: advanced filters (sent to the Worker, which checks them against its own lists).
    var without by rememberSaveable { mutableStateOf(listOf<String>()) }
    var formats by rememberSaveable { mutableStateOf(listOf<String>()) }
    var statuses by rememberSaveable { mutableStateOf(listOf<String>()) }
    var yearFrom by rememberSaveable { mutableStateOf("") }
    var yearTo by rememberSaveable { mutableStateOf("") }
    var minScore by rememberSaveable { mutableStateOf<Int?>(null) }
    var sort by rememberSaveable { mutableStateOf("popular") }
    var more by rememberSaveable { mutableStateOf(false) }
    var onlyFound by rememberSaveable { mutableStateOf(false) }
    // B9: which sources are asked, across every ecosystem; kept between visits.
    var off by remember { mutableStateOf(BlackSource.sourcesOff(context)) }
    var sourcesSheet by remember { mutableStateOf(false) }
    val all = providers()
    val asked = BlackSource.chosen(all, off) { it.name }
    var phase by remember { mutableStateOf<BsPhase>(BsPhase.Idle) }
    val hits = remember { mutableStateListOf<BlackSource.SourceHit>() }
    var progress by remember { mutableStateOf(0 to 0) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    val list = rememberLazyListState()
    val focus = LocalFocusManager.current

    val filter = BlackSource.Filter(
        words = words, genres = genres.toSet(), demographic = demographic,
        tags = tagsText.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        without = without.toSet(), formats = formats.toSet(), statuses = statuses.toSet(),
        yearFrom = yearFrom.toIntOrNull(), yearTo = yearTo.toIntOrNull(), minScore = minScore, sort = sort,
    ).cleaned()

    fun start() {
        if (filter.empty) return
        job?.cancel(); hits.clear(); progress = 0 to 0
        focus.clearFocus()
        val f = filter
        job = scope.launch {
            phase = BsPhase.Loading
            val page = runCatching { BlackSource.search(f) }.getOrElse { e ->
                phase = BsPhase.Error(busy = (e as? BlackSource.Failure)?.message == "busy"); return@launch
            }
            phase = BsPhase.Done(page.items)
            checking = true
            runCatching {
                BlackSource.findInSources(page.items, f, BlackSource.chosen(providers(), off) { it.name },
                    onHit = { h -> scope.launch { if (hits.none { it.item.key == h.item.key }) hits += h } },
                    onProgress = { d, t -> progress = d to t })
            }
            checking = false
        }
    }

    // The filters fill most of a phone screen: once AniList answers, bring the answer into view (item 1 = status line).
    androidx.compose.runtime.LaunchedEffect(phase) { if (phase is BsPhase.Done) list.animateScrollToItem(1) }

    if (!optedIn) {
        TsukiDialog(
            onDismissRequest = onBack,
            title = { Text(stringResource(R.string.bs_optin_title)) },
            text = { Text(stringResource(R.string.bs_optin_text), modifier = Modifier.testTag("blacksource_optin")) },
            confirmButton = {
                DialogConfirmButton(stringResource(R.string.bs_optin_accept), onClick = { BlackSource.setOptedIn(context, true); optedIn = true },
                    modifier = Modifier.testTag("blacksource_optin_accept"))
            },
            dismissButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.bs_optin_decline)) } },
        )
    }

    if (sourcesSheet) BsSourcesSheet(all, off, onChange = { off = it; BlackSource.setSourcesOff(context, it) }, onDismiss = { sourcesSheet = false })

    Column(Modifier.fillMaxSize().imePadding().testTag("blacksource_screen")) {
        SubPageHeader(
            title = stringResource(R.string.bs_title), onBack = onBack, subtitle = stringResource(R.string.bs_subtitle),
            actions = {
                if (optedIn) TextButton(onClick = { BlackSource.setOptedIn(context, false); job?.cancel(); onBack() }, modifier = Modifier.testTag("blacksource_turn_off")) {
                    Text(stringResource(R.string.bs_turn_off), color = TextSecondary)
                }
            },
        )
        LazyColumn(Modifier.fillMaxSize().testTag("blacksource_list"), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                BsField(words, { words = it.take(80) }, stringResource(R.string.bs_words_hint), "blacksource_words", onDone = ::start)
                Spacer(Modifier.height(12.dp))
                BsLabel(stringResource(R.string.bs_genres))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BlackSource.GENRES.forEach { g ->
                        BsChip(g, g in genres, "bs_genre_$g") { genres = if (g in genres) genres - g else (genres + g).takeLast(4); without = without - g }
                    }
                }
                Spacer(Modifier.height(12.dp))
                BsLabel(stringResource(R.string.bs_demographic))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BlackSource.DEMOGRAPHICS.forEach { d -> BsChip(d, demographic == d, "bs_demo_$d") { demographic = if (demographic == d) null else d } }
                }
                Spacer(Modifier.height(12.dp))
                BsField(tagsText, { tagsText = it.take(200) }, stringResource(R.string.bs_tags_hint), "blacksource_tags", onDone = ::start)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { more = !more }, modifier = Modifier.testTag("bs_more")) {
                    Text(stringResource(if (more) R.string.bs9_fewer else R.string.bs9_more), color = SpotifyGreen)
                }
                if (more) BsAdvanced(
                    without, { g -> without = if (g in without) without - g else (without + g).takeLast(BlackSource.MAX_GENRES); genres = genres - g },
                    formats, { x -> formats = if (x in formats) formats - x else formats + x },
                    statuses, { x -> statuses = if (x in statuses) statuses - x else statuses + x },
                    yearFrom, { yearFrom = it.filter(Char::isDigit).take(4) }, yearTo, { yearTo = it.filter(Char::isDigit).take(4) },
                    minScore, { s -> minScore = if (minScore == s) null else s }, sort, { sort = it },
                )
                Spacer(Modifier.height(8.dp))
                // B9: the sources asked; a tap opens the multi-select across ecosystems.
                Surface(onClick = { sourcesSheet = true }, color = GlassFill, shape = yuRound(14), modifier = Modifier.fillMaxWidth().testTag("bs_sources")) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(stringResource(R.string.bs9_sources, asked.size, all.size), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.bs9_sources_hint), color = TextSecondary, fontSize = 12.sp)
                    }
                }
                if (!filter.empty && asked.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    // The extra-time note: each title is looked up in every chosen source.
                    Text(stringResource(R.string.bs9_cost, BlackSource.lookups(12, asked.size, filter.words.isNotBlank()), asked.size),
                        color = TextTertiary, fontSize = 12.sp, modifier = Modifier.testTag("bs_cost"))
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(
                    onClick = ::start, enabled = !filter.empty && phase != BsPhase.Loading, shape = yuRound(22),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = SpotifyGreen, contentColor = OnAccent),
                    modifier = Modifier.fillMaxWidth().testTag("blacksource_search"),
                ) { Text(stringResource(R.string.bs_search), fontWeight = FontWeight.Bold) }
                if (filter.count > 0 || filter.words.isNotBlank()) TextButton(onClick = {
                    words = ""; tagsText = ""; genres = emptyList(); demographic = null; without = emptyList(); formats = emptyList()
                    statuses = emptyList(); yearFrom = ""; yearTo = ""; minScore = null; sort = "popular"
                }, modifier = Modifier.testTag("bs_clear")) { Text(stringResource(R.string.bs9_clear, filter.count), color = TextSecondary) }
                Spacer(Modifier.height(12.dp))
            }
            when (val p = phase) {
                BsPhase.Idle -> item { Text(stringResource(R.string.bs_idle), color = TextTertiary, fontSize = 13.sp) }
                BsPhase.Loading -> item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = SpotifyGreen, strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(stringResource(R.string.bs_loading), color = TextSecondary, fontSize = 13.sp)
                    }
                }
                is BsPhase.Error -> item {
                    Text(stringResource(if (p.busy) R.string.bs_error_busy else R.string.bs_error), color = TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.testTag("blacksource_error"))
                }
                is BsPhase.Done -> {
                    item {
                        val (d, t) = progress
                        val line = when {
                            p.titles.isEmpty() -> stringResource(R.string.bs_none)
                            checking && t > 0 -> stringResource(R.string.bs_checking, d, t)
                            t == 0 && !checking -> stringResource(R.string.bs_no_sources, p.titles.size)
                            else -> stringResource(R.string.bs_found, p.titles.size)
                        }
                        Text(line, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.testTag("blacksource_progress"))
                        if (p.titles.isNotEmpty() && asked.isNotEmpty()) BsChip(stringResource(R.string.bs9_only_found), onlyFound, "bs_only_found") { onlyFound = !onlyFound }
                        Spacer(Modifier.height(8.dp))
                    }
                    // B9: "Only in my sources" hides titles no source had (while checking, titles wait for their turn).
                    val shown = if (onlyFound) p.titles.filter { t -> hits.any { it.title?.id == t.id } } else p.titles
                    items(shown, key = { "t${it.id}" }) { t ->
                        BsTitleCard(t, hits.filter { it.title?.id == t.id }, finished = !checking, onOpenTitle = onOpenTitle, onSearchWords = onSearchWords)
                    }
                    val own = hits.filter { it.title == null }
                    if (own.isNotEmpty()) {
                        item { Spacer(Modifier.height(8.dp)); BsLabel(stringResource(R.string.bs_own_genres)) }
                        items(own, key = { "o${it.item.key}" }) { h ->
                            BsHitRow(h.item.name, h.item.api + " · " + h.providerTags.take(4).joinToString(", "), "blacksource_own_hit") { onOpenTitle(h.item) }
                        }
                    }
                }
            }
        }
    }
}

/** B9: leave-out genres, format, status, start years, minimum score and order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BsAdvanced(
    without: List<String>, onWithout: (String) -> Unit, formats: List<String>, onFormat: (String) -> Unit,
    statuses: List<String>, onStatus: (String) -> Unit, from: String, onFrom: (String) -> Unit, to: String, onTo: (String) -> Unit,
    score: Int?, onScore: (Int) -> Unit, sort: String, onSort: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("bs_advanced")) {
        BsLabel(stringResource(R.string.bs9_format))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BlackSource.FORMATS.forEach { x -> BsChip(bsFormatLabel(x), x in formats, "bs_format_$x") { onFormat(x) } }
        }
        Spacer(Modifier.height(12.dp))
        BsLabel(stringResource(R.string.bs9_status))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BlackSource.STATUSES.forEach { x -> BsChip(bsStatusLabel(x), x in statuses, "bs_status_$x") { onStatus(x) } }
        }
        Spacer(Modifier.height(12.dp))
        BsLabel(stringResource(R.string.bs9_years))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { BsField(from, onFrom, stringResource(R.string.bs9_year_from), "bs_year_from", number = true, onDone = {}) }
            Column(Modifier.weight(1f)) { BsField(to, onTo, stringResource(R.string.bs9_year_to), "bs_year_to", number = true, onDone = {}) }
        }
        Spacer(Modifier.height(12.dp))
        BsLabel(stringResource(R.string.bs9_score))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BlackSource.SCORES.forEach { s -> BsChip(stringResource(R.string.bs9_score_at_least, s), score == s, "bs_score_$s") { onScore(s) } }
        }
        Spacer(Modifier.height(12.dp))
        BsLabel(stringResource(R.string.bs9_without))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BlackSource.GENRES.forEach { g -> BsChip(g, g in without, "bs_without_$g") { onWithout(g) } }
        }
        Spacer(Modifier.height(12.dp))
        BsLabel(stringResource(R.string.bs9_sort))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BlackSource.SORTS.forEach { s -> BsChip(bsSortLabel(s), sort == s, "bs_sort_$s") { onSort(s) } }
        }
    }
}

@Composable
private fun bsFormatLabel(x: String) = when (x) {
    "TV" -> stringResource(R.string.bs9_f_tv); "TV_SHORT" -> stringResource(R.string.bs9_f_tv_short); "MOVIE" -> stringResource(R.string.bs9_f_movie)
    "SPECIAL" -> stringResource(R.string.bs9_f_special); "OVA" -> "OVA"; "ONA" -> "ONA"; else -> x
}

@Composable
private fun bsStatusLabel(x: String) = when (x) {
    "RELEASING" -> stringResource(R.string.bs9_s_airing); "FINISHED" -> stringResource(R.string.bs9_s_finished)
    "NOT_YET_RELEASED" -> stringResource(R.string.bs9_s_upcoming); "HIATUS" -> stringResource(R.string.bs9_s_hiatus); else -> x
}

@Composable
private fun bsSortLabel(s: String) = when (s) {
    "score" -> stringResource(R.string.bs9_sort_score); "trending" -> stringResource(R.string.bs9_sort_trending)
    "newest" -> stringResource(R.string.bs9_sort_newest); else -> stringResource(R.string.bs9_sort_popular)
}

/** B9: every installed source grouped by ecosystem; a group switch turns its sources on or off together. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BsSourcesSheet(all: List<MainAPI>, off: Set<String>, onChange: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val p = dev.asfakul.yumeiro.ui.theme.LocalMtPalette.current
    val groups = remember(all) { all.groupBy { dev.asfakul.yumeiro.sources.SourceHub.ecosystemOf(it).label }.toSortedMap() }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.surfaceContainerHigh, dragHandle = dev.asfakul.yumeiro.ui.components.sheetDragHandle(), modifier = Modifier.testTag("bs_sources_sheet"),
    ) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Text(stringResource(R.string.bs9_sources_title), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(stringResource(R.string.bs9_sources_note), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                if (all.isEmpty()) Text(stringResource(R.string.bs9_sources_none), color = TextTertiary, fontSize = 13.sp)
            }
            groups.forEach { (eco, apis) ->
                val names = apis.map { it.name }
                val on = names.count { it !in off }
                item(key = "g_$eco") {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.bs9_group, eco, on, names.size), color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(checked = on == names.size, onCheckedChange = { onChange(BlackSource.toggleGroup(names, off)) },
                            modifier = Modifier.testTag("bs_eco_$eco"))
                    }
                }
                items(apis, key = { "s_" + eco + "_" + it.name }) { api ->
                    val checked = api.name !in off
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = { onChange(if (checked) off + api.name else off - api.name) },
                            modifier = Modifier.testTag("bs_src_${api.name}"))
                        Text(api.name, color = TextPrimary, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(api.lang, color = TextTertiary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BsLabel(t: String) = Text(t, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))

@Composable
private fun BsChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) = FilterChip(
    selected = selected, onClick = onClick, label = { Text(label, maxLines = 1, fontSize = 13.sp) },
    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SpotifyGreen, selectedLabelColor = OnAccent, labelColor = TextPrimary),
    modifier = Modifier.testTag(tag),
)

@Composable
private fun BsField(value: String, onChange: (String) -> Unit, hint: String, tag: String, number: Boolean = false, onDone: () -> Unit) = OutlinedTextField(
    value = value, onValueChange = onChange, singleLine = true, shape = yuRound(22),
    placeholder = { Text(hint, color = TextTertiary, fontSize = 13.sp) },
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = if (number) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text),
    keyboardActions = KeyboardActions(onSearch = { onDone() }),
    colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SpotifyGreen, unfocusedBorderColor = DarkOutline, focusedContainerColor = GlassFill, unfocusedContainerColor = GlassFill,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    ),
    modifier = Modifier.fillMaxWidth().testTag(tag),
)

@Composable
private fun BsTitleCard(
    t: BlackSource.Title, hits: List<BlackSource.SourceHit>, finished: Boolean,
    onOpenTitle: (OnlineItem) -> Unit, onSearchWords: (String) -> Unit,
) {
    Surface(color = GlassFill, shape = yuRound(18), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("blacksource_title")) {
        Column(Modifier.padding(12.dp)) {
            Text(t.english ?: t.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2)
            val facts = listOfNotNull(t.year?.toString(), t.format, t.episodes?.let { stringResource(R.string.bs_episodes, it) }, t.demographic)
            if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = TextSecondary, fontSize = 12.sp)
            // B9: status and AniList score, so the advanced filters can be seen at work.
            val more = listOfNotNull(t.status?.let { bsStatusLabel(it) }, t.score?.let { stringResource(R.string.bs9_score_of, it) })
            if (more.isNotEmpty()) Text(more.joinToString(" · "), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.testTag("bs_title_more"))
            if (t.genres.isNotEmpty() || t.tags.isNotEmpty()) Text((t.genres + t.tags.take(4)).joinToString(", "), color = TextTertiary, fontSize = 12.sp, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            hits.forEach { h -> BsHitRow(h.item.name, h.item.api, "blacksource_hit") { onOpenTitle(h.item) } }
            if (hits.isEmpty() && finished) {
                TextButton(onClick = { onSearchWords(t.english ?: t.title) }, modifier = Modifier.testTag("blacksource_search_title")) {
                    Text(stringResource(R.string.bs_search_sources), color = SpotifyGreen)
                }
            }
        }
    }
}

@Composable
private fun BsHitRow(name: String, detail: String, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = SpotifyGreen.copy(alpha = 0.12f), shape = yuRound(12), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag(tag)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(name, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
            Text(detail, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
        }
    }
}
