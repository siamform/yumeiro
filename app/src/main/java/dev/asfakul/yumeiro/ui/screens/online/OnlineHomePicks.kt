/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineRow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Popular online" rows on the Home tab: the first rows of the enabled extensions. Renders nothing
 * when no extension is installed or none has a home page, so Home stays exactly as before.
 */
@Composable
fun OnlineHomePicks(
    onOpenTitle: (OnlineItem) -> Unit,
    onSeeAll: () -> Unit,
    header: @Composable (title: String, onSeeAll: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val engine by ExtensionManager.state.collectAsState()
    LaunchedEffect(Unit) { ExtensionManager.start(context) }
    val adultOn by dev.asfakul.yumeiro.online.OnlineSettings.adult.collectAsState()
    val adultPrivate by dev.asfakul.yumeiro.online.OnlineSettings.adultPrivate.collectAsState()
    val providers = remember(engine.generation, engine.ready, adultOn, adultPrivate) {
        if (engine.ready) ExtensionManager.providers().filter { it.hasMainPage }
            .filterNot { (!adultOn || adultPrivate) && dev.asfakul.yumeiro.online.AdultContent.isAdultApi(it.name) }.take(3) else emptyList()
    }
    var rows by remember { mutableStateOf<List<OnlineRow>?>(null) }
    LaunchedEffect(providers) {
        if (providers.isEmpty()) { rows = emptyList(); return@LaunchedEffect }
        val out = mutableListOf<OnlineRow>()
        for (api in providers) {
            val home = withTimeoutOrNull(30_000) { runCatching { OnlineRepository.home(api) }.getOrNull() }.orEmpty()
            home.firstOrNull { it.items.isNotEmpty() }?.let { out += it }
            rows = out.toList()
        }
        rows = out.toList()
    }
    val visible = rows
    LaunchedEffect(Unit) { OnlineHistory.ensureLoaded(context) }
    val historyAll by OnlineHistory.entries.collectAsState()
    val history = remember(historyAll, adultOn, adultPrivate) { OnlineHistory.forFeed(historyAll, adultOn, adultPrivate) }
    val resume = remember(history, providers) { history.filter { h -> providers.any { it.name == h.api } || ExtensionManager.provider(h.api) != null }.take(6) }
    if (resume.isEmpty() && (providers.isEmpty() || (visible != null && visible.isEmpty()))) return
    Column(modifier.fillMaxWidth().testTag("home_online_picks"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (resume.isNotEmpty()) {
            header("Continue watching", onSeeAll)
            PosterGrid(resume.map { it.toItem() }, onClick = onOpenTitle, rows = 1, showSource = true, padding = 0.dp,
                progress = { item -> resume.firstOrNull { it.url == item.url && it.api == item.api }?.fraction })
        }
        if (visible == null) { if (providers.isNotEmpty()) PosterGridSkeleton(rows = 1) }
        else visible.forEach { row ->
            header(if (visible.size == 1) "Popular online" else "${row.title} · ${row.api}", onSeeAll)
            PosterGrid(row.items, onClick = onOpenTitle, horizontal = row.horizontal, rows = 2, padding = 0.dp)
        }
    }
}
